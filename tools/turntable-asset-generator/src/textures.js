import * as THREE from 'three';

/**
 * High-resolution procedural PBR maps & Studio Softbox PMREM environment
 * calibrated for photorealistic top-down hardware rendering.
 */

export function createStudioEnvironment(renderer, isDark = false) {
  const pmremGenerator = new THREE.PMREMGenerator(renderer);
  pmremGenerator.compileEquirectangularShader();

  const envScene = new THREE.Scene();
  envScene.background = new THREE.Color(isDark ? 0x101319 : 0xe9ecf2);

  // Overhead broad studio softbox (main diffused wrap across brushed aluminum & knobs)
  const topSoftbox = new THREE.Mesh(
    new THREE.PlaneGeometry(18, 12),
    new THREE.MeshBasicMaterial({
      color: isDark ? 0xdbe5f7 : 0xffffff,
      side: THREE.DoubleSide,
    }),
  );
  topSoftbox.position.set(-1.5, 9.0, -1.2);
  topSoftbox.rotation.x = Math.PI / 2;
  topSoftbox.material.color.multiplyScalar(isDark ? 1.55 : 1.35);
  envScene.add(topSoftbox);

  // Upper-right rim strip light (sculpts tonearm gimbal, counterweight & headshell silver chamfers)
  const rimStrip = new THREE.Mesh(
    new THREE.PlaneGeometry(6, 16),
    new THREE.MeshBasicMaterial({
      color: isDark ? 0xcbd8ef : 0xfafcff,
      side: THREE.DoubleSide,
    }),
  );
  rimStrip.position.set(7.5, 4.2, -3.0);
  rimStrip.lookAt(0, 0, 0);
  rimStrip.material.color.multiplyScalar(isDark ? 1.4 : 1.2);
  envScene.add(rimStrip);

  // Left-side warm-neutral fill bounce (prevents harsh black shadows on frame edges)
  const leftFill = new THREE.Mesh(
    new THREE.PlaneGeometry(8, 14),
    new THREE.MeshBasicMaterial({
      color: isDark ? 0x2f3747 : 0xf4f3ef,
      side: THREE.DoubleSide,
    }),
  );
  leftFill.position.set(-8.5, 3.0, 1.5);
  leftFill.lookAt(0, 0, 0);
  envScene.add(leftFill);

  // Dark studio flags for high-contrast metal reflections
  const darkFlag = new THREE.Mesh(
    new THREE.PlaneGeometry(20, 8),
    new THREE.MeshBasicMaterial({
      color: isDark ? 0x05070a : 0x2c3038,
      side: THREE.DoubleSide,
    }),
  );
  darkFlag.position.set(0, 2.0, 9.5);
  darkFlag.lookAt(0, 0, 0);
  envScene.add(darkFlag);

  const envRT = pmremGenerator.fromScene(envScene, 0.035);
  pmremGenerator.dispose();
  envScene.traverse((o) => {
    if (o.geometry) o.geometry.dispose();
    if (o.material) o.material.dispose();
  });
  return envRT;
}

/**
 * 2048px Horizontal Brushed Aluminum Maps (albedo subtle grain + roughness + anisotropic normal).
 */
