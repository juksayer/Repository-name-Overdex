'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

const script = fs.readFileSync(path.join(__dirname, '../templates/audio_review_playback.js'), 'utf8');
const context = {window: {}, Uint8Array, DataView, TypeError, RangeError, Error};
vm.runInNewContext(script, context);
const {slicePcm16Wav} = context.window.OverdexAudioWindows;

function chunk(kind, contents) {
  const output = Buffer.alloc(8 + contents.length + contents.length % 2);
  output.write(kind, 0, 4, 'ascii');
  output.writeUInt32LE(contents.length, 4);
  contents.copy(output, 8);
  return output;
}

function riff(chunks) {
  const contents = Buffer.concat(chunks);
  const header = Buffer.alloc(12);
  header.write('RIFF', 0, 'ascii');
  header.writeUInt32LE(contents.length + 4, 4);
  header.write('WAVE', 8, 'ascii');
  return Buffer.concat([header, contents]);
}

function fixture({sampleRate = 8000, channels = 1, frames = 9600, extraChunks = []} = {}) {
  const format = Buffer.alloc(16);
  format.writeUInt16LE(1, 0);
  format.writeUInt16LE(channels, 2);
  format.writeUInt32LE(sampleRate, 4);
  format.writeUInt32LE(sampleRate * channels * 2, 8);
  format.writeUInt16LE(channels * 2, 12);
  format.writeUInt16LE(16, 14);
  const pcm = Buffer.alloc(frames * channels * 2);
  for (let frame = 0; frame < frames; frame += 1) {
    for (let channel = 0; channel < channels; channel += 1) {
      pcm.writeInt16LE(((frame * 97 + channel * 19001) % 65536) - 32768, (frame * channels + channel) * 2);
    }
  }
  return {format, pcm, wav: riff([chunk('fmt ', format), ...extraChunks, chunk('data', pcm)])};
}

function pcmOf(result) {
  return Buffer.from(result.bytes.buffer, result.bytes.byteOffset + 44, result.bytes.byteLength - 44);
}

test('nonzero 416 ms excerpt preserves all 3328 mono frames and source bytes', () => {
  const source = fixture();
  const original = Buffer.from(source.wav);
  const excerpt = slicePcm16Wav(source.wav, 0.672, 1.088);
  assert.equal(excerpt.start, 0.672);
  assert.equal(excerpt.end, 1.088);
  assert.equal(excerpt.durationSeconds, 0.416);
  assert.equal(excerpt.sampleRateHz, 8000);
  assert.equal(excerpt.channelCount, 1);
  assert.equal(excerpt.bytes.length, 44 + 3328 * 2);
  assert.deepEqual(pcmOf(excerpt), source.pcm.subarray(5376 * 2, 8704 * 2));
  assert.deepEqual(source.wav, original);
  assert.notEqual(excerpt.bytes.buffer, source.wav.buffer);
  excerpt.bytes[44] ^= 255;
  assert.deepEqual(source.wav, original);
});

test('writes a canonical 44-byte PCM header and round-trips the output', () => {
  const excerpt = slicePcm16Wav(fixture().wav, 0.144, 0.560);
  const header = Buffer.from(excerpt.bytes);
  assert.equal(header.toString('ascii', 0, 4), 'RIFF');
  assert.equal(header.readUInt32LE(4), header.length - 8);
  assert.equal(header.toString('ascii', 8, 16), 'WAVEfmt ');
  assert.equal(header.readUInt32LE(16), 16);
  assert.equal(header.readUInt16LE(20), 1);
  assert.equal(header.readUInt16LE(22), 1);
  assert.equal(header.readUInt32LE(24), 8000);
  assert.equal(header.readUInt32LE(28), 16000);
  assert.equal(header.readUInt16LE(32), 2);
  assert.equal(header.readUInt16LE(34), 16);
  assert.equal(header.toString('ascii', 36, 40), 'data');
  assert.equal(header.readUInt32LE(40), 6656);
  assert.deepEqual(slicePcm16Wav(excerpt.bytes, 0, excerpt.durationSeconds).bytes, excerpt.bytes);
});

test('stereo preserves interleaving and rounds both endpoints to complete frames', () => {
  const source = fixture({sampleRate: 44100, channels: 2, frames: 100});
  const excerpt = slicePcm16Wav(source.wav, 10.49 / 44100, 35.51 / 44100);
  assert.equal(excerpt.start, 10 / 44100);
  assert.equal(excerpt.end, 36 / 44100);
  assert.equal(excerpt.durationSeconds, 26 / 44100);
  assert.equal(excerpt.sampleRateHz, 44100);
  assert.equal(excerpt.channelCount, 2);
  assert.deepEqual(pcmOf(excerpt), source.pcm.subarray(10 * 4, 36 * 4));
  assert.equal(new DataView(excerpt.bytes.buffer).getUint16(32, true), 4);
});

