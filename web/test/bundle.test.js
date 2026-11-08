'use strict';
// Checks the built bundle in src/main/resources/web: present, self-contained, GGUF reader included, no Node-only imports.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const web = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'web');

test('the bundle, the styles and the page template exist', () => {
  for (const f of ['tensor.js', 'tensor.css', 'shell.css', 'view.html']) assert.ok(fs.statSync(path.join(web, f)).size > 0, f);
  assert.deepStrictEqual(fs.readdirSync(web).sort(), ['shell.css', 'tensor.css', 'tensor.js', 'view.html']);
});

test('the bundle makes no network calls and loads nothing from outside the private origin', () => {
  const s = fs.readFileSync(path.join(web, 'tensor.js'), 'latin1');
  assert.ok(!/require\(["'](fs|path|worker_threads|child_process)["']\)/.test(s), 'Node import');
  assert.ok(!/https?:\/\/(?!www\.w3\.org|github\.com\/)[a-z0-9.-]+\.(com|org|io|net)\/[^"'\s]*\.(js|wasm)/i.test(s), 'remote code');
  // The GGUF library carries Hub URLs as text, but the core hands it a local reader (tensor-viewer.invalid), and the
  // page CSP (connect-src 'self') blocks any other request.
  assert.ok(s.includes('tensor-viewer.invalid'), 'the GGUF reader must use the local range reader');
});

test('the GGUF, safetensors and PyTorch readers are in the bundle (not stubbed)', () => {
  const s = fs.readFileSync(path.join(web, 'tensor.js'), 'latin1');
  assert.ok(!s.includes('GGUF files are not supported'), 'GGUF stub still bundled');
  assert.ok(s.includes('tensor-viewer.invalid'), 'GGUF reader missing');
  assert.ok(s.includes('Not a safetensors file'), 'safetensors reader missing');
  assert.ok(s.includes('data.pkl'), 'PyTorch zip reader missing');
});

test('the page template has a strict CSP and every placeholder', () => {
  const t = fs.readFileSync(path.join(web, 'view.html'), 'utf8');
  assert.match(t, /default-src 'none'/);
  assert.match(t, /connect-src 'self'/);
  for (const k of ['NONCE', 'TOKEN', 'KIND', 'TITLE', 'BODYCLASS', 'FOOTER', 'CONFIG']) assert.ok(t.includes('{{' + k + '}}'), k);
});
