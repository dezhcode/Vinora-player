import * as THREE from './vendor/three.module.min.js';
import { PLATTER_CENTER } from './plinth.js';
import { VINYL_INNER_GROOVE_R, VINYL_OUTER_GROOVE_R, VINYL_TOP_Y } from './vinyl.js';

const PIVOT_POS = new THREE.Vector3(1.60, 0.18, -1.16);
const EFFECTIVE_LENGTH = 2.28; // Distance from gimbal pivot to diamond stylus tip

// Precompute exact planar vector from Pivot -> Platter Center
const DX_PC = PLATTER_CENTER.x - PIVOT_POS.x; // -1.78
const DZ_PC = PLATTER_CENTER.z - PIVOT_POS.z; // +1.24
const DIST_PC = Math.hypot(DX_PC, DZ_PC); // ~2.1693
const THETA_PC = Math.atan2(DX_PC, DZ_PC); // ~ -55.14 deg

const REST_YAW = THREE.MathUtils.degToRad(2.8); // Cleanly parked over armrest dock

/**
 * Computes exact gimbal yaw angle (radians around +Y) so the stylus needle
 * tracks directly across the vinyl grooves from outer rim to inner run-out.
 */
export function computeGrooveYaw(progressFraction) {
  const p = Math.max(0, Math.min(1, progressFraction));
  const r = VINYL_OUTER_GROOVE_R - (VINYL_OUTER_GROOVE_R - VINYL_INNER_GROOVE_R) * p;
  const cosAlpha = Math.max(
    -1,
    Math.min(1, (DIST_PC * DIST_PC + EFFECTIVE_LENGTH * EFFECTIVE_LENGTH - r * r) / (2 * DIST_PC * EFFECTIVE_LENGTH)),
  );
  const alpha = Math.acos(cosAlpha);
  return THETA_PC + alpha;
}

/**
 * Builds an articulated, multi-part audiophile mechanical tonearm assembly:
 * - Circular machined mounting base + 3 slotted screws + anti-skate dial
 * - Vertical pillar + U-shaped gimbal bearing yoke + horizontal pivot pins
 * - Cueing lift bridge + rubberized armrest dock
 * - Rear stub + knurled counterweight cylinder + tracking force ring
 * - Curved S-shaped brushed/carbon tonearm tube (TubeGeometry)
 * - Skeletonized angled headshell + finger lift + mounting screws + 4 color-coded lead wires
 * - Copper-anodized MC cartridge + angled cantilever + diamond stylus tip
 */
