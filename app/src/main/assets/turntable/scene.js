import * as THREE from './vendor/three.module.min.js';
import { RoomEnvironment } from './vendor/RoomEnvironment.js';
import {
  createBrushedMetalMaps,
  createKnurlNormalMap,
  createRubberMatRoughnessMap,
  createVinylGrooveMaps,
} from './textures.js';
import { createPlinthAssembly, PLATTER_CENTER } from './plinth.js';
import { createVinylPlatterAssembly } from './vinyl.js';
import { computeGrooveYaw, createTonearmAssembly } from './tonearm.js';

const STANDARD_33_RPM_RAD_PER_SEC = (33.333333 * Math.PI * 2) / 60; // ~3.49066 rad/s

const canvas = document.getElementById('turntable-canvas');

// --- 1. High-DPI Hardware WebGL Renderer ---
const renderer = new THREE.WebGLRenderer({
  canvas,
  antialias: true,
  alpha: true,
  powerPreference: 'high-performance',
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2.25));
renderer.setSize(window.innerWidth, window.innerHeight, false);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.08;

// --- 2. Scene & Studio Environment Reflection Map ---
const scene = new THREE.Scene();
const pmremGenerator = new THREE.PMREMGenerator(renderer);
pmremGenerator.compileEquirectangularShader();
const roomEnv = new RoomEnvironment();
const envRT = pmremGenerator.fromScene(roomEnv, 0.04);
scene.environment = envRT.texture;
roomEnv.dispose();
pmremGenerator.dispose();

// --- 3. Studio Product Photography Camera (Subtle 3D Hero Elevation) ---
const camera = new THREE.PerspectiveCamera(
  34,
  window.innerWidth / Math.max(1, window.innerHeight),
  0.1,
  30,
);
const baseCamPos = new THREE.Vector3(0.0, 8.15, 2.48);
const baseLookAt = new THREE.Vector3(0.0, 0.14, 0.04);
camera.position.copy(baseCamPos);
camera.lookAt(baseLookAt);

// --- 4. Multi-Point Studio Photography Lighting Rig ---
const hemiLight = new THREE.HemisphereLight(0xffffff, 0xc9d2e2, 0.65);
scene.add(hemiLight);

// Primary overhead-left softbox Key Light casting real soft shadows
const keyLight = new THREE.DirectionalLight(0xfffaf2, 2.35);
keyLight.position.set(-3.4, 7.2, 2.6);
keyLight.castShadow = true;
keyLight.shadow.mapSize.set(2048, 2048);
keyLight.shadow.camera.near = 1.5;
keyLight.shadow.camera.far = 16;
keyLight.shadow.camera.left = -3.4;
keyLight.shadow.camera.right = 3.4;
keyLight.shadow.camera.top = 3.4;
keyLight.shadow.camera.bottom = -3.4;
keyLight.shadow.bias = -0.0004;
keyLight.shadow.normalBias = 0.012;
scene.add(keyLight);

// Cool Fill Light from front-right
const fillLight = new THREE.DirectionalLight(0xd9e4f5, 0.95);
fillLight.position.set(4.2, 4.0, 3.8);
scene.add(fillLight);

// Crisp Grazing Rim Light from back-right separating tonearm & vinyl bevel
const rimLight = new THREE.DirectionalLight(0xffffff, 1.45);
rimLight.position.set(2.8, 3.2, -4.4);
scene.add(rimLight);

// Subtle specular grazing spot across the vinyl grooves
const vinylHighlight = new THREE.SpotLight(0xfffdf8, 4.2, 14, Math.PI / 4, 0.65, 1.2);
vinylHighlight.position.set(-2.0, 4.4, -2.5);
vinylHighlight.target.position.copy(PLATTER_CENTER);
scene.add(vinylHighlight);
scene.add(vinylHighlight.target);

