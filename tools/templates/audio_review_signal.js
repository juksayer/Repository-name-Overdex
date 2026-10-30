/* Offline signal views. Input is the generator's fixed-scale analyze_wav() data. */
(() => {
  'use strict';

  const STYLE_ID = 'overdex-audio-signal-style';
  const FREQUENCY_MAX = 4000;
  const DB_MIN = -90;
  const DB_MAX = 0;
  const LEFT = 33;
  const RIGHT = 9;
  const TOP = 7;
  const BOTTOM = 23;
  const WAVE_HEIGHT = 70;
  const SPECTRUM_HEIGHT = 100;
  let nextId = 0;

  const paletteStops = [
    [0, [15, 24, 43]],
    [.25, [31, 63, 109]],
    [.5, [27, 135, 161]],
    [.75, [111, 206, 178]],
    [1, [255, 226, 125]],
  ];
  const palette = Array.from({length:256}, (_, value) => {
    const position = value / 255;
    const upper = paletteStops.findIndex(stop => stop[0] >= position);
    if (upper === 0) return paletteStops[0][1];
    const [end, last] = paletteStops[upper];
    const [start, first] = paletteStops[upper - 1];
    const mix = (position - start) / (end - start);
    return first.map((component, channel) => Math.round(component + (last[channel] - component) * mix));
  });

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = String(text);
    return node;
  }

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .odx-signal { margin:13px 0 10px; width:100%; min-width:170px; box-sizing:border-box; color:#304856; }
      .odx-signal * { box-sizing:border-box; }
      .odx-signal-heading { display:flex; flex-wrap:wrap; justify-content:space-between; gap:2px 8px; margin:8px 0 3px; font:600 11px/1.4 system-ui,sans-serif; }
      .odx-signal-heading span:last-child { color:#59717d; font-weight:400; }
      .odx-signal canvas { display:block; width:100%; border-radius:5px; }
      .odx-signal-caption { margin:4px 0 0; color:#526b78; font:11px/1.45 system-ui,sans-serif; }
      .odx-signal-selection { margin:4px 0 0; color:#775100; font:600 11px/1.4 system-ui,sans-serif; overflow-wrap:anywhere; }
      .odx-signal-cursor { margin:2px 0 0; color:#4d6472; font:11px/1.4 system-ui,sans-serif; font-variant-numeric:tabular-nums; }
      .odx-signal-legend { display:flex; align-items:center; gap:6px; color:#526b78; font:10px/1.4 system-ui,sans-serif; margin:2px 0 0 33px; }
      .odx-signal-gradient { flex:1; min-width:35px; height:7px; border-radius:2px; background:linear-gradient(90deg,#0f182b 0%,#1f3f6d 25%,#1b87a1 50%,#6fceb2 75%,#ffe27d 100%); }
      .odx-signal-unavailable { margin:8px 0; padding:10px; border:1px solid #d2dfe0; border-radius:6px; color:#526b78; font:12px/1.5 system-ui,sans-serif; background:#f3f7f6; }
    `;
    document.head.append(style);
  }

  function clamp(value, low, high) { return Math.max(low, Math.min(high, value)); }
  function timeText(value) { return value.toFixed(3) + ' s'; }

  function validSignal(signal, duration) {
    if (!signal || !Number.isFinite(duration) || duration <= 0 ||
        !Array.isArray(signal.waveform) || !signal.waveform.length || signal.waveform.length > 20000) return false;
    if (signal.waveform.some(bin => !Array.isArray(bin) || bin.length !== 2 ||
        !bin.every(Number.isFinite) || bin[0] > bin[1] || bin[0] < -1 || bin[1] > 1)) return false;
    const spectrum = signal.spectrogram;
    return Boolean(spectrum && Number.isInteger(spectrum.columns) && spectrum.columns > 0 && spectrum.columns <= 20000 &&
      Number.isInteger(spectrum.rows) && spectrum.rows > 0 && spectrum.rows <= 1024 &&
      spectrum.columns * spectrum.rows <= 2000000 && Array.isArray(spectrum.values) &&
      spectrum.values.length === spectrum.columns * spectrum.rows &&
      spectrum.values.every(value => Number.isInteger(value) && value >= 0 && value <= 255) &&
      Number.isFinite(spectrum.maxFrequencyHz) && spectrum.maxFrequencyHz > 0 && spectrum.maxFrequencyHz <= FREQUENCY_MAX &&
      Number.isFinite(spectrum.minDb) && Number.isFinite(spectrum.maxDb) && spectrum.minDb < spectrum.maxDb &&
      Number.isFinite(spectrum.frameStartSeconds) && spectrum.frameStartSeconds >= 0 &&
      Number.isFinite(spectrum.frameStepSeconds) && (spectrum.frameStepSeconds > 0 || (spectrum.columns === 1 && spectrum.frameStepSeconds === 0)) &&
      Number.isFinite(spectrum.frameWindowSeconds) && spectrum.frameWindowSeconds > 0 &&
      (!spectrum.valueLayout || spectrum.valueLayout === 'column-major') &&
      (!spectrum.scale || spectrum.scale === 'peak-amplitude-dbfs'));
  }

  function ticks(duration, width) {
    const target = duration / Math.max(2, Math.floor(width / 60));
    const magnitude = 10 ** Math.floor(Math.log10(target));
    const scaled = target / magnitude;
    const step = (scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10) * magnitude;
    const result = [0];
    for (let value = step; value < duration - step * .4; value += step) result.push(value);
    result.push(duration);
    return result;
  }

  function create(media, options = {}) {
    installStyle();
    const label = typeof options.label === 'string' && options.label ? options.label : 'Audio';
    const root = element('figure', 'odx-signal');
    const id = 'odx-audio-signal-' + (++nextId);
    root.setAttribute('aria-label', label + ' signal views');
    const signal = media && media.signal;
    const duration = media && media.durationSeconds;
    if (!validSignal(signal, duration)) {
      root.append(element('p', 'odx-signal-unavailable', 'Signal views are unavailable for this audio. You can still listen using its audio controls.'));
      return {element:root, setSelection() {}, setCursor() {}, destroy() { root.remove(); }};
    }

    const spectrum = signal.spectrogram;
    let destroyed = false;
    let selection = null;
    let cursor = null;
    let width = 0;
    let pixelRatio = 0;
    let pendingResize = null;
    let lastCursorText = '';
    const makePlot = (title, scale, plotHeight, description) => {
      const heading = element('div', 'odx-signal-heading');
      heading.append(element('span', '', title), element('span', '', scale));
      const canvas = element('canvas');
      const height = TOP + plotHeight + BOTTOM;
      canvas.style.height = height + 'px';
      canvas.setAttribute('role', 'img');
      canvas.setAttribute('aria-label', label + ': ' + description);
      canvas.setAttribute('aria-describedby', id + '-caption ' + id + '-selection ' + id + '-cursor');
      canvas.textContent = description;
      root.append(heading, canvas);
      const cache = document.createElement('canvas');
      return {canvas, cache, plotHeight, height, context:canvas.getContext('2d'), cached:cache.getContext('2d')};
    };
    const waveform = makePlot('Waveform', 'Amplitude −1 to +1', WAVE_HEIGHT,
      'Waveform on a fixed digital full-scale amplitude axis, minus one to plus one. Time runs from zero to ' + timeText(duration) + '.');
    const spectrogram = makePlot('Spectrogram', '0–4 kHz · same scale', SPECTRUM_HEIGHT,
      'Spectrogram: time left to right, frequency zero to four thousand hertz bottom to top. Colors show peak amplitude from minus ninety to zero dBFS, using the same scale for every clip.');
    const legend = element('div', 'odx-signal-legend');
    legend.setAttribute('aria-label', 'Color scale: dark blue is minus 90 dBFS or quieter; pale yellow is zero dBFS.');
    legend.append(element('span', '', '−90 dBFS'), element('span', 'odx-signal-gradient'), element('span', '', '0 dBFS'));
    root.append(legend);
    const caption = element('figcaption', 'odx-signal-caption', 'Time within this audio file · fixed amplitude and color scales. These views help compare timing and texture; they do not identify a move.');
    caption.id = id + '-caption';
    const selectionText = element('p', 'odx-signal-selection', 'No changing-spectrum interval selected.');
    selectionText.id = id + '-selection';
    selectionText.setAttribute('aria-live', 'polite');
    const cursorText = element('p', 'odx-signal-cursor', 'Playback cursor not shown.');
    cursorText.id = id + '-cursor';
    root.append(caption, selectionText, cursorText);
    if (Number.isFinite(spectrum.paddedEndSeconds) && spectrum.paddedEndSeconds >= .0005) {
      root.append(element('p', 'odx-signal-caption', 'The last spectrum window includes ' + timeText(spectrum.paddedEndSeconds) + ' of zero padding beyond the file’s end. The time axis shows only the original audio.'));
    }

    const heatmap = document.createElement('canvas');
    heatmap.width = spectrum.columns;
    heatmap.height = spectrum.rows;
    const heatContext = heatmap.getContext('2d');
    if (!waveform.context || !waveform.cached || !spectrogram.context || !spectrogram.cached || !heatContext) {
      root.replaceChildren(element('p', 'odx-signal-unavailable', 'This browser cannot draw the signal views. Audio playback is still available.'));
      return {element:root, setSelection() {}, setCursor() {}, destroy() { root.remove(); }};
    }
    // The heatmap is generated once. Playback redraws only these cached images plus overlays.
    const pixels = heatContext.createImageData(spectrum.columns, spectrum.rows);
    for (let column = 0; column < spectrum.columns; column++) {
      for (let row = 0; row < spectrum.rows; row++) {
        const value = spectrum.values[column * spectrum.rows + row];
        const db = spectrum.minDb + value / 255 * (spectrum.maxDb - spectrum.minDb);
        const rgb = palette[Math.round(clamp((db - DB_MIN) / (DB_MAX - DB_MIN), 0, 1) * 255)];
        const pixel = ((spectrum.rows - 1 - row) * spectrum.columns + column) * 4;
        pixels.data[pixel] = rgb[0];
        pixels.data[pixel + 1] = rgb[1];
        pixels.data[pixel + 2] = rgb[2];
        pixels.data[pixel + 3] = 255;
      }
    }
    heatContext.putImageData(pixels, 0, 0);

    function xAt(seconds) { return LEFT + seconds / duration * (width - LEFT - RIGHT); }
    function prepare(plot) {
      plot.canvas.width = Math.round(width * pixelRatio);
      plot.canvas.height = Math.round(plot.height * pixelRatio);
      plot.cache.width = plot.canvas.width;
      plot.cache.height = plot.canvas.height;
      plot.cached.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      plot.cached.fillStyle = '#f4f8f8';
      plot.cached.fillRect(0, 0, width, plot.height);
      plot.cached.font = '10px system-ui, sans-serif';
      plot.cached.textBaseline = 'middle';
    }
    function axes(plot, values, position, dark = false) {
      const context = plot.cached;
      context.lineWidth = .6;
      values.forEach(([value, text]) => {
        const y = position(value);
        context.strokeStyle = dark ? 'rgba(209,236,240,.16)' : '#cbdfe0';
        context.beginPath(); context.moveTo(LEFT, y); context.lineTo(width - RIGHT, y); context.stroke();
        context.fillStyle = '#3f6479'; context.textAlign = 'right'; context.fillText(text, LEFT - 5, y);
      });
      const timeTicks = ticks(duration, width - LEFT - RIGHT);
      timeTicks.forEach((value, index) => {
        const x = xAt(value);
        context.strokeStyle = dark ? 'rgba(209,236,240,.16)' : '#d3e3e3';
        context.beginPath(); context.moveTo(x, TOP); context.lineTo(x, TOP + plot.plotHeight + 3); context.stroke();
        context.fillStyle = '#3f6479';
        context.textAlign = index === 0 ? 'left' : index === timeTicks.length - 1 ? 'right' : 'center';
        const decimals = duration < .1 ? 3 : duration < 1 ? 2 : duration < 10 ? 1 : 0;
        context.fillText(value === 0 ? '0 s' : value.toFixed(decimals) + ' s', x, TOP + plot.plotHeight + 13);
      });
    }
    function buildWaveform() {
      const context = waveform.cached;
      const center = TOP + WAVE_HEIGHT / 2;
      const amplitudeY = amplitude => center - amplitude * WAVE_HEIGHT / 2;
      axes(waveform, [[1, '+1'], [0, '0'], [-1, '−1']], amplitudeY);
      context.strokeStyle = '#187d82'; context.lineWidth = 1;
      context.beginPath();
      const count = signal.waveform.length;
      const columns = Math.max(1, Math.floor(width - LEFT - RIGHT));
      // Aggregate all source bins under a pixel so narrow impulses survive resizing.
      for (let column = 0; column < columns; column++) {
        const first = Math.min(count - 1, Math.floor(column * count / columns));
        const last = Math.min(count, Math.max(first + 1, Math.ceil((column + 1) * count / columns)));
        let low = 1; let high = -1;
        for (let bin = first; bin < last; bin++) { low = Math.min(low, signal.waveform[bin][0]); high = Math.max(high, signal.waveform[bin][1]); }
        const x = LEFT + column + .5;
        const top = amplitudeY(high); const bottom = amplitudeY(low);
        context.moveTo(x, top); context.lineTo(x, Math.max(top + .5, bottom));
      }
      context.stroke();
    }
    function buildSpectrogram() {
      const context = spectrogram.cached;
      const height = SPECTRUM_HEIGHT * spectrum.maxFrequencyHz / FREQUENCY_MAX;
      const center = spectrum.frameStartSeconds + spectrum.frameWindowSeconds / 2;
      const start = spectrum.columns === 1 ? spectrum.frameStartSeconds : center - spectrum.frameStepSeconds / 2;
      const end = spectrum.columns === 1 ? spectrum.frameStartSeconds + spectrum.frameWindowSeconds :
        center + (spectrum.columns - .5) * spectrum.frameStepSeconds;
      context.fillStyle = '#e4edef';
      context.fillRect(LEFT, TOP, width - LEFT - RIGHT, SPECTRUM_HEIGHT);
      context.save();
      context.beginPath(); context.rect(LEFT, TOP, width - LEFT - RIGHT, SPECTRUM_HEIGHT); context.clip();
      context.imageSmoothingEnabled = false;
      context.drawImage(heatmap, xAt(start), TOP + SPECTRUM_HEIGHT - height, xAt(end) - xAt(start), height);
      context.restore();
      axes(spectrogram, [[0, '0'], [1000, '1k'], [2000, '2k'], [3000, '3k'], [4000, '4k']],
        value => TOP + SPECTRUM_HEIGHT - value / FREQUENCY_MAX * SPECTRUM_HEIGHT, true);
    }
    function overlay(plot) {
      const context = plot.context;
      context.setTransform(1, 0, 0, 1, 0, 0);
      context.clearRect(0, 0, plot.canvas.width, plot.canvas.height);
      context.drawImage(plot.cache, 0, 0);
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.save(); context.beginPath(); context.rect(LEFT, TOP, width - LEFT - RIGHT, plot.plotHeight); context.clip();
      if (selection) {
        const start = xAt(selection.start); const end = xAt(selection.end);
        context.fillStyle = 'rgba(255,192,65,.16)'; context.fillRect(start, TOP, end - start, plot.plotHeight);
        context.strokeStyle = '#c88007'; context.lineWidth = 1.5; context.setLineDash([4, 3]);
        context.strokeRect(start + .5, TOP + .5, Math.max(0, end - start - 1), plot.plotHeight - 1);
        context.setLineDash([]);
      }
      if (cursor !== null) {
        const x = clamp(xAt(cursor), LEFT + 1, width - RIGHT - 1);
        context.strokeStyle = '#112c41'; context.lineWidth = 3;
        context.beginPath(); context.moveTo(x, TOP); context.lineTo(x, TOP + plot.plotHeight); context.stroke();
        context.strokeStyle = '#fff9e8'; context.lineWidth = 1;
        context.beginPath(); context.moveTo(x, TOP); context.lineTo(x, TOP + plot.plotHeight); context.stroke();
        context.fillStyle = '#fff9e8'; context.beginPath(); context.moveTo(x - 4, TOP); context.lineTo(x + 4, TOP); context.lineTo(x, TOP + 5); context.closePath(); context.fill();
      }
      context.restore();
    }
    function draw() {
      if (destroyed || !width) return;
      overlay(waveform); overlay(spectrogram);
    }
    function resize() {
      pendingResize = null;
      if (destroyed) return;
      const measured = Math.max(170, Math.round(root.getBoundingClientRect().width || 170));
      const ratio = clamp(window.devicePixelRatio || 1, 1, 3);
      if (width === measured && pixelRatio === ratio) return;
      width = measured; pixelRatio = ratio;
      prepare(waveform); prepare(spectrogram); buildWaveform(); buildSpectrogram(); draw();
    }
    function queueResize() {
      if (!destroyed && pendingResize === null) pendingResize = requestAnimationFrame(resize);
    }
    const observer = typeof ResizeObserver === 'function' ? new ResizeObserver(queueResize) : null;
    if (observer) observer.observe(root);
    window.addEventListener('resize', queueResize);
    queueResize();

    return {
      element:root,
      setSelection(range) {
        if (destroyed) return;
        const valid = range && Number.isFinite(range.start) && Number.isFinite(range.end) && range.end > range.start;
        const start = valid ? clamp(range.start, 0, duration) : 0;
        const end = valid ? clamp(range.end, 0, duration) : 0;
        selection = valid && end > start ? {start, end} : null;
        selectionText.textContent = selection ? 'Changing-spectrum interval: ' + timeText(selection.start) + '–' + timeText(selection.end) + ' (amber).' : 'No changing-spectrum interval selected.';
        draw();
      },
      setCursor(seconds) {
        if (destroyed) return;
        const next = Number.isFinite(seconds) ? clamp(seconds, 0, duration) : null;
        if (cursor === next) return;
        cursor = next;
        const text = cursor === null ? 'Playback cursor not shown.' : 'Playback cursor: ' + timeText(cursor);
        if (text !== lastCursorText) { cursorText.textContent = text; lastCursorText = text; }
        draw();
      },
      destroy() {
        if (destroyed) return;
        destroyed = true;
        if (observer) observer.disconnect();
        window.removeEventListener('resize', queueResize);
        if (pendingResize !== null) cancelAnimationFrame(pendingResize);
        [waveform.canvas, waveform.cache, spectrogram.canvas, spectrogram.cache, heatmap].forEach(canvas => { canvas.width = 0; canvas.height = 0; });
        root.remove();
      },
    };
  }

  window.OverdexAudioSignals = Object.freeze({create});
})();