export function createBrushedAluminumMaps(isDark = false) {
  const size = 2048;

  const colorCanvas = document.createElement('canvas');
  colorCanvas.width = size;
  colorCanvas.height = size;
  const cCtx = colorCanvas.getContext('2d');

  const roughCanvas = document.createElement('canvas');
  roughCanvas.width = size;
  roughCanvas.height = size;
  const rCtx = roughCanvas.getContext('2d');

  const normCanvas = document.createElement('canvas');
  normCanvas.width = size;
  normCanvas.height = size;
  const nCtx = normCanvas.getContext('2d');

  // Subtle horizontal gradient matching studio sheen across silver plate
  const baseGrad = cCtx.createLinearGradient(0, 0, size, size * 0.85);
  if (isDark) {
    baseGrad.addColorStop(0.0, '#2e3440');
    baseGrad.addColorStop(0.55, '#252b36');
    baseGrad.addColorStop(1.0, '#1e232d');
  } else {
    baseGrad.addColorStop(0.0, '#dcdedf');
    baseGrad.addColorStop(0.45, '#d5d7da');
    baseGrad.addColorStop(1.0, '#cbcdcf');
  }
  cCtx.fillStyle = baseGrad;
  cCtx.fillRect(0, 0, size, size);

  rCtx.fillStyle = 'rgb(88, 88, 88)';
  rCtx.fillRect(0, 0, size, size);

  nCtx.fillStyle = 'rgb(128, 128, 255)';
  nCtx.fillRect(0, 0, size, size);

  let seed = 424242;
  const rand = () => {
    seed = (seed * 16807) % 2147483647;
    return (seed - 1) / 2147483646;
  };

  for (let y = 0; y < size; y++) {
    const s = rand();
    // Subtle full-width brushed pass
    const cAlpha = (s - 0.5) * 0.055;
    if (cAlpha > 0) {
      cCtx.fillStyle = `rgba(255,255,255,${cAlpha.toFixed(3)})`;
    } else {
      cCtx.fillStyle = `rgba(0,0,0,${(-cAlpha).toFixed(3)})`;
    }
    cCtx.fillRect(0, y, size, 1);

    const rVal = Math.floor(68 + s * 46);
    rCtx.fillStyle = `rgb(${rVal},${rVal},${rVal})`;
    rCtx.fillRect(0, y, size, 1);

    const ny = Math.floor(116 + s * 24);
    nCtx.fillStyle = `rgb(128,${ny},255)`;
    nCtx.fillRect(0, y, size, 1);

    // Fine partial micro-scratches along brushing axis
    const passes = rand() > 0.45 ? 2 : 1;
    for (let k = 0; k < passes; k++) {
      const sx = rand() * size;
      const sw = 80 + rand() * 520;
      const bright = rand() > 0.5;
      cCtx.fillStyle = bright ? 'rgba(255,255,255,0.032)' : 'rgba(20,24,30,0.025)';
      cCtx.fillRect(sx, y, sw, 1);

      const rg = Math.floor(62 + rand() * 56);
      rCtx.fillStyle = `rgba(${rg},${rg},${rg},0.48)`;
      rCtx.fillRect(sx, y, sw, 1);
    }
  }

  const map = new THREE.CanvasTexture(colorCanvas);
  map.colorSpace = THREE.SRGBColorSpace;
  map.anisotropy = 8;

  const roughnessMap = new THREE.CanvasTexture(roughCanvas);
  roughnessMap.wrapS = THREE.RepeatWrapping;
  roughnessMap.wrapT = THREE.RepeatWrapping;

  const normalMap = new THREE.CanvasTexture(normCanvas);
  normalMap.wrapS = THREE.RepeatWrapping;
  normalMap.wrapT = THREE.RepeatWrapping;

  return { map, roughnessMap, normalMap };
}

/**
 * 1024px Micro-Bead Matte Finish for Warm Off-White / Dark Titanium Lower Deck & Outer Frame.
 */
export function createMatteBeadMaps() {
  const size = 512;
  const roughCanvas = document.createElement('canvas');
  roughCanvas.width = size;
  roughCanvas.height = size;
  const rCtx = roughCanvas.getContext('2d');
  const img = rCtx.createImageData(size, size);

  let s = 918273;
  for (let i = 0; i < img.data.length; i += 4) {
    s = (s * 16807) % 2147483647;
    const v = 165 + (s % 28);
    img.data[i] = v;
    img.data[i + 1] = v;
    img.data[i + 2] = v;
    img.data[i + 3] = 255;
  }
  rCtx.putImageData(img, 0, 0);

  const roughnessMap = new THREE.CanvasTexture(roughCanvas);
  roughnessMap.wrapS = THREE.RepeatWrapping;
  roughnessMap.wrapT = THREE.RepeatWrapping;
  roughnessMap.repeat.set(6, 6);
  return { roughnessMap };
}

/**
 * 512px Radial Concentric Brushed Aluminum Maps for Machined Dials & Platter Ring.
 */
export function createRadialBrushedMaps(isDark = false) {
  const size = 512;
  const cCanvas = document.createElement('canvas');
  cCanvas.width = size;
  cCanvas.height = size;
  const cCtx = cCanvas.getContext('2d');

  const nCanvas = document.createElement('canvas');
  nCanvas.width = size;
  nCanvas.height = size;
  const nCtx = nCanvas.getContext('2d');

  const cx = size / 2;
  const cy = size / 2;

  cCtx.fillStyle = isDark ? '#4a5263' : '#c6c9cf';
  cCtx.fillRect(0, 0, size, size);

  nCtx.fillStyle = 'rgb(128,128,255)';
  nCtx.fillRect(0, 0, size, size);

  let s = 77113;
  const rand = () => {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };

  // Concentric lathe rings
  for (let r = 2; r < size * 0.5; r += 1) {
    const t = rand();
    cCtx.strokeStyle = t > 0.5
      ? `rgba(255,255,255,${(0.03 + t * 0.05).toFixed(3)})`
      : `rgba(0,0,0,${(0.025 + t * 0.04).toFixed(3)})`;
    cCtx.lineWidth = 1;
    cCtx.beginPath();
    cCtx.arc(cx, cy, r, 0, Math.PI * 2);
    cCtx.stroke();
  }

  // Conical anisotropic studio sheen (matches top-down circular brushed knobs in reference)
  const sweep = cCtx.createConicGradient(-Math.PI * 0.72, cx, cy);
  if (isDark) {
    sweep.addColorStop(0.0, 'rgba(255,255,255,0.24)');
    sweep.addColorStop(0.18, 'rgba(0,0,0,0.14)');
    sweep.addColorStop(0.45, 'rgba(255,255,255,0.28)');
    sweep.addColorStop(0.72, 'rgba(0,0,0,0.18)');
    sweep.addColorStop(1.0, 'rgba(255,255,255,0.24)');
  } else {
    sweep.addColorStop(0.0, 'rgba(255,255,255,0.42)');
    sweep.addColorStop(0.22, 'rgba(55,60,70,0.20)');
    sweep.addColorStop(0.48, 'rgba(255,255,255,0.45)');
    sweep.addColorStop(0.75, 'rgba(48,52,62,0.22)');
    sweep.addColorStop(1.0, 'rgba(255,255,255,0.42)');
  }
  cCtx.fillStyle = sweep;
  cCtx.fillRect(0, 0, size, size);

  const map = new THREE.CanvasTexture(cCanvas);
  map.colorSpace = THREE.SRGBColorSpace;
  map.anisotropy = 8;
  return { map };
}

