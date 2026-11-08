'use strict';
// Runs the tensor-viewer-vscode 0.5.1 core tests against the sources that go into the bundle (web/src/tensor/core),
// plus the bundle checks and the large-file test. Fixtures are the VS Code repo's test/fixtures (each under 5 MB).
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const files = [
  ...fs.readdirSync(path.join(__dirname, 'tensor')).filter(f => f.endsWith('.test.js')).sort().map(f => path.join('test', 'tensor', f)),
  path.join('test', 'bundle.test.js'),
];
const r = spawnSync(process.execPath, ['--test', ...files], { cwd: root, stdio: 'inherit' });
process.exit(r.status);