// --- 5. Build Modular 3D Turntable Hardware ---
const maps = {
  ...(() => {
    const b = createBrushedMetalMaps();
    return { brushedRoughness: b.roughnessMap, brushedNormal: b.normalMap };
  })(),
  ...(() => {
    const v = createVinylGrooveMaps();
    return { vinylNormal: v.normalMap, vinylRoughness: v.roughnessMap };
  })(),
  knurlNormal: createKnurlNormalMap(),
  rubberRoughness: createRubberMatRoughnessMap(),
};

const turntableGroup = new THREE.Group();
scene.add(turntableGroup);

const plinth = createPlinthAssembly(maps);
turntableGroup.add(plinth.group);

const platter = createVinylPlatterAssembly(maps);
turntableGroup.add(platter.root);

const tonearm = createTonearmAssembly(maps);
turntableGroup.add(tonearm.root);

// Soft ambient studio ground shadow catcher beneath the feet
const shadowFloor = new THREE.Mesh(
  new THREE.PlaneGeometry(10, 10),
  new THREE.ShadowMaterial({ opacity: 0.16 }),
);
shadowFloor.rotation.x = -Math.PI / 2;
shadowFloor.position.y = -0.296;
shadowFloor.receiveShadow = true;
turntableGroup.add(shadowFloor);

// --- 6. Mechanical & Physics State Machine ---
const state = {
  isPlaying: false,
  progress: 0,
  volume: 0.78,
  isDark: false,
  reducedMotion: false,
  pausedRendering: false,

  // Dynamic physics state
  angularVelocity: 0,
  vinylAngle: 0,
  armYaw: tonearm.REST_YAW,
  armLift: 0.02, // 0 = seated on vinyl, > 0 = lifted upward (positive lift rotates arm UP)
  armPhase: 'PARKED', // PARKED | LIFTING_TO_PLAY | SWINGING_IN | DROPPING | TRACKING | LIFTED_PAUSED | RETURNING
  buttonDepress: 0,
  swapOffset: 0,
  swapVelocity: 0,
  isScratching: false,
  isDraggingKnob: false,
  camTiltX: 0,
  camTiltY: 0,
  targetCamTiltX: 0,
  targetCamTiltY: 0,
};

function damp(current, target, lambda, dt) {
  return current + (target - current) * (1 - Math.exp(-lambda * dt));
}

// --- 7. Touch Raycasting (Scratch Vinyl, Press 3D Start/Stop, Turn 3D Volume Dial, Camera Tilt) ---
const raycaster = new THREE.Raycaster();
const pointerNDC = new THREE.Vector2();
const scratchPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -PLATTER_CENTER.y - 0.088);
const scratchHit = new THREE.Vector3();

let activePointerId = null;
let dragMode = 'NONE'; // SCRATCH | KNOB | TILT | BUTTON
let lastScratchAngle = 0;
let lastPointerY = 0;
let lastPointerX = 0;

function updateNDC(clientX, clientY) {
  const rect = canvas.getBoundingClientRect();
  pointerNDC.x = ((clientX - rect.left) / Math.max(1, rect.width)) * 2 - 1;
  pointerNDC.y = -((clientY - rect.top) / Math.max(1, rect.height)) * 2 + 1;
}

