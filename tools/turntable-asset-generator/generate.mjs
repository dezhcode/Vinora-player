import { execFile } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
import { startStaticServer } from './preview-server.mjs';

const execFileAsync = promisify(execFile);
const GENERATOR_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(GENERATOR_DIR, '..', '..');
const OUTPUT_DIR = path.join(GENERATOR_DIR, 'output');
const ANDROID_ASSETS_DIR = path.join(REPO_ROOT, 'app', 'src', 'main', 'assets', 'turntable');

const RESOLUTIONS = [512, 1024, 2048];
const MASTER_SIZE = Number(process.env.MASTER_SIZE || 4096);

function parseThemesArg() {
  const arg = process.argv.find((a) => a.startsWith('--themes='));
  if (!arg) return ['light', 'dark'];
  return arg.replace('--themes=', '').split(',').map((s) => s.trim()).filter(Boolean);
}

async function writeDataUrlPng(dataUrl, destPath) {
  const base64 = dataUrl.replace(/^data:image\/png;base64,/, '');
  await fs.mkdir(path.dirname(destPath), { recursive: true });
  await fs.writeFile(destPath, Buffer.from(base64, 'base64'));
}

async function runFfmpegCropScaleWebp({
  srcPng,
  destWebp,
  cropRectPx,
  targetWidth,
  targetHeight,
  lossless = false,
  quality = 94,
}) {
  await fs.mkdir(path.dirname(destWebp), { recursive: true });
  const filters = [];
  if (cropRectPx) {
    filters.push(`crop=${cropRectPx.w}:${cropRectPx.h}:${cropRectPx.x}:${cropRectPx.y}`);
  }
  filters.push(`scale=${targetWidth}:${targetHeight}:flags=lanczos+accurate_rnd`);

  const args = [
    '-y',
    '-i', srcPng,
    '-vf', filters.join(','),
    '-c:v', 'libwebp',
    '-lossless', lossless ? '1' : '0',
    '-quality', String(quality),
    '-compression_level', '5',
    ...(lossless ? [] : ['-pix_fmt', 'yuva420p']),
    destWebp,
  ];
  await execFileAsync('ffmpeg', args);
}

function normBoundsToMasterCrop(bounds, masterSize) {
  // Keep even pixel dimensions for crisp yuva420p/lanczos alignment
  const rawX = Math.max(0, Math.floor(bounds.x * masterSize));
  const rawY = Math.max(0, Math.floor(bounds.y * masterSize));
  const rawW = Math.min(masterSize - rawX, Math.ceil(bounds.width * masterSize));
  const rawH = Math.min(masterSize - rawY, Math.ceil(bounds.height * masterSize));
  const x = rawX & ~1;
  const y = rawY & ~1;
  const w = Math.max(2, Math.min(masterSize - x, (rawW + 1) & ~1));
  const h = Math.max(2, Math.min(masterSize - y, (rawH + 1) & ~1));
  return { x, y, w, h };
}

function masterCropToTargetSize(cropPx, masterSize, res) {
  const scale = res / masterSize;
  const w = Math.max(2, Math.round(cropPx.w * scale));
  const h = Math.max(2, Math.round(cropPx.h * scale));
  return { w, h };
}

