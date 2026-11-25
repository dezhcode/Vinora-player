import * as THREE from './vendor/three.module.min.js';
import { PLATTER_CENTER } from './plinth.js';
import { createDefaultLabelTexture } from './textures.js';

export const VINYL_OUTER_GROOVE_R = 1.47;
export const VINYL_INNER_GROOVE_R = 0.66;
export const VINYL_TOP_Y = PLATTER_CENTER.y + 0.088;

const VINYL_VERT_SHADER = /* glsl */ `
  varying vec2 vLocalPos;
  void main() {
    vLocalPos = position.xz;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

const VINYL_FRAG_SHADER = /* glsl */ `
  precision highp float;
  varying vec2 vLocalPos;
  uniform float uSpinAngle;
  uniform float uHighlightStrength;

  void main() {
    float r = length(vLocalPos);
    float localAngle = atan(vLocalPos.y, vLocalPos.x);
    float worldAngle = localAngle - uSpinAngle;

    // Deep pure obsidian black vinyl base (zero brown/gold tint)
    vec3 deepBlack = vec3(0.016, 0.018, 0.022);

    // Subtle concentric micro-grooves + track separation pause bands
    float normR = clamp((r - 0.65) / (1.53 - 0.65), 0.0, 1.0);
    float deadWax = step(r, 0.64);
    float outerLip = smoothstep(1.535, 1.568, r);

    float pauseBand1 = 1.0 - smoothstep(0.0, 0.011, abs(normR - 0.28));
    float pauseBand2 = 1.0 - smoothstep(0.0, 0.011, abs(normR - 0.56));
    float pauseBand3 = 1.0 - smoothstep(0.0, 0.011, abs(normR - 0.82));
    float pauseMask = clamp(pauseBand1 + pauseBand2 + pauseBand3 + deadWax + outerLip, 0.0, 1.0);

    // Ultra-fine modulated circular groove rings (locked to physical rotating vinyl)
    float modPhase = sin(localAngle * 6.0 + r * 38.0) * 0.22;
    float fineGroove = sin((r + modPhase * 0.012) * 320.0) * 0.5 + 0.5;
    float macroGroove = sin(r * 115.0 + modPhase * 0.4) * 0.5 + 0.5;
    float groovePattern = mix(fineGroove * 0.65 + macroGroove * 0.35, 0.22, pauseMask);

    // Controlled studio anisotropic specular sweeps (stationary studio softbox reflection across spinning grooves)
    float lobe1 = pow(max(0.0, cos(worldAngle - 0.82)), 10.0);
    float lobe2 = pow(max(0.0, cos(worldAngle + 2.32)), 14.0);
    float radialAtten = smoothstep(0.56, 0.72, r) * (1.0 - smoothstep(1.52, 1.57, r));

    float grooveSheen = (lobe1 * 0.115 + lobe2 * 0.075) * (0.45 + 0.55 * groovePattern) * radialAtten;
    float deadWaxSheen = (lobe1 * 0.065 + lobe2 * 0.045) * deadWax;
    float microRingHighlight = groovePattern * 0.022 * (1.0 - pauseMask);

    // Crisp outer rim hairline catchlight
    float rimCatch = smoothstep(1.545, 1.568, r) * 0.055;

    vec3 neutralSpecular = vec3(0.78, 0.82, 0.89);
    vec3 color = deepBlack
      + neutralSpecular * (grooveSheen + deadWaxSheen) * uHighlightStrength
      + vec3(0.08, 0.09, 0.11) * microRingHighlight
      + vec3(0.22, 0.24, 0.28) * rimCatch;

    gl_FragColor = vec4(color, 1.0);
  }