canvas.addEventListener('pointerdown', (e) => {
  if (activePointerId !== null) return;
  activePointerId = e.pointerId;
  canvas.setPointerCapture(e.pointerId);
  lastPointerX = e.clientX;
  lastPointerY = e.clientY;
  updateNDC(e.clientX, e.clientY);
  raycaster.setFromCamera(pointerNDC, camera);

  // 1. Check 3D Start/Stop Plunger Button
  if (raycaster.intersectObject(plinth.startButtonGroup, true).length > 0) {
    dragMode = 'BUTTON';
    state.buttonDepress = 1.0;
    return;
  }

  // 2. Check 3D Rotary Volume Dial
  if (raycaster.intersectObject(plinth.volumeKnobGroup, true).length > 0) {
    dragMode = 'KNOB';
    state.isDraggingKnob = true;
    return;
  }

  // 3. Check 3D Vinyl Platter for circular scratch/scrub
  if (raycaster.intersectObject(platter.vinylMesh, false).length > 0) {
    if (raycaster.ray.intersectPlane(scratchPlane, scratchHit)) {
      const dx = scratchHit.x - PLATTER_CENTER.x;
      const dz = scratchHit.z - PLATTER_CENTER.z;
      const dist = Math.hypot(dx, dz);
      if (dist >= 0.22 && dist <= 1.62) {
        dragMode = 'SCRATCH';
        state.isScratching = true;
        lastScratchAngle = Math.atan2(dz, dx);
        return;
      }
    }
  }

  // 4. Outer chassis drag = subtle luxury studio camera inspection parallax
  dragMode = 'TILT';
});

canvas.addEventListener('pointermove', (e) => {
  if (e.pointerId !== activePointerId) return;
  const dy = e.clientY - lastPointerY;
  const dx = e.clientX - lastPointerX;
  lastPointerX = e.clientX;
  lastPointerY = e.clientY;

  if (dragMode === 'SCRATCH') {
    updateNDC(e.clientX, e.clientY);
    raycaster.setFromCamera(pointerNDC, camera);
    if (raycaster.ray.intersectPlane(scratchPlane, scratchHit)) {
      const rx = scratchHit.x - PLATTER_CENTER.x;
      const rz = scratchHit.z - PLATTER_CENTER.z;
      const angle = Math.atan2(rz, rx);
      let delta = angle - lastScratchAngle;
      if (delta > Math.PI) delta -= Math.PI * 2;
      if (delta < -Math.PI) delta += Math.PI * 2;
      lastScratchAngle = angle;

      // Rotating vinyl clockwise visually = -delta around +Y
      state.vinylAngle -= delta;
      const seekMs = Math.round((delta / (Math.PI * 2)) * 5500);
      if (seekMs !== 0 && window.AndroidBridge && window.AndroidBridge.onScrubDelta) {
        window.AndroidBridge.onScrubDelta(seekMs);
      }
    }
  } else if (dragMode === 'KNOB') {
    const nextVol = Math.max(0, Math.min(1, state.volume - dy / 170));
    state.volume = nextVol;
    if (window.AndroidBridge && window.AndroidBridge.onVolumeChange) {
      window.AndroidBridge.onVolumeChange(nextVol);
    }
  } else if (dragMode === 'TILT') {
    state.targetCamTiltX = Math.max(-0.55, Math.min(0.55, state.targetCamTiltX + dx * 0.003));
    state.targetCamTiltY = Math.max(-0.45, Math.min(0.45, state.targetCamTiltY + dy * 0.003));
  }
});

function endPointer(e) {
  if (e.pointerId !== activePointerId) return;
  if (dragMode === 'BUTTON') {
    updateNDC(e.clientX, e.clientY);
    raycaster.setFromCamera(pointerNDC, camera);
    if (raycaster.intersectObject(plinth.startButtonGroup, true).length > 0) {
      if (window.AndroidBridge && window.AndroidBridge.onTogglePlay) {
        window.AndroidBridge.onTogglePlay();
      }
    }
  }
  state.isScratching = false;
  state.isDraggingKnob = false;
  state.targetCamTiltX = 0;
  state.targetCamTiltY = 0;
  dragMode = 'NONE';
  activePointerId = null;
}

canvas.addEventListener('pointerup', endPointer);
canvas.addEventListener('pointercancel', endPointer);

// --- 8. Responsive Viewport Framing ---
function handleResize() {
  const w = Math.max(1, window.innerWidth);
  const h = Math.max(1, window.innerHeight);
  camera.aspect = w / h;
  // Adapt camera distance cleanly so the full 3D Plinth never clips horizontally or vertically
  const fitFactor = Math.max(1.0, 1.05 / Math.min(1, camera.aspect));
  camera.position.set(
    baseCamPos.x,
    baseCamPos.y * fitFactor,
    baseCamPos.z * fitFactor,
  );
  camera.updateProjectionMatrix();
  renderer.setSize(w, h, false);
}
window.addEventListener('resize', handleResize);
handleResize();

