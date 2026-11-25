import * as THREE from './vendor/three.module.min.js';
import { RoomEnvironment } from './vendor/RoomEnvironment.js';
import {
  createBrushedMetalMaps,
  createKnurlNormalMap,
  createRubberMatRoughnessMap,
} from './textures.js';
import { createPlinthAssembly, PLATTER_CENTER } from './plinth.js';
import { createVinylPlatterAssembly } from './vinyl.js';
import { computeGrooveYaw, createTonearmAssembly } from './tonearm.js';

const STANDARD_33_RPM_RAD_PER_SEC = (33.333333 * Math.PI * 2) / 60; // 33⅓ RPM (~3.49066 rad/s)

const canvas = document.getElementById('turntable-canvas');

function notifyBridge(fnName, arg) {
  try {
    if (window.AndroidBridge && typeof window.AndroidBridge[fnName] === 'function') {
      if (arg !== undefined) window.AndroidBridge[fnName](arg);
      else window.AndroidBridge[fnName]();
    }
  } catch (_) {}
}

let renderer;
try {
  renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: 'default',
  });
} catch (err) {
  notifyBridge('onWebGLFailed', String(err || 'init_error'));
  throw err;
}

renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.04;

const scene = new THREE.Scene();

// Bake lightweight Studio RoomEnvironment once & dispose generator immediately
let envRT = null;
function ensureEnvMap() {
  if (envRT) return envRT.texture;
  const pmremGenerator = new THREE.PMREMGenerator(renderer);
  pmremGenerator.compileEquirectangularShader();
  const roomEnv = new RoomEnvironment();
  envRT = pmremGenerator.fromScene(roomEnv, 0.04);
  roomEnv.dispose();
  pmremGenerator.dispose();
  return envRT.texture;
}
scene.environment = ensureEnvMap();

// --- Strict Horizontal Top-Down Orthographic Camera (~88.3 deg vertical overhead) ---
// Zero perspective warping or tilting; 3D relief & height read cleanly through lighting & cast shadows.
const camera = new THREE.OrthographicCamera(-2.4, 2.4, 2.4, -2.4, 0.1, 30);
camera.position.set(0.0, 12.0, 0.36);
camera.lookAt(new THREE.Vector3(0.0, 0.12, 0.0));

// --- Studio Lighting Rig Sculpting Horizontal Elevation & Material Contrast ---
const hemiLight = new THREE.HemisphereLight(0xffffff, 0xc8d0de, 0.72);
scene.add(hemiLight);

const keyLight = new THREE.DirectionalLight(0xfffcf8, 2.15);
keyLight.position.set(-4.0, 7.8, 3.0);
keyLight.castShadow = true;
keyLight.shadow.mapSize.set(1024, 1024);
keyLight.shadow.camera.near = 1.5;
keyLight.shadow.camera.far = 16;
keyLight.shadow.camera.left = -3.1;
keyLight.shadow.camera.right = 3.1;
keyLight.shadow.camera.top = 3.1;
keyLight.shadow.camera.bottom = -3.1;
keyLight.shadow.bias = -0.0005;
keyLight.shadow.normalBias = 0.012;
scene.add(keyLight);

const fillLight = new THREE.DirectionalLight(0xdce6f5, 0.90);
fillLight.position.set(4.2, 4.6, 3.4);
scene.add(fillLight);

const rimLight = new THREE.DirectionalLight(0xffffff, 1.30);
rimLight.position.set(2.8, 4.2, -4.2);
scene.add(rimLight);

