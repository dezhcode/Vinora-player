import * as THREE from './vendor/three.module.min.js';
import { PLATTER_CENTER } from './plinth.js';
import { VINYL_INNER_GROOVE_R, VINYL_OUTER_GROOVE_R, VINYL_TOP_Y } from './vinyl.js';

const PIVOT_POS = new THREE.Vector3(1.60, 0.17, -1.14);
const EFFECTIVE_LENGTH = 2.26; // Distance from gimbal pivot to diamond stylus tip

const DX_PC = PLATTER_CENTER.x - PIVOT_POS.x;
const DZ_PC = PLATTER_CENTER.z - PIVOT_POS.z;
const DIST_PC = Math.hypot(DX_PC, DZ_PC);
const THETA_PC = Math.atan2(DX_PC, DZ_PC);

const REST_YAW = THREE.MathUtils.degToRad(2.8);

/**
 * Exact gimbal yaw angle so the diamond stylus tracks vinyl grooves from outer rim to inner run-out.
 */
export function computeGrooveYaw(progressFraction) {
  const p = Math.max(0, Math.min(1, progressFraction));
  const r = VINYL_OUTER_GROOVE_R - (VINYL_OUTER_GROOVE_R - VINYL_INNER_GROOVE_R) * p;
  const cosAlpha = Math.max(
    -1,
    Math.min(1, (DIST_PC * DIST_PC + EFFECTIVE_LENGTH * EFFECTIVE_LENGTH - r * r) / (2 * DIST_PC * EFFECTIVE_LENGTH)),
  );
  return THETA_PC + Math.acos(cosAlpha);
}

/**
 * Modern Industrial Articulated Tonearm Assembly (mobile-optimized polygon budgets):
 * - Anodized brushed aluminum mounting flange + anti-skate dial
 * - Precision U-yoke gimbal + knurled counterweight
 * - S-curve matte carbon wand seamlessly joined into a bayonet collar
 * - Skeletonized anodized headshell + machined titanium/silver cartridge + visible cantilever & diamond stylus
 */