// --- 9. Main Delta-Time Animation Loop ---
let lastTimeMs = performance.now();
let rafId = null;

function animate(nowMs) {
  if (state.pausedRendering) {
    rafId = null;
    return;
  }
  rafId = requestAnimationFrame(animate);

  const dt = Math.min(0.05, Math.max(0.001, (nowMs - lastTimeMs) / 1000));
  lastTimeMs = nowMs;

  const targetGrooveYaw = computeGrooveYaw(state.progress);

  // --- Mechanical Tonearm State Machine (Independent Multi-Stage Kinematics) ---
  if (state.isPlaying || state.isScratching) {
    if (state.armPhase === 'PARKED' || state.armPhase === 'RETURNING') {
      state.armPhase = 'LIFTING_TO_PLAY';
    }
    if (state.armPhase === 'LIFTING_TO_PLAY') {
      state.armLift = damp(state.armLift, 0.145, 14, dt);
      if (state.armLift > 0.115) {
        state.armPhase = 'SWINGING_IN';
      }
    } else if (state.armPhase === 'SWINGING_IN') {
      state.armLift = damp(state.armLift, 0.145, 12, dt);
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 8.5, dt);
      if (Math.abs(state.armYaw - targetGrooveYaw) < 0.025) {
        state.armPhase = 'DROPPING';
      }
    } else if (state.armPhase === 'DROPPING' || state.armPhase === 'LIFTED_PAUSED') {
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 10, dt);
      state.armLift = damp(state.armLift, 0.0, 10, dt);
      if (state.armLift < 0.012) {
        state.armLift = 0.0;
        state.armPhase = 'TRACKING';
      }
    } else {
      // TRACKING: Smoothly follow playback progress across the vinyl grooves
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 12, dt);
      state.armLift = damp(state.armLift, 0.0, 14, dt);
    }
  } else {
    // Stopped or Paused
    if (state.progress <= 0.004 || state.progress >= 0.996) {
      // End of track or reset -> lift, return to armrest, and lower onto dock
      if (state.armPhase === 'TRACKING' || state.armPhase === 'DROPPING' || state.armPhase === 'LIFTED_PAUSED') {
        state.armPhase = 'RETURNING';
      }
      if (state.armPhase === 'RETURNING') {
        const nearDock = Math.abs(state.armYaw - tonearm.REST_YAW) < 0.03;
        state.armLift = damp(state.armLift, nearDock ? 0.02 : 0.145, 10, dt);
        if (state.armLift > 0.095 || nearDock) {
          state.armYaw = damp(state.armYaw, tonearm.REST_YAW, 7.5, dt);
        }
        if (nearDock && Math.abs(state.armLift - 0.02) < 0.015) {
          state.armPhase = 'PARKED';
        }
      } else {
        state.armYaw = damp(state.armYaw, tonearm.REST_YAW, 8, dt);
        state.armLift = damp(state.armLift, 0.02, 8, dt);
      }
    } else {
      // Paused mid-track -> gently lift stylus off the groove & hold position
      state.armPhase = 'LIFTED_PAUSED';
      state.armLift = damp(state.armLift, 0.11, 10, dt);
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 8, dt);
    }
  }

  // Apply Tonearm Hierarchy Transforms
  // Positive lift rotates local +Z arm upward (-X rotation)
  tonearm.yawGroup.rotation.y = state.armYaw;
  tonearm.liftGroup.rotation.x = -state.armLift;
  // Cueing lifter rises smoothly under the arm whenever lifted
  const cueOffset = Math.min(0.035, state.armLift * 0.28);
  tonearm.cueStem.position.y = 0.075 + cueOffset;
  tonearm.cueArch.position.y = 0.145 + cueOffset;

  // --- 33⅓ RPM Platter Motor Spin-up & Inertia Coast-Down ---
  const platterCanSpin = state.isPlaying && !state.isScratching;
  const targetSpeed = platterCanSpin
    ? (state.reducedMotion ? 0.65 : STANDARD_33_RPM_RAD_PER_SEC)
    : 0;
  const motorLambda = platterCanSpin ? 3.2 : 2.1;
  state.angularVelocity = damp(state.angularVelocity, targetSpeed, motorLambda, dt);
  if (Math.abs(state.angularVelocity) < 0.002 && targetSpeed === 0) {
    state.angularVelocity = 0;
  }
  state.vinylAngle = (state.vinylAngle - state.angularVelocity * dt) % (Math.PI * 2);
  platter.spinningGroup.rotation.y = state.vinylAngle;

  // --- Vinyl Track Swap Elastic Slide ---
  state.swapOffset = damp(state.swapOffset, 0, 11, dt);
  platter.recordGroup.position.x = state.swapOffset;

  // --- Physical Rotary Volume Knob & Start/Stop Plunger Button ---
  const targetKnobAngle = THREE.MathUtils.degToRad(135 - state.volume * 270);
  plinth.volumeKnobGroup.rotation.y = damp(plinth.volumeKnobGroup.rotation.y, targetKnobAngle, 16, dt);

  const btnTargetY = (dragMode === 'BUTTON' ? -0.030 : (state.isPlaying ? -0.016 : 0.0)) + 0.18;
  plinth.startButtonGroup.position.y = damp(plinth.startButtonGroup.position.y, btnTargetY, 22, dt);
  const targetLed = state.isPlaying ? 1.35 : 0.0;
  plinth.ledMat.emissiveIntensity = damp(plinth.ledMat.emissiveIntensity, targetLed, 10, dt);

  // --- Subtle Camera Parallax Tilt ---
  state.camTiltX = damp(state.camTiltX, state.targetCamTiltX, 8, dt);
  state.camTiltY = damp(state.camTiltY, state.targetCamTiltY, 8, dt);
  turntableGroup.rotation.y = state.camTiltX * 0.25;
  turntableGroup.rotation.x = state.camTiltY * 0.18;

  renderer.render(scene, camera);
}

