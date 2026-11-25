import * as THREE from './vendor/three.module.min.js';
import { PLATTER_CENTER } from './plinth.js';
import { createDefaultLabelTexture } from './textures.js';

export const VINYL_OUTER_GROOVE_R = 1.47;
export const VINYL_INNER_GROOVE_R = 0.66;
export const VINYL_TOP_Y = PLATTER_CENTER.y + 0.088;

/**
 * Builds the 3D machined aluminum platter, 72 instanced strobe dots,
 * damped rubber platter mat, thick beveled 12" vinyl LP disc with InstancedMesh grooves,
 * printed paper center label, and lathe-turned stainless steel spindle.
 */
export function createVinylPlatterAssembly(maps) {
  const root = new THREE.Group();
  root.name = 'PlatterRoot';
  root.position.copy(PLATTER_CENTER);

  // Rotating platter assembly (spins at 33 1/3 RPM)
  const spinningGroup = new THREE.Group();
  spinningGroup.name = 'SpinningPlatter';
  root.add(spinningGroup);

  // 1. Die-Cast Aluminum Platter with Beveled Outer Edge
  const platterProfile = [
    new THREE.Vector2(0.0, 0.0),
    new THREE.Vector2(1.62, 0.0),
    new THREE.Vector2(1.645, 0.015),
    new THREE.Vector2(1.635, 0.042),
    new THREE.Vector2(1.60, 0.056),
    new THREE.Vector2(0.0, 0.056),
  ];
  const platterGeo = new THREE.LatheGeometry(platterProfile, 96);
  const platterMat = new THREE.MeshStandardMaterial({
    color: 0xdce2ec,
    metalness: 0.90,
    roughness: 0.24,
    envMapIntensity: 1.2,
  });
  const platterMesh = new THREE.Mesh(platterGeo, platterMat);
  platterMesh.castShadow = true;
  platterMesh.receiveShadow = true;
  spinningGroup.add(platterMesh);

  // 72 Instanced Strobe Timing Dots around the platter bevel (Single GPU draw call!)
  const dotGeo = new THREE.CylinderGeometry(0.012, 0.012, 0.008, 10);
  const dotMat = new THREE.MeshStandardMaterial({
    color: 0x262b35,
    metalness: 0.5,
    roughness: 0.4,
  });
  const dotCount = 72;
  const strobeDots = new THREE.InstancedMesh(dotGeo, dotMat, dotCount);
  const dummy = new THREE.Object3D();
  for (let i = 0; i < dotCount; i++) {
    const a = (i / dotCount) * Math.PI * 2;
    const r = 1.622;
    dummy.position.set(Math.cos(a) * r, 0.049, Math.sin(a) * r);
    dummy.rotation.set(0, -a, -0.45);
    dummy.updateMatrix();
    strobeDots.setMatrixAt(i, dummy.matrix);
  }
  strobeDots.instanceMatrix.needsUpdate = true;
  spinningGroup.add(strobeDots);

  // 2. Damped Silicone Platter Mat
  const matMesh = new THREE.Mesh(
    new THREE.CylinderGeometry(1.55, 1.56, 0.012, 72),
    new THREE.MeshStandardMaterial({
      color: 0x181a20,
      metalness: 0.04,
      roughness: 0.92,
      roughnessMap: maps.rubberRoughness,
    }),
  );
  matMesh.position.y = 0.060;
  matMesh.receiveShadow = true;
  spinningGroup.add(matMesh);

  // 3. Swappable Vinyl Record Group (animates smoothly on track switch)
  const recordGroup = new THREE.Group();
  recordGroup.name = 'VinylRecord';
  recordGroup.position.y = 0.066;
  spinningGroup.add(recordGroup);

  // Realistic 180g Vinyl Disc Profile with raised Groove-Guard Lip & Center Label Recess
  const vinylProfile = [
    new THREE.Vector2(0.044, 0.0),
    new THREE.Vector2(1.57, 0.0),
    new THREE.Vector2(1.582, 0.009),
    new THREE.Vector2(1.582, 0.020),
    new THREE.Vector2(1.568, 0.026),
    new THREE.Vector2(1.545, 0.022), // Groove-guard rim
    new THREE.Vector2(0.585, 0.022), // Playable groove bed (y = 0.022 -> world y = 0.20+0.066+0.022 = 0.288)
    new THREE.Vector2(0.565, 0.025), // Label step
    new THREE.Vector2(0.044, 0.025),
  ];
  const vinylGeo = new THREE.LatheGeometry(vinylProfile, 96);

  // Generate clean planar top-down UVs for the vinyl disc so the radial groove normalMap aligns perfectly!
  const posAttr = vinylGeo.attributes.position;
  const uvAttr = vinylGeo.attributes.uv;
  const maxR = 1.582;
  for (let i = 0; i < posAttr.count; i++) {
    const vx = posAttr.getX(i);
    const vz = posAttr.getZ(i);
    const u = 0.5 + (vx / (maxR * 2.0));
    const v = 0.5 - (vz / (maxR * 2.0));
    uvAttr.setXY(i, u, v);
  }
  uvAttr.needsUpdate = true;

  const vinylMat = new THREE.MeshPhysicalMaterial({
    color: 0x090a0e,
    metalness: 0.06,
    roughness: 0.21,
    normalMap: maps.vinylNormal,
    normalScale: new THREE.Vector2(0.62, 0.62),
    roughnessMap: maps.vinylRoughness,
    clearcoat: 1.0,
    clearcoatRoughness: 0.075,
    reflectivity: 0.92,
    iridescence: 0.24,
    iridescenceIOR: 1.42,
    iridescenceThicknessRange: [120, 420],
    envMapIntensity: 1.35,
  });

  const vinylMesh = new THREE.Mesh(vinylGeo, vinylMat);
  vinylMesh.name = 'VinylDiscSurface';
  vinylMesh.castShadow = true;
  vinylMesh.receiveShadow = true;
  recordGroup.add(vinylMesh);

  // 4. Instanced Concentric 3D Micro-Groove Rings (Single Draw Call for crisp close-up depth!)
  const grooveRingCount = 52;
  const grooveRingGeo = new THREE.RingGeometry(0.996, 1.0, 96);
  grooveRingGeo.rotateX(-Math.PI / 2);
  const grooveRingMat = new THREE.MeshStandardMaterial({
    color: 0x1b1f29,
    metalness: 0.28,
    roughness: 0.16,
    transparent: true,
    opacity: 0.34,
  });
  const grooveInstances = new THREE.InstancedMesh(grooveRingGeo, grooveRingMat, grooveRingCount);
  const gDummy = new THREE.Object3D();
  for (let i = 0; i < grooveRingCount; i++) {
    const frac = i / (grooveRingCount - 1);
    const r = VINYL_INNER_GROOVE_R + (VINYL_OUTER_GROOVE_R - VINYL_INNER_GROOVE_R) * frac;
    const isTrackPauseBand = i === 14 || i === 29 || i === 42;
    gDummy.position.set(0, 0.0226, 0);
    gDummy.scale.set(r, 1, isTrackPauseBand ? r : r);
    gDummy.updateMatrix();
    grooveInstances.setMatrixAt(i, gDummy.matrix);
  }
  grooveInstances.instanceMatrix.needsUpdate = true;
  recordGroup.add(grooveInstances);

  // 5. Printed Paper Center Label with Active Track Album Artwork
  let currentLabelTex = createDefaultLabelTexture(false);
  const labelGeo = new THREE.CylinderGeometry(0.562, 0.562, 0.004, 72);
  const labelSideMat = new THREE.MeshStandardMaterial({ color: 0xe6e8ee, roughness: 0.78 });
  const labelTopMat = new THREE.MeshStandardMaterial({
    map: currentLabelTex,
    roughness: 0.68,
    metalness: 0.03,
    envMapIntensity: 0.55,
  });
  const labelMesh = new THREE.Mesh(labelGeo, [labelSideMat, labelTopMat, labelSideMat]);
  labelMesh.position.y = 0.0252;
  labelMesh.receiveShadow = true;
  recordGroup.add(labelMesh);

  // Pressed circular label ridge crease
  const labelCrease = new THREE.Mesh(
    new THREE.TorusGeometry(0.24, 0.0035, 10, 48),
    new THREE.MeshStandardMaterial({ color: 0xc5cbd6, roughness: 0.65 }),
  );
  labelCrease.rotation.x = Math.PI / 2;
  labelCrease.position.y = 0.0272;
  recordGroup.add(labelCrease);

  // 6. Machined Stainless Steel Center Spindle (LatheGeometry)
  const spindleProfile = [
    new THREE.Vector2(0.0, 0.0),
    new THREE.Vector2(0.056, 0.0),
    new THREE.Vector2(0.056, 0.028),
    new THREE.Vector2(0.041, 0.034),
    new THREE.Vector2(0.041, 0.112),
    new THREE.Vector2(0.026, 0.145),
    new THREE.Vector2(0.0, 0.155),
  ];
  const spindleGeo = new THREE.LatheGeometry(spindleProfile, 36);
  const spindleMat = new THREE.MeshStandardMaterial({
    color: 0xf4f7fc,
    metalness: 0.98,
    roughness: 0.11,
    envMapIntensity: 1.4,
  });
  const spindleMesh = new THREE.Mesh(spindleGeo, spindleMat);
  spindleMesh.position.y = 0.015;
  spindleMesh.castShadow = true;
  root.add(spindleMesh);

  // Update artwork texture cleanly with proper GPU disposal
  const imageLoader = new THREE.TextureLoader();
  let lastArtworkKey = '';

  function setArtwork(dataUriOrNull, isDark) {
    const key = (dataUriOrNull || '__default__') + '_' + (isDark ? 'd' : 'l');
    if (key === lastArtworkKey) return;
    lastArtworkKey = key;

    if (!dataUriOrNull) {
      const fallbackTex = createDefaultLabelTexture(isDark);
      labelTopMat.map = fallbackTex;
      labelTopMat.needsUpdate = true;
      currentLabelTex.dispose();
      currentLabelTex = fallbackTex;
      return;
    }

    imageLoader.load(
      dataUriOrNull,
      (loadedTex) => {
        loadedTex.colorSpace = THREE.SRGBColorSpace;
        loadedTex.anisotropy = 8;
        loadedTex.center.set(0.5, 0.5);
        labelTopMat.map = loadedTex;
        labelTopMat.needsUpdate = true;
        currentLabelTex.dispose();
        currentLabelTex = loadedTex;
      },
      undefined,
      () => {
        const fallbackTex = createDefaultLabelTexture(isDark);
        labelTopMat.map = fallbackTex;
        labelTopMat.needsUpdate = true;
        currentLabelTex.dispose();
        currentLabelTex = fallbackTex;
      },
    );
  }

  function setTheme(isDark) {
    platterMat.color.setHex(isDark ? 0x677082 : 0xdce2ec);
    if (lastArtworkKey.startsWith('__default__')) {
      setArtwork(null, isDark);
    }
  }

  return {
    root,
    spinningGroup,
    recordGroup,
    vinylMesh,
    setArtwork,
    setTheme,
  };
}
