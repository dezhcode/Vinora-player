import * as THREE from './vendor/three.module.min.js';

/**
 * Procedural PBR Texture & Normal Map Factory for Plinth Turntable.
 * Generates brushed anodized aluminum, vinyl micro-groove normal/roughness maps,
 * knurled hardware rings, paper record label grain, and engraved branding plate.
 */

export function createBrushedMetalMaps() {
  const size = 512;
  const roughCanvas = document.createElement('canvas');
  roughCanvas.width = size;
  roughCanvas.height = size;
  const rCtx = roughCanvas.getContext('2d');

  const normCanvas = document.createElement('canvas');
  normCanvas.width = size;
  normCanvas.height = size;
  const nCtx = normCanvas.getContext('2d');

  // Base neutral normal (128, 128, 255)
  nCtx.fillStyle = 'rgb(128, 128, 255)';
  nCtx.fillRect(0, 0, size, size);

  rCtx.fillStyle = 'rgb(128, 128, 128)';
  rCtx.fillRect(0, 0, size, size);

  // Fine horizontal anisotropic brushed metal streaks
  let seed = 1337;
  const rand = () => {
    seed = (seed * 16807) % 2147483647;
    return (seed - 1) / 2147483646;
  };

  for (let y = 0; y < size; y++) {
    const streak = rand();
    const rVal = Math.floor(95 + streak * 70);
    rCtx.fillStyle = `rgb(${rVal},${rVal},${rVal})`;
    rCtx.fillRect(0, y, size, 1);

    const ny = Math.floor(116 + streak * 24);
    nCtx.fillStyle = `rgb(128, ${ny}, 255)`;
    nCtx.fillRect(0, y, size, 1);

    // Occasional micro-grain horizontal scratches
    if (rand() > 0.55) {
      const sx = rand() * size;
      const sw = 40 + rand() * 180;
      const g = Math.floor(110 + rand() * 55);
      rCtx.fillStyle = `rgba(${g},${g},${g},0.45)`;
      rCtx.fillRect(sx, y, sw, 1);
    }
  }

  const roughnessMap = new THREE.CanvasTexture(roughCanvas);
  roughnessMap.wrapS = THREE.RepeatWrapping;
  roughnessMap.wrapT = THREE.RepeatWrapping;
  roughnessMap.repeat.set(2, 2);

  const normalMap = new THREE.CanvasTexture(normCanvas);
  normalMap.wrapS = THREE.RepeatWrapping;
  normalMap.wrapT = THREE.RepeatWrapping;
  normalMap.repeat.set(2, 2);

  return { roughnessMap, normalMap };
}

