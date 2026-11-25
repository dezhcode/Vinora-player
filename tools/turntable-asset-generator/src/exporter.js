import * as THREE from 'three';
import { createStudioEnvironment } from './textures.js';
import {
  buildTurntableScene,
  CAMERA_HALF_SPAN,
  EFFECTIVE_LENGTH,
  KNOB_OUTER_R,
  LEFT_KNOB_POS,
  PIVOT_POS,
  PLATTER_CENTER,
  PLATTER_RIM_R,
  RIGHT_KNOB_POS,
  SPINDLE_R,
  TONEARM_INNER_DEG,
  TONEARM_KEYFRAME_ANGLES_DEG,
  TONEARM_OUTER_DEG,
  TONEARM_REST_DEG,
  VINYL_INNER_GROOVE_R,
  VINYL_LABEL_R,
  VINYL_OUTER_GROOVE_R,
  VINYL_OUTER_R,
} from './turntableModel.js';

/**
 * Self-contained Three.js Offscreen/Canvas Render Engine + Normalized Coordinate Extractor.
 * Used both by headless Playwright CLI (`generate.mjs`) and interactive browser preview (`index.html`).
 */
export function createTurntableExporter({ canvas, width = 2048, height = 2048, isDark = false }) {
  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    preserveDrawingBuffer: true,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(1);
  renderer.setSize(width, height, false);
  renderer.setClearColor(0x000000, 0);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = isDark ? 1.06 : 1.02;

  const scene = new THREE.Scene();
  const envRT = createStudioEnvironment(renderer, isDark);
  scene.environment = envRT.texture;

  // Strict 90-degree top-down OrthographicCamera (+Z down, +X right, -Z up)
  const camera = new THREE.OrthographicCamera(
    -CAMERA_HALF_SPAN,
    CAMERA_HALF_SPAN,
    CAMERA_HALF_SPAN,
    -CAMERA_HALF_SPAN,
    0.1,
    30,
  );
  camera.position.set(0, 12.0, 0);
  camera.up.set(0, 0, -1);
  camera.lookAt(0, 0, 0);
  camera.updateMatrixWorld(true);
  camera.updateProjectionMatrix();

  // Studio Three-Point Overhead Rig calibrated to IMG_20261125_171547_5581814c.jpg
  const hemiLight = new THREE.HemisphereLight(
    0xffffff,
    isDark ? 0x1b212c : 0xdad8d2,
    isDark ? 0.56 : 0.78,
  );
  scene.add(hemiLight);

  const keyLight = new THREE.DirectionalLight(0xfffdf9, isDark ? 2.15 : 1.85);
  keyLight.position.set(-3.2, 8.6, -2.6); // upper-left studio key casting gentle down-right relief shadow
  keyLight.castShadow = true;
  keyLight.shadow.mapSize.set(2048, 2048);
  keyLight.shadow.camera.near = 1.0;
  keyLight.shadow.camera.far = 16.0;
  keyLight.shadow.camera.left = -2.8;
  keyLight.shadow.camera.right = 2.8;
  keyLight.shadow.camera.top = 2.8;
  keyLight.shadow.camera.bottom = -2.8;
  keyLight.shadow.bias = -0.0003;
  keyLight.shadow.normalBias = 0.008;
  keyLight.shadow.radius = 3.5;
  scene.add(keyLight);

  const fillLight = new THREE.DirectionalLight(0xeef3fa, isDark ? 0.95 : 0.78);
  fillLight.position.set(4.5, 5.2, 3.2);
  scene.add(fillLight);

  const rimLight = new THREE.DirectionalLight(0xffffff, isDark ? 1.25 : 0.95);
  rimLight.position.set(3.2, 4.8, -4.4);
  scene.add(rimLight);

  const turntable = buildTurntableScene(isDark);
  scene.add(turntable.root);

  function worldPointToNormalized(vec3) {
    const p = vec3.clone().project(camera);
    return {
      x: Number(((p.x + 1) * 0.5).toFixed(6)),
      y: Number(((1 - p.y) * 0.5).toFixed(6)),
    };
  }

  function worldRadiusToNormalized(r) {
    return Number((r / (CAMERA_HALF_SPAN * 2)).toFixed(6));
  }

  function squareCropAround(centerNorm, radiusNorm, padFactor = 1.08) {
    const half = radiusNorm * padFactor;
    const x = Math.max(0, centerNorm.x - half);
    const y = Math.max(0, centerNorm.y - half);
    const maxW = Math.min(1 - x, half * 2);
    const maxH = Math.min(1 - y, half * 2);
    const side = Math.min(maxW, maxH);
    const cx = centerNorm.x;
    const cy = centerNorm.y;
    const cleanHalf = Math.min(half, cx, 1 - cx, cy, 1 - cy);
    return {
      x: Number((cx - cleanHalf).toFixed(6)),
      y: Number((cy - cleanHalf).toFixed(6)),
      width: Number((cleanHalf * 2).toFixed(6)),
      height: Number((cleanHalf * 2).toFixed(6)),
    };
  }

  function extractManifest() {
    turntable.showComposite(TONEARM_REST_DEG);
    scene.updateMatrixWorld(true);

    const vinylCenter = worldPointToNormalized(PLATTER_CENTER);
    const tonearmPivot = worldPointToNormalized(PIVOT_POS);
    const leftKnobCenter = worldPointToNormalized(LEFT_KNOB_POS);
    const rightKnobCenter = worldPointToNormalized(RIGHT_KNOB_POS);

    const platterRimRadius = worldRadiusToNormalized(PLATTER_RIM_R);
    const vinylOuterRadius = worldRadiusToNormalized(VINYL_OUTER_R);
    const playableOuterRadius = worldRadiusToNormalized(VINYL_OUTER_GROOVE_R);
    const playableInnerRadius = worldRadiusToNormalized(VINYL_INNER_GROOVE_R);
    const albumLabelRadius = worldRadiusToNormalized(VINYL_LABEL_R);
    const spindleRadius = worldRadiusToNormalized(SPINDLE_R);
    const knobRadius = worldRadiusToNormalized(KNOB_OUTER_R);
    const effectiveLength = worldRadiusToNormalized(EFFECTIVE_LENGTH);

    // Compute tight unified crop box covering all 10 tonearm sweep angles + counterweight + finger lift + shadow
    let armMinX = 1, armMinY = 1, armMaxX = 0, armMaxY = 0;
    const armBox = new THREE.Box3();
    for (const deg of TONEARM_KEYFRAME_ANGLES_DEG) {
      turntable.setTonearmAngleDeg(deg);
      turntable.layerGroups.tonearmMoving.updateMatrixWorld(true);
      armBox.setFromObject(turntable.layerGroups.tonearmMoving);
      const minN = worldPointToNormalized(armBox.min);
      const maxN = worldPointToNormalized(armBox.max);
      armMinX = Math.min(armMinX, minN.x, maxN.x);
      armMaxX = Math.max(armMaxX, minN.x, maxN.x);
      armMinY = Math.min(armMinY, minN.y, maxN.y);
      armMaxY = Math.max(armMaxY, minN.y, maxN.y);
    }
    // Expand gently for soft directional shadow penumbra
    const armCrop = {
      x: Number(Math.max(0, armMinX - 0.032).toFixed(6)),
      y: Number(Math.max(0, armMinY - 0.025).toFixed(6)),
      width: 0,
      height: 0,
    };
    const armRight = Math.min(1, armMaxX + 0.036);
    const armBottom = Math.min(1, armMaxY + 0.036);
    armCrop.width = Number((armRight - armCrop.x).toFixed(6));
    armCrop.height = Number((armBottom - armCrop.y).toFixed(6));

    const pivotInTonearmCrop = {
      x: Number(((tonearmPivot.x - armCrop.x) / armCrop.width).toFixed(6)),
      y: Number(((tonearmPivot.y - armCrop.y) / armCrop.height).toFixed(6)),
    };

    const platterCrop = squareCropAround(vinylCenter, platterRimRadius, 1.045);
    const vinylCrop = squareCropAround(vinylCenter, vinylOuterRadius, 1.04);
    const spindleCrop = squareCropAround(vinylCenter, spindleRadius, 1.85);
    const leftKnobCrop = squareCropAround(leftKnobCenter, knobRadius, 1.15);
    const rightKnobCrop = squareCropAround(rightKnobCenter, knobRadius, 1.15);

    const chassisBox = new THREE.Box3().setFromObject(turntable.layerGroups.outerFrame);
    const chassisMin = worldPointToNormalized(chassisBox.min);
    const chassisMax = worldPointToNormalized(chassisBox.max);

    return {
      schemaVersion: 1,
      generator: 'plinth-threejs-prerender-v1',
      referenceSizePx: 4096,
      aspectRatio: 1.0,
      themes: ['light', 'dark'],
      resolutions: [512, 1024, 2048],
      geometry: {
        chassisBounds: {
          x: Number(Math.min(chassisMin.x, chassisMax.x).toFixed(6)),
          y: Number(Math.min(chassisMin.y, chassisMax.y).toFixed(6)),
          width: Number(Math.abs(chassisMax.x - chassisMin.x).toFixed(6)),
          height: Number(Math.abs(chassisMax.y - chassisMin.y).toFixed(6)),
        },
        vinylCenter,
        platterRimRadius,
        vinylOuterRadius,
        playableOuterRadius,
        playableInnerRadius,
        albumLabelRadius,
        spindleRadius,
        tonearmPivot,
        tonearmEffectiveLength: effectiveLength,
        tonearmRestAngleDeg: Number(TONEARM_REST_DEG.toFixed(2)),
        tonearmOuterGrooveAngleDeg: Number(TONEARM_OUTER_DEG.toFixed(2)),
        tonearmInnerGrooveAngleDeg: Number(TONEARM_INNER_DEG.toFixed(2)),
        tonearmHeadShellOffsetDeg: -23.0,
        tonearmKeyframeAnglesDeg: TONEARM_KEYFRAME_ANGLES_DEG,
        leftKnobCenter,
        leftKnobRadius: knobRadius,
        rightKnobCenter,
        rightKnobRadius: knobRadius,
      },
      layers: {
        turntableBase: {
          file: 'turntable_base.webp',
          zIndex: 0,
          bounds: { x: 0, y: 0, width: 1, height: 1 },
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        metalPlatter: {
          file: 'metal_platter.webp',
          zIndex: 1,
          bounds: platterCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        leftKnobNormal: {
          file: 'left_knob_normal.webp',
          zIndex: 2,
          bounds: leftKnobCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        leftKnobPressed: {
          file: 'left_knob_pressed.webp',
          zIndex: 2,
          bounds: leftKnobCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        rightKnobNormal: {
          file: 'right_knob_normal.webp',
          zIndex: 2,
          bounds: rightKnobCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        rightKnobPressed: {
          file: 'right_knob_pressed.webp',
          zIndex: 2,
          bounds: rightKnobCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        vinylRecord: {
          file: 'vinyl_record.webp',
          zIndex: 3,
          bounds: vinylCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        vinylReflection: {
          file: 'vinyl_reflection.webp',
          zIndex: 5,
          bounds: vinylCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        centerSpindle: {
          file: 'center_spindle.webp',
          zIndex: 6,
          bounds: spindleCrop,
          transformOrigin: { x: 0.5, y: 0.5 },
        },
        tonearmShadow: {
          filePattern: 'tonearm_shadow_f{index}.webp',
          zIndex: 7,
          bounds: armCrop,
          transformOrigin: pivotInTonearmCrop,
          frameCount: TONEARM_KEYFRAME_ANGLES_DEG.length,
        },
        tonearmArm: {
          filePattern: 'tonearm_arm_f{index}.webp',
          zIndex: 8,
          bounds: armCrop,
          transformOrigin: pivotInTonearmCrop,
          frameCount: TONEARM_KEYFRAME_ANGLES_DEG.length,
        },
      },
    };
  }

  function renderLayerPngDataUrl(passName, armDeg = TONEARM_REST_DEG) {
    turntable.showLayerPass(passName, armDeg);
    renderer.setClearColor(0x000000, 0);
    renderer.clear();
    renderer.render(scene, camera);
    return canvas.toDataURL('image/png');
  }

  function renderCompositePngDataUrl(armDeg = TONEARM_OUTER_DEG, bgHex = null) {
    turntable.showComposite(armDeg);
    if (bgHex === null) {
      renderer.setClearColor(0x000000, 0);
    } else {
      renderer.setClearColor(bgHex, 1);
    }
    renderer.clear();
    renderer.render(scene, camera);
    return canvas.toDataURL('image/png');
  }

  function dispose() {
    turntable.dispose();
    envRT.dispose();
    renderer.dispose();
  }

  return {
    extractManifest,
    renderLayerPngDataUrl,
    renderCompositePngDataUrl,
    dispose,
  };
}
