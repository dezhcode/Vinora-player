import * as THREE from 'three';
import {
  createBrushedAluminumMaps,
  createChassisContactShadowTexture,
  createCounterweightTextures,
  createMatteBeadMaps,
  createRadialBrushedMaps,
} from './textures.js';

export const CAMERA_HALF_SPAN = 2.28; // World X & Z span [-2.28, +2.28] -> total 4.56
export const PLATTER_CENTER = new THREE.Vector3(-0.34, 0.17, 0.01);
export const PIVOT_POS = new THREE.Vector3(1.42, 0.17, -1.14);
export const LEFT_KNOB_POS = new THREE.Vector3(-1.45, 0.17, 1.36);
export const RIGHT_KNOB_POS = new THREE.Vector3(1.45, 0.17, 1.36);

export const VINYL_OUTER_R = 1.395;
export const VINYL_OUTER_GROOVE_R = 1.31;
export const VINYL_INNER_GROOVE_R = 0.59;
export const VINYL_LABEL_R = 0.445;
export const PLATTER_RIM_R = 1.472;
export const SPINDLE_R = 0.044;
export const KNOB_OUTER_R = 0.186;
export const EFFECTIVE_LENGTH = 2.02;

const DX_PC = PLATTER_CENTER.x - PIVOT_POS.x;
const DZ_PC = PLATTER_CENTER.z - PIVOT_POS.z;
const DIST_PC = Math.hypot(DX_PC, DZ_PC);
const THETA_PC = Math.atan2(DX_PC, DZ_PC);

export function computeGrooveYawRad(progress) {
  const p = Math.max(0, Math.min(1, progress));
  const r = VINYL_OUTER_GROOVE_R - (VINYL_OUTER_GROOVE_R - VINYL_INNER_GROOVE_R) * p;
  const cosAlpha = Math.max(
    -1,
    Math.min(1, (DIST_PC * DIST_PC + EFFECTIVE_LENGTH * EFFECTIVE_LENGTH - r * r) / (2 * DIST_PC * EFFECTIVE_LENGTH)),
  );
  return THETA_PC + Math.acos(cosAlpha);
}

export const TONEARM_REST_DEG = -13.5;
export const TONEARM_OUTER_DEG = THREE.MathUtils.radToDeg(computeGrooveYawRad(0));
export const TONEARM_INNER_DEG = THREE.MathUtils.radToDeg(computeGrooveYawRad(1));
export const TONEARM_KEYFRAME_ANGLES_DEG = [
  Number(TONEARM_REST_DEG.toFixed(2)),
  -16.4,
  Number(TONEARM_OUTER_DEG.toFixed(2)),
  -22.2,
  -25.2,
  -28.2,
  -31.2,
  -34.2,
  -37.1,
  Number(TONEARM_INNER_DEG.toFixed(2)),
];

function createRoundedRectShape(w, h, r) {
  const s = new THREE.Shape();
  const hw = w / 2;
  const hh = h / 2;
  s.moveTo(-hw + r, -hh);
  s.lineTo(hw - r, -hh);
  s.quadraticCurveTo(hw, -hh, hw, -hh + r);
  s.lineTo(hw, hh - r);
  s.quadraticCurveTo(hw, hh, hw - r, hh);
  s.lineTo(-hw + r, hh);
  s.quadraticCurveTo(-hw, hh, -hw, hh - r);
  s.lineTo(-hw, -hh + r);
  s.quadraticCurveTo(-hw, -hh, -hw + r, -hh);
  return s;
}

function createRoundedRectPath(w, h, r) {
  const p = new THREE.Path();
  const hw = w / 2;
  const hh = h / 2;
  p.moveTo(-hw + r, -hh);
  p.lineTo(hw - r, -hh);
  p.quadraticCurveTo(hw, -hh, hw, -hh + r);
  p.lineTo(hw, hh - r);
  p.quadraticCurveTo(hw, hh, hw - r, hh);
  p.lineTo(-hw + r, hh);
  p.quadraticCurveTo(-hw, hh, -hw, hh - r);
  p.lineTo(-hw, -hh + r);
  p.quadraticCurveTo(-hw, -hh, -hw + r, -hh);
  return p;
}

function normalizeExtrudeTopUVs(geo, width, height, centerX = 0, centerY = 0) {
  const pos = geo.attributes.position;
  const uv = geo.attributes.uv;
  if (!pos || !uv) return;
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i);
    const y = pos.getY(i);
    uv.setXY(i, (x - centerX) / width + 0.5, (y - centerY) / height + 0.5);
  }
  uv.needsUpdate = true;
}

function createSplitTopPlateShape(w, h, r, splitFromTopRatio) {
  // Note: Shape coordinates are in XY before rotateX(+PI/2) or (-PI/2).
  // We'll use rotateX(-PI/2): local +Y becomes -Z (top of screen), local -Y becomes +Z (bottom of screen).
  const hw = w / 2;
  const hh = h / 2;
  const splitY = hh - h * splitFromTopRatio; // upper section has y in [splitY, hh]

  const upper = new THREE.Shape();
  upper.moveTo(-hw, splitY);
  upper.lineTo(hw, splitY);
  upper.lineTo(hw, hh - r);
  upper.quadraticCurveTo(hw, hh, hw - r, hh);
  upper.lineTo(-hw + r, hh);
  upper.quadraticCurveTo(-hw, hh, -hw, hh - r);
  upper.lineTo(-hw, splitY);

  const lower = new THREE.Shape();
  lower.moveTo(-hw, splitY);
  lower.lineTo(-hw, -hh + r);
  lower.quadraticCurveTo(-hw, -hh, -hw + r, -hh);
  lower.lineTo(hw - r, -hh);
  lower.quadraticCurveTo(hw, -hh, hw, -hh + r);
  lower.lineTo(hw, splitY);
  lower.lineTo(-hw, splitY);

  return { upper, lower, splitY };
}