// --- Build Modular 3D Hardware ---
const maps = {
  ...(() => {
    const b = createBrushedMetalMaps();
    return { brushedRoughness: b.roughnessMap, brushedNormal: b.normalMap };
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

// Soft live ground shadow catcher for High/Medium modes
const shadowFloor = new THREE.Mesh(
  new THREE.PlaneGeometry(8, 8),
  new THREE.ShadowMaterial({ opacity: 0.14 }),
);
shadowFloor.rotation.x = -Math.PI / 2;
shadowFloor.position.y = -0.22;
shadowFloor.receiveShadow = true;
turntableGroup.add(shadowFloor);

// --- Adaptive Quality & Mobile Power State ---
const state = {
  isPlaying: false,
  progress: 0,
  volume: 0.78,
  isDark: false,
  reducedMotion: false,
  batterySaver: false,
  requestedQuality: 'AUTO', // AUTO | HIGH | MEDIUM | LOW
  activeTier: 'HIGH',
  pausedRendering: false,
  contextLost: false,

  // Kinematics
  angularVelocity: 0,
  vinylAngle: 0,
  armYaw: tonearm.REST_YAW,
  armLift: 0.02,
  armPhase: 'PARKED',
  buttonDepress: 0,
  swapOffset: 0,
  isScratching: false,
  isDraggingKnob: false,
  needsImmediateFrame: true,
};

let minFrameIntervalMs = 0; // 0 = full VSync (60/120fps), 20 = ~50fps, 33 = 30fps

function resolveInitialAutoTier() {
  const cores = navigator.hardwareConcurrency || 4;
  const dpr = window.devicePixelRatio || 1;
  return (cores >= 6 && dpr >= 1.8) ? 'HIGH' : 'MEDIUM';
}

let currentAutoTier = resolveInitialAutoTier();
let frameAccumMs = 0;
let frameSampleCount = 0;

function applyQualityTier(tier) {
  if (state.activeTier === tier && !state.needsImmediateFrame) return;
  state.activeTier = tier;

  const nativeDpr = window.devicePixelRatio || 1;
  if (tier === 'LOW') {
    renderer.setPixelRatio(Math.min(nativeDpr, 1.0));
    renderer.shadowMap.enabled = false;
    shadowFloor.visible = false;
    scene.environment = null;
    minFrameIntervalMs = 32; // Stable ~30 FPS
  } else if (tier === 'MEDIUM') {
    renderer.setPixelRatio(Math.min(nativeDpr, 1.25));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFShadowMap;
    if (keyLight.shadow.mapSize.width !== 512) {
      keyLight.shadow.mapSize.set(512, 512);
      if (keyLight.shadow.map) {
        keyLight.shadow.map.dispose();
        keyLight.shadow.map = null;
      }
    }
    shadowFloor.visible = true;
    scene.environment = ensureEnvMap();
    minFrameIntervalMs = 19; // Target 45-60 FPS
  } else {
    // HIGH
    renderer.setPixelRatio(Math.min(nativeDpr, 1.5));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    if (keyLight.shadow.mapSize.width !== 1024) {
      keyLight.shadow.mapSize.set(1024, 1024);
      if (keyLight.shadow.map) {
        keyLight.shadow.map.dispose();
        keyLight.shadow.map = null;
      }
    }
    shadowFloor.visible = true;
    scene.environment = ensureEnvMap();
    minFrameIntervalMs = 0;
  }
  handleResize();
  state.needsImmediateFrame = true;
}

function refreshEffectiveTier() {
  if (state.batterySaver) {
    applyQualityTier('LOW');
    return;
  }
  if (state.requestedQuality === 'AUTO') {
    applyQualityTier(currentAutoTier);
  } else {
    applyQualityTier(state.requestedQuality);
  }
}

function damp(current, target, lambda, dt) {
  return current + (target - current) * (1 - Math.exp(-lambda * dt));
}

// --- Touch Raycasting (Scratch Deep-Black Vinyl, Press Start/Stop, Turn Volume Dial) ---
// Strictly horizontal turntable: NO tilt rotation drag!
const raycaster = new THREE.Raycaster();
const pointerNDC = new THREE.Vector2();
const scratchPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), -PLATTER_CENTER.y - 0.088);
const scratchHit = new THREE.Vector3();

let activePointerId = null;
let dragMode = 'NONE';
let lastScratchAngle = 0;
let lastPointerY = 0;

function updateNDC(clientX, clientY) {
  const rect = canvas.getBoundingClientRect();
  pointerNDC.x = ((clientX - rect.left) / Math.max(1, rect.width)) * 2 - 1;
  pointerNDC.y = -((clientY - rect.top) / Math.max(1, rect.height)) * 2 + 1;
}

canvas.addEventListener('pointerdown', (e) => {
  if (activePointerId !== null) return;
  activePointerId = e.pointerId;
  canvas.setPointerCapture(e.pointerId);
  lastPointerY = e.clientY;
  updateNDC(e.clientX, e.clientY);
  raycaster.setFromCamera(pointerNDC, camera);

  if (raycaster.intersectObject(plinth.startButtonGroup, true).length > 0) {
    dragMode = 'BUTTON';
    state.buttonDepress = 1.0;
    wakeLoop();
    return;
  }

  if (raycaster.intersectObject(plinth.volumeKnobGroup, true).length > 0) {
    dragMode = 'KNOB';
    state.isDraggingKnob = true;
    wakeLoop();
    return;
  }

  if (raycaster.intersectObject(platter.vinylMesh, false).length > 0) {
    if (raycaster.ray.intersectPlane(scratchPlane, scratchHit)) {
      const dx = scratchHit.x - PLATTER_CENTER.x;
      const dz = scratchHit.z - PLATTER_CENTER.z;
      const dist = Math.hypot(dx, dz);
      if (dist >= 0.22 && dist <= 1.60) {
        dragMode = 'SCRATCH';
        state.isScratching = true;
        lastScratchAngle = Math.atan2(dz, dx);
        wakeLoop();
        return;
      }
    }
  }
  dragMode = 'NONE';
});

canvas.addEventListener('pointermove', (e) => {
  if (e.pointerId !== activePointerId || dragMode === 'NONE') return;
  const dy = e.clientY - lastPointerY;
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

      state.vinylAngle -= delta;
      const seekMs = Math.round((delta / (Math.PI * 2)) * 5500);
      if (seekMs !== 0) {
        notifyBridge('onScrubDelta', seekMs);
      }
      wakeLoop();
    }
  } else if (dragMode === 'KNOB') {
    const nextVol = Math.max(0, Math.min(1, state.volume - dy / 170));
    state.volume = nextVol;
    notifyBridge('onVolumeChange', nextVol);
    wakeLoop();
  }
});

