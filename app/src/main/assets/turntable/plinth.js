import * as THREE from './vendor/three.module.min.js';
import { RoundedBoxGeometry } from './vendor/RoundedBoxGeometry.js';
import { createBakedShadowTexture, createPlinthBadgeTexture } from './textures.js';

export const PLATTER_CENTER = new THREE.Vector3(-0.18, 0.20, 0.08);

/**
 * Modern Industrial CNC-Machined Horizontal Plinth Chassis:
 * - Precision brushed aluminum / titanium body with crisp chamfered edges (low-poly mobile optimized)
 * - Pre-baked soft ambient contact shadow + recessed dark anodized platter well
 * - Corner Torx screws, sleek rear silver terminals, rotary volume dial, and Start/Stop key
 */
export function createPlinthAssembly(maps) {
  const group = new THREE.Group();
  group.name = 'PlinthAssembly';

  // --- Modern Industrial Monochrome & Aluminum Materials ---
  const chassisMat = new THREE.MeshStandardMaterial({
    color: 0xeaf0f7,
    metalness: 0.72,
    roughness: 0.34,
    roughnessMap: maps.brushedRoughness,
    normalMap: maps.brushedNormal,
    normalScale: new THREE.Vector2(0.16, 0.16),
    envMapIntensity: 1.0,
  });

  const trimBevelMat = new THREE.MeshStandardMaterial({
    color: 0xf7f9fc,
    metalness: 0.90,
    roughness: 0.18,
    envMapIntensity: 1.15,
  });

  const darkMetalMat = new THREE.MeshStandardMaterial({
    color: 0x1c2029,
    metalness: 0.82,
    roughness: 0.34,
  });

  const ceramicBtnMat = new THREE.MeshStandardMaterial({
    color: 0xfafbfd,
    metalness: 0.08,
    roughness: 0.20,
    envMapIntensity: 0.95,
  });

  const ledMat = new THREE.MeshStandardMaterial({
    color: 0x697282,
    emissive: 0x9ec2ff,
    emissiveIntensity: 0.0,
    roughness: 0.22,
    metalness: 0.15,
  });

  // 1. Flat Horizontal CNC-Machined Aluminum Plinth Body (low segment count for fast mobile rendering)
  const plinthWidth = 4.24;
  const plinthHeight = 0.34;
  const plinthDepth = 4.04;
  const bodyGeo = new RoundedBoxGeometry(plinthWidth, plinthHeight, plinthDepth, 3, 0.18);
  const bodyMesh = new THREE.Mesh(bodyGeo, chassisMat);
  bodyMesh.castShadow = true;
  bodyMesh.receiveShadow = true;
  group.add(bodyMesh);

  // Subtle dark anodized acoustic sub-chassis lip visible around perimeter
  const skirtGeo = new RoundedBoxGeometry(plinthWidth - 0.12, 0.05, plinthDepth - 0.12, 2, 0.12);
  const skirtMesh = new THREE.Mesh(skirtGeo, darkMetalMat);
  skirtMesh.position.y = -plinthHeight / 2 - 0.022;
  group.add(skirtMesh);

  // Pre-baked ambient occlusion / contact shadow beneath the horizontal body (zero runtime shadow cost)
  const bakedShadowTex = createBakedShadowTexture();
  const bakedShadowMat = new THREE.MeshBasicMaterial({
    map: bakedShadowTex,
    transparent: true,
    opacity: 0.72,
    depthWrite: false,
  });
  const bakedShadowMesh = new THREE.Mesh(new THREE.PlaneGeometry(4.95, 4.75), bakedShadowMat);
  bakedShadowMesh.rotation.x = -Math.PI / 2;
  bakedShadowMesh.position.y = -plinthHeight / 2 - 0.055;
  group.add(bakedShadowMesh);

  // 2. Machined Recessed Platter Well + Aluminum Collar
  const topY = plinthHeight / 2;
  const wellCenter = new THREE.Vector3(PLATTER_CENTER.x, topY, PLATTER_CENTER.z);

  const wellFloorGeo = new THREE.CylinderGeometry(1.68, 1.68, 0.018, 56);
  const wellFloorMat = new THREE.MeshStandardMaterial({
    color: 0x161920,
    metalness: 0.35,
    roughness: 0.76,
  });
  const wellFloor = new THREE.Mesh(wellFloorGeo, wellFloorMat);
  wellFloor.position.set(wellCenter.x, topY + 0.002, wellCenter.z);
  wellFloor.receiveShadow = true;
  group.add(wellFloor);

  const wellRimProfile = [
    new THREE.Vector2(1.64, 0.0),
    new THREE.Vector2(1.66, 0.018),
    new THREE.Vector2(1.72, 0.018),
    new THREE.Vector2(1.75, 0.001),
  ];
  const wellRimGeo = new THREE.LatheGeometry(wellRimProfile, 56);
  const wellRim = new THREE.Mesh(wellRimGeo, trimBevelMat);
  wellRim.position.set(wellCenter.x, topY - 0.001, wellCenter.z);
  wellRim.receiveShadow = true;
  group.add(wellRim);

  // 3. Corner Torx Precision Screws (InstancedMesh single draw call)
  const screwGeo = new THREE.CylinderGeometry(0.040, 0.040, 0.012, 12);
  const screwSlotGeo = new THREE.BoxGeometry(0.044, 0.006, 0.010);
  const screwCoords = [
    [-1.90, -1.80],
    [1.90, -1.80],
    [-1.90, 1.80],
    [1.90, 1.80],
  ];
  const screwHeads = new THREE.InstancedMesh(screwGeo, trimBevelMat, 4);
  const screwSlots = new THREE.InstancedMesh(screwSlotGeo, darkMetalMat, 4);
  const dummy = new THREE.Object3D();
  screwCoords.forEach(([sx, sz], i) => {
    dummy.position.set(sx, topY - 0.001, sz);
    dummy.rotation.set(0, 0, 0);
    dummy.updateMatrix();
    screwHeads.setMatrixAt(i, dummy.matrix);

    dummy.position.set(sx, topY + 0.004, sz);
    dummy.rotation.set(0, 0.65, 0);
    dummy.updateMatrix();
    screwSlots.setMatrixAt(i, dummy.matrix);
  });
  group.add(screwHeads);
  group.add(screwSlots);

  // 4. Sleek Rear Anodized Steel Terminals (Modern silver/slate, no brass/gold)
  const portGeo = new THREE.CylinderGeometry(0.046, 0.046, 0.11, 16);
  portGeo.rotateX(Math.PI / 2);
  const portXs = [0.24, 0.48, 0.72];
  portXs.forEach((rx) => {
    const port = new THREE.Mesh(portGeo, trimBevelMat);
    port.position.set(rx, 0.02, -plinthDepth / 2 - 0.035);
    group.add(port);
  });

  // 5. Minimalist PLINTH Nameplate on Front-Left Edge
  let badgeTex = createPlinthBadgeTexture(false);
  const badgeMat = new THREE.MeshBasicMaterial({
    map: badgeTex,
    transparent: true,
    opacity: 0.85,
  });
  const badgeMesh = new THREE.Mesh(new THREE.PlaneGeometry(0.74, 0.185), badgeMat);
  badgeMesh.rotation.x = -Math.PI / 2;
  badgeMesh.position.set(-1.38, topY + 0.002, 1.85);
  group.add(badgeMesh);

  // 6. Bottom-Left 3D Machined Rotary Volume Dial
  const knobPos = new THREE.Vector3(-1.60, topY, 1.54);
  const knobBezel = new THREE.Mesh(
    new THREE.RingGeometry(0.235, 0.278, 36),
    trimBevelMat,
  );
  knobBezel.rotation.x = -Math.PI / 2;
  knobBezel.position.set(knobPos.x, topY + 0.002, knobPos.z);
  group.add(knobBezel);

  const volumeKnobGroup = new THREE.Group();
  volumeKnobGroup.name = 'VolumeKnob';
  volumeKnobGroup.position.set(knobPos.x, topY, knobPos.z);

  const knobSkirtMat = new THREE.MeshStandardMaterial({
    color: 0xd9dfe9,
    metalness: 0.86,
    roughness: 0.26,
    normalMap: maps.knurlNormal,
    normalScale: new THREE.Vector2(0.42, 0.1),
  });
  const knobSkirt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.228, 0.244, 0.095, 36),
    knobSkirtMat,
  );
  knobSkirt.position.y = 0.048;
  knobSkirt.castShadow = true;
  knobSkirt.receiveShadow = true;
  volumeKnobGroup.add(knobSkirt);

  const knobTopCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.214, 0.228, 0.018, 36),
    ceramicBtnMat,
  );
  knobTopCap.position.y = 0.102;
  knobTopCap.castShadow = true;
  knobTopCap.receiveShadow = true;
  volumeKnobGroup.add(knobTopCap);

  const spokeMat = new THREE.MeshStandardMaterial({
    color: 0x5c667a,
    metalness: 0.45,
    roughness: 0.4,
  });
  for (let i = 0; i < 3; i++) {
    const angle = (i * Math.PI * 2) / 3;
    const spoke = new THREE.Mesh(new THREE.BoxGeometry(0.015, 0.007, 0.19), spokeMat);
    spoke.position.set(Math.sin(angle) * 0.095, 0.112, Math.cos(angle) * 0.095);
    spoke.rotation.y = angle;
    volumeKnobGroup.add(spoke);
  }
  const dialDot = new THREE.Mesh(
    new THREE.CylinderGeometry(0.020, 0.020, 0.009, 12),
    darkMetalMat,
  );
  dialDot.position.set(0, 0.113, -0.145);
  volumeKnobGroup.add(dialDot);

  group.add(volumeKnobGroup);

  // 7. Bottom-Right 3D Physical Start/Stop Button + Crisp Pinhole Status LED
  const btnPos = new THREE.Vector3(1.60, topY, 1.54);
  const btnWell = new THREE.Mesh(
    new THREE.TorusGeometry(0.248, 0.020, 12, 36),
    trimBevelMat,
  );
  btnWell.rotation.x = Math.PI / 2;
  btnWell.position.set(btnPos.x, topY + 0.004, btnPos.z);
  group.add(btnWell);

  const startButtonGroup = new THREE.Group();
  startButtonGroup.name = 'StartStopButton';
  startButtonGroup.position.set(btnPos.x, topY, btnPos.z);

  const btnSkirt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.222, 0.232, 0.082, 36),
    trimBevelMat,
  );
  btnSkirt.position.y = 0.041;
  btnSkirt.castShadow = true;
  btnSkirt.receiveShadow = true;
  startButtonGroup.add(btnSkirt);

  const btnCap = new THREE.Mesh(
    new THREE.CylinderGeometry(0.210, 0.222, 0.022, 36),
    ceramicBtnMat,
  );
  btnCap.position.y = 0.088;
  btnCap.castShadow = true;
  btnCap.receiveShadow = true;
  startButtonGroup.add(btnCap);

  const ledMesh = new THREE.Mesh(
    new THREE.CylinderGeometry(0.020, 0.020, 0.008, 12),
    ledMat,
  );
  ledMesh.position.set(0, 0.100, 0);
  startButtonGroup.add(ledMesh);

  group.add(startButtonGroup);

  function setTheme(isDark) {
    if (isDark) {
      chassisMat.color.setHex(0x282e39);
      chassisMat.metalness = 0.78;
      chassisMat.roughness = 0.36;
      trimBevelMat.color.setHex(0x4c5669);
      ceramicBtnMat.color.setHex(0x1f242e);
      knobSkirtMat.color.setHex(0x363d4c);
      wellFloorMat.color.setHex(0x090b0f);
      bakedShadowMat.opacity = 0.92;
    } else {
      chassisMat.color.setHex(0xeaf0f7);
      chassisMat.metalness = 0.72;
      chassisMat.roughness = 0.34;
      trimBevelMat.color.setHex(0xf7f9fc);
      ceramicBtnMat.color.setHex(0xfafbfd);
      knobSkirtMat.color.setHex(0xd9dfe9);
      wellFloorMat.color.setHex(0x161920);
      bakedShadowMat.opacity = 0.68;
    }
    const nextBadge = createPlinthBadgeTexture(isDark);
    badgeMat.map = nextBadge;
    badgeMat.needsUpdate = true;
    badgeTex.dispose();
    badgeTex = nextBadge;
  }

  function dispose() {
    bakedShadowTex.dispose();
    badgeTex.dispose();
    group.traverse((obj) => {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) {
        if (Array.isArray(obj.material)) obj.material.forEach((m) => m.dispose());
        else obj.material.dispose();
      }
    });
  }

  return {
    group,
    volumeKnobGroup,
    startButtonGroup,
    ledMat,
    setTheme,
    dispose,
  };
}