async function main() {
  const themes = parseThemesArg();
  console.log(`Starting Plinth 3D Pre-Render Generator (master=${MASTER_SIZE}px, themes=${themes.join(',')})...`);

  const { server, url } = await startStaticServer(0);
  const browser = await chromium.launch({
    headless: true,
    args: [
      '--no-sandbox',
      '--use-gl=angle',
      '--use-angle=swiftshader',
      '--enable-webgl',
      '--enable-unsafe-swiftshader',
      '--ignore-gpu-blocklist',
    ],
  });

  try {
    const page = await browser.newPage({
      viewport: { width: 1024, height: 1024 },
      deviceScaleFactor: 1,
    });
    page.on('pageerror', (err) => {
      console.error('Page error:', err);
    });
    await page.goto(url, { waitUntil: 'networkidle' });

    let sharedManifest = null;

    for (const theme of themes) {
      const isDark = theme === 'dark';
      console.log(`\n=== Rendering Theme: ${theme} at ${MASTER_SIZE}x${MASTER_SIZE} ===`);

      const manifest = await page.evaluate(
        ({ size, dark }) => window.__PLINTH_GENERATOR__.init(size, dark),
        { size: MASTER_SIZE, dark: isDark },
      );

      // Update pixel-snapped crop bounds in manifest so runtime exact bounds match WebP aspect 1:1
      if (!sharedManifest) {
        for (const [key, layerSpec] of Object.entries(manifest.layers)) {
          const c = normBoundsToMasterCrop(layerSpec.bounds, MASTER_SIZE);
          const adjustedBounds = {
            x: Number((c.x / MASTER_SIZE).toFixed(6)),
            y: Number((c.y / MASTER_SIZE).toFixed(6)),
            width: Number((c.w / MASTER_SIZE).toFixed(6)),
            height: Number((c.h / MASTER_SIZE).toFixed(6)),
          };
          // Recompute transformOrigin relative to snapped crop rect if tonearm
          if (key === 'tonearmArm' || key === 'tonearmShadow') {
            const p = manifest.geometry.tonearmPivot;
            layerSpec.transformOrigin = {
              x: Number(((p.x - adjustedBounds.x) / adjustedBounds.width).toFixed(6)),
              y: Number(((p.y - adjustedBounds.y) / adjustedBounds.height).toFixed(6)),
            };
          }
          layerSpec.bounds = adjustedBounds;
        }
        sharedManifest = manifest;
      }

      const masterDir = path.join(OUTPUT_DIR, `masters-${MASTER_SIZE}`, theme);
      await fs.mkdir(masterDir, { recursive: true });

      // 1. Render visual composite preview (1024/2048 check) for visual inspection
      const compUrl = await page.evaluate(
        ({ deg, bg }) => window.__PLINTH_GENERATOR__.renderComposite(deg, bg),
        { deg: manifest.geometry.tonearmOuterGrooveAngleDeg, bg: isDark ? 0x14171d : 0xf4f6fa },
      );
      const compMasterPng = path.join(OUTPUT_DIR, `composite_${theme}_${MASTER_SIZE}.png`);
      await writeDataUrlPng(compUrl, compMasterPng);
      await execFileAsync('ffmpeg', [
        '-y', '-i', compMasterPng,
        '-vf', 'scale=1024:1024:flags=lanczos',
        path.join(OUTPUT_DIR, `composite_${theme}_1024.png`),
      ]);
      await fs.rm(compMasterPng, { force: true });

      // 2. Render fixed & interactive single passes at 4096x4096 master PNG
      const singlePasses = [
        { pass: 'turntable_base', layerKey: 'turntableBase', file: 'turntable_base.png', webp: 'turntable_base.webp', lossless: false, q: 94 },
        { pass: 'metal_platter', layerKey: 'metalPlatter', file: 'metal_platter.png', webp: 'metal_platter.webp', lossless: false, q: 94 },
        { pass: 'vinyl_record', layerKey: 'vinylRecord', file: 'vinyl_record.png', webp: 'vinyl_record.webp', lossless: false, q: 94 },
        { pass: 'vinyl_reflection', layerKey: 'vinylReflection', file: 'vinyl_reflection.png', webp: 'vinyl_reflection.webp', lossless: true, q: 96 },
        { pass: 'center_spindle', layerKey: 'centerSpindle', file: 'center_spindle.png', webp: 'center_spindle.webp', lossless: true, q: 96 },
        { pass: 'left_knob_normal', layerKey: 'leftKnobNormal', file: 'left_knob_normal.png', webp: 'left_knob_normal.webp', lossless: true, q: 95 },
        { pass: 'left_knob_pressed', layerKey: 'leftKnobPressed', file: 'left_knob_pressed.png', webp: 'left_knob_pressed.webp', lossless: true, q: 95 },
        { pass: 'right_knob_normal', layerKey: 'rightKnobNormal', file: 'rightKnobNormal.png'.replace('rightKnobNormal', 'right_knob_normal'), webp: 'right_knob_normal.webp', lossless: true, q: 95 },
        { pass: 'right_knob_pressed', layerKey: 'rightKnobPressed', file: 'right_knob_pressed.png', webp: 'right_knob_pressed.webp', lossless: true, q: 95 },
      ];

      const exportJobs = [];

      for (const spec of singlePasses) {
        process.stdout.write(`  Rendering ${spec.pass}... `);
        const pngData = await page.evaluate(
          ({ p }) => window.__PLINTH_GENERATOR__.renderLayer(p),
          { p: spec.pass },
        );
        const masterPngPath = path.join(masterDir, spec.file);
        await writeDataUrlPng(pngData, masterPngPath);
        process.stdout.write('OK\n');

        const cropPx = normBoundsToMasterCrop(sharedManifest.layers[spec.layerKey].bounds, MASTER_SIZE);
        exportJobs.push({
          masterPngPath,
          webpName: spec.webp,
          cropPx: spec.layerKey === 'turntableBase' ? null : cropPx,
          lossless: spec.lossless,
          quality: spec.q,
        });
      }

      // 3. Render Multi-Angle Pre-Rendered Tonearm Keyframes + Independent Cast Shadow Keyframes
      const angles = sharedManifest.geometry.tonearmKeyframeAnglesDeg;
      for (let i = 0; i < angles.length; i++) {
        const deg = angles[i];
        const idxStr = String(i).padStart(2, '0');
        process.stdout.write(`  Rendering tonearm keyframe f${idxStr} (${deg} deg)... `);

        const armData = await page.evaluate(
          ({ d }) => window.__PLINTH_GENERATOR__.renderLayer('tonearm_arm', d),
          { d: deg },
        );
        const armPngPath = path.join(masterDir, `tonearm_arm_f${idxStr}.png`);
        await writeDataUrlPng(armData, armPngPath);

        const shadowData = await page.evaluate(
          ({ d }) => window.__PLINTH_GENERATOR__.renderLayer('tonearm_shadow', d),
          { d: deg },
        );
        const shadowPngPath = path.join(masterDir, `tonearm_shadow_f${idxStr}.png`);
        await writeDataUrlPng(shadowData, shadowPngPath);
        process.stdout.write('OK\n');

        const armCropPx = normBoundsToMasterCrop(sharedManifest.layers.tonearmArm.bounds, MASTER_SIZE);
        exportJobs.push({
          masterPngPath: armPngPath,
          webpName: `tonearm_arm_f${idxStr}.webp`,
          cropPx: armCropPx,
          lossless: true,
          quality: 95,
        });
        exportJobs.push({
          masterPngPath: shadowPngPath,
          webpName: `tonearm_shadow_f${idxStr}.webp`,
          cropPx: armCropPx,
          lossless: false,
          quality: 90,
        });
      }

      // 4. Transcode & Crop Multi-Resolution WebPs (512, 1024, 2048) directly into Android assets
      console.log(`  Generating 512/1024/2048 WebPs for ${theme}...`);
      for (const res of RESOLUTIONS) {
        const resDir = path.join(ANDROID_ASSETS_DIR, theme, String(res));
        await fs.mkdir(resDir, { recursive: true });
        // Process in batches of 6 parallel ffmpeg workers
        for (let b = 0; b < exportJobs.length; b += 6) {
          const batch = exportJobs.slice(b, b + 6);
          await Promise.all(
            batch.map((job) => {
              const targetSize = job.cropPx
                ? masterCropToTargetSize(job.cropPx, MASTER_SIZE, res)
                : { w: res, h: res };
              return runFfmpegCropScaleWebp({
                srcPng: job.masterPngPath,
                destWebp: path.join(resDir, job.webpName),
                cropRectPx: job.cropPx,
                targetWidth: targetSize.w,
                targetHeight: targetSize.h,
                lossless: job.lossless && res <= 1024,
                quality: job.quality,
              });
            }),
          );
        }
      }

      await page.evaluate(() => window.__PLINTH_GENERATOR__.dispose());
    }

    // Write final turntable-layout.json to both output/ and Android assets/turntable/
    const manifestJson = JSON.stringify(sharedManifest, null, 2);
    await fs.writeFile(path.join(OUTPUT_DIR, 'turntable-layout.json'), manifestJson, 'utf8');
    await fs.mkdir(ANDROID_ASSETS_DIR, { recursive: true });
    await fs.writeFile(path.join(ANDROID_ASSETS_DIR, 'turntable-layout.json'), manifestJson, 'utf8');

    console.log('\nAsset generation complete! Manifest written to app/src/main/assets/turntable/turntable-layout.json');
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