export function createTonearmAssembly(maps) {
  const root = new THREE.Group();
  root.name = 'TonearmAssembly';

  const chromeMat = new THREE.MeshStandardMaterial({
    color: 0xe6ecf5,
    metalness: 0.94,
    roughness: 0.15,
    envMapIntensity: 1.25,
  });

  const satinMetalMat = new THREE.MeshStandardMaterial({
    color: 0xc2cad8,
    metalness: 0.86,
    roughness: 0.26,
    envMapIntensity: 1.1,
  });

  const carbonMat = new THREE.MeshStandardMaterial({
    color: 0x1a1e26,
    metalness: 0.52,
    roughness: 0.28,
    envMapIntensity: 1.0,
  });

  const darkAnodizedMat = new THREE.MeshStandardMaterial({
    color: 0x14171e,
    metalness: 0.74,
    roughness: 0.34,
  });

  const knurledMetalMat = new THREE.MeshStandardMaterial({
    color: 0x9aa3b4,
    metalness: 0.90,
    roughness: 0.28,
    normalMap: maps.knurlNormal,
    normalScale: new THREE.Vector2(0.5, 0.16),
  });

  // Modern brushed titanium cartridge housing (replaces warm brown/copper)
  const cartridgeHousingMat = new THREE.MeshStandardMaterial({
    color: 0x3b4252,
    metalness: 0.84,
    roughness: 0.25,
    envMapIntensity: 1.15,
  });

  const rubberMat = new THREE.MeshStandardMaterial({
    color: 0x14161b,
    roughness: 0.86,
    metalness: 0.05,
  });

  // 1. Stationary Mounting Base Plate
  const baseGroup = new THREE.Group();
  baseGroup.position.copy(PIVOT_POS);
  root.add(baseGroup);

  const plateBezel = new THREE.Mesh(
    new THREE.CylinderGeometry(0.35, 0.38, 0.032, 32),
    satinMetalMat,
  );
  plateBezel.position.y = 0.016;
  plateBezel.castShadow = true;
  plateBezel.receiveShadow = true;
  baseGroup.add(plateBezel);

  const plateRecess = new THREE.Mesh(
    new THREE.CylinderGeometry(0.27, 0.27, 0.036, 32),
    darkAnodizedMat,
  );
  plateRecess.position.y = 0.018;
  plateRecess.receiveShadow = true;
  baseGroup.add(plateRecess);

  const screwGeo = new THREE.CylinderGeometry(0.021, 0.021, 0.012, 10);
  for (let i = 0; i < 3; i++) {
    const a = (i * Math.PI * 2) / 3 + 0.4;
    const screw = new THREE.Mesh(screwGeo, chromeMat);
    screw.position.set(Math.cos(a) * 0.315, 0.034, Math.sin(a) * 0.315);
    baseGroup.add(screw);
  }

  // Calibrated Anti-Skate Dial
  const antiSkateGroup = new THREE.Group();
  antiSkateGroup.position.set(-0.27, 0.032, 0.14);
  const asKnob = new THREE.Mesh(
    new THREE.CylinderGeometry(0.066, 0.072, 0.052, 20),
    knurledMetalMat,
  );
  asKnob.position.y = 0.026;
  asKnob.castShadow = true;
  antiSkateGroup.add(asKnob);
  const asCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.056, 0.056, 0.008, 20),
    darkAnodizedMat,
  );
  asCap.position.y = 0.054;
  antiSkateGroup.add(asCap);
  const asMarker = new THREE.Mesh(
    new THREE.BoxGeometry(0.01, 0.01, 0.044),
    chromeMat,
  );
  asMarker.position.set(0, 0.058, -0.02);
  antiSkateGroup.add(asMarker);
  baseGroup.add(antiSkateGroup);

  const pillar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.105, 0.130, 0.14, 24),
    satinMetalMat,
  );
  pillar.position.y = 0.095;
  pillar.castShadow = true;
  baseGroup.add(pillar);

  // 2. Stationary Armrest Dock + Hydraulic Cueing Lifter
  const dockYaw = REST_YAW;
  const dockDist = 1.22;
  const dockPos = new THREE.Vector3(
    PIVOT_POS.x + Math.sin(dockYaw) * dockDist,
    PIVOT_POS.y,
    PIVOT_POS.z + Math.cos(dockYaw) * dockDist,
  );
  const dockGroup = new THREE.Group();
  dockGroup.position.copy(dockPos);
  dockGroup.rotation.y = dockYaw;
  root.add(dockGroup);

  const dockCollar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.060, 0.070, 0.02, 16),
    satinMetalMat,
  );
  dockCollar.position.y = 0.01;
  dockGroup.add(dockCollar);

  const dockStem = new THREE.Mesh(
    new THREE.CylinderGeometry(0.021, 0.025, 0.18, 12),
    chromeMat,
  );
  dockStem.position.y = 0.10;
  dockStem.castShadow = true;
  dockGroup.add(dockStem);

  const cradleBase = new THREE.Mesh(
    new THREE.BoxGeometry(0.108, 0.020, 0.052),
    rubberMat,
  );
  cradleBase.position.y = 0.185;
  dockGroup.add(cradleBase);

  const cradleWallGeo = new THREE.BoxGeometry(0.020, 0.046, 0.052);
  const wallL = new THREE.Mesh(cradleWallGeo, rubberMat);
  wallL.position.set(-0.044, 0.21, 0);
  dockGroup.add(wallL);
  const wallR = new THREE.Mesh(cradleWallGeo, rubberMat);
  wallR.position.set(0.044, 0.21, 0);
  dockGroup.add(wallR);

  const cueingBarGroup = new THREE.Group();
  cueingBarGroup.position.set(PIVOT_POS.x - 0.12, PIVOT_POS.y, PIVOT_POS.z + 0.56);
  const cueStem = new THREE.Mesh(
    new THREE.CylinderGeometry(0.018, 0.022, 0.15, 12),
    chromeMat,
  );
  cueStem.position.y = 0.075;
  cueingBarGroup.add(cueStem);
  const cueArch = new THREE.Mesh(
    new THREE.TorusGeometry(0.32, 0.011, 8, 18, 1.05),
    rubberMat,
  );
  cueArch.rotation.x = Math.PI / 2;
  cueArch.rotation.z = 1.25;
  cueArch.position.set(0.10, 0.145, -0.35);
  cueingBarGroup.add(cueArch);
  root.add(cueingBarGroup);

  // 3. Articulated Yaw Group
  const yawGroup = new THREE.Group();
  yawGroup.name = 'TonearmYaw';
  const pivotHeightY = PIVOT_POS.y + 0.235;
  yawGroup.position.set(PIVOT_POS.x, pivotHeightY, PIVOT_POS.z);
  yawGroup.rotation.y = REST_YAW;
  root.add(yawGroup);

  const yokeBase = new THREE.Mesh(
    new THREE.CylinderGeometry(0.112, 0.112, 0.030, 22),
    satinMetalMat,
  );
  yokeBase.position.y = -0.065;
  yokeBase.castShadow = true;
  yawGroup.add(yokeBase);

  const yokeArmGeo = new THREE.BoxGeometry(0.035, 0.11, 0.088);
  const yokeL = new THREE.Mesh(yokeArmGeo, satinMetalMat);
  yokeL.position.set(-0.092, -0.005, 0);
  yokeL.castShadow = true;
  yawGroup.add(yokeL);
  const yokeR = new THREE.Mesh(yokeArmGeo, satinMetalMat);
  yokeR.position.set(0.092, -0.005, 0);
  yokeR.castShadow = true;
  yawGroup.add(yokeR);

  const pinGeo = new THREE.CylinderGeometry(0.021, 0.021, 0.23, 12);
  pinGeo.rotateZ(Math.PI / 2);
  yawGroup.add(new THREE.Mesh(pinGeo, chromeMat));

  // 4. Articulated Pitch/Lift Group
  const liftGroup = new THREE.Group();
  liftGroup.name = 'TonearmLift';
  yawGroup.add(liftGroup);

  const barrel = new THREE.Mesh(
    new THREE.CylinderGeometry(0.064, 0.064, 0.13, 20),
    carbonMat,
  );
  barrel.rotation.z = Math.PI / 2;
  barrel.castShadow = true;
  liftGroup.add(barrel);

  const topGimbalCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.054, 0.062, 0.036, 20),
    chromeMat,
  );
  topGimbalCap.position.y = 0.060;
  liftGroup.add(topGimbalCap);

  // Rear Counterweight
  const stub = new THREE.Mesh(
    new THREE.CylinderGeometry(0.025, 0.025, 0.40, 14),
    chromeMat,
  );
  stub.rotation.x = Math.PI / 2;
  stub.position.set(0, 0, -0.21);
  liftGroup.add(stub);

  const cwGroup = new THREE.Group();
  cwGroup.position.set(0, 0, -0.31);
  const cwBody = new THREE.Mesh(
    new THREE.CylinderGeometry(0.096, 0.096, 0.18, 24),
    knurledMetalMat,
  );
  cwBody.rotation.x = Math.PI / 2;
  cwBody.castShadow = true;
  cwGroup.add(cwBody);

  const cwFrontRing = new THREE.Mesh(
    new THREE.CylinderGeometry(0.099, 0.099, 0.030, 24),
    darkAnodizedMat,
  );
  cwFrontRing.rotation.x = Math.PI / 2;
  cwFrontRing.position.z = 0.084;
  cwGroup.add(cwFrontRing);

  const cwRearCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.070, 0.096, 0.024, 20),
    chromeMat,
  );
  cwRearCap.rotation.x = Math.PI / 2;
  cwRearCap.position.z = -0.102;
  cwGroup.add(cwRearCap);
  liftGroup.add(cwGroup);

  // Precision S-Curve Carbon Wand terminating cleanly inside the Headshell Bayonet Collar
  const stylusRelY = VINYL_TOP_Y - pivotHeightY;
  const shellY = stylusRelY + 0.118;
  const headshellYaw = THREE.MathUtils.degToRad(-16);
  const bayonetLocalZ = -0.225;

  const bayonetPos = new THREE.Vector3(
    bayonetLocalZ * Math.sin(headshellYaw),
    shellY,
    EFFECTIVE_LENGTH + bayonetLocalZ * Math.cos(headshellYaw),
  );
  const preBayonetPos = new THREE.Vector3(
    (bayonetLocalZ - 0.22) * Math.sin(headshellYaw),
    shellY + 0.004,
    EFFECTIVE_LENGTH + (bayonetLocalZ - 0.22) * Math.cos(headshellYaw),
  );

  const wandCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0, 0.006, 0.05),
    new THREE.Vector3(0.065, 0.006, 0.65),
    new THREE.Vector3(0.116, 0.004, 1.30),
    preBayonetPos,
    bayonetPos,
  ]);
  const wandMesh = new THREE.Mesh(
    new THREE.TubeGeometry(wandCurve, 28, 0.025, 12, false),
    carbonMat,
  );
  wandMesh.castShadow = true;
  liftGroup.add(wandMesh);

  // 5. Connected Headshell, Bayonet Collar, Titanium Cartridge & Delicate Diamond Stylus
  const headshellGroup = new THREE.Group();
  headshellGroup.position.set(0, 0, EFFECTIVE_LENGTH);
  headshellGroup.rotation.y = headshellYaw;
  liftGroup.add(headshellGroup);

  const bayonetCollar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.035, 0.032, 0.084, 16),
    chromeMat,
  );
  bayonetCollar.rotation.x = Math.PI / 2;
  bayonetCollar.position.set(0, shellY, bayonetLocalZ);
  bayonetCollar.castShadow = true;
  headshellGroup.add(bayonetCollar);

  const shellNeck = new THREE.Mesh(
    new THREE.CylinderGeometry(0.027, 0.027, 0.072, 14),
    darkAnodizedMat,
  );
  shellNeck.rotation.x = Math.PI / 2;
  shellNeck.position.set(0, shellY, bayonetLocalZ + 0.045);
  headshellGroup.add(shellNeck);

  // Skeletonized CNC Headshell Twin Side Rails
  const railGeo = new THREE.BoxGeometry(0.021, 0.021, 0.25);
  [-0.047, 0.047].forEach((rx) => {
    const rail = new THREE.Mesh(railGeo, darkAnodizedMat);
    rail.position.set(rx, shellY, -0.095);
    rail.castShadow = true;
    headshellGroup.add(rail);
  });

  const rearBridge = new THREE.Mesh(
    new THREE.BoxGeometry(0.116, 0.021, 0.054),
    darkAnodizedMat,
  );
  rearBridge.position.set(0, shellY, -0.195);
  headshellGroup.add(rearBridge);

  const shellTopBridge = new THREE.Mesh(
    new THREE.BoxGeometry(0.112, 0.015, 0.070),
    satinMetalMat,
  );
  shellTopBridge.position.set(0, shellY + 0.006, -0.032);
  shellTopBridge.castShadow = true;
  headshellGroup.add(shellTopBridge);

  const cartScrewGeo = new THREE.CylinderGeometry(0.012, 0.012, 0.020, 10);
  [-0.032, 0.032].forEach((sx) => {
    const s = new THREE.Mesh(cartScrewGeo, chromeMat);
    s.position.set(sx, shellY + 0.014, -0.032);
    headshellGroup.add(s);
  });

  const fingerLiftCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0.047, shellY, -0.02),
    new THREE.Vector3(0.125, shellY + 0.006, 0.01),
    new THREE.Vector3(0.152, shellY + 0.030, 0.042),
  ]);
  const fingerLiftMesh = new THREE.Mesh(
    new THREE.TubeGeometry(fingerLiftCurve, 10, 0.008, 8, false),
    chromeMat,
  );
  fingerLiftMesh.castShadow = true;
  headshellGroup.add(fingerLiftMesh);

  // Machined Anodized Titanium Cartridge Body + Brushed Silver Nose
  const cartridgeBody = new THREE.Mesh(
    new THREE.BoxGeometry(0.090, 0.064, 0.152),
    cartridgeHousingMat,
  );
  cartridgeBody.position.set(0, shellY - 0.040, -0.042);
  cartridgeBody.castShadow = true;
  headshellGroup.add(cartridgeBody);

  const cartridgeNose = new THREE.Mesh(
    new THREE.BoxGeometry(0.066, 0.040, 0.040),
    satinMetalMat,
  );
  cartridgeNose.position.set(0, shellY - 0.048, 0.042);
  cartridgeNose.castShadow = true;
  headshellGroup.add(cartridgeNose);

  // Subtle monochrome/silver lead wires
  const wireMat = new THREE.MeshStandardMaterial({ color: 0xb0b8c8, roughness: 0.4 });
  [-0.024, -0.008, 0.008, 0.024].forEach((wx) => {
    const wireCurve = new THREE.CatmullRomCurve3([
      new THREE.Vector3(wx, shellY - 0.030, -0.118),
      new THREE.Vector3(wx * 1.05, shellY - 0.012, -0.165),
      new THREE.Vector3(wx * 0.45, shellY - 0.002, bayonetLocalZ + 0.015),
    ]);
    headshellGroup.add(new THREE.Mesh(new THREE.TubeGeometry(wireCurve, 6, 0.0045, 5, false), wireMat));
  });

  // Elastomer suspension damper + Cantilever + Precision Diamond Stylus Tip
  const damperCollar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.012, 0.012, 0.022, 10),
    rubberMat,
  );
  damperCollar.rotation.x = Math.PI / 2.6;
  damperCollar.position.set(0, shellY - 0.066, -0.014);
  headshellGroup.add(damperCollar);

  const cantileverCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0, shellY - 0.064, -0.018),
    new THREE.Vector3(0, stylusRelY + 0.015, 0.0),
  ]);
  headshellGroup.add(new THREE.Mesh(new THREE.TubeGeometry(cantileverCurve, 6, 0.0065, 8, false), chromeMat));

  const stylusCone = new THREE.Mesh(
    new THREE.ConeGeometry(0.008, 0.018, 10),
    chromeMat,
  );
  stylusCone.rotation.x = Math.PI;
  stylusCone.position.set(0, stylusRelY + 0.009, 0.0);
  headshellGroup.add(stylusCone);

  function setTheme(isDark) {
    satinMetalMat.color.setHex(isDark ? 0x4c5566 : 0xc2cad8);
    chromeMat.color.setHex(isDark ? 0xcdd6e4 : 0xe6ecf5);
    knurledMetalMat.color.setHex(isDark ? 0x626b7d : 0x9aa3b4);
    cartridgeHousingMat.color.setHex(isDark ? 0x566074 : 0x3b4252);
  }

  function dispose() {
    root.traverse((obj) => {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) {
        if (Array.isArray(obj.material)) obj.material.forEach((m) => m.dispose());
        else obj.material.dispose();
      }
    });
  }

  return {
    root,
    yawGroup,
    liftGroup,
    cueStem,
    cueArch,
    REST_YAW,
    setTheme,
    dispose,
  };
}