const VINYL_GROOVE_VERT = /* glsl */ `
  varying vec2 vLocalPos;
  void main() {
    vLocalPos = position.xz;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

const VINYL_GROOVE_FRAG = /* glsl */ `
  precision highp float;
  varying vec2 vLocalPos;
  uniform float uInnerR;
  uniform float uOuterR;
  uniform float uLabelR;

  void main() {
    float r = length(vLocalPos);
    float angle = atan(vLocalPos.y, vLocalPos.x);

    // Pure obsidian black vinyl base
    vec3 baseBlack = vec3(0.015, 0.016, 0.019);

    float normR = clamp((r - uInnerR) / (uOuterR - uInnerR), 0.0, 1.0);
    float deadWax = 1.0 - smoothstep(uInnerR - 0.018, uInnerR + 0.005, r);
    float leadIn = smoothstep(uOuterR - 0.015, uOuterR + 0.025, r);

    // 4 realistic quiet track gap bands
    float gap1 = 1.0 - smoothstep(0.0, 0.012, abs(normR - 0.24));
    float gap2 = 1.0 - smoothstep(0.0, 0.010, abs(normR - 0.51));
    float gap3 = 1.0 - smoothstep(0.0, 0.011, abs(normR - 0.76));
    float gapMask = clamp(gap1 + gap2 + gap3 + deadWax + leadIn, 0.0, 1.0);

    // Modulated spiral groove density + gentle organic pressing variation so 33⅓ RPM spin is unmistakable
    float audioMod = sin(angle * 7.0 + r * 42.0) * 0.18 + sin(angle * 13.0 - r * 70.0) * 0.09;
    float fineGroove = sin((r + audioMod * 0.009) * 390.0) * 0.5 + 0.5;
    float midGroove = sin((r + audioMod * 0.005) * 145.0) * 0.5 + 0.5;
    float grooveHeight = mix(fineGroove * 0.62 + midGroove * 0.38, 0.18, gapMask);

    // Subtle rotational micro-sheen locked to physical vinyl (fixed studio cones live in separate layer!)
    float microAngleMod = 0.85 + 0.15 * sin(angle * 2.0 + r * 18.0);
    float ringHighlight = grooveHeight * 0.030 * microAngleMod * (1.0 - gapMask);
    float bevelEdge = smoothstep(uOuterR + 0.022, uOuterR + 0.052, r) * 0.045;

    // Subtle dead-wax ring border
    float deadWaxEdge = (1.0 - smoothstep(0.0, 0.008, abs(r - uInnerR))) * 0.022;

    vec3 color = baseBlack
      + vec3(0.11, 0.115, 0.125) * ringHighlight
      + vec3(0.16, 0.17, 0.19) * bevelEdge
      + vec3(0.09, 0.095, 0.105) * deadWaxEdge;

    gl_FragColor = vec4(color, 1.0);
  }
`;

const VINYL_REFLECTION_FRAG = /* glsl */ `
  precision highp float;
  varying vec2 vLocalPos;
  uniform float uInnerR;
  uniform float uOuterR;
  uniform float uLabelR;
  uniform float uDarkMode;

  void main() {
    float r = length(vLocalPos);
    if (r < uLabelR || r > uOuterR + 0.06) discard;

    // Screen/world top-left is (-X, -Z). Wait: vLocalPos.x = X, vLocalPos.y = Z.
    // atan(Z, X):
    // 10:45 (upper-left: -X, -Z) => ~ -2.35 rad (-135 deg)
    // 4:45  (lower-right: +X, +Z) => ~ +0.78 rad (+45 deg)
    float angle = atan(vLocalPos.y, vLocalPos.x);

    // Dual crisp anisotropic studio light cones matching IMG_20261125_171547_5581814c.jpg
    float primaryConeA = pow(max(0.0, cos(angle + 2.30)), 11.0); // ~10:45 upper-left
    float primaryConeB = pow(max(0.0, cos(angle - 0.84)), 12.0); // ~4:45 lower-right

    // Tight inner specular core inside each cone + softer broad shoulder
    float coreA = pow(max(0.0, cos(angle + 2.26)), 38.0);
    float coreB = pow(max(0.0, cos(angle - 0.88)), 42.0);

    // Subtle cool side ambient sheen (~7:30 and ~1:30)
    float sideFill = pow(max(0.0, cos(angle - 2.35)), 16.0) * 0.18
                   + pow(max(0.0, cos(angle + 0.78)), 16.0) * 0.16;

    // Radial falloff over groove zone + glossy dead-wax edge + crisp outer edge catchlight
    float grooveMask = smoothstep(uLabelR + 0.02, uInnerR + 0.06, r) * (1.0 - smoothstep(uOuterR + 0.01, uOuterR + 0.055, r));
    float grooveMicro = 0.72 + 0.28 * (sin(r * 380.0) * 0.5 + 0.5);
    float deadWaxMask = smoothstep(uLabelR, uLabelR + 0.02, r) * (1.0 - smoothstep(uInnerR - 0.02, uInnerR + 0.02, r));
    float rimCatch = smoothstep(uOuterR + 0.018, uOuterR + 0.044, r) * (1.0 - smoothstep(uOuterR + 0.044, uOuterR + 0.058, r));

    float coneEnergy = (primaryConeA * 0.24 + primaryConeB * 0.26 + coreA * 0.22 + coreB * 0.24 + sideFill) * grooveMask * grooveMicro;
    float deadWaxEnergy = (primaryConeA * 0.14 + primaryConeB * 0.16) * deadWaxMask;
    float rimEnergy = (0.14 + primaryConeA * 0.25 + primaryConeB * 0.25) * rimCatch;

    float alpha = clamp((coneEnergy + deadWaxEnergy + rimEnergy) * (uDarkMode > 0.5 ? 1.08 : 0.96), 0.0, 0.78);
    vec3 specularTint = vec3(0.95, 0.97, 1.0);
    gl_FragColor = vec4(specularTint * alpha, alpha);
  }
