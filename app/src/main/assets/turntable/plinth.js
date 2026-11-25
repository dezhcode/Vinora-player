import * as THREE from './vendor/three.module.min.js';
import { RoundedBoxGeometry } from './vendor/RoundedBoxGeometry.js';
import { createPlinthBadgeTexture } from './textures.js';

export const PLATTER_CENTER = new THREE.Vector3(-0.18, 0.20, 0.08);

/**
 * Builds the CNC-machined 3D Plinth chassis with chamfered bezel, isolation feet,
 * recessed platter well, rear brass RCA jacks, Torx corner screws, 3D rotary volume dial,
 * and physical ceramic/metal start-stop button.
 */
export function createPlinthAssembly(maps) {
  const group = new THREE.Group();
  group.name = 'PlinthAssembly';

  // --- Materials ---
  const chassisMat = new THREE.MeshPhysicalMaterial({
    color: 0xeaeef5,
    metalness: 0.72,
    roughness: 0.34,
    roughnessMap: maps.brushedRoughness,
    normalMap: maps.brushedNormal,
    normalScale: new THREE.Vector2(0.18, 0.18),
    clearcoat: 0.28,
    clearcoatRoughness: 0.42,
    envMapIntensity: 1.05,
  });

  const trimBevelMat = new THREE.MeshStandardMaterial({
    color: 0xf7f9fc,
    metalness: 0.92,
    roughness: 0.18,
    envMapIntensity: 1.2,
  });

  const darkMetalMat = new THREE.MeshStandardMaterial({
    color: 0x222630,
    metalness: 0.85,
    roughness: 0.32,
  });

  const rubberFootMat = new THREE.MeshStandardMaterial({
    color: 0x16181e,
    metalness: 0.05,
    roughness: 0.88,
    roughnessMap: maps.rubberRoughness,
  });

  const brassMat = new THREE.MeshStandardMaterial({
    color: 0xc99e63,
    metalness: 0.94,
    roughness: 0.22,
    envMapIntensity: 1.25,
  });

  const ceramicBtnMat = new THREE.MeshPhysicalMaterial({
    color: 0xfbfcfe,
    metalness: 0.04,
    roughness: 0.16,
    clearcoat: 0.92,
    clearcoatRoughness: 0.08,
    envMapIntensity: 1.0,
  });

  const ledMat = new THREE.MeshStandardMaterial({
    color: 0x707888,
    emissive: 0xd99b43,
    emissiveIntensity: 0.0,
    roughness: 0.25,
    metalness: 0.1,
  });

  // 1. Main Thick Brushed Aluminum / Titanium Plinth Body
  const plinthWidth = 4.30;
  const plinthHeight = 0.36;
  const plinthDepth = 4.10;
  const bodyGeo = new RoundedBoxGeometry(plinthWidth, plinthHeight, plinthDepth, 6, 0.20);
  const bodyMesh = new THREE.Mesh(bodyGeo, chassisMat);
  bodyMesh.castShadow = true;
  bodyMesh.receiveShadow = true;
  group.add(bodyMesh);

  // Subtle recessed dark acoustic underside skirt + 4 CNC isolation feet
  const skirtGeo = new RoundedBoxGeometry(plinthWidth - 0.18, 0.06, plinthDepth - 0.18, 3, 0.14);
  const skirtMesh = new THREE.Mesh(skirtGeo, darkMetalMat);
  skirtMesh.position.y = -plinthHeight / 2 - 0.025;
  skirtMesh.castShadow = true;
  group.add(skirtMesh);

  const footGeo = new THREE.CylinderGeometry(0.24, 0.21, 0.08, 32);
  const footRingGeo = new THREE.CylinderGeometry(0.245, 0.245, 0.025, 32);
  const footCoords = [
    [-1.72, -1.62],
    [1.72, -1.62],
    [-1.72, 1.62],
    [1.72, 1.62],
  ];
  for (const [fx, fz] of footCoords) {
    const foot = new THREE.Mesh(footGeo, rubberFootMat);
    foot.position.set(fx, -plinthHeight / 2 - 0.075, fz);
    foot.castShadow = true;
    group.add(foot);

    const collar = new THREE.Mesh(footRingGeo, trimBevelMat);
    collar.position.set(fx, -plinthHeight / 2 - 0.045, fz);
    group.add(collar);
  }

  // 2. Machined Top Deck Inner Chamfer Border Frame
  const topY = plinthHeight / 2;
  const wellCenter = new THREE.Vector3(PLATTER_CENTER.x, topY, PLATTER_CENTER.z);

  // Recessed platter well dark shadow floor + precision machined metal bezel collar
  const wellFloorGeo = new THREE.CylinderGeometry(1.69, 1.69, 0.02, 72);
  const wellFloorMat = new THREE.MeshStandardMaterial({
    color: 0x1e222b,
    metalness: 0.35,
    roughness: 0.72,
  });
  const wellFloor = new THREE.Mesh(wellFloorGeo, wellFloorMat);
  wellFloor.position.set(wellCenter.x, topY + 0.002, wellCenter.z);
  wellFloor.receiveShadow = true;
  group.add(wellFloor);

  const wellRimProfile = [
    new THREE.Vector2(1.65, 0.0),
    new THREE.Vector2(1.67, 0.018),
    new THREE.Vector2(1.73, 0.018),
    new THREE.Vector2(1.76, 0.001),
  ];
  const wellRimGeo = new THREE.LatheGeometry(wellRimProfile, 72);
  const wellRim = new THREE.Mesh(wellRimGeo, trimBevelMat);
  wellRim.position.set(wellCenter.x, topY - 0.001, wellCenter.z);
  wellRim.receiveShadow = true;
  group.add(wellRim);

  // 3. Corner Torx Chassis Screws (Real 3D recessed heads + Torx slot)
  const screwGroupGeo = new THREE.CylinderGeometry(0.042, 0.042, 0.012, 16);
  const screwSlotGeo = new THREE.BoxGeometry(0.048, 0.006, 0.011);
  const screwCoords = [
    [-1.92, -1.82],
    [1.92, -1.82],
    [-1.92, 1.82],
    [1.92, 1.82],
  ];
  for (const [sx, sz] of screwCoords) {
    const head = new THREE.Mesh(screwGroupGeo, trimBevelMat);
    head.position.set(sx, topY - 0.001, sz);
    group.add(head);
    const slot = new THREE.Mesh(screwSlotGeo, darkMetalMat);
    slot.position.set(sx, topY + 0.004, sz);
    slot.rotation.y = 0.6;
    group.add(slot);
  }

  // 4. Rear Edge Brass RCA Connectors & Ground Post (Authentic Hi-Fi Backplane Detail!)
  const rcaBodyGeo = new THREE.CylinderGeometry(0.052, 0.052, 0.14, 24);
  rcaBodyGeo.rotateX(Math.PI / 2);
  const rcaPinGeo = new THREE.CylinderGeometry(0.020, 0.020, 0.09, 16);
  rcaPinGeo.rotateX(Math.PI / 2);
  const rcaInsulatorGeo = new THREE.CylinderGeometry(0.042, 0.042, 0.03, 20);
  rcaInsulatorGeo.rotateX(Math.PI / 2);

  const rcaXs = [0.22, 0.46];
  const rcaColors = [0xc95348, 0xf2f4f8]; // Right Red / Left White insulators
  rcaXs.forEach((rx, idx) => {
    const base = new THREE.Mesh(rcaBodyGeo, brassMat);
    base.position.set(rx, 0.02, -plinthDepth / 2 - 0.04);
    base.castShadow = true;
    group.add(base);

    const ins = new THREE.Mesh(
      rcaInsulatorGeo,
      new THREE.MeshStandardMaterial({ color: rcaColors[idx], roughness: 0.4 }),
    );
    ins.position.set(rx, 0.02, -plinthDepth / 2 - 0.10);
    group.add(ins);

    const pin = new THREE.Mesh(rcaPinGeo, brassMat);
    pin.position.set(rx, 0.02, -plinthDepth / 2 - 0.13);
    group.add(pin);
  });

  // Ground knurled thumb screw
  const gndPost = new THREE.Mesh(
    new THREE.CylinderGeometry(0.036, 0.036, 0.10, 20),
    brassMat,
  );
  gndPost.rotation.x = Math.PI / 2;
  gndPost.position.set(0.70, 0.02, -plinthDepth / 2 - 0.045);
  group.add(gndPost);

  // 5. Engraved PLINTH Nameplate on Front-Left Edge
  let badgeTex = createPlinthBadgeTexture(false);
  const badgeMat = new THREE.MeshBasicMaterial({
    map: badgeTex,
    transparent: true,
    opacity: 0.85,
  });
  const badgeMesh = new THREE.Mesh(new THREE.PlaneGeometry(0.76, 0.19), badgeMat);
  badgeMesh.rotation.x = -Math.PI / 2;
  badgeMesh.position.set(-1.38, topY + 0.002, 1.88);
  group.add(badgeMesh);

  // 6. Bottom-Left 3D Rotary Volume / Gain Dial (Knurled skirt + 3-spoke engraved top)
  const knobPos = new THREE.Vector3(-1.62, topY, 1.56);
  const knobBezel = new THREE.Mesh(
    new THREE.RingGeometry(0.24, 0.285, 48),
    trimBevelMat,
  );
  knobBezel.rotation.x = -Math.PI / 2;
  knobBezel.position.set(knobPos.x, topY + 0.002, knobPos.z);
  group.add(knobBezel);

  const volumeKnobGroup = new THREE.Group();
  volumeKnobGroup.name = 'VolumeKnob';
  volumeKnobGroup.position.set(knobPos.x, topY, knobPos.z);

  const knobSkirtGeo = new THREE.CylinderGeometry(0.235, 0.252, 0.11, 48, 1, false);
  const knobSkirtMat = new THREE.MeshStandardMaterial({
    color: 0xd9dfe9,
    metalness: 0.88,
    roughness: 0.26,
    normalMap: maps.knurlNormal,
    normalScale: new THREE.Vector2(0.45, 0.1),
  });
  const knobSkirt = new THREE.Mesh(knobSkirtGeo, knobSkirtMat);
  knobSkirt.position.y = 0.055;
  knobSkirt.castShadow = true;
  knobSkirt.receiveShadow = true;
  volumeKnobGroup.add(knobSkirt);

  const knobTopCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.22, 0.235, 0.02, 48),
    ceramicBtnMat,
  );
  knobTopCap.position.y = 0.115;
  knobTopCap.castShadow = true;
  knobTopCap.receiveShadow = true;
  volumeKnobGroup.add(knobTopCap);

  // Engraved 3-spoke pointer lines on top of the rotary dial
  const spokeMat = new THREE.MeshStandardMaterial({
    color: 0x677185,
    metalness: 0.4,
    roughness: 0.45,
  });
  for (let i = 0; i < 3; i++) {
    const angle = (i * Math.PI * 2) / 3;
    const spoke = new THREE.Mesh(new THREE.BoxGeometry(0.016, 0.008, 0.20), spokeMat);
    spoke.position.set(Math.sin(angle) * 0.10, 0.125, Math.cos(angle) * 0.10);
    spoke.rotation.y = angle;
    volumeKnobGroup.add(spoke);
  }
  const dialDot = new THREE.Mesh(
    new THREE.CylinderGeometry(0.022, 0.022, 0.01, 16),
    darkMetalMat,
  );
  dialDot.position.set(0, 0.126, -0.15);
  volumeKnobGroup.add(dialDot);

  group.add(volumeKnobGroup);

  // 7. Bottom-Right 3D Physical Start/Stop Plunger Button + LED Indicator
  const btnPos = new THREE.Vector3(1.62, topY, 1.56);
  const btnWell = new THREE.Mesh(
    new THREE.TorusGeometry(0.255, 0.022, 16, 48),
    trimBevelMat,
  );
  btnWell.rotation.x = Math.PI / 2;
  btnWell.position.set(btnPos.x, topY + 0.005, btnPos.z);
  group.add(btnWell);

  const startButtonGroup = new THREE.Group();
  startButtonGroup.name = 'StartStopButton';
  startButtonGroup.position.set(btnPos.x, topY, btnPos.z);

  const btnSkirt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.228, 0.238, 0.09, 48),
    trimBevelMat,
  );
  btnSkirt.position.y = 0.045;
  btnSkirt.castShadow = true;
  btnSkirt.receiveShadow = true;
  startButtonGroup.add(btnSkirt);

  const btnCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.215, 0.228, 0.024, 48),
    ceramicBtnMat,
  );
  btnCap.position.y = 0.095;
  btnCap.castShadow = true;
  btnCap.receiveShadow = true;
  startButtonGroup.add(btnCap);

  // Power status pinhole LED
  const ledMesh = new THREE.Mesh(
    new THREE.CylinderGeometry(0.022, 0.022, 0.008, 16),
    ledMat,
  );
  ledMesh.position.set(0, 0.107, 0);
  startButtonGroup.add(ledMesh);

  group.add(startButtonGroup);

  // Theme updater for Light Studio <-> Dark Titanium
  function setTheme(isDark) {
    if (isDark) {
      chassisMat.color.setHex(0x2a303c);
      chassisMat.metalness = 0.78;
      chassisMat.roughness = 0.36;
      trimBevelMat.color.setHex(0x525c70);
      ceramicBtnMat.color.setHex(0x212630);
      knobSkirtMat.color.setHex(0x3a4150);
      wellFloorMat.color.setHex(0x0c0e13);
    } else {
      chassisMat.color.setHex(0xeaeef5);
      chassisMat.metalness = 0.72;
      chassisMat.roughness = 0.34;
      trimBevelMat.color.setHex(0xf7f9fc);
      ceramicBtnMat.color.setHex(0xfbfcfe);
      knobSkirtMat.color.setHex(0xd9dfe9);
      wellFloorMat.color.setHex(0x1e222b);
    }
    const nextBadge = createPlinthBadgeTexture(isDark);
    badgeMat.map = nextBadge;
    badgeMat.needsUpdate = true;
    badgeTex.dispose();
    badgeTex = nextBadge;
  }

  return {
    group,
    volumeKnobGroup,
    startButtonGroup,
    ledMat,
    setTheme,
  };
}