/**
 * Counterweight engraved tracking-force scale + micro-knurl texture.
 */
export function createCounterweightTextures(isDark = false) {
  const w = 512;
  const h = 256;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');

  ctx.fillStyle = isDark ? '#6c7587' : '#d2d6dc';
  ctx.fillRect(0, 0, w, h);

  // Horizontal brushed sheen on barrel circumference
  let s = 3319;
  for (let y = 0; y < h; y += 2) {
    s = (s * 16807) % 2147483647;
    const a = ((s % 100) / 100) * 0.04;
    ctx.fillStyle = s % 2 === 0 ? `rgba(255,255,255,${a})` : `rgba(0,0,0,${a})`;
    ctx.fillRect(0, y, w, 2);
  }

  // Engraved scale ring ticks along front gauge band
  ctx.strokeStyle = isDark ? 'rgba(20, 24, 30, 0.72)' : 'rgba(42, 46, 54, 0.58)';
  for (let i = 0; i < 24; i++) {
    const x = (i / 24) * w;
    const major = i % 3 === 0;
    ctx.lineWidth = major ? 3 : 1.5;
    ctx.beginPath();
    ctx.moveTo(x, h * 0.16);
    ctx.lineTo(x, h * (major ? 0.34 : 0.27));
    ctx.stroke();
  }

  const map = new THREE.CanvasTexture(canvas);
  map.colorSpace = THREE.SRGBColorSpace;
  map.wrapS = THREE.RepeatWrapping;

  // Fine knurl normal map
  const nw = 256;
  const nh = 64;
  const nCanvas = document.createElement('canvas');
  nCanvas.width = nw;
  nCanvas.height = nh;
  const nCtx = nCanvas.getContext('2d');
  nCtx.fillStyle = 'rgb(128,128,255)';
  nCtx.fillRect(0, 0, nw, nh);
  for (let x = 0; x < nw; x += 4) {
    nCtx.fillStyle = 'rgb(180,128,245)';
    nCtx.fillRect(x, 0, 2, nh);
    nCtx.fillStyle = 'rgb(76,128,245)';
    nCtx.fillRect(x + 2, 0, 2, nh);
  }
  const normalMap = new THREE.CanvasTexture(nCanvas);
  normalMap.wrapS = THREE.RepeatWrapping;
  normalMap.wrapT = THREE.RepeatWrapping;
  normalMap.repeat.set(4, 1);

  return { map, normalMap };
}

/**
 * Soft studio contact shadow underneath the square plinth chassis.
 */
export function createChassisContactShadowTexture(isDark = false) {
  const size = 1024;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');

  ctx.clearRect(0, 0, size, size);

  // Outer diffuse penumbra
  for (let i = 16; i >= 1; i--) {
    const pad = 42 + i * 3.8;
    const alpha = isDark ? 0.022 : 0.013;
    ctx.fillStyle = `rgba(12, 16, 26, ${alpha})`;
    ctx.beginPath();
    ctx.roundRect(pad, pad + 10, size - pad * 2, size - pad * 2, 76 + i * 2);
    ctx.fill();
  }

  // Tight contact occlusion right beneath plinth perimeter
  ctx.fillStyle = isDark ? 'rgba(0, 0, 0, 0.42)' : 'rgba(20, 24, 34, 0.19)';
  ctx.beginPath();
  ctx.roundRect(68, 74, size - 136, size - 140, 68);
  ctx.fill();

  const tex = new THREE.CanvasTexture(canvas);
  tex.anisotropy = 4;
  return tex;
}
