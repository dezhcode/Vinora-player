# Plinth 3D Turntable Asset Generator

Standalone build-time Three.js PBR rendering pipeline for Plinth.
This tool is **not** compiled or bundled into the Android APK.

## Architecture

- `src/textures.js` — Procedural 2K horizontal brushed aluminum, micro-bead matte chassis, radial lathe knob, counterweight scale markings, studio contact shadow, and studio softbox PMREM environment.
- `src/turntableModel.js` — High-detail 90° top-down 3D turntable matching the Plinth industrial reference (two-tone brushed silver / matte off-white split deck, rear hinges, deep black grooved vinyl, stationary studio specular cones, machined dials, and articulated straight-arm + slotted silver headshell assembly).
- `src/exporter.js` — Strict `OrthographicCamera` renderer + normalized world-to-viewport coordinate & crop-rect JSON extractor (`turntable-layout.json`).
- `src/index.html` + `preview-server.mjs` — Interactive studio inspector UI (`npm run preview`).
- `generate.mjs` — Headless Playwright WebGL2 + `ffmpeg` batch pipeline (`npm run generate`).

## Regenerating Assets

```bash
cd tools/turntable-asset-generator
npm install
npm run generate
```

Outputs:
1. **4096×4096 lossless master PNGs**: `tools/turntable-asset-generator/output/masters-4096/{light,dark}/`
2. **Multi-resolution WebP layers (`512`, `1024`, `2048`)**: `app/src/main/assets/turntable/{light,dark}/{512,1024,2048}/`
3. **Normalized coordinate & layer manifest**: `app/src/main/assets/turntable/turntable-layout.json`