export function createVinylGrooveMaps() {
  const size = 1024;
  const cx = size / 2;
  const cy = size / 2;

  const normCanvas = document.createElement('canvas');
  normCanvas.width = size;
  normCanvas.height = size;
  const nCtx = normCanvas.getContext('2d');
  const nImg = nCtx.createImageData(size, size);
  const nData = nImg.data;

  const roughCanvas = document.createElement('canvas');
  roughCanvas.width = size;
  roughCanvas.height = size;
  const rCtx = roughCanvas.getContext('2d');
  const rImg = rCtx.createImageData(size, size);
  const rData = rImg.data;

  const maxR = size * 0.495;
  const labelEdgeR = size * 0.176;
  const deadWaxEdgeR = size * 0.21;

  for (let y = 0; y < size; y++) {
    const dy = y - cy;
    for (let x = 0; x < size; x++) {
      const dx = x - cx;
      const idx = (y * size + x) * 4;
      const r = Math.sqrt(dx * dx + dy * dy);

      if (r < labelEdgeR || r > maxR) {
        nData[idx] = 128;
        nData[idx + 1] = 128;
        nData[idx + 2] = 255;
        nData[idx + 3] = 255;
        rData[idx] = 65;
        rData[idx + 1] = 65;
        rData[idx + 2] = 65;
        rData[idx + 3] = 255;
        continue;
      }

      if (r < deadWaxEdgeR) {
        // Glossy dead-wax run-out area
        nData[idx] = 128;
        nData[idx + 1] = 128;
        nData[idx + 2] = 255;
        nData[idx + 3] = 255;
        rData[idx] = 32;
        rData[idx + 1] = 32;
        rData[idx + 2] = 32;
        rData[idx + 3] = 255;
        continue;
      }

      // Concentric high-frequency modulated audio grooves + track gaps
      const normR = (r - deadWaxEdgeR) / (maxR - deadWaxEdgeR);
      const isTrackGap =
        Math.abs(normR - 0.28) < 0.012 ||
        Math.abs(normR - 0.56) < 0.012 ||
        Math.abs(normR - 0.82) < 0.012;

      const angle = Math.atan2(dy, dx);
      const mod = isTrackGap ? 0.0 : 0.22 * Math.sin(angle * 7.0 + r * 0.35);
      const groovePhase = Math.sin((r + mod) * 1.85);
      const slope = isTrackGap ? 0.0 : groovePhase * 0.52;

      const ux = dx / (r || 1);
      const uy = dy / (r || 1);
      // Tangent-space normal perturbation radially across groove walls
      const nx = Math.max(0, Math.min(255, Math.floor(128 + ux * slope * 95)));
      const ny = Math.max(0, Math.min(255, Math.floor(128 - uy * slope * 95)));

      nData[idx] = nx;
      nData[idx + 1] = ny;
      nData[idx + 2] = 242;
      nData[idx + 3] = 255;

      const roughVal = isTrackGap
        ? 30
        : Math.floor(54 + (groovePhase * 0.5 + 0.5) * 44);
      rData[idx] = roughVal;
      rData[idx + 1] = roughVal;
      rData[idx + 2] = roughVal;
      rData[idx + 3] = 255;
    }
  }

  nCtx.putImageData(nImg, 0, 0);
  rCtx.putImageData(rImg, 0, 0);

  const normalMap = new THREE.CanvasTexture(normCanvas);
  normalMap.anisotropy = 8;

  const roughnessMap = new THREE.CanvasTexture(roughCanvas);
  roughnessMap.anisotropy = 8;

  return { normalMap, roughnessMap };
}

export function createKnurlNormalMap() {
  const w = 256;
  const h = 64;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = 'rgb(128,128,255)';
  ctx.fillRect(0, 0, w, h);

  for (let x = 0; x < w; x += 4) {
    ctx.fillStyle = 'rgb(185,128,245)';
    ctx.fillRect(x, 0, 2, h);
    ctx.fillStyle = 'rgb(75,128,245)';
    ctx.fillRect(x + 2, 0, 2, h);
  }

  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(6, 1);
  return tex;
}

export function createDefaultLabelTexture(isDark) {
  const size = 512;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  const cx = size / 2;
  const cy = size / 2;

  const grad = ctx.createRadialGradient(cx, cy, 20, cx, cy, size * 0.5);
  grad.addColorStop(0, isDark ? '#F3F4F6' : '#FAF9F5');
  grad.addColorStop(1, isDark ? '#D7DCE4' : '#E4E7EE');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, size, size);

  // Subtle paper fiber rings
  ctx.strokeStyle = 'rgba(40, 46, 58, 0.14)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(cx, cy, 196, 0, Math.PI * 2);
  ctx.stroke();

  ctx.beginPath();
  ctx.arc(cx, cy, 152, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = '#1B1F28';
  ctx.font = 'bold 28px sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('PLINTH', cx, cy - 88);

  ctx.fillStyle = '#5D6678';
  ctx.font = '600 18px monospace';
  ctx.fillText('33⅓ RPM • HI-FI', cx, cy + 106);

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  return tex;
}

export function createPlinthBadgeTexture(isDark) {
  const w = 256;
  const h = 64;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');

  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = isDark ? '#C8D0DF' : '#353C4A';
  ctx.font = '700 28px sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  ctx.fillText('PLINTH', 12, h / 2 - 4);

  ctx.fillStyle = isDark ? '#7B8599' : '#7D8699';
  ctx.font = '600 13px monospace';
  ctx.fillText('MODEL P-01 • DIRECT DRIVE', 12, h / 2 + 18);

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

export function createRubberMatRoughnessMap() {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  const img = ctx.createImageData(size, size);
  let s = 42;
  for (let i = 0; i < img.data.length; i += 4) {
    s = (s * 16807) % 2147483647;
    const v = 205 + ((s % 45));
    img.data[i] = v;
    img.data[i + 1] = v;
    img.data[i + 2] = v;
    img.data[i + 3] = 255;
  }
  ctx.putImageData(img, 0, 0);
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(3, 3);
  return tex;
}
