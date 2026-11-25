import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT_DIR = path.dirname(fileURLToPath(import.meta.url));

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.css': 'text/css; charset=utf-8',
};

export function startStaticServer(port = 0) {
  const server = http.createServer(async (req, res) => {
    try {
      const reqPath = (req.url || '/').split('?')[0];
      const mapped = reqPath === '/' ? '/src/index.html' : reqPath;
      const safePath = path.normalize(mapped).replace(/^(\.\.(\/|\\|$))+/, '');
      const fullPath = path.join(ROOT_DIR, safePath);
      if (!fullPath.startsWith(ROOT_DIR)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
      }
      const data = await fs.readFile(fullPath);
      const ext = path.extname(fullPath).toLowerCase();
      res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
      res.end(data);
    } catch {
      res.writeHead(404);
      res.end('Not Found');
    }
  });

  return new Promise((resolve) => {
    server.listen(port, '127.0.0.1', () => {
      const addr = server.address();
      resolve({ server, port: addr.port, url: `http://127.0.0.1:${addr.port}/src/index.html` });
    });
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 4173);
  const { url } = await startStaticServer(port);
  console.log(`Plinth 3D Asset Generator Preview running at ${url}`);
}
