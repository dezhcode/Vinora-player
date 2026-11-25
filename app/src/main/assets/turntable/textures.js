import * as THREE from './vendor/three.module.min.js';

/**
 * Lightweight Procedural Texture & Pre-Baked Shadow Factory for Plinth Turntable.
 * Optimized for low startup latency and minimal mobile GPU RAM usage.
 */

export function createBrushedMetalMaps() {
  const size = 256;
  const roughCanvas = document.createElement('canvas');
  roughCanvas.width = size;
  roughCanvas.height = size;
  const rCtx = roughCanvas.getContext('2d');

  const normCanvas = document.createElement('canvas');
  normCanvas.width = size;
  normCanvas.height = size;
  const nCtx = normCanvas.getContext('2d');

  nCtx.fillStyle = 'rgb(128, 128, 255)';
  nCtx.fillRect(0, 0, size, size);

  rCtx.fillStyle = 'rgb(124, 124, 124)';
  rCtx.fillRect(0, 0, size, size);

  let seed = 1337;
  const rand = () => {
    seed = (seed * 16807) % 2147483647;
    return (seed - 1) / 2147483646;
  };

  for (let y = 0; y < size; y++) {
    const streak = rand();
    const rVal = Math.floor(96 + streak * 64);
    rCtx.fillStyle = `rgb(${rVal},${rVal},${rVal})`;
    rCtx.fillRect(0, y, size, 1);

    const ny = Math.floor(118 + streak * 20);
    nCtx.fillStyle = `rgb(128, ${ny}, 255)`;
    nCtx.fillRect(0, y, size, 1);

    if (rand() > 0.6) {
      const sx = rand() * size;
      const sw = 28 + rand() * 110;
      const g = Math.floor(112 + rand() * 48);
      rCtx.fillStyle = `rgba(${g},${g},${g},0.42)`;
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

export function createKnurlNormalMap() {
  const w = 128;
  const h = 32;
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
  tex.repeat.set(5, 1);
  return tex;
}

export function createDefaultLabelTexture(isDark) {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  const cx = size / 2;
  const cy = size / 2;

  const grad = ctx.createRadialGradient(cx, cy, 10, cx, cy, size * 0.5);
  grad.addColorStop(0, isDark ? '#20242D' : '#F4F6FA');
  grad.addColorStop(1, isDark ? '#151820' : '#DFE4EE');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, size, size);

  ctx.strokeStyle = isDark ? 'rgba(220, 228, 242, 0.14)' : 'rgba(24, 28, 36, 0.12)';
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.arc(cx, cy, 102, 0, Math.PI * 2);
  ctx.stroke();

  ctx.beginPath();
  ctx.arc(cx, cy, 78, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = isDark ? '#E8EDF5' : '#161A22';
  ctx.font = 'bold 16px sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('PLINTH', cx, cy - 44);

  ctx.fillStyle = isDark ? '#8B95A8' : '#636D80';
  ctx.font = '600 10px monospace';
  ctx.fillText('33⅓ RPM • DIRECT', cx, cy + 54);

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
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
  ctx.fillStyle = isDark ? '#D2D9E6' : '#252B36';
  ctx.font = '700 26px sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  ctx.fillText('PLINTH', 12, h / 2 - 6);

  ctx.fillStyle = isDark ? '#7B8599' : '#6E7789';
  ctx.font = '600 12px monospace';
  ctx.fillText('P-01  DIRECT DRIVE', 12, h / 2 + 16);

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

export function createBakedShadowTexture() {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');

  ctx.clearRect(0, 0, size, size);
  const grad = ctx.createRadialGradient(size / 2, size / 2, size * 0.22, size / 2, size / 2, size * 0.49);
  grad.addColorStop(0, 'rgba(10, 14, 24, 0.34)');
  grad.addColorStop(0.62, 'rgba(10, 14, 24, 0.16)');
  grad.addColorStop(1, 'rgba(10, 14, 24, 0.0)');

  ctx.fillStyle = grad;
  ctx.beginPath();
  ctx.roundRect(14, 14, size - 28, size - 28, 36);
  ctx.fill();

  const tex = new THREE.CanvasTexture(canvas);
  tex.anisotropy = 2;
  return tex;
}

export function createRubberMatRoughnessMap() {
  const size = 128;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  const img = ctx.createImageData(size, size);
  let s = 42;
  for (let i = 0; i < img.data.length; i += 4) {
    s = (s * 16807) % 2147483647;
    const v = 208 + (s % 40);
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