test('walks odd-sized padded chunks and data preceding an extended format chunk', () => {
  const source = fixture({frames: 16});
  const wav = riff([
    chunk('JUNK', Buffer.from([1, 2, 3])),
    chunk('data', source.pcm),
    chunk('LIST', Buffer.from([9])),
    chunk('fmt ', Buffer.concat([source.format, Buffer.alloc(2)])),
  ]);
  const excerpt = slicePcm16Wav(wav, 2 / 8000, 9 / 8000);
  assert.deepEqual(pcmOf(excerpt), source.pcm.subarray(4, 18));
});

test('reads Uint8Array views at nonzero byte offsets without using surrounding bytes', () => {
  const source = fixture({frames: 16});
  const outer = new Uint8Array(source.wav.length + 23).fill(0xa5);
  outer.set(source.wav, 11);
  const view = outer.subarray(11, 11 + source.wav.length);
  const excerpt = slicePcm16Wav(view, 0, 16 / 8000);
  assert.deepEqual(pcmOf(excerpt), source.pcm);
  assert.equal(outer[10], 0xa5);
  assert.equal(outer[outer.length - 1], 0xa5);
});

test('allows at most one frame of end rounding, clamping to real source length', () => {
  const source = fixture({frames: 10});
  const excerpt = slicePcm16Wav(source.wav, 8 / 8000, 11 / 8000);
  assert.equal(excerpt.end, 10 / 8000);
  assert.equal(excerpt.durationSeconds, 2 / 8000);
  assert.deepEqual(pcmOf(excerpt), source.pcm.subarray(16));
  assert.throws(() => slicePcm16Wav(source.wav, 0, 11.01 / 8000), RangeError);
});

test('rejects invalid, nonfinite, empty and out-of-bounds ranges', () => {
  const {wav} = fixture();
  for (const [start, end] of [
    [-0.1, 0.4], [0.5, 0.5], [0.6, 0.5], [0, 1.3], [1.2, 1.200125],
    [NaN, 0.4], [0, NaN], [0, Infinity], [-Infinity, 0.4], ['0', 0.4], [0, null],
    [0.1, 0.1 + 0.1 / 8000], [1.2 - 0.1 / 8000, 1.2],
  ]) assert.throws(() => slicePcm16Wav(wav, start, end), RangeError, `${start} to ${end}`);
});

test('rejects unsupported encoding, sample width, channel count and inconsistent PCM headers', () => {
  for (const [offset, value, width] of [
    [20, 3, 2], [20, 0xfffe, 2], [34, 8, 2], [22, 0, 2], [22, 3, 2],
    [24, 0, 4], [28, 8000, 4], [32, 4, 2],
  ]) {
    const {wav} = fixture();
    if (width === 2) wav.writeUInt16LE(value, offset);
    else wav.writeUInt32LE(value, offset);
    assert.throws(() => slicePcm16Wav(wav, 0, 0.4));
  }
});

test('rejects missing, duplicate, empty and incomplete PCM chunks', () => {
  const source = fixture({frames: 4});
  for (const chunks of [
    [], [chunk('fmt ', source.format)], [chunk('data', source.pcm)],
    [chunk('fmt ', source.format.subarray(0, 14)), chunk('data', source.pcm)],
    [chunk('fmt ', source.format), chunk('fmt ', source.format), chunk('data', source.pcm)],
    [chunk('fmt ', source.format), chunk('data', source.pcm), chunk('data', source.pcm)],
    [chunk('fmt ', source.format), chunk('data', Buffer.alloc(0))],
    [chunk('fmt ', source.format), chunk('data', Buffer.from([1, 2, 3]))],
  ]) assert.throws(() => slicePcm16Wav(riff(chunks), 0, 2 / 8000));
});

test('rejects malformed signatures, RIFF/chunk lengths, partial headers and missing odd padding', () => {
  assert.throws(() => slicePcm16Wav([], 0, 0.1), TypeError);
  for (const length of [0, 4, 11]) assert.throws(() => slicePcm16Wav(new Uint8Array(length), 0, 0.1));
  for (const corrupt of [
    wav => { wav.write('RIFX', 0); },
    wav => { wav.write('NOPE', 8); },
    wav => { wav.writeUInt32LE(3, 4); },
    wav => { wav.writeUInt32LE(wav.length, 4); },
    wav => { wav.writeUInt32LE(0xffffffff, 16); },
    wav => { wav.writeUInt32LE(wav.length - 9, 4); },
  ]) {
    const {wav} = fixture();
    corrupt(wav);
    assert.throws(() => slicePcm16Wav(wav, 0, 0.4));
  }
  assert.throws(() => slicePcm16Wav(riff([Buffer.from([1, 2, 3])]), 0, 0.1));
  const oddWithoutPad = chunk('JUNK', Buffer.from([1])).subarray(0, 9);
  assert.throws(() => slicePcm16Wav(riff([oddWithoutPad]), 0, 0.1), /padding/);
});

test('helper remains safe to inline into an HTML script element', () => {
  assert.equal(script.toLowerCase().includes('<' + '/script'), false);
});