`;

/**
 * Builds the 3D machined aluminum platter, instanced strobe timing dots,
 * deep-black 180g beveled vinyl LP disc with a single-pass custom GLSL groove shader,
 * minimalist center label, and stainless steel center spindle.
 */
export function createVinylPlatterAssembly(maps) {
  const root = new THREE.Group();
  root.name = 'PlatterRoot';
  root.position.copy(PLATTER_CENTER);

  const spinningGroup = new THREE.Group();
  spinningGroup.name = 'SpinningPlatter';
  root.add(spinningGroup);

  // 1. Machined Aluminum Platter Perimeter Ring (optimized 64 segments)
  const platterProfile = [
    new THREE.Vector2(0.0, 0.0),
    new THREE.Vector2(1.615, 0.0),
    new THREE.Vector2(1.640, 0.015),
    new THREE.Vector2(1.630, 0.042),
    new THREE.Vector2(1.595, 0.056),
    new THREE.Vector2(0.0, 0.056),
  ];
  const platterGeo = new THREE.LatheGeometry(platterProfile, 64);
  const platterMat = new THREE.MeshStandardMaterial({
    color: 0xdce2ec,
    metalness: 0.88,
    roughness: 0.24,
    envMapIntensity: 1.15,
  });
  const platterMesh = new THREE.Mesh(platterGeo, platterMat);
  platterMesh.receiveShadow = true;
  spinningGroup.add(platterMesh);

  // 48 Instanced Strobe Timing Dots (single draw call)
  const dotGeo = new THREE.CylinderGeometry(0.011, 0.011, 0.007, 8);
  const dotMat = new THREE.MeshStandardMaterial({
    color: 0x20242d,
    metalness: 0.5,
    roughness: 0.42,
  });
  const dotCount = 48;
  const strobeDots = new THREE.InstancedMesh(dotGeo, dotMat, dotCount);
  const dummy = new THREE.Object3D();
  for (let i = 0; i < dotCount; i++) {
    const a = (i / dotCount) * Math.PI * 2;
    const r = 1.618;
    dummy.position.set(Math.cos(a) * r, 0.049, Math.sin(a) * r);
    dummy.rotation.set(0, -a, -0.45);
    dummy.updateMatrix();
    strobeDots.setMatrixAt(i, dummy.matrix);
  }
  strobeDots.instanceMatrix.needsUpdate = true;
  spinningGroup.add(strobeDots);

  // 2. Damped Silicone Platter Mat
  const matMesh = new THREE.Mesh(
    new THREE.CylinderGeometry(1.55, 1.56, 0.012, 56),
    new THREE.MeshStandardMaterial({
      color: 0x101217,
      metalness: 0.04,
      roughness: 0.92,
      roughnessMap: maps.rubberRoughness,
    }),
  );
  matMesh.position.y = 0.060;
  spinningGroup.add(matMesh);

  // 3. Swappable 180g Deep-Black Vinyl LP Group
  const recordGroup = new THREE.Group();
  recordGroup.name = 'VinylRecord';
  recordGroup.position.y = 0.066;
  spinningGroup.add(recordGroup);

  // Real 3D beveled outer vinyl thickness rim (deep matte-satin black)
  const vinylEdgeProfile = [
    new THREE.Vector2(0.044, 0.0),
    new THREE.Vector2(1.566, 0.0),
    new THREE.Vector2(1.578, 0.009),
    new THREE.Vector2(1.578, 0.019),
    new THREE.Vector2(1.564, 0.025),
    new THREE.Vector2(1.542, 0.022),
    new THREE.Vector2(0.562, 0.022),
    new THREE.Vector2(0.044, 0.022),
  ];
  const vinylBodyGeo = new THREE.LatheGeometry(vinylEdgeProfile, 64);
  const vinylBodyMat = new THREE.MeshStandardMaterial({
    color: 0x050608,
    metalness: 0.10,
    roughness: 0.34,
  });
  const vinylMesh = new THREE.Mesh(vinylBodyGeo, vinylBodyMat);
  vinylMesh.name = 'VinylDiscSurface';
  vinylMesh.castShadow = true;
  vinylMesh.receiveShadow = true;
  recordGroup.add(vinylMesh);

  // Custom lightweight GPU shader disc for razor-sharp concentric grooves on Deep Black vinyl
  const grooveDiscGeo = new THREE.RingGeometry(0.562, 1.564, 72, 1);
  grooveDiscGeo.rotateX(-Math.PI / 2);
  // Keep vertex coordinates flat in local XZ for vLocalPos
  const grooveShaderMat = new THREE.ShaderMaterial({
    vertexShader: VINYL_VERT_SHADER,
    fragmentShader: VINYL_FRAG_SHADER,
    uniforms: {
      uSpinAngle: { value: 0.0 },
      uHighlightStrength: { value: 1.0 },
    },
  });
  const grooveDiscMesh = new THREE.Mesh(grooveDiscGeo, grooveShaderMat);
  grooveDiscMesh.position.y = 0.0228;
  recordGroup.add(grooveDiscMesh);

  // 4. Minimalist Center Label with Active Track Album Artwork
  let currentLabelTex = createDefaultLabelTexture(false);
  const labelGeo = new THREE.CylinderGeometry(0.558, 0.558, 0.005, 56);
  const labelSideMat = new THREE.MeshStandardMaterial({ color: 0x1b1e26, roughness: 0.8 });
  const labelTopMat = new THREE.MeshStandardMaterial({
    map: currentLabelTex,
    roughness: 0.68,
    metalness: 0.03,
    envMapIntensity: 0.55,
  });
  const labelMesh = new THREE.Mesh(labelGeo, [labelSideMat, labelTopMat, labelSideMat]);
  labelMesh.position.y = 0.0254;
  labelMesh.receiveShadow = true;
  recordGroup.add(labelMesh);

  // Subtle pressed circular crease on paper label
  const labelCrease = new THREE.Mesh(
    new THREE.TorusGeometry(0.24, 0.003, 8, 36),
    new THREE.MeshStandardMaterial({ color: 0x6f7787, roughness: 0.65 }),
  );
  labelCrease.rotation.x = Math.PI / 2;
  labelCrease.position.y = 0.0282;
  recordGroup.add(labelCrease);

  // 5. Machined Stainless Steel Center Spindle
  const spindleProfile = [
    new THREE.Vector2(0.0, 0.0),
    new THREE.Vector2(0.054, 0.0),
    new THREE.Vector2(0.054, 0.028),
    new THREE.Vector2(0.040, 0.034),
    new THREE.Vector2(0.040, 0.112),
    new THREE.Vector2(0.024, 0.145),
    new THREE.Vector2(0.0, 0.155),
  ];
  const spindleGeo = new THREE.LatheGeometry(spindleProfile, 24);
  const spindleMat = new THREE.MeshStandardMaterial({
    color: 0xf0f4fa,
    metalness: 0.96,
    roughness: 0.12,
    envMapIntensity: 1.3,
  });
  const spindleMesh = new THREE.Mesh(spindleGeo, spindleMat);
  spindleMesh.position.y = 0.015;
  spindleMesh.castShadow = true;
  root.add(spindleMesh);

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
        loadedTex.anisotropy = 4;
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
    platterMat.color.setHex(isDark ? 0x5c6475 : 0xdce2ec);
    grooveShaderMat.uniforms.uHighlightStrength.value = isDark ? 1.1 : 1.0;
    if (lastArtworkKey.startsWith('__default__')) {
      setArtwork(null, isDark);
    }
  }

  function setSpinAngle(angleRad) {
    spinningGroup.rotation.y = angleRad;
    grooveShaderMat.uniforms.uSpinAngle.value = angleRad;
  }

  function dispose() {
    currentLabelTex.dispose();
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
    spinningGroup,
    recordGroup,
    vinylMesh,
    setSpinAngle,
    setArtwork,
    setTheme,
    dispose,
  };
}