rafId = requestAnimationFrame(animate);

// --- 10. Public Bridge API called from Kotlin/Compose ---
window.plinthTurntable = {
  setState(next) {
    if (typeof next.isPlaying === 'boolean') state.isPlaying = next.isPlaying;
    if (typeof next.progress === 'number') state.progress = next.progress;
    if (typeof next.volume === 'number' && !state.isDraggingKnob) state.volume = next.volume;
    if (typeof next.reducedMotion === 'boolean') state.reducedMotion = next.reducedMotion;
    if (typeof next.isDark === 'boolean' && next.isDark !== state.isDark) {
      state.isDark = next.isDark;
      plinth.setTheme(next.isDark);
      platter.setTheme(next.isDark);
      tonearm.setTheme(next.isDark);
      hemiLight.intensity = next.isDark ? 0.42 : 0.65;
      keyLight.intensity = next.isDark ? 2.65 : 2.35;
      fillLight.intensity = next.isDark ? 1.15 : 0.95;
      shadowFloor.material.opacity = next.isDark ? 0.36 : 0.16;
    }
    if (next.artworkDataUri !== undefined) {
      platter.setArtwork(next.artworkDataUri, state.isDark);
    }
  },
  triggerTrackSwap(direction) {
    if (!state.reducedMotion) {
      state.swapOffset = (direction >= 0 ? 1 : -1) * 0.85;
    }
  },
  setPaused(paused) {
    state.pausedRendering = !!paused;
    if (!state.pausedRendering && rafId === null) {
      lastTimeMs = performance.now();
      rafId = requestAnimationFrame(animate);
    }
  },
};
