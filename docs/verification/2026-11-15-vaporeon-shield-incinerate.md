# Moondrop: Incinerate interrupted by shielded Water Pulse

## Source and trainer account

The trainer reports that the last Incinerate was lethal, Vaporeon then used a
Charged Move, the player shielded it, and Vaporeon fainted. They propose that an
unshielded lethal hit to the attacker could instead leave Vaporeon alive and
restore the prior HP color; this recording does not exercise that counterfactual.
The played match was a Team Leader battle with Turtonator versus Vaporeon at
the finish. An earlier attempt in the same video was backed out while checking
the saved party; its separate `9b204819-...` archive has no `match_started`
article and must not be combined with the completed `b16a482c-...` battle.

Original 165.845 s video:
`/home/sean/Videos/Overdex/Moondrop-2026-11-15_09-31-37-5vBuhv.mp4`,
SHA-256 `f2c6f4651cab2e669d9ec03f5684f0e964848a68037018ae11ab1bd686e9774d`.
Played archive SHA-256
`005e78ef51a4f272a89b7a555cb80241703b57770a21b9f3966f34efb797253f`;
all 229 included artifacts verified and the copied archive matches the device.
Both original archives and review outputs are retained read-only under
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-15-moondrop-0931-review/`.
Video is 1072×2400 and includes AAC audio; game video and audio streams have
different container start times (video 0.346856 s, audio 0 s). Times below are
explicitly video PTS, audio position, or archive monotonic time, not assumed to
be interchangeable.

## Observed exchange

The sampled native video shows Turtonator's fire effect across Vaporeon around
137–138.5 s. In the bar contact sheet the small red remainder is visible at
137.981 s and orange by 138.047 s. The orange remainder remains visible as
`Attack incoming!` and the shield choice appear; a shield is chosen, Vaporeon's
Water Pulse is blocked, and Vaporeon disappears in the following sequence
around 147–148 s. `YOU WIN!` follows. This is consistent with the trainer's
reported finishing Incinerate and demonstrates why orange before the shield is
not yet an independently confirmed faint. The contact sheet samples frames; it
does not establish an exact game-turn boundary or prove the counterfactual.

An original retained opponent HP evidence frame at archive GO+84.132 s visually
matches a source video frame near 135.7–135.8 s. This is an approximate visual
anchor, not an exact common clock. The last recorded PLAYER Fast Move use is
GO+82.150 s, with a separately measured opponent HP step; it precedes the
finishing exchange. A later original sound observation starts GO+85.473 s
with a detected onset 800 ms into that clip. Its retained candidates include
Incinerate at 0.96145 and Water Gun at 0.95515, but the original recognition
explicitly records `referenceMatchAccepted=false` and an unknown side. Preserve
both candidates and the clip; a close candidate is not an accepted attack.

At GO+86.430 s, the complete opponent bar observation has **0 live columns,
7 damage-colored columns, 169 unresolved columns**, status `UNRESOLVED_COLOR`.
It therefore does not assert orange-only or zero. It was captured about 4.65 s
before its article was recorded. The archive also has an unclassified 26.1%
opponent HP reading just beforehand after a cluster of ~4–5% red readings;
its association with visual effects needs separate review and should not be
assumed accurate from this list alone.

The archive records two opponent faints (Sneasel and Sealeo), a Water Pulse
announcement via OCR, and WIN. It has no final Vaporeon faint, no later player
Fast Move use, no accepted `Attack incoming` testimony, and no post-choice shield
count: its last player-shield observation is two before the choice. It therefore
does not independently record the complete shield-and-faint chain seen in the
video. New `LIVE_COLOR` observations with mixed live/damage columns occur in
the match, but this alone does not validate the new reader's precision.

## Audio and Replay

A retrospective, full-waveform exploratory comparison found the bundled
Vaporeon cry's strongest candidate in the recording's audio near 146.152 s
(score about 0.840). In original retained 13-second `CHARGED_MOVE_EFFECT`
clip `723a7319-...`, the analogous strongest candidate is 6.0825 s into the
clip (score about 0.482). These are correlation values, not probabilities;
the exploratory comparison does not by itself establish why the cry occurred.
The clip is mixed with the Charged Move and shield sequence. The archive's live
cry witness did not publish a final Vaporeon cry.

Trainer clarification: Vaporeon was the opponent's last remaining Pokémon and
the player had no Vaporeon. The trainer identifies the ending cry as its faint
cry and proposes accepting a unique last-opponent cry with sub-full HP as a
faint, with a full-HP cry indicating entry. This match's departure and win
support that interpretation. The more general rule is recorded as CRY-06 for
testing, including the important counterexample of a previously damaged Pokémon
returning as the last survivor; low HP alone cannot establish that an entry did
not just occur. This clarification is additional context, not a rewritten audio
observation or an automatically recovered cry.

A read-only JVM review ran the production archived-audio loader with the actual
ZIP and packaged references, then constructed `MatchReplayModel`. It recovered
no cries, charges or fast effects, and reported unavailable references
`LIQUIDATION`, `TRIPLEAXEL`, `SCORCHINGSANDS` and `BRUTALSWING`. Its Replay
projection contains two faint markers, no final Vaporeon faint, no final player
Fast Move, and no finishing association. The opponent's recorded charge is
projected as unknown side/name in this harness even though the OCR text names
Vaporeon and Water Pulse. From GO+86 through GO+108 s the sampled Replay HP
state retains approximately 4.3% as an unconfirmed display value; the isolated
26.1% raw observation is not the value displayed at those sampled moments.
The test verifies the original ZIP hash and unchanged
serialized archive. This is a desktop reconstruction of the archived data,
not a visual observation of the phone's Replay or proof of phone timing.

## Scope and next step

This is a useful positive shield-interruption case, and no second match is
needed to begin investigation. The smallest next task is to trace why the
independent final flame/audio and the shield/charge transition did not become
source-timed usable observations, then evaluate preserving the pending attack
through the charge without awarding a KO before independent faint evidence.
A separate recording is needed to validate the trainer's lethal-unshielded
counterfactual. No archive, recording, game outcome, reader, or installed
build was changed by this review. Diagnostic reports and the manually sampled
`final-exchange.png` and `bar-sequence.png` are on the backup drive.