export function createTonearmAssembly(maps) {
  const root = new THREE.Group();
  root.name = 'TonearmAssembly';

  // --- Precision Materials ---
  const chromeMat = new THREE.MeshStandardMaterial({
    color: 0xeaf0f8,
    metalness: 0.96,
    roughness: 0.14,
    envMapIntensity: 1.35,
  });

  const satinMetalMat = new THREE.MeshStandardMaterial({
    color: 0xc2cad8,
    metalness: 0.88,
    roughness: 0.28,
    envMapIntensity: 1.15,
  });

  const carbonMat = new THREE.MeshPhysicalMaterial({
    color: 0x1c2029,
    metalness: 0.45,
    roughness: 0.28,
    clearcoat: 0.65,
    clearcoatRoughness: 0.18,
    envMapIntensity: 1.1,
  });

  const darkAnodizedMat = new THREE.MeshStandardMaterial({
    color: 0x161920,
    metalness: 0.72,
    roughness: 0.34,
  });

  const knurledMetalMat = new THREE.MeshStandardMaterial({
    color: 0x9aa3b4,
    metalness: 0.92,
    roughness: 0.28,
    normalMap: maps.knurlNormal,
    normalScale: new THREE.Vector2(0.55, 0.18),
  });

  const cartridgeCopperMat = new THREE.MeshPhysicalMaterial({
    color: 0xb85c42,
    metalness: 0.82,
    roughness: 0.26,
    clearcoat: 0.45,
  });

  const rubberMat = new THREE.MeshStandardMaterial({
    color: 0x15171c,
    roughness: 0.86,
    metalness: 0.05,
  });

  // 1. Stationary Mounting Base Plate at Pivot
  const baseGroup = new THREE.Group();
  baseGroup.position.copy(PIVOT_POS);
  root.add(baseGroup);

  const plateBezel = new THREE.Mesh(
    new THREE.CylinderGeometry(0.36, 0.39, 0.032, 48),
    satinMetalMat,
  );
  plateBezel.position.y = 0.016;
  plateBezel.castShadow = true;
  plateBezel.receiveShadow = true;
  baseGroup.add(plateBezel);

  const plateRecess = new THREE.Mesh(
    new THREE.CylinderGeometry(0.28, 0.28, 0.036, 48),
    darkAnodizedMat,
  );
  plateRecess.position.y = 0.018;
  plateRecess.receiveShadow = true;
  baseGroup.add(plateRecess);

  // 3 Miniature Mounting Screws on Flange
  const screwGeo = new THREE.CylinderGeometry(0.022, 0.022, 0.012, 14);
  for (let i = 0; i < 3; i++) {
    const a = (i * Math.PI * 2) / 3 + 0.4;
    const screw = new THREE.Mesh(screwGeo, chromeMat);
    screw.position.set(Math.cos(a) * 0.325, 0.034, Math.sin(a) * 0.325);
    baseGroup.add(screw);
  }

  // Calibrated Anti-Skate Dial beside Gimbal
  const antiSkateGroup = new THREE.Group();
  antiSkateGroup.position.set(-0.27, 0.032, 0.14);
  const asKnob = new THREE.Mesh(
    new THREE.CylinderGeometry(0.068, 0.074, 0.055, 24),
    knurledMetalMat,
  );
  asKnob.position.y = 0.027;
  asKnob.castShadow = true;
  antiSkateGroup.add(asKnob);
  const asCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.058, 0.058, 0.008, 24),
    darkAnodizedMat,
  );
  asCap.position.y = 0.056;
  antiSkateGroup.add(asCap);
  const asMarker = new THREE.Mesh(
    new THREE.BoxGeometry(0.01, 0.01, 0.045),
    chromeMat,
  );
  asMarker.position.set(0, 0.06, -0.02);
  antiSkateGroup.add(asMarker);
  baseGroup.add(antiSkateGroup);

  // Stationary Vertical Gimbal Pillar
  const pillar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.11, 0.135, 0.14, 32),
    satinMetalMat,
  );
  pillar.position.y = 0.095;
  pillar.castShadow = true;
  baseGroup.add(pillar);

  // 2. Stationary Armrest Dock + Hydraulic Cueing Lifter Platform
  const dockYaw = REST_YAW;
  const dockDist = 1.24;
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
    new THREE.CylinderGeometry(0.062, 0.072, 0.02, 20),
    satinMetalMat,
  );
  dockCollar.position.y = 0.01;
  dockGroup.add(dockCollar);

  const dockStem = new THREE.Mesh(
    new THREE.CylinderGeometry(0.022, 0.026, 0.18, 16),
    chromeMat,
  );
  dockStem.position.y = 0.10;
  dockStem.castShadow = true;
  dockGroup.add(dockStem);

  // Rubber U-shaped Arm Cradle
  const cradleBase = new THREE.Mesh(
    new THREE.BoxGeometry(0.11, 0.022, 0.055),
    rubberMat,
  );
  cradleBase.position.y = 0.185;
  cradleBase.castShadow = true;
  dockGroup.add(cradleBase);

  const cradleWallGeo = new THREE.BoxGeometry(0.022, 0.048, 0.055);
  const wallL = new THREE.Mesh(cradleWallGeo, rubberMat);
  wallL.position.set(-0.044, 0.21, 0);
  dockGroup.add(wallL);
  const wallR = new THREE.Mesh(cradleWallGeo, rubberMat);
  wallR.position.set(0.044, 0.21, 0);
  dockGroup.add(wallR);

  // Cueing Lifter Arc Bar near inner tonearm arc
  const cueingBarGroup = new THREE.Group();
  cueingBarGroup.position.set(PIVOT_POS.x - 0.12, PIVOT_POS.y, PIVOT_POS.z + 0.56);
  const cueStem = new THREE.Mesh(
    new THREE.CylinderGeometry(0.018, 0.022, 0.15, 16),
    chromeMat,
  );
  cueStem.position.y = 0.075;
  cueingBarGroup.add(cueStem);
  const cueArch = new THREE.Mesh(
    new THREE.TorusGeometry(0.32, 0.011, 10, 24, 1.05),
    rubberMat,
  );
  cueArch.rotation.x = Math.PI / 2;
  cueArch.rotation.z = 1.25;
  cueArch.position.set(0.10, 0.145, -0.35);
  cueingBarGroup.add(cueArch);
  root.add(cueingBarGroup);

  // 3. Articulated Yaw Group (rotates horizontally around gimbal Y axis)
  const yawGroup = new THREE.Group();
  yawGroup.name = 'TonearmYaw';
  const pivotHeightY = PIVOT_POS.y + 0.235; // Height of horizontal gimbal bearing axis
  yawGroup.position.set(PIVOT_POS.x, pivotHeightY, PIVOT_POS.z);
  yawGroup.rotation.y = REST_YAW;
  root.add(yawGroup);

  // Machined U-shaped Gimbal Bearing Yoke (rotates with yaw)
  const yokeBase = new THREE.Mesh(
    new THREE.CylinderGeometry(0.115, 0.115, 0.032, 28),
    satinMetalMat,
  );
  yokeBase.position.y = -0.065;
  yokeBase.castShadow = true;
  yawGroup.add(yokeBase);

  const yokeArmGeo = new THREE.BoxGeometry(0.036, 0.11, 0.09);
  const yokeL = new THREE.Mesh(yokeArmGeo, satinMetalMat);
  yokeL.position.set(-0.095, -0.005, 0);
  yokeL.castShadow = true;
  yawGroup.add(yokeL);
  const yokeR = new THREE.Mesh(yokeArmGeo, satinMetalMat);
  yokeR.position.set(0.095, -0.005, 0);
  yokeR.castShadow = true;
  yawGroup.add(yokeR);

  // Precision Horizontal Bearing Pins & Polished Cap
  const pinGeo = new THREE.CylinderGeometry(0.022, 0.022, 0.24, 16);
  pinGeo.rotateZ(Math.PI / 2);
  const bearingPin = new THREE.Mesh(pinGeo, chromeMat);
  yawGroup.add(bearingPin);

  // 4. Articulated Pitch/Lift Group (lifts vertically around gimbal X axis)
  const liftGroup = new THREE.Group();
  liftGroup.name = 'TonearmLift';
  yawGroup.add(liftGroup);

  // Central Gimbal Barrel Housing
  const barrel = new THREE.Mesh(
    new THREE.CylinderGeometry(0.066, 0.066, 0.135, 28),
    carbonMat,
  );
  barrel.rotation.z = Math.PI / 2;
  barrel.castShadow = true;
  liftGroup.add(barrel);

  const topGimbalCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.056, 0.064, 0.038, 28),
    chromeMat,
  );
  topGimbalCap.position.y = 0.062;
  topGimbalCap.castShadow = true;
  liftGroup.add(topGimbalCap);

  // Rear Counterweight Stub Shaft + Knurled Counterweight + Brass Scale Ring
  const stub = new THREE.Mesh(
    new THREE.CylinderGeometry(0.026, 0.026, 0.42, 20),
    chromeMat,
  );
  stub.rotation.x = Math.PI / 2;
  stub.position.set(0, 0, -0.22);
  stub.castShadow = true;
  liftGroup.add(stub);

  const cwGroup = new THREE.Group();
  cwGroup.position.set(0, 0, -0.32);
  const cwBody = new THREE.Mesh(
    new THREE.CylinderGeometry(0.098, 0.098, 0.19, 36),
    knurledMetalMat,
  );
  cwBody.rotation.x = Math.PI / 2;
  cwBody.castShadow = true;
  cwBody.receiveShadow = true;
  cwGroup.add(cwBody);

  const cwFrontRing = new THREE.Mesh(
    new THREE.CylinderGeometry(0.101, 0.101, 0.032, 36),
    darkAnodizedMat,
  );
  cwFrontRing.rotation.x = Math.PI / 2;
  cwFrontRing.position.z = 0.088;
  cwGroup.add(cwFrontRing);

  const cwRearCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.072, 0.098, 0.025, 32),
    chromeMat,
  );
  cwRearCap.rotation.x = Math.PI / 2;
  cwRearCap.position.z = -0.106;
  cwGroup.add(cwRearCap);
  liftGroup.add(cwGroup);

  // Calibrated Tonearm Wand (S-shaped precision curve terminating directly inside the headshell bayonet socket)
  // At lift = 0, stylus tip sits at exact (0, stylusRelY, EFFECTIVE_LENGTH) in liftGroup space.
  const stylusRelY = VINYL_TOP_Y - pivotHeightY; // e.g., 0.288 - 0.415 = -0.127
  const shellY = stylusRelY + 0.118;
  const headshellYaw = THREE.MathUtils.degToRad(-16);
  const bayonetLocalZ = -0.225;

  // Exact liftGroup-space coordinate of the headshell's rear bayonet socket
  const bayonetPos = new THREE.Vector3(
    bayonetLocalZ * Math.sin(headshellYaw),
    shellY,
    EFFECTIVE_LENGTH + bayonetLocalZ * Math.cos(headshellYaw),
  );

  // Smooth tangent entry vector matching the headshell's -16 deg angle
  const preBayonetPos = new THREE.Vector3(
    (bayonetLocalZ - 0.22) * Math.sin(headshellYaw),
    shellY + 0.004,
    EFFECTIVE_LENGTH + (bayonetLocalZ - 0.22) * Math.cos(headshellYaw),
  );

  const wandCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0, 0.006, 0.05),
    new THREE.Vector3(0.065, 0.006, 0.66),
    new THREE.Vector3(0.118, 0.004, 1.32),
    preBayonetPos,
    bayonetPos,
  ]);
  const wandGeo = new THREE.TubeGeometry(wandCurve, 48, 0.026, 18, false);
  const wandMesh = new THREE.Mesh(wandGeo, carbonMat);
  wandMesh.castShadow = true;
  wandMesh.receiveShadow = true;
  liftGroup.add(wandMesh);

  // 5. Headshell, Bayonet Coupling, Finger Lift, Lead Wires, MC Cartridge & Diamond Stylus
  const headshellGroup = new THREE.Group();
  headshellGroup.position.set(0, 0, EFFECTIVE_LENGTH);
  headshellGroup.rotation.y = headshellYaw;
  liftGroup.add(headshellGroup);

  // Machined Chrome Bayonet Coupling Collar seated directly over the wand-to-headshell junction
  const bayonetCollar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.036, 0.033, 0.085, 24),
    chromeMat,
  );
  bayonetCollar.rotation.x = Math.PI / 2;
  bayonetCollar.position.set(0, shellY, bayonetLocalZ);
  bayonetCollar.castShadow = true;
  headshellGroup.add(bayonetCollar);

  // Solid cylindrical neck bridge locking bayonet collar straight into the headshell body
  const shellNeck = new THREE.Mesh(
    new THREE.CylinderGeometry(0.028, 0.028, 0.075, 20),
    darkAnodizedMat,
  );
  shellNeck.rotation.x = Math.PI / 2;
  shellNeck.position.set(0, shellY, bayonetLocalZ + 0.045);
  headshellGroup.add(shellNeck);

  // Skeletonized CNC Headshell Frame (twin side rails + rear bridge so cartridge, lead wires, and stylus stay cleanly visible)
  const railGeo = new THREE.BoxGeometry(0.022, 0.022, 0.255);
  [-0.048, 0.048].forEach((rx) => {
    const rail = new THREE.Mesh(railGeo, darkAnodizedMat);
    rail.position.set(rx, shellY, -0.095);
    rail.castShadow = true;
    headshellGroup.add(rail);
  });

  // Rear headshell yoke collar-bridge + top cartridge mounting crossbar
  const rearBridge = new THREE.Mesh(
    new THREE.BoxGeometry(0.118, 0.022, 0.055),
    darkAnodizedMat,
  );
  rearBridge.position.set(0, shellY, -0.195);
  rearBridge.castShadow = true;
  headshellGroup.add(rearBridge);

  const shellTopBridge = new THREE.Mesh(
    new THREE.BoxGeometry(0.114, 0.016, 0.072),
    satinMetalMat,
  );
  shellTopBridge.position.set(0, shellY + 0.006, -0.032);
  shellTopBridge.castShadow = true;
  headshellGroup.add(shellTopBridge);

  const cartScrewGeo = new THREE.CylinderGeometry(0.013, 0.013, 0.022, 12);
  [-0.032, 0.032].forEach((sx) => {
    const s = new THREE.Mesh(cartScrewGeo, chromeMat);
    s.position.set(sx, shellY + 0.014, -0.032);
    headshellGroup.add(s);
  });

  // Curved Chrome Finger-Lift Lever projecting cleanly out of the headshell side
  const fingerLiftCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0.048, shellY, -0.02),
    new THREE.Vector3(0.13, shellY + 0.006, 0.01),
    new THREE.Vector3(0.158, shellY + 0.032, 0.045),
  ]);
  const fingerLiftMesh = new THREE.Mesh(
    new THREE.TubeGeometry(fingerLiftCurve, 14, 0.0085, 10, false),
    chromeMat,
  );
  fingerLiftMesh.castShadow = true;
  headshellGroup.add(fingerLiftMesh);

  // Copper Moving-Coil Cartridge Body bolted flush under the headshell crossbar with tapered visible front nose
  const cartridgeBody = new THREE.Mesh(
    new THREE.BoxGeometry(0.092, 0.066, 0.155),
    cartridgeCopperMat,
  );
  cartridgeBody.position.set(0, shellY - 0.040, -0.042);
  cartridgeBody.castShadow = true;
  headshellGroup.add(cartridgeBody);

  // Tapered brushed-chrome front cartridge nose & open stylus viewport
  const cartridgeNose = new THREE.Mesh(
    new THREE.BoxGeometry(0.068, 0.042, 0.042),
    satinMetalMat,
  );
  cartridgeNose.position.set(0, shellY - 0.048, 0.042);
  cartridgeNose.castShadow = true;
  headshellGroup.add(cartridgeNose);

  // 4 Miniature Color-Coded Lead Wires bridging cartridge pins into the bayonet collar (visible through open rear frame)
  const wireColors = [0xd14b4b, 0xf2f4f8, 0x3b78d8, 0x45a866];
  wireColors.forEach((col, wi) => {
    const wx = -0.028 + wi * 0.0185;
    const wireCurve = new THREE.CatmullRomCurve3([
      new THREE.Vector3(wx, shellY - 0.030, -0.118),
      new THREE.Vector3(wx * 1.05, shellY - 0.012, -0.165),
      new THREE.Vector3(wx * 0.45, shellY - 0.002, bayonetLocalZ + 0.015),
    ]);
    const wire = new THREE.Mesh(
      new THREE.TubeGeometry(wireCurve, 8, 0.005, 6, false),
      new THREE.MeshStandardMaterial({ color: col, roughness: 0.45 }),
    );
    headshellGroup.add(wire);
  });

  // Elastomer stylus suspension damper + Cantilever Rod + Diamond Stylus Tip
  // Cantilever projects forward & downward right below the cartridge nose so the needle contact is crystal clear
  const damperCollar = new THREE.Mesh(
    new THREE.CylinderGeometry(0.013, 0.013, 0.024, 12),
    rubberMat,
  );
  damperCollar.rotation.x = Math.PI / 2.6;
  damperCollar.position.set(0, shellY - 0.066, -0.014);
  headshellGroup.add(damperCollar);

  const cantileverCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0, shellY - 0.064, -0.018),
    new THREE.Vector3(0, stylusRelY + 0.016, 0.0),
  ]);
  const cantilever = new THREE.Mesh(
    new THREE.TubeGeometry(cantileverCurve, 8, 0.007, 10, false),
    chromeMat,
  );
  headshellGroup.add(cantilever);

  const stylusCone = new THREE.Mesh(
    new THREE.ConeGeometry(0.0085, 0.019, 12),
    chromeMat,
  );
  stylusCone.rotation.x = Math.PI;
  stylusCone.position.set(0, stylusRelY + 0.0095, 0.0);
  headshellGroup.add(stylusCone);

  function setTheme(isDark) {
    satinMetalMat.color.setHex(isDark ? 0x4a5262 : 0xc2cad8);
    chromeMat.color.setHex(isDark ? 0xcad3e2 : 0xeaf0f8);
    knurledMetalMat.color.setHex(isDark ? 0x626b7d : 0x9aa3b4);
  }

  return {
    root,
    yawGroup,
    liftGroup,
    cueStem,
    cueArch,
    REST_YAW,
    setTheme,
  };
}