function endPointer(e) {
  if (e.pointerId !== activePointerId) return;
  if (dragMode === 'BUTTON') {
    updateNDC(e.clientX, e.clientY);
    raycaster.setFromCamera(pointerNDC, camera);
    if (raycaster.intersectObject(plinth.startButtonGroup, true).length > 0) {
      notifyBridge('onTogglePlay');
    }
  }
  state.isScratching = false;
  state.isDraggingKnob = false;
  dragMode = 'NONE';
  activePointerId = null;
  wakeLoop();
}

canvas.addEventListener('pointerup', endPointer);
canvas.addEventListener('pointercancel', endPointer);

// --- Responsive Orthographic Viewport Framing ---
function handleResize() {
  const w = Math.max(1, window.innerWidth);
  const h = Math.max(1, window.innerHeight);
  const aspect = w / h;
  const baseHalfSpan = 2.34;

  let halfH = baseHalfSpan;
  let halfW = baseHalfSpan * aspect;
  if (halfW < baseHalfSpan) {
    halfW = baseHalfSpan;
    halfH = baseHalfSpan / aspect;
  }

  camera.left = -halfW;
  camera.right = halfW;
  camera.top = halfH;
  camera.bottom = -halfH;
  camera.updateProjectionMatrix();
  renderer.setSize(w, h, false);
  state.needsImmediateFrame = true;
}
window.addEventListener('resize', () => {
  handleResize();
  wakeLoop();
});

// --- WebGL Context Loss & Restoration Handling ---
canvas.addEventListener('webglcontextlost', (e) => {
  e.preventDefault();
  state.contextLost = true;
  if (rafId !== null) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  notifyBridge('onWebGLContextLost');
});

canvas.addEventListener('webglcontextrestored', () => {
  state.contextLost = false;
  envRT = null;
  refreshEffectiveTier();
  notifyBridge('onWebGLContextRestored');
  wakeLoop();
});

// --- Demand-Driven Delta-Time Animation Loop ---
let lastTimeMs = performance.now();
let lastRenderMs = 0;
let rafId = null;

function wakeLoop() {
  state.needsImmediateFrame = true;
  if (!state.pausedRendering && !state.contextLost && rafId === null) {
    lastTimeMs = performance.now();
    rafId = requestAnimationFrame(animate);
  }
}