`;

export function buildTurntableScene(isDark = false) {
  const root = new THREE.Group();
  root.name = 'TurntableRoot';

  const brushedMaps = createBrushedAluminumMaps(isDark);
  const matteMaps = createMatteBeadMaps();
  const radialKnobMaps = createRadialBrushedMaps(isDark);
  const cwTex = createCounterweightTextures(isDark);
  const chassisShadowTex = createChassisContactShadowTexture(isDark);

  // --- Physical Materials ---
  const creamMat = new THREE.MeshPhysicalMaterial({
    color: isDark ? 0x1d222b : 0xebe9e3,
    metalness: isDark ? 0.24 : 0.03,
    roughness: isDark ? 0.52 : 0.64,
    roughnessMap: matteMaps.roughnessMap,
    clearcoat: isDark ? 0.12 : 0.04,
    clearcoatRoughness: 0.6,
    envMapIntensity: isDark ? 0.95 : 0.72,
  });

  const brushedTopPlateMat = new THREE.MeshPhysicalMaterial({
    color: 0xffffff,
    map: brushedMaps.map,
    roughnessMap: brushedMaps.roughnessMap,
    normalMap: brushedMaps.normalMap,
    normalScale: new THREE.Vector2(0.14, 0.14),
    metalness: isDark ? 0.82 : 0.76,
    roughness: isDark ? 0.34 : 0.31,
    anisotropy: 0.68,
    anisotropyRotation: 0.0,
    clearcoat: 0.06,
    clearcoatRoughness: 0.42,
    envMapIntensity: isDark ? 1.15 : 0.95,
  });

  const brightSilverMat = new THREE.MeshPhysicalMaterial({
    color: isDark ? 0x9aa4b6 : 0xc5cad3,
    metalness: 0.94,
    roughness: 0.21,
    clearcoat: 0.16,
    clearcoatRoughness: 0.20,
    envMapIntensity: 1.24,
  });

  const brushedSilverHardwareMat = new THREE.MeshPhysicalMaterial({
    color: isDark ? 0x7e8799 : 0xb9bec7,
    metalness: 0.90,
    roughness: 0.26,
    anisotropy: 0.55,
    envMapIntensity: 1.15,
  });

  const platterBevelMat = new THREE.MeshPhysicalMaterial({
    color: isDark ? 0xffffff : 0xf2eee8,
    map: radialKnobMaps.map,
    metalness: 0.88,
    roughness: 0.24,
    anisotropy: 0.72,
    envMapIntensity: 1.16,
  });

  const darkRecessMat = new THREE.MeshStandardMaterial({
    color: isDark ? 0x06080b : 0x111318,
    metalness: 0.28,
    roughness: 0.66,
  });

  const seamLineMat = new THREE.MeshStandardMaterial({
    color: isDark ? 0x0b0e13 : 0xbcbab4,
    metalness: 0.2,
    roughness: 0.7,
  });

  const carbonArmMat = new THREE.MeshPhysicalMaterial({
    color: 0x16181d,
    metalness: 0.42,
    roughness: 0.26,
    clearcoat: 0.32,
    clearcoatRoughness: 0.25,
    envMapIntensity: 1.05,
  });

  const counterweightMat = new THREE.MeshPhysicalMaterial({
    color: 0xffffff,
    map: cwTex.map,
    normalMap: cwTex.normalMap,
    normalScale: new THREE.Vector2(0.32, 0.10),
    metalness: 0.88,
    roughness: 0.26,
    envMapIntensity: 1.2,
  });

  const shadowReceiverMat = new THREE.ShadowMaterial({
    color: new THREE.Color(0x05070d),
    opacity: isDark ? 0.38 : 0.22,
  });

  // =========================================================================
  // LAYER GROUPS
  // =========================================================================
  const layerChassisShadow = new THREE.Group();
  const layerOuterFrame = new THREE.Group();
  const layerRearHinges = new THREE.Group();
  const layerSplitDeck = new THREE.Group();
  const layerTonearmPivotBase = new THREE.Group();
  const layerMetalPlatter = new THREE.Group();
  const layerVinylRecord = new THREE.Group();
  const layerVinylReflection = new THREE.Group();
  const layerCenterSpindle = new THREE.Group();
  const layerLeftKnob = new THREE.Group();
  const layerRightKnob = new THREE.Group();
  const layerTonearmMoving = new THREE.Group();
  const layerTonearmShadow = new THREE.Group();

  root.add(
    layerChassisShadow,
    layerOuterFrame,
    layerRearHinges,
    layerSplitDeck,
    layerTonearmPivotBase,
    layerMetalPlatter,
    layerVinylRecord,
    layerVinylReflection,
    layerCenterSpindle,
    layerLeftKnob,
    layerRightKnob,
    layerTonearmMoving,
    layerTonearmShadow,
  );

  // --- 1. Chassis Contact Shadow ---
  const chassisShadowPlane = new THREE.Mesh(
    new THREE.PlaneGeometry(4.56, 4.56),
    new THREE.MeshBasicMaterial({
      map: chassisShadowTex,
      transparent: true,
      depthWrite: false,
    }),
  );
  chassisShadowPlane.rotation.x = -Math.PI / 2;
  chassisShadowPlane.position.y = -0.18;
  layerChassisShadow.add(chassisShadowPlane);

  // --- 2. Outer Rounded Chamfered Perimeter Frame ---
  const outerW = 4.00;
  const outerH = 3.92;
  const outerR = 0.28;
  const innerW = 3.74;
  const innerH = 3.66;
  const innerR = 0.20;

  const frameShape = createRoundedRectShape(outerW, outerH, outerR);
  frameShape.holes.push(createRoundedRectPath(innerW, innerH, innerR));
  const frameGeo = new THREE.ExtrudeGeometry(frameShape, {
    depth: 0.28,
    bevelEnabled: true,
    bevelThickness: 0.032,
    bevelSize: 0.026,
    bevelSegments: 5,
    curveSegments: 28,
  });
  frameGeo.rotateX(-Math.PI / 2);
  const frameMesh = new THREE.Mesh(frameGeo, creamMat);
  frameMesh.position.y = -0.12;
  frameMesh.castShadow = true;
  frameMesh.receiveShadow = true;
  layerOuterFrame.add(frameMesh);

  // Inner dark shadow hairline between outer bezel and top plate
  const trayShape = createRoundedRectShape(innerW + 0.04, innerH + 0.04, innerR + 0.01);
  const trayGeo = new THREE.ExtrudeGeometry(trayShape, {
    depth: 0.26,
    bevelEnabled: false,
    curveSegments: 24,
  });
  trayGeo.rotateX(-Math.PI / 2);
  const trayMesh = new THREE.Mesh(trayGeo, seamLineMat);
  trayMesh.position.y = -0.115;
  layerOuterFrame.add(trayMesh);

  // --- 3. Rear Chrome Hinges (Left & Right at Upper Edge) ---
  [-1.04, 1.04].forEach((hx) => {
    const hingeGroup = new THREE.Group();
    hingeGroup.position.set(hx, 0.145, -outerH / 2 - 0.028);

    // Cream backing bridge
    const tabGeo = new THREE.BoxGeometry(0.42, 0.035, 0.045);
    const tab = new THREE.Mesh(tabGeo, creamMat);
    tab.position.set(0, -0.01, 0.02);
    hingeGroup.add(tab);

    // 3 segmented brushed chrome hinge knuckles
    [-0.145, 0.0, 0.145].forEach((kx, idx) => {
      const len = idx === 1 ? 0.15 : 0.11;
      const barrelGeo = new THREE.CylinderGeometry(0.029, 0.029, len, 20);
      barrelGeo.rotateZ(Math.PI / 2);
      const barrel = new THREE.Mesh(barrelGeo, brightSilverMat);
      barrel.position.set(kx, 0.01, -0.004);
      barrel.castShadow = true;
      hingeGroup.add(barrel);
    });

    // Dark joint collars between knuckles
    [-0.073, 0.073].forEach((jx) => {
      const jointGeo = new THREE.CylinderGeometry(0.030, 0.030, 0.012, 16);
      jointGeo.rotateZ(Math.PI / 2);
      const joint = new THREE.Mesh(jointGeo, seamLineMat);
      joint.position.set(jx, 0.01, -0.004);
      hingeGroup.add(joint);
    });

    layerRearHinges.add(hingeGroup);
  });

  // --- 4. Inner Two-Tone Split Deck (Upper ~65% Brushed Aluminum + Lower ~35% Matte Cream) ---
  const plateW = innerW - 0.016;
  const plateH = innerH - 0.016;
  const plateR = innerR - 0.01;
  const { upper, lower, splitY } = createSplitTopPlateShape(plateW, plateH, plateR, 0.652);

  const upperGeo = new THREE.ExtrudeGeometry(upper, {
    depth: 0.04,
    bevelEnabled: true,
    bevelThickness: 0.006,
    bevelSize: 0.006,
    bevelSegments: 2,
    curveSegments: 24,
  });
  normalizeExtrudeTopUVs(upperGeo, plateW, plateH);
  upperGeo.rotateX(-Math.PI / 2);
  const upperMesh = new THREE.Mesh(upperGeo, brushedTopPlateMat);
  upperMesh.position.y = 0.130;
  upperMesh.receiveShadow = true;
  layerSplitDeck.add(upperMesh);

  const lowerGeo = new THREE.ExtrudeGeometry(lower, {
    depth: 0.04,
    bevelEnabled: true,
    bevelThickness: 0.006,
    bevelSize: 0.006,
    bevelSegments: 2,
    curveSegments: 24,
  });
  normalizeExtrudeTopUVs(lowerGeo, plateW, plateH);
  lowerGeo.rotateX(-Math.PI / 2);
  const lowerMesh = new THREE.Mesh(lowerGeo, creamMat);
  lowerMesh.position.y = 0.130;
  lowerMesh.receiveShadow = true;
  layerSplitDeck.add(lowerMesh);

  // Subtle dark horizontal seam between upper aluminum and lower cream panels
  // Note: splitY in shape Y becomes z = -splitY after rotateX(-PI/2)
  const splitWorldZ = -splitY;
  const seamStrip = new THREE.Mesh(
    new THREE.BoxGeometry(plateW, 0.006, 0.012),
    seamLineMat,
  );
  seamStrip.position.set(0, 0.171, splitWorldZ);
  layerSplitDeck.add(seamStrip);

  // Subtle circular recessed platter well outline on the deck
  const platterWellGlow = new THREE.Mesh(
    new THREE.RingGeometry(PLATTER_RIM_R - 0.02, PLATTER_RIM_R + 0.045, 96),
    new THREE.MeshBasicMaterial({
      color: isDark ? 0x040507 : 0x7e8490,
      transparent: true,
      opacity: isDark ? 0.55 : 0.25,
      depthWrite: false,
    }),
  );
  platterWellGlow.rotation.x = -Math.PI / 2;
  platterWellGlow.position.set(PLATTER_CENTER.x, 0.177, PLATTER_CENTER.z);
  layerSplitDeck.add(platterWellGlow);

  // --- 5. Stationary Tonearm Gimbal Well + Anti-Skate Dial + Cue Pin ---
  const gimbalBase = new THREE.Group();
  gimbalBase.position.copy(PIVOT_POS);
  layerTonearmPivotBase.add(gimbalBase);

  // Raised silver/cream bezel collar around gimbal basin
  const basinCollar = new THREE.Mesh(
    new THREE.LatheGeometry([
      new THREE.Vector2(0.255, 0.0),
      new THREE.Vector2(0.268, 0.024),
      new THREE.Vector2(0.295, 0.022),
      new THREE.Vector2(0.312, 0.0),
    ], 48),
    creamMat,
  );
  basinCollar.receiveShadow = true;
  gimbalBase.add(basinCollar);

  // Dark obsidian recessed gimbal well
  const basinWell = new THREE.Mesh(
    new THREE.CylinderGeometry(0.262, 0.262, 0.020, 48),
    darkRecessMat,
  );
  basinWell.position.y = 0.008;
  basinWell.receiveShadow = true;
  gimbalBase.add(basinWell);

  // Fine inner silver circular groove inside well
  const basinInnerRing = new THREE.Mesh(
    new THREE.RingGeometry(0.242, 0.258, 48),
    brushedSilverHardwareMat,
  );
  basinInnerRing.rotation.x = -Math.PI / 2;
  basinInnerRing.position.y = 0.020;
  gimbalBase.add(basinInnerRing);

  // Protruding Knurled Anti-Skate / Bias Cylinder on right edge of gimbal well (~4 o'clock)
  const biasDialGroup = new THREE.Group();
  biasDialGroup.position.set(0.265, 0.036, 0.068);
  biasDialGroup.rotation.y = THREE.MathUtils.degToRad(-14);
  const biasNeck = new THREE.Mesh(
    new THREE.CylinderGeometry(0.054, 0.054, 0.08, 20),
    brushedSilverHardwareMat,
  );
  biasNeck.rotation.z = Math.PI / 2;
  biasDialGroup.add(biasNeck);

  const biasKnob = new THREE.Mesh(
    new THREE.CylinderGeometry(0.072, 0.072, 0.068, 24),
    counterweightMat,
  );
  biasKnob.rotation.z = Math.PI / 2;
  biasKnob.position.x = 0.056;
  biasKnob.castShadow = true;
  biasDialGroup.add(biasKnob);

  const biasOuterCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.056, 0.072, 0.012, 20),
    brightSilverMat,
  );
  biasOuterCap.rotation.z = Math.PI / 2;
  biasOuterCap.position.x = 0.095;
  biasDialGroup.add(biasOuterCap);
  gimbalBase.add(biasDialGroup);

  // Fine vertical armrest / cueing guide pin extending below gimbal well
  const cueRod = new THREE.Mesh(
    new THREE.CylinderGeometry(0.007, 0.007, 0.24, 12),
    brushedSilverHardwareMat,
  );
  cueRod.rotation.x = Math.PI / 2;
  cueRod.position.set(0.045, 0.032, 0.30);
  cueRod.castShadow = true;
  gimbalBase.add(cueRod);

  // --- 6. Bottom-Left & Bottom-Right Machined Aluminum Dials (Normal & Pressed states) ---
  function buildCornerKnob(group, pos) {
    const knobRoot = new THREE.Group();
    knobRoot.position.copy(pos);
    group.add(knobRoot);

    // Outer recessed cream ring well
    const wellBezel = new THREE.Mesh(
      new THREE.LatheGeometry([
        new THREE.Vector2(0.146, 0.0),
        new THREE.Vector2(0.154, 0.014),
        new THREE.Vector2(0.182, 0.014),
        new THREE.Vector2(0.192, 0.0),
      ], 48),
      creamMat,
    );
    wellBezel.receiveShadow = true;
    knobRoot.add(wellBezel);

    // Soft recessed shadow seam around knob cap (subtle hardware gap, not thick black line)
    const moat = new THREE.Mesh(
      new THREE.RingGeometry(0.142, 0.154, 48),
      new THREE.MeshBasicMaterial({
        color: isDark ? 0x06080b : 0x72767e,
        transparent: true,
        opacity: isDark ? 0.85 : 0.45,
      }),
    );
    moat.rotation.x = -Math.PI / 2;
    moat.position.y = 0.008;
    knobRoot.add(moat);

    // Movable Plunger Group (animated downward for pressed sprite)
    const plunger = new THREE.Group();
    plunger.position.y = 0.022;
    knobRoot.add(plunger);

    const skirt = new THREE.Mesh(
      new THREE.CylinderGeometry(0.144, 0.150, 0.036, 48),
      brushedSilverHardwareMat,
    );
    skirt.castShadow = true;
    skirt.receiveShadow = true;
    plunger.add(skirt);

    // Conical brushed top dial face
    const capProfile = [
      new THREE.Vector2(0.0, 0.022),
      new THREE.Vector2(0.118, 0.020),
      new THREE.Vector2(0.136, 0.016),
      new THREE.Vector2(0.144, 0.008),
    ];
    const capGeo = new THREE.LatheGeometry(capProfile, 56);
    // Planar top UVs for radial brushed map
    const posAttr = capGeo.attributes.position;
    const uvAttr = capGeo.attributes.uv;
    for (let i = 0; i < posAttr.count; i++) {
      const px = posAttr.getX(i);
      const pz = posAttr.getZ(i);
      uvAttr.setXY(i, px / 0.29 + 0.5, pz / 0.29 + 0.5);
    }
    uvAttr.needsUpdate = true;
    const cap = new THREE.Mesh(capGeo, platterBevelMat);
    cap.castShadow = true;
    cap.receiveShadow = true;
    plunger.add(cap);

    // Inner chamfer groove ring on dial face
    const innerGroove = new THREE.Mesh(
      new THREE.RingGeometry(0.115, 0.121, 48),
      new THREE.MeshBasicMaterial({
        color: isDark ? 0x161920 : 0x797e87,
        transparent: true,
        opacity: 0.55,
      }),
    );
    innerGroove.rotation.x = -Math.PI / 2;
    innerGroove.position.y = 0.0215;
    plunger.add(innerGroove);

    return { knobRoot, plunger };
  }

  const leftKnob = buildCornerKnob(layerLeftKnob, LEFT_KNOB_POS);
  const rightKnob = buildCornerKnob(layerRightKnob, RIGHT_KNOB_POS);

  // --- 7. Machined Outer Metal Platter Ring ---
  const platterBase = new THREE.Group();
  platterBase.position.copy(PLATTER_CENTER);
  layerMetalPlatter.add(platterBase);

  const platterProfile = [
    new THREE.Vector2(1.360, 0.0),
    new THREE.Vector2(1.392, 0.048),
    new THREE.Vector2(1.448, 0.046),
    new THREE.Vector2(PLATTER_RIM_R, 0.016),
    new THREE.Vector2(PLATTER_RIM_R + 0.012, 0.0),
  ];
  const platterRingGeo = new THREE.LatheGeometry(platterProfile, 96);
  const pPos = platterRingGeo.attributes.position;
  const pUv = platterRingGeo.attributes.uv;
  for (let i = 0; i < pPos.count; i++) {
    pUv.setXY(i, pPos.getX(i) / (PLATTER_RIM_R * 2.1) + 0.5, pPos.getZ(i) / (PLATTER_RIM_R * 2.1) + 0.5);
  }
  pUv.needsUpdate = true;
  const platterRingMesh = new THREE.Mesh(platterRingGeo, platterBevelMat);
  platterRingMesh.castShadow = true;
  platterRingMesh.receiveShadow = true;
  platterBase.add(platterRingMesh);

  // --- 8. Deep Black Rotating Vinyl Record Disc ---
  const vinylGroup = new THREE.Group();
  vinylGroup.position.set(PLATTER_CENTER.x, PLATTER_CENTER.y + 0.048, PLATTER_CENTER.z);
  layerVinylRecord.add(vinylGroup);

  const vinylBody = new THREE.Mesh(
    new THREE.CylinderGeometry(VINYL_OUTER_R, VINYL_OUTER_R + 0.008, 0.022, 96),
    new THREE.MeshPhysicalMaterial({
      color: 0x060709,
      metalness: 0.08,
      roughness: 0.28,
      clearcoat: 0.35,
    }),
  );
  vinylBody.castShadow = true;
  vinylBody.receiveShadow = true;
  vinylGroup.add(vinylBody);

  const vinylSurfaceGeo = new THREE.RingGeometry(VINYL_LABEL_R, VINYL_OUTER_R, 128, 1);
  vinylSurfaceGeo.rotateX(-Math.PI / 2);
  const vinylSurfaceMat = new THREE.ShaderMaterial({
    vertexShader: VINYL_GROOVE_VERT,
    fragmentShader: VINYL_GROOVE_FRAG,
    uniforms: {
      uInnerR: { value: VINYL_INNER_GROOVE_R },
      uOuterR: { value: VINYL_OUTER_GROOVE_R },
      uLabelR: { value: VINYL_LABEL_R },
    },
  });
  const vinylSurface = new THREE.Mesh(vinylSurfaceGeo, vinylSurfaceMat);
  vinylSurface.position.y = 0.012;
  vinylGroup.add(vinylSurface);

  // Neutral fallback archival center label seat (underneath dynamic album art in Compose)
  const labelSeat = new THREE.Mesh(
    new THREE.CylinderGeometry(VINYL_LABEL_R, VINYL_LABEL_R, 0.004, 64),
    new THREE.MeshStandardMaterial({
      color: isDark ? 0x222732 : 0xedece7,
      roughness: 0.72,
      metalness: 0.02,
    }),
  );
  labelSeat.position.y = 0.0135;
  vinylGroup.add(labelSeat);

  // Subtle pressed stamper crease & outer edge ring on paper label
  const stamperRing = new THREE.Mesh(
    new THREE.RingGeometry(0.175, 0.182, 48),
    new THREE.MeshBasicMaterial({
      color: isDark ? 0x3b4252 : 0xc7c6c0,
      transparent: true,
      opacity: 0.65,
    }),
  );
  stamperRing.rotation.x = -Math.PI / 2;
  stamperRing.position.y = 0.0158;
  vinylGroup.add(stamperRing);

  // --- 9. Stationary Dual-Cone Studio Specular Vinyl Reflection Overlay ---
  const reflectionGeo = new THREE.RingGeometry(VINYL_LABEL_R, VINYL_OUTER_R + 0.055, 128, 1);
  reflectionGeo.rotateX(-Math.PI / 2);
  const reflectionMat = new THREE.ShaderMaterial({
    vertexShader: VINYL_GROOVE_VERT,
    fragmentShader: VINYL_REFLECTION_FRAG,
    transparent: true,
    depthWrite: false,
    uniforms: {
      uInnerR: { value: VINYL_INNER_GROOVE_R },
      uOuterR: { value: VINYL_OUTER_GROOVE_R },
      uLabelR: { value: VINYL_LABEL_R },
      uDarkMode: { value: isDark ? 1.0 : 0.0 },
    },
  });
  const reflectionMesh = new THREE.Mesh(reflectionGeo, reflectionMat);
  reflectionMesh.position.set(PLATTER_CENTER.x, PLATTER_CENTER.y + 0.064, PLATTER_CENTER.z);
  layerVinylReflection.add(reflectionMesh);

  // --- 10. Machined Center Spindle ---
  const spindleGroup = new THREE.Group();
  spindleGroup.position.set(PLATTER_CENTER.x, PLATTER_CENTER.y + 0.064, PLATTER_CENTER.z);
  layerCenterSpindle.add(spindleGroup);

  const spindleShadowCollar = new THREE.Mesh(
    new THREE.RingGeometry(SPINDLE_R * 0.9, SPINDLE_R * 1.45, 32),
    new THREE.MeshBasicMaterial({
      color: 0x101318,
      transparent: true,
      opacity: isDark ? 0.55 : 0.24,
    }),
  );
  spindleShadowCollar.rotation.x = -Math.PI / 2;
  spindleShadowCollar.position.y = 0.001;
  spindleGroup.add(spindleShadowCollar);

  const spindleProfile = [
    new THREE.Vector2(0.0, 0.0),
    new THREE.Vector2(SPINDLE_R, 0.0),
    new THREE.Vector2(SPINDLE_R, 0.022),
    new THREE.Vector2(SPINDLE_R * 0.72, 0.028),
    new THREE.Vector2(SPINDLE_R * 0.72, 0.088),
    new THREE.Vector2(SPINDLE_R * 0.42, 0.115),
    new THREE.Vector2(0.0, 0.122),
  ];
  const spindleMesh = new THREE.Mesh(
    new THREE.LatheGeometry(spindleProfile, 32),
    brightSilverMat,
  );
  spindleMesh.castShadow = true;
  spindleGroup.add(spindleMesh);

  // --- 11. Articulated Moving Tonearm Assembly + Synchronized Shadow ---
  const tonearmYawGroup = new THREE.Group();
  tonearmYawGroup.position.set(PIVOT_POS.x, PIVOT_POS.y + 0.14, PIVOT_POS.z);
  tonearmYawGroup.rotation.y = THREE.MathUtils.degToRad(TONEARM_REST_DEG);
  layerTonearmMoving.add(tonearmYawGroup);

  // Outer slotted gimbal yoke ring & fork
  const gimbalRing = new THREE.Mesh(
    new THREE.CylinderGeometry(0.145, 0.152, 0.042, 36),
    brushedSilverHardwareMat,
  );
  gimbalRing.position.y = -0.025;
  gimbalRing.castShadow = true;
  tonearmYawGroup.add(gimbalRing);

  const gimbalDarkWell = new THREE.Mesh(
    new THREE.CylinderGeometry(0.118, 0.118, 0.046, 32),
    darkRecessMat,
  );
  gimbalDarkWell.position.y = -0.022;
  tonearmYawGroup.add(gimbalDarkWell);

  // Crossbar bearing pin
  const bearingBridge = new THREE.Mesh(
    new THREE.BoxGeometry(0.285, 0.036, 0.056),
    brushedSilverHardwareMat,
  );
  bearingBridge.position.y = 0.008;
  bearingBridge.castShadow = true;
  tonearmYawGroup.add(bearingBridge);

  // Center brushed turret cap + thin concentric dark ring
  const turretCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.084, 0.092, 0.054, 32),
    brightSilverMat,
  );
  turretCap.position.y = 0.034;
  turretCap.castShadow = true;
  tonearmYawGroup.add(turretCap);

  const turretRing = new THREE.Mesh(
    new THREE.RingGeometry(0.054, 0.064, 32),
    darkRecessMat,
  );
  turretRing.rotation.x = -Math.PI / 2;
  turretRing.position.y = 0.062;
  tonearmYawGroup.add(turretRing);

  // Chrome Rear Stub + Stepped Engraved Counterweight Cylinder
  const rearStub = new THREE.Mesh(
    new THREE.CylinderGeometry(0.024, 0.024, 0.44, 16),
    brightSilverMat,
  );
  rearStub.rotation.x = Math.PI / 2;
  rearStub.position.set(0, 0.004, -0.23);
  rearStub.castShadow = true;
  tonearmYawGroup.add(rearStub);

  const cwGroup = new THREE.Group();
  cwGroup.position.set(0, 0.004, -0.34);
  tonearmYawGroup.add(cwGroup);

  const cwMain = new THREE.Mesh(
    new THREE.CylinderGeometry(0.112, 0.112, 0.195, 36),
    counterweightMat,
  );
  cwMain.rotation.x = Math.PI / 2;
  cwMain.castShadow = true;
  cwGroup.add(cwMain);

  const cwIsolationBand = new THREE.Mesh(
    new THREE.CylinderGeometry(0.115, 0.115, 0.020, 32),
    darkRecessMat,
  );
  cwIsolationBand.rotation.x = Math.PI / 2;
  cwIsolationBand.position.z = 0.038;
  cwGroup.add(cwIsolationBand);

  const cwRearStep = new THREE.Mesh(
    new THREE.CylinderGeometry(0.056, 0.078, 0.052, 28),
    brightSilverMat,
  );
  cwRearStep.rotation.x = Math.PI / 2;
  cwRearStep.position.z = -0.116;
  cwRearStep.castShadow = true;
  cwGroup.add(cwRearStep);

  // Tapered silver exit collar connecting gimbal to straight black carbon wand
  const exitCollar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.036, 0.048, 0.125, 20),
    brightSilverMat,
  );
  exitCollar.rotation.x = Math.PI / 2;
  exitCollar.position.set(0, 0.006, 0.145);
  exitCollar.castShadow = true;
  tonearmYawGroup.add(exitCollar);

  // Straight Matte-Black Carbon Tonearm Wand
  const bayonetZ = 1.72;
  const wandLen = bayonetZ - 0.18;
  const wandMesh = new THREE.Mesh(
    new THREE.CylinderGeometry(0.028, 0.028, wandLen, 20),
    carbonArmMat,
  );
  wandMesh.rotation.x = Math.PI / 2;
  wandMesh.position.set(0, 0.006, 0.18 + wandLen / 2);
  wandMesh.castShadow = true;
  tonearmYawGroup.add(wandMesh);

  // Angled Slotted Brushed-Silver Headshell + Twin Mounting Screws + Finger Lift + Stylus
  const headshellPivot = new THREE.Group();
  headshellPivot.position.set(0, 0.006, bayonetZ);
  headshellPivot.rotation.y = THREE.MathUtils.degToRad(-23.0);
  tonearmYawGroup.add(headshellPivot);

  // Silver bayonet coupling collar
  const hsCollar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.040, 0.032, 0.095, 20),
    brightSilverMat,
  );
  hsCollar.rotation.x = Math.PI / 2;
  hsCollar.position.set(0, 0.0, 0.035);
  hsCollar.castShadow = true;
  headshellPivot.add(hsCollar);

  // Brushed silver machined headshell plate (chamfered rounded silhouette)
  const hsShape = new THREE.Shape();
  hsShape.moveTo(-0.044, -0.01);
  hsShape.lineTo(0.044, -0.01);
  hsShape.lineTo(0.068, 0.045);
  hsShape.lineTo(0.064, 0.255);
  hsShape.lineTo(-0.064, 0.255);
  hsShape.lineTo(-0.068, 0.045);
  hsShape.closePath();

  const hsPlateGeo = new THREE.ExtrudeGeometry(hsShape, {
    depth: 0.026,
    bevelEnabled: true,
    bevelThickness: 0.006,
    bevelSize: 0.006,
    bevelSegments: 3,
  });
  hsPlateGeo.rotateX(Math.PI / 2);
  const hsPlate = new THREE.Mesh(hsPlateGeo, brightSilverMat);
  hsPlate.position.set(0, 0.015, 0.068);
  hsPlate.castShadow = true;
  hsPlate.receiveShadow = true;
  headshellPivot.add(hsPlate);

  // Twin oblong dark mounting slots & cylindrical silver cartridge screws
  [-0.030, 0.030].forEach((sx) => {
    const slotRecess = new THREE.Mesh(
      new THREE.BoxGeometry(0.020, 0.010, 0.092),
      darkRecessMat,
    );
    slotRecess.position.set(sx, 0.018, 0.195);
    headshellPivot.add(slotRecess);

    const screw = new THREE.Mesh(
      new THREE.CylinderGeometry(0.013, 0.013, 0.018, 16),
      brightSilverMat,
    );
    screw.position.set(sx, 0.022, 0.185);
    screw.castShadow = true;
    headshellPivot.add(screw);
  });

  // Curved silver finger lift extending outward to right side
  const liftCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0.058, 0.008, 0.195),
    new THREE.Vector3(0.115, 0.012, 0.205),
    new THREE.Vector3(0.145, 0.032, 0.235),
  ]);
  const liftHook = new THREE.Mesh(
    new THREE.TubeGeometry(liftCurve, 14, 0.009, 10, false),
    brightSilverMat,
  );
  liftHook.castShadow = true;
  headshellPivot.add(liftHook);

  // Dark matte cartridge housing & leading nose block + cantilever & diamond stylus
  const cartridge = new THREE.Mesh(
    new THREE.BoxGeometry(0.092, 0.064, 0.165),
    darkRecessMat,
  );
  cartridge.position.set(0, -0.028, 0.220);
  cartridge.castShadow = true;
  headshellPivot.add(cartridge);

  const stylusFrame = new THREE.Mesh(
    new THREE.BoxGeometry(0.066, 0.034, 0.044),
    brushedSilverHardwareMat,
  );
  stylusFrame.position.set(0, -0.020, 0.308);
  stylusFrame.castShadow = true;
  headshellPivot.add(stylusFrame);

  const cantilever = new THREE.Mesh(
    new THREE.CylinderGeometry(0.005, 0.005, 0.042, 8),
    brightSilverMat,
  );
  cantilever.rotation.x = Math.PI / 2.2;
  cantilever.position.set(0, -0.045, 0.320);
  headshellPivot.add(cantilever);

  // Shadow-catching invisible floor for isolated tonearm shadow pass
  const tonearmShadowFloor = new THREE.Mesh(
    new THREE.PlaneGeometry(4.56, 4.56),
    shadowReceiverMat,
  );
  tonearmShadowFloor.rotation.x = -Math.PI / 2;
  tonearmShadowFloor.position.y = 0.178;
  tonearmShadowFloor.receiveShadow = true;
  layerTonearmShadow.add(tonearmShadowFloor);

  // Store original colorWrite flags to allow clean shadow-only renders
  const tonearmMeshes = [];
  layerTonearmMoving.traverse((obj) => {
    if (obj.isMesh) tonearmMeshes.push(obj);
  });

  // Helper to isolate any named layer or composite
  const allLayers = [
    layerChassisShadow,
    layerOuterFrame,
    layerRearHinges,
    layerSplitDeck,
    layerTonearmPivotBase,
    layerMetalPlatter,
    layerVinylRecord,
    layerVinylReflection,
    layerCenterSpindle,
    layerLeftKnob,
    layerRightKnob,
    layerTonearmMoving,
    layerTonearmShadow,
  ];

  function hideAllLayers() {
    allLayers.forEach((g) => {
      g.visible = false;
    });
    // Ensure tonearm materials render normally by default
    setTonearmShadowOnly(false);
  }

  function setTonearmShadowOnly(shadowOnly) {
    tonearmMeshes.forEach((m) => {
      const mats = Array.isArray(m.material) ? m.material : [m.material];
      mats.forEach((mat) => {
        mat.colorWrite = !shadowOnly;
        mat.depthWrite = !shadowOnly;
      });
    });
  }

  function setTonearmAngleDeg(deg) {
    tonearmYawGroup.rotation.y = THREE.MathUtils.degToRad(deg);
  }

  function setKnobPressed(side, pressed) {
    const target = side === 'left' ? leftKnob : rightKnob;
    target.plunger.position.y = pressed ? 0.004 : 0.022;
  }

  function showComposite(armDeg = -19.07) {
    allLayers.forEach((g) => {
      g.visible = true;
    });
    setTonearmShadowOnly(false);
    setKnobPressed('left', false);
    setKnobPressed('right', false);
    setTonearmAngleDeg(armDeg);
  }

  function showLayerPass(passName, armDeg = TONEARM_REST_DEG) {
    hideAllLayers();
    setKnobPressed('left', false);
    setKnobPressed('right', false);

    switch (passName) {
      case 'turntable_base':
        layerChassisShadow.visible = true;
        layerOuterFrame.visible = true;
        layerRearHinges.visible = true;
        layerSplitDeck.visible = true;
        layerTonearmPivotBase.visible = true;
        break;
      case 'outer_frame':
        layerOuterFrame.visible = true;
        break;
      case 'rear_hinges':
        layerRearHinges.visible = true;
        break;
      case 'tonearm_pivot':
        layerTonearmPivotBase.visible = true;
        break;
      case 'metal_platter':
        layerMetalPlatter.visible = true;
        break;
      case 'vinyl_record':
        layerVinylRecord.visible = true;
        break;
      case 'vinyl_reflection':
        layerVinylReflection.visible = true;
        break;
      case 'center_spindle':
        layerCenterSpindle.visible = true;
        break;
      case 'left_knob_normal':
        layerLeftKnob.visible = true;
        setKnobPressed('left', false);
        break;
      case 'left_knob_pressed':
        layerLeftKnob.visible = true;
        setKnobPressed('left', true);
        break;
      case 'right_knob_normal':
        layerRightKnob.visible = true;
        setKnobPressed('right', false);
        break;
      case 'right_knob_pressed':
        layerRightKnob.visible = true;
        setKnobPressed('right', true);
        break;
      case 'tonearm_arm':
        layerTonearmMoving.visible = true;
        setTonearmShadowOnly(false);
        setTonearmAngleDeg(armDeg);
        break;
      case 'tonearm_shadow':
        layerTonearmMoving.visible = true;
        layerTonearmShadow.visible = true;
        setTonearmShadowOnly(true);
        setTonearmAngleDeg(armDeg);
        break;
      default:
        throw new Error(`Unknown layer pass: ${passName}`);
    }
  }

  function dispose() {
    brushedMaps.map.dispose();
    brushedMaps.roughnessMap.dispose();
    brushedMaps.normalMap.dispose();
    matteMaps.roughnessMap.dispose();
    radialKnobMaps.map.dispose();
    cwTex.map.dispose();
    cwTex.normalMap.dispose();
    chassisShadowTex.dispose();
    root.traverse((o) => {
      if (o.geometry) o.geometry.dispose();
      if (o.material) {
        if (Array.isArray(o.material)) o.material.forEach((m) => m.dispose());
        else o.material.dispose();
      }
    });
  }

  return {
    root,
    layerGroups: {
      chassisShadow: layerChassisShadow,
      outerFrame: layerOuterFrame,
      rearHinges: layerRearHinges,
      splitDeck: layerSplitDeck,
      tonearmPivot: layerTonearmPivotBase,
      metalPlatter: layerMetalPlatter,
      vinylRecord: layerVinylRecord,
      vinylReflection: layerVinylReflection,
      centerSpindle: layerCenterSpindle,
      leftKnob: layerLeftKnob,
      rightKnob: layerRightKnob,
      tonearmMoving: layerTonearmMoving,
      tonearmShadow: layerTonearmShadow,
    },
    showComposite,
    showLayerPass,
    setTonearmAngleDeg,
    setKnobPressed,
    dispose,
  };
}
