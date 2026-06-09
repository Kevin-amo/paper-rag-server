import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const files = {
  http: readFileSync(resolve(root, 'src/api/http.ts'), 'utf8'),
  vite: readFileSync(resolve(root, 'vite.config.ts'), 'utf8'),
  compose: readFileSync(resolve(root, '../docker-compose.yml'), 'utf8'),
};

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  files.http.includes("import.meta.env.VITE_API_PREFIX?.trim() ?? '/api'"),
  'frontend API prefix should default to /api so SPA routes and API routes do not collide',
);

assert(
  !/['"]\/admin['"]\s*:\s*\{/.test(files.vite),
  'Vite dev proxy must not proxy /admin because it is also a Vue Router page route',
);

assert(
  /['"]\/api['"]\s*:\s*\{/.test(files.vite) && files.vite.includes("path.replace(/^\\/api/, '')"),
  'Vite dev proxy should proxy /api and rewrite it to backend root paths',
);

assert(
  files.compose.includes('VITE_API_PREFIX: ${VITE_API_PREFIX:-/api}'),
  'Docker frontend build should default VITE_API_PREFIX to /api',
);

assert(
  !/\^\/\(auth\|admin\|documents\|conversations\|agent\)/.test(files.compose),
  'Nginx must not proxy /admin directly because browser refresh should serve the SPA',
);

assert(
  files.compose.includes('location /api/') && files.compose.includes('rewrite ^/api/(.*)$$ /$$1 break;'),
  'Nginx should proxy /api/ and rewrite it to backend root paths',
);

console.log('routing config avoids SPA/API path collisions');
