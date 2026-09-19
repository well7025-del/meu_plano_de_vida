import { createReadStream, statSync } from 'node:fs';
import { extname, join, normalize, resolve, sep } from 'node:path';
import type { IncomingMessage, ServerResponse } from 'node:http';

/**
 * Servidor de arquivos estaticos.
 *
 * O mesmo processo entrega a API e o app. Para um piloto isso vale mais que
 * elegancia: e um endereco so para mandar no grupo, um deploy so para manter,
 * e a origem e a mesma — sem CORS, sem chave, sem configuracao no celular de
 * ninguem.
 */
const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.map': 'application/json; charset=utf-8',
};

export interface Mount {
  /** Prefixo da URL, ex.: '/core'. '' serve a raiz. */
  prefix: string;
  /** Pasta no disco. */
  dir: string;
}

export function serveStatic(
  mounts: Mount[],
  req: IncomingMessage,
  res: ServerResponse,
  pathname: string,
): boolean {
  if (req.method !== 'GET' && req.method !== 'HEAD') return false;

  for (const mount of mounts) {
    if (mount.prefix && !pathname.startsWith(mount.prefix)) continue;
    const rel = mount.prefix ? pathname.slice(mount.prefix.length) : pathname;
    const file = resolveInside(mount.dir, rel === '' || rel === '/' ? '/index.html' : rel);
    if (!file) continue;

    try {
      const info = statSync(file);
      if (!info.isFile()) continue;

      const type = TYPES[extname(file)] ?? 'application/octet-stream';
      // O shell do app nao pode ficar em cache: durante um piloto a correcao
      // precisa chegar no celular de todo mundo no proximo refresh.
      const cache = file.endsWith('index.html') || file.endsWith('sw.js')
        ? 'no-cache'
        : 'public, max-age=300';

      res.writeHead(200, {
        'content-type': type,
        'content-length': info.size,
        'cache-control': cache,
      });
      if (req.method === 'HEAD') {
        res.end();
        return true;
      }
      createReadStream(file).pipe(res);
      return true;
    } catch {
      continue;
    }
  }
  return false;
}

/**
 * Resolve o caminho e garante que ele nao escapa da pasta servida.
 *
 * Sem isto, `GET /../../etc/passwd` le o disco inteiro. E o tipo de furo que
 * so aparece depois de o endereco estar circulando num grupo de WhatsApp.
 */
function resolveInside(dir: string, rel: string): string | null {
  let decoded: string;
  try {
    decoded = decodeURIComponent(rel);
  } catch {
    return null;
  }
  if (decoded.includes('\0')) return null;

  const base = resolve(dir);
  const target = resolve(join(base, normalize(decoded)));
  if (target !== base && !target.startsWith(base + sep)) return null;
  return target;
}
