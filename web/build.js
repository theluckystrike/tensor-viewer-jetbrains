'use strict';
// Builds the browser bundle into ../src/main/resources/web (committed, so the Gradle build needs no Node).
const esbuild = require('esbuild');
const path = require('path');
const fs = require('fs');
const root = __dirname;
const out = path.join(root, '..', 'src', 'main', 'resources', 'web');
fs.mkdirSync(out, { recursive: true });
const shims = path.join(root, 'src', 'shared');
esbuild.build({
  bundle: true, minify: true, sourcemap: false, legalComments: 'none', logLevel: 'warning', absWorkingDir: root,
  platform: 'browser', target: 'chrome120', format: 'iife',
  inject: [path.join(shims, 'buffer-global.js')],
  // core/source.js loads fs for its Node FileSource, which the page never uses: the page reads through HttpSource.
  alias: {
    // The browser build of the GGUF reader (its package exports point esbuild at the Node build, which needs fs).
    '@huggingface/gguf': path.join(root, 'node_modules', '@huggingface', 'gguf', 'dist', 'browser', 'index.js'),
    // It uses only the GGML quant tables of @huggingface/tasks; the rest of that package (model cards, snippets) is left out.
    '@huggingface/tasks': path.join(root, 'node_modules', '@huggingface', 'tasks', 'dist', 'commonjs', 'gguf.js'),
    fs: path.join(shims, 'fs-shim.js'), 'node:fs': path.join(shims, 'fs-shim.js') },
  entryPoints: ['src/tensor/page.js'], outfile: path.join(out, 'tensor.js'),
}).then(() => {
  fs.copyFileSync(path.join(root, 'src/tensor/webview.css'), path.join(out, 'tensor.css'));
  for (const f of fs.readdirSync(out)) console.log(f.padEnd(22), fs.statSync(path.join(out, f)).size);
}, e => { console.error(e && e.message); process.exit(1); });
