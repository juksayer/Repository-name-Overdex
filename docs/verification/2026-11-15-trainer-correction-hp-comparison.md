# Trainer-described Vaporeon finish: first comparison

## Scope and separation

This is an offline, read-only baseline for using a trainer's correction as a
label. The user described Turtonator's Incinerate, the opponent Vaporeon's
charged move, the player's shield, and Vaporeon's subsequent faint. The
description and prior video review supply the label; this experiment did not
read an actual exported correction file or claim that an automatic reader
accepted the final move or faint. Source-video and archive clocks remain
distinct. The video-to-archive alignment is approximate.

The Vaporeon video is a different recording from all prior comparison sources.
We reused the pre-existing exploratory full-bar color/fade tracker with its
prior thresholds unchanged, rather than tuning it to this one label. Historical
comparison results include 13 KO windows, 12 nonlethal windows, two living
switches and two screen transitions. They come from five video files and one
sparse retained-image source, with overlapping windows; they are not 29
independent matches. The prior examples were used in developing the tracker,
so they are not a blind holdout for its thresholds. This experiment trains no
model and does not establish population accuracy. Historical measurements are
reused from the prior study; their underlying video hashes were checked in that
study, not rechecked in this run.

## Comparison

The Vaporeon window from video 135.5–149 s contains 255 decoded frames, with
156 geometry candidates from the exploratory tracker. Around the independently
reviewed Incinerate contact, three consecutive bright orange-only *median-column*
measurements at 138.704–138.809 s arm the candidate at 138.809 s. The next
readable frame, 138.881 s, contains one dim-aware live-colored column and
clears that candidate. Its color can also be background/contamination: it is
not proof of surviving HP. A median-column probe is not the production reader
and cannot exclude a single live source pixel. The user-reported faint occurs
later, after the shield, around 147–148 s. Therefore the pre-shield candidate
cannot by itself be recorded as a faint or its cause.

The tracker reports 12 armed frames overall in this broad window and no
two-frame relative fade. Do not interpret all 12 as Vaporeon HP evidence:
inspection of video 144.861922 s shows its magenta candidate over a battlefield
line/card area during the charged-move camera, not the opponent's complete HP
bar. Shield graphics also overlap the tracked area. These are actual
localization/occlusion failures, not new observations of Vaporeon's HP. The
generated contact sheet identifies the reviewed source frames and rectangles.

In the historical exploratory windows, 12 of 13 KO windows armed at least once;
the remaining KO window had no complete candidate. None of the 12 nonlethal
windows or either living-switch window armed. The two screen-transition windows
also did not arm, but contained no complete candidate, so they are weak
negative controls. These are window-level descriptive counts, not independent
success rates. The old tracker emitted two raw relative-fade events among the
KO windows; one was later shown to cross the Sealeo-to-Vaporeon identity
boundary. Sparse retained Cramorant frames supply the other; neither proves
continuous, generally reliable detection. The user-described Vaporeon example
does not produce a two-frame relative fade.

The preserved Vaporeon archive independently records an opponent
`complete_hp_bar_observed` at GO+86.429766 s with `UNRESOLVED_COLOR`: zero
live columns, seven damage-colored columns, and 169 unmasked uncertain
columns. This is unknown, not zero HP. The source image's clock is not equated
to video 138.809 s. The archive does not itself establish the missing final
Incinerate/faint; the trainer's correction remains distinct evidence.

## Implications and next evaluation

An orange-only candidate can help wake a bounded follow-up, but this example
shows that it must survive an intervening charged move and shield without being
promoted immediately to a faint. Confirmation needs independently captured
departure/identity/resource evidence or an explicit trainer assertion; retain
unknown when the bar is obscured, incomplete, mixed with background, or
mislocalized. Preserve the complete-outline check and tiny-survivor protection.
Never use the trainer label as an input feature when evaluating automatic
recognition, and keep paired views and multiple windows from the same battle
in the same data split.

The useful next experiment is an appearance-bound, shield-aware pending-hit
comparison against *new*, source-separated matches, with reviewed living
switches, nonlethal hits, and the counterfactual where the attacker faints
during the charged move. This recording does not demonstrate that
counterfactual. No adaptive thresholds or live reader behavior were changed.

## Reproduction and checks

The read-only script, per-frame measurements, source groups, archived payload
samples, contact sheet and test log are in
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-15-trainer-label-comparison/`.
Run `compare.py` with `PYTHONPATH=/tmp/overdex-video-review-python:/tmp/overdex-terminal-motion-python`;
the dependencies and older tracker remain external research tools. The script
checks the recorded video and archive SHA-256 before and after reading them.
The original video hash is `f2c6f4651cab2e669d9ec03f5684f0e964848a68037018ae11ab1bd686e9774d`;
the source archive hash is `005e78ef51a4f272a89b7a555cb80241703b57770a21b9f3966f34efb797253f`.

The focused production/regression run passed 29 tests with zero skips,
including recorded complete bars, background masking, tiny-survivor controls,
Sealeo and the trainer-projection Vaporeon archive test. The latter tests the
explicit Replay correction, not automatic KO detection. No APK was built or
installed for this offline study.
