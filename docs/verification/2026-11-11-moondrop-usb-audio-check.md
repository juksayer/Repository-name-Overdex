# Moondrop USB recording with Overdex: short audio compatibility check

## Result and limit

A short Pokémon GO menu recording successfully saved H.264 video and AAC phone
audio while Overdex continued its own observation and playback-audio capture.
The Overdex process and foreground service remained running, and its frame
delivery counters continued advancing with no recorded rejections during the
sampled intervals.

This is a **14.336-second menu compatibility check**, not proof of whole-battle
performance, unchanged recognition latency, stable long-session frame rate, or
correct move/faint recognition. No match was played for this check.

## Retained evidence

Evidence folder:
`/home/sean/Videos/Overdex/compatibility-check-2026-11-11_22-05-57/`.

Recording:
`/home/sean/Videos/Overdex/compatibility-check-2026-11-11_22-05-57/moondrop-audio-15s.mp4`.

- Device: Moondrop `MDPH00124112500414`, Android 14.
- Recorder: local scrcpy 5.0.1, playback audio duplication enabled.
- Recorder exit code: `0`; its log reports recording complete after the time
  limit.
- File size: **14,502,561 bytes**.
- SHA-256: `e11a3554a5da22192f26db289452bd7ee4d9132914189f9852b216a14042767b`.
- Container duration: **14.336 seconds**.
- Video: H.264, **1072×2400**, 318 decoded frames, first PTS 0.110978 s, last
  PTS 14.166911 s; approximately 22.55 frames/sec across that interval.
- Audio: AAC, **48 kHz**, 672 decoded audio frames. The saved analysis reports
  20,744 nonzero samples, peak amplitude 0.1610413, and RMS 0.0052749.
- A short audio effect occupies **7.104–7.338667 seconds** of the file across
  eleven non-silent decoded audio frames.

`media-verification.json`, `capture-summary.json`, `audio-signal-spans.json`, and
`recorder.log` retain these measurements. The MP4 streams, duration, file size,
and hash were independently reread when writing this report. The saved
`first-video-frame.png` shows the Pokémon GO Battle menu with Droidball visible.

## Concurrent Overdex evidence

`capture-during.log` records Overdex's own playback source becoming non-silent:

```text
1794456367.397 32416 32452 I ODX_AUDIO_INPUT:
source=PLAYBACK_POKEMON_GO state=SIGNAL_PRESENT rate=48000 channels=2
```

This is a logged wall-clock epoch, distinct from the MP4's relative timestamps.
It demonstrates concurrent Overdex signal capture during the short recording;
the log and MP4 timestamps are not assumed to identify precisely the same sample.

The same log contains:

| Logged epoch seconds | Frame attempts | Successful emissions | Rejected | Reported interval FPS |
| ---: | ---: | ---: | ---: | ---: |
| 1794456362.067 | 1760 | 1760 | 0 | 17.9 |
| 1794456367.066 | 1871 | 1871 | 0 | 22.1 |
| 1794456372.071 | 1978 | 1978 | 0 | 21.3 |

These counters show 218 additional successful emissions over the sampled ten
seconds. Zero emission rejection does not establish that every game frame was
captured, that every reader processed it, or that recording adds no CPU cost.

Independent retained system checks agree:

- `pid-before.txt` and `pid-after.txt` both contain Overdex PID **32416**.
- `service-before.txt`, `service-during.txt`, and `service-after.txt` contain the
  same `DroidballService` service record, foreground and started.
- `projection-during.txt` still identifies **com.example.overdex** as the screen
  MediaProjection owner.
- `audio-policy-during.txt` lists two active remote-submix AudioRecord clients:
  Overdex UID **10461**, session **809**, and the recorder's shell UID **2000**,
  session **841**. Both use 48 kHz PCM input. Pokémon GO's output is also active.

The USB recorder therefore did not replace Overdex's MediaProjection or silence
its own playback source in this sampled menu check. Whole-battle acceptance
remains a separate live test.

## Desktop recording workflow

The installed shortcut is
`/home/sean/Desktop/Record Moondrop with Audio.desktop`, which invokes
`/home/sean/.local/share/overdex-tools/record-moondrop-with-audio.sh`.

1. Connect and unlock the Moondrop over USB; it must be the authorized device.
2. Start Overdex observation normally, then open **Record Moondrop with Audio**
   on the desktop.
3. Play on the phone. The computer preview does not control the phone and is
   silent; the recording includes duplicated phone audio.
4. Close the preview window to finish and save the recording. The launcher
   reports the saved pathname.

New recordings are saved under **`/home/sean/Videos/Overdex/`**, named
`Moondrop-YYYY-MM-DD_HH-MM-SS-XXXXXX.mp4`. The launcher reserves a fresh pathname
and does not replace an older recording. Its diagnostic logs are under
`/home/sean/.local/share/overdex-tools/recording-logs/`.

The launcher targets the Moondrop serial explicitly, uses audio duplication with
AAC, requires audio, disables desktop audio playback and remote control, and
caps requested video rate at 30 FPS. These are inspected launcher settings,
not a guarantee of 30 captured frames/sec or full-battle recognition accuracy.

This report used existing local evidence only; writing it performed no device
actions or production-code changes.