function animate(nowMs) {
  if (state.pausedRendering || state.contextLost) {
    rafId = null;
    return;
  }

  const elapsedSinceRender = nowMs - lastRenderMs;
  if (minFrameIntervalMs > 0 && !state.needsImmediateFrame && elapsedSinceRender < minFrameIntervalMs) {
    rafId = requestAnimationFrame(animate);
    return;
  }
  lastRenderMs = nowMs;
  state.needsImmediateFrame = false;

  const dt = Math.min(0.05, Math.max(0.001, (nowMs - lastTimeMs) / 1000));
  lastTimeMs = nowMs;

  // Rolling Auto-Quality adaptive monitor
  if (!state.batterySaver && state.requestedQuality === 'AUTO') {
    frameAccumMs += dt * 1000;
    frameSampleCount += 1;
    if (frameSampleCount >= 40) {
      const avgMs = frameAccumMs / frameSampleCount;
      frameAccumMs = 0;
      frameSampleCount = 0;
      if (avgMs > 28 && currentAutoTier === 'HIGH') {
        currentAutoTier = 'MEDIUM';
        applyQualityTier('MEDIUM');
      } else if (avgMs > 36 && currentAutoTier === 'MEDIUM') {
        currentAutoTier = 'LOW';
        applyQualityTier('LOW');
      }
    }
  }

  const targetGrooveYaw = computeGrooveYaw(state.progress);

  // --- Tonearm Multi-Stage Kinematics ---
  if (state.isPlaying || state.isScratching) {
    if (state.armPhase === 'PARKED' || state.armPhase === 'RETURNING') {
      state.armPhase = 'LIFTING_TO_PLAY';
    }
    if (state.armPhase === 'LIFTING_TO_PLAY') {
      state.armLift = damp(state.armLift, 0.145, 14, dt);
      if (state.armLift > 0.115) state.armPhase = 'SWINGING_IN';
    } else if (state.armPhase === 'SWINGING_IN') {
      state.armLift = damp(state.armLift, 0.145, 12, dt);
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 8.5, dt);
      if (Math.abs(state.armYaw - targetGrooveYaw) < 0.025) state.armPhase = 'DROPPING';
    } else if (state.armPhase === 'DROPPING' || state.armPhase === 'LIFTED_PAUSED') {
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 10, dt);
      state.armLift = damp(state.armLift, 0.0, 10, dt);
      if (state.armLift < 0.012) {
        state.armLift = 0.0;
        state.armPhase = 'TRACKING';
      }
    } else {
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 12, dt);
      state.armLift = damp(state.armLift, 0.0, 14, dt);
    }
  } else {
    if (state.progress <= 0.004 || state.progress >= 0.996) {
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
      state.armPhase = 'LIFTED_PAUSED';
      state.armLift = damp(state.armLift, 0.11, 10, dt);
      state.armYaw = damp(state.armYaw, targetGrooveYaw, 8, dt);
    }
  }

  tonearm.yawGroup.rotation.y = state.armYaw;
  tonearm.liftGroup.rotation.x = -state.armLift;
  const cueOffset = Math.min(0.035, state.armLift * 0.28);
  tonearm.cueStem.position.y = 0.075 + cueOffset;
  tonearm.cueArch.position.y = 0.145 + cueOffset;

  // --- Exact 33⅓ RPM Vinyl Platter Motor & Inertia ---
  const platterCanSpin = state.isPlaying && !state.isScratching;
  const targetSpeed = platterCanSpin
    ? (state.reducedMotion ? 0.55 : STANDARD_33_RPM_RAD_PER_SEC)
    : 0;
  const motorLambda = platterCanSpin ? 3.2 : 2.2;
  state.angularVelocity = damp(state.angularVelocity, targetSpeed, motorLambda, dt);
  if (Math.abs(state.angularVelocity) < 0.002 && targetSpeed === 0) {
    state.angularVelocity = 0;
  }
  state.vinylAngle = (state.vinylAngle - state.angularVelocity * dt) % (Math.PI * 2);
  platter.setSpinAngle(state.vinylAngle);

  // --- Vinyl Swap Elastic Slide ---
  state.swapOffset = damp(state.swapOffset, 0, 11, dt);
  if (Math.abs(state.swapOffset) < 0.0008) state.swapOffset = 0;
  platter.recordGroup.position.x = state.swapOffset;

  // --- Physical Rotary Volume Knob & Start/Stop Plunger ---
  const targetKnobAngle = THREE.MathUtils.degToRad(135 - state.volume * 270);
  plinth.volumeKnobGroup.rotation.y = damp(plinth.volumeKnobGroup.rotation.y, targetKnobAngle, 16, dt);

  const btnTargetY = (dragMode === 'BUTTON' ? -0.028 : (state.isPlaying ? -0.015 : 0.0)) + 0.17;
  plinth.startButtonGroup.position.y = damp(plinth.startButtonGroup.position.y, btnTargetY, 22, dt);
  const targetLed = state.isPlaying ? 1.25 : 0.0;
  plinth.ledMat.emissiveIntensity = damp(plinth.ledMat.emissiveIntensity, targetLed, 10, dt);

  renderer.render(scene, camera);

  // Stop unnecessary GPU/RAF ticks automatically once paused & all mechanics settled
  const armTargetYaw = (state.isPlaying || state.isScratching)
    ? targetGrooveYaw
    : ((state.progress <= 0.004 || state.progress >= 0.996) ? tonearm.REST_YAW : targetGrooveYaw);
  const armTargetLift = (state.isPlaying || state.isScratching)
    ? 0.0
    : ((state.progress <= 0.004 || state.progress >= 0.996) ? 0.02 : 0.11);

  const settled =
    !state.isPlaying &&
    !state.isScratching &&
    !state.isDraggingKnob &&
    dragMode !== 'BUTTON' &&
    state.angularVelocity === 0 &&
    state.swapOffset === 0 &&
    Math.abs(state.armYaw - armTargetYaw) < 0.001 &&
    Math.abs(state.armLift - armTargetLift) < 0.001 &&
    Math.abs(plinth.volumeKnobGroup.rotation.y - targetKnobAngle) < 0.002 &&
    Math.abs(plinth.startButtonGroup.position.y - btnTargetY) < 0.001 &&
    Math.abs(plinth.ledMat.emissiveIntensity - targetLed) < 0.01;

  if (settled) {
    rafId = null;
  } else {
    rafId = requestAnimationFrame(animate);
  }
}

