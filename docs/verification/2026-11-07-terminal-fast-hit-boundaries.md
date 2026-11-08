# Recorded Fast Moves at the final battle boundary

Two completed-archive presentation cases could hide a supported finishing hit:

- A hit whose source time exactly equaled `MatchEnded` was excluded by a strict
  less-than comparison, despite the replay's event selection admitting that
  closing instant elsewhere.
- A move with no known duration started its decorative neutral animation at
  the impact witness. If a faint or result closed the same ordinary-play window,
  the animation had no visible interval.

The closing result instant now admits an independently supported hit. Evidence
after that instant is still rejected. The same instant closes the outgoing
ordinary-play window instead of beginning an attack after the result.

For an unknown-duration finishing hit only, the existing neutral 450 ms visual
may fit before its source impact. The entire visual must fit inside the current
ordinary-play window; it cannot borrow time across a charge, appearance change
or Match start. `TERMINAL_NEUTRAL_PRESENTATION_FIT` identifies the presentation
adjustment. The move name, type and reference duration remain unresolved when
they were unresolved before. Source Article IDs and times remain intact, and
the neutral fit supplies no exact energy.

Five new regression tests cover known and unknown hits at the result, a neutral
hit at a recipient faint, short/zero windows, phase and identity boundaries,
post-result rejection, rewind, archive immutability, and no fabricated faint or
energy. Focused schedule tests and the full 1,996-test suite pass; the debug APK
builds. Independent review found no additional boundary issue.

These fixes do not recover an absent final charged move. The separate
[evening Match review](2026-11-07-evening-final-hit-review.md) finds visible Rock
Tomb source frames outside the compact archive's accepted testimony. That
recognition/retention gap remains unresolved by this presentation repair.
