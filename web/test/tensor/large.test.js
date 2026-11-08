'use strict';
// A 20 GB GGUF must open by reading its header only. The source below claims 20 GB: the first bytes are a real
// GGUF fixture and the rest reads as zeros, so nothing is written to disk. It counts every byte the parser asks for.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { openTensorFile } = require('../../src/tensor/core/formats');
const { FIX } = require('./helpers');

class SparseSource {
  constructor(head, size) { this.head = head; this.size = size; this.random = true; this.bytesRead = 0; this.reads = 0; }
  async read(offset, length) {
    if (offset < 0 || length < 0 || offset + length > this.size) throw new RangeError('past the end');
    this.bytesRead += length; this.reads++;
    const out = new Uint8Array(length);
    if (offset < this.head.length) out.set(this.head.subarray(offset, Math.min(this.head.length, offset + length)));
    return out;
  }
  async close() {}
}

test('a 20 GB GGUF opens from its header in a few reads', async () => {
  const head = new Uint8Array(fs.readFileSync(path.join(FIX, 'tiny-llama-q8.gguf')));
  const src = new SparseSource(head, 20 * 1024 ** 3);
  const t0 = Date.now();
  const m = await openTensorFile(src, 'big-model-Q8_0.gguf');
  const ms = Date.now() - t0;
  assert.strictEqual(m.format, 'gguf');
  assert.ok(m.tensors.length > 0);
  assert.strictEqual(m.fileSize, 20 * 1024 ** 3);
  assert.ok(src.bytesRead < 16 * 1048576, `read ${src.bytesRead} bytes`);
  assert.ok(ms < 2000, `took ${ms} ms`);
  assert.ok(m.info.some(x => x.label === 'Architecture'));
});

test('a 20 GB safetensors file opens from its header only', async () => {
  const head = new Uint8Array(fs.readFileSync(path.join(FIX, 'gpt2_tiny.safetensors')));
  const n = Number(new DataView(head.buffer, head.byteOffset).getBigUint64(0, true));
  const src = new SparseSource(head.subarray(0, 8 + n), 20 * 1024 ** 3);
  const m = await openTensorFile(src, 'model-00001-of-00004.safetensors');
  assert.strictEqual(m.format, 'safetensors');
  assert.ok(m.tensors.length > 0);
  assert.strictEqual(src.bytesRead, 8 + n);
});
