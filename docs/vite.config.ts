import fs from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, type Plugin } from 'vite';

const __dirname = dirname(fileURLToPath(import.meta.url));

function custom404Plugin(): Plugin {
  const shouldServe404Html = (url: string | undefined, accept: string | undefined): boolean => {
    if (!url || url === '/404.html' || url === '/index.html') return false;
    const clean = url.split('?')[0];
    if (clean.startsWith('/@') || clean.startsWith('/src/') || clean.startsWith('/node_modules/')) {
      return false;
    }
    const wantsHtml = !accept || accept.includes('text/html') || accept.includes('*/*');
    const hasExtension = /\.[a-z0-9]+$/i.test(clean);
    return wantsHtml && (!hasExtension || clean.endsWith('.html'));
  };

  return {
    name: 'proximity-custom-404',
    configureServer(server) {
      return () => {
        server.middlewares.use(async (req, res, next) => {
          if (req.method !== 'GET' && req.method !== 'HEAD') return next();
          if (!shouldServe404Html(req.url, req.headers.accept)) return next();

          try {
            const rawHtml = fs.readFileSync(resolve(__dirname, '404.html'), 'utf-8');
            const transformed = await server.transformIndexHtml(req.originalUrl || '/404.html', rawHtml);
            res.statusCode = 404;
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            res.end(req.method === 'HEAD' ? undefined : transformed);
          } catch (err) {
            next(err);
          }
        });
      };
    },
    configurePreviewServer(server) {
      return () => {
        server.middlewares.use((req, res, next) => {
          if (req.method !== 'GET' && req.method !== 'HEAD') return next();
          if (!shouldServe404Html(req.url, req.headers.accept)) return next();

          const dist404 = resolve(__dirname, 'dist', '404.html');
          if (!fs.existsSync(dist404)) return next();

          const html = fs.readFileSync(dist404, 'utf-8');
          res.statusCode = 404;
          res.setHeader('Content-Type', 'text/html; charset=utf-8');
          res.end(req.method === 'HEAD' ? undefined : html);
        });
      };
    }
  };
}

export default defineConfig({
  appType: 'mpa',
  plugins: [custom404Plugin()],
  build: {
    target: 'es2022',
    outDir: 'dist',
    assetsInlineLimit: 4096,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        notFound: resolve(__dirname, '404.html')
      },
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/three/')) {
            return 'three-vendor';
          }
        }
      }
    }
  }
});
