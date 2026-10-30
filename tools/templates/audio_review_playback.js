/* Lossless PCM excerpts for offline audio review. Source files stay unchanged. */
(() => {
  'use strict';

  function slicePcm16Wav(bytes, startSeconds, endSeconds) {
    if (!(bytes instanceof Uint8Array)) throw new TypeError('WAV input must be a Uint8Array.');
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const tag = offset => String.fromCharCode(...bytes.subarray(offset, offset + 4));
    if (bytes.byteLength < 12 || tag(0) !== 'RIFF' || tag(8) !== 'WAVE') {
      throw new Error('Expected a RIFF/WAVE file.');
    }
    const riffEnd = view.getUint32(4, true) + 8;
    if (riffEnd < 12 || riffEnd > bytes.byteLength) throw new Error('Invalid RIFF length.');

    let format = null;
    let data = null;
    for (let offset = 12; offset < riffEnd;) {
      if (riffEnd - offset < 8) throw new Error('Truncated WAV chunk header.');
      const kind = tag(offset);
      const size = view.getUint32(offset + 4, true);
      const start = offset + 8;
      const end = start + size;
      const next = end + (size % 2);
      if (next > riffEnd) throw new Error('Truncated WAV chunk or padding.');
      if (kind === 'fmt ') {
        if (format !== null || size < 16) throw new Error('Invalid or duplicate WAV format chunk.');
        format = {
          encoding: view.getUint16(start, true),
          channelCount: view.getUint16(start + 2, true),
          sampleRateHz: view.getUint32(start + 4, true),
          byteRate: view.getUint32(start + 8, true),
          blockAlign: view.getUint16(start + 12, true),
          bitsPerSample: view.getUint16(start + 14, true),
        };
      } else if (kind === 'data') {
        if (data !== null) throw new Error('Multiple WAV data chunks are unsupported.');
        data = {start, size};
      }
      offset = next;
    }
    if (format === null || data === null) throw new Error('WAV format and data chunks are required.');
    const {channelCount, sampleRateHz, blockAlign, byteRate} = format;
    if (format.encoding !== 1 || format.bitsPerSample !== 16 ||
        (channelCount !== 1 && channelCount !== 2)) {
      throw new Error('Only PCM 16-bit mono or stereo WAV files are supported.');
    }
    if (sampleRateHz === 0 || blockAlign !== channelCount * 2 || byteRate !== sampleRateHz * blockAlign ||
        data.size === 0 || data.size % blockAlign !== 0) {
      throw new Error('Invalid PCM format or incomplete sample frame.');
    }

    const frameCount = data.size / blockAlign;
    // Saved decimal timestamps may round the end just beyond the last sample frame.
    const requestedEndFrame = endSeconds * sampleRateHz;
    const roundingTolerance = Number.EPSILON * Math.max(frameCount, Math.abs(requestedEndFrame), 1) * 4;
    if (!Number.isFinite(startSeconds) || !Number.isFinite(endSeconds) ||
        startSeconds < 0 || endSeconds <= startSeconds || startSeconds * sampleRateHz >= frameCount ||
        requestedEndFrame > frameCount + 1 + roundingTolerance) {
      throw new RangeError('Excerpt must be a nonempty range within the WAV duration.');
    }
    const startFrame = Math.round(startSeconds * sampleRateHz);
    const endFrame = Math.min(frameCount, Math.round(requestedEndFrame));
    if (endFrame <= startFrame) throw new RangeError('Excerpt contains no complete sample frames.');

    const dataSize = (endFrame - startFrame) * blockAlign;
    if (dataSize > 0xffffffff - 36) throw new RangeError('Excerpt exceeds the RIFF size limit.');
    const result = new Uint8Array(44 + dataSize);
    const header = new DataView(result.buffer);
    const writeTag = (offset, value) => {
      for (let index = 0; index < value.length; index += 1) result[offset + index] = value.charCodeAt(index);
    };
    writeTag(0, 'RIFF');
    header.setUint32(4, 36 + dataSize, true);
    writeTag(8, 'WAVE');
    writeTag(12, 'fmt ');
    header.setUint32(16, 16, true);
    header.setUint16(20, 1, true);
    header.setUint16(22, channelCount, true);
    header.setUint32(24, sampleRateHz, true);
    header.setUint32(28, byteRate, true);
    header.setUint16(32, blockAlign, true);
    header.setUint16(34, 16, true);
    writeTag(36, 'data');
    header.setUint32(40, dataSize, true);
    result.set(bytes.subarray(data.start + startFrame * blockAlign, data.start + endFrame * blockAlign), 44);
    return {
      bytes: result,
      start: startFrame / sampleRateHz,
      end: endFrame / sampleRateHz,
      durationSeconds: (endFrame - startFrame) / sampleRateHz,
      sampleRateHz,
      channelCount,
    };
  }

  window.OverdexAudioWindows = Object.freeze({slicePcm16Wav});
})();