refreshEffectiveTier();
wakeLoop();

// --- Public Kotlin/Compose Bridge API ---
window.plinthTurntable = {
  setState(next) {
    if (typeof next.isPlaying === 'boolean') state.isPlaying = next.isPlaying;
    if (typeof next.progress === 'number') state.progress = next.progress;
    if (typeof next.volume === 'number' && !state.isDraggingKnob) state.volume = next.volume;
    if (typeof next.reducedMotion === 'boolean') state.reducedMotion = next.reducedMotion;
    if (typeof next.batterySaver === 'boolean') state.batterySaver = next.batterySaver;
    if (typeof next.graphicsQuality === 'string') state.requestedQuality = next.graphicsQuality;

    refreshEffectiveTier();

    if (typeof next.isDark === 'boolean' && next.isDark !== state.isDark) {
      state.isDark = next.isDark;
      plinth.setTheme(next.isDark);
      platter.setTheme(next.isDark);
      tonearm.setTheme(next.isDark);
      hemiLight.intensity = next.isDark ? 0.48 : 0.72;
      keyLight.intensity = next.isDark ? 2.45 : 2.15;
      fillLight.intensity = next.isDark ? 1.05 : 0.90;
      shadowFloor.material.opacity = next.isDark ? 0.28 : 0.14;
    }
    if (next.artworkDataUri !== undefined) {
      platter.setArtwork(next.artworkDataUri, state.isDark);
    }
    wakeLoop();
  },
  triggerTrackSwap(direction) {
    if (!state.reducedMotion && !state.batterySaver) {
      state.swapOffset = (direction >= 0 ? 1 : -1) * 0.85;
    }
    wakeLoop();
  },
  setPaused(paused) {
    state.pausedRendering = !!paused;
    if (state.pausedRendering && rafId !== null) {
      cancelAnimationFrame(rafId);
      rafId = null;
    } else if (!state.pausedRendering) {
      wakeLoop();
    }
  },
  dispose() {
    if (rafId !== null) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
    plinth.dispose();
    platter.dispose();
    tonearm.dispose();
    maps.brushedRoughness.dispose();
    maps.brushedNormal.dispose();
    maps.knurlNormal.dispose();
    maps.rubberRoughness.dispose();
    if (envRT) {
      envRT.dispose();
      envRT = null;
    }
    renderer.dispose();
  },
};
