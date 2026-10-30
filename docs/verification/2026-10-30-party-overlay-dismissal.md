# Team Select menu dismissal before countdown — 2026-10-30

The user reported the Choose Your Party overlay staying on screen through the Match Countdown and hiding countdown glyphs. Possible league/restriction coverage was reported as uncertain.

## Cause and change

The setup menu remained expanded while automatic battle requests waited for a native team-info badge. During that interval its `BATTLE_READY` display was still the large setup menu, so waiting for countdown OCR or a later native badge to change the layout could hide the very pixels needed to recognize the transition.

Automatic battle requests and the session's COUNTDOWN phase now collapse the setup menu immediately while the compact battle layout waits for native badge evidence. A delayed Team Select acceptance or pre-battle phase cannot reopen the setup menu after that transition. A deliberate Droidball tap remains available, and an explicit Hide while waiting stays hidden when badges and later automatic cues arrive. The compact battle HUD becomes visible when its existing native badge gate succeeds; automatic display still requires Team Select or user authorization.

A small presentation-only geometry check also watches the calibrated party-card backgrounds and Use This Party control in fresh capture frames. It watches while seeking Team Select, before the accepted-screen OCR can reveal a menu, so a delayed acceptance cannot miss the earlier baseline. After it has seen that lower party surface, two samples showing sustained disappearance collapse the menu without waiting for countdown OCR. It samples at most ten times per second, uses a sparse pixel grid without crop allocation, and resets its baseline at a new Match or observation attempt. Missing/occluded/invalid regions do not count as disappearance. Capture and the durable witnesses continue normally.

This check is not a new witness: it does not publish a party, countdown, species, or MatchStarted conclusion. It only dismisses Overdex presentation. Raw Articles, source timestamps, and old archives remain unchanged.

The menu paper keeps its measured width, height, and placement. Droidball's existing body moves into the menu's header corner while expanded, retaining its tap-to-collapse action. Previously its separate row above the paper extended the native window into the CP restriction aperture. The expanded window now starts at the paper's top, below the calibrated league and restriction crops; collapsing restores the separate edge-docked body. Existing menu action rows remain in place.

## Focused checks

- Party loss after a real visible baseline dismisses without any countdown OCR.
- Scenery without that baseline, a transient loss, unavailable input, old frames, and a restarted attempt do not falsely carry the prior departure forward.
- Automatic battle cues and manual countdown arming remove setup before native badges appear.
- Delayed Team Select and phase events cannot reopen the menu during countdown.
- A native badge enables the battle HUD; explicit scanning/navigation and deliberate taps remain usable.
- The expanded native window starts below the CP restriction strip at Pixel/Moondrop reference heights and their doubled size, independent of the Android window inset.

## Validation

Coordinated focused tests passed (67 cases); the full suite passed (1,547 tests,
zero failures/errors/skips), and the debug APK built and was installed on idle
Moondrop with data preserved. The installed hash matches the tested APK; see
[coordinated verification](2026-10-30-new-matches-and-move-correction.md#validation).

Preserved full Team Select frames from the two new matches show the cup title
and CP limit already visible, while the old body row overlaps eligible-type
icons. Source geometry now keeps the expanded window below that area. The league
classifier still mislabels this seasonal layout; menu placement alone does not
repair that separate inference.

A fresh physical battle remains necessary to confirm menu dismissal before
3/2/1 and subsequent badge-anchored HUD appearance. No battle was played during
the application/editor checks.
