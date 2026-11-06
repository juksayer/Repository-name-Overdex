# Private single-elimination tournaments — November 5, 2026

The user requested a testable combination of a simple host-run bracket and a
pre-match lobby. There must be at least two players and three completed Pokémon
GO battles across the tournament, with one host responsible for settings and
invitations. Single elimination is an accepted format.

## Using the first version

1. On each phone, use Profile → Trainer Comms to sign in to a separate account and
   register its trainer name and twelve-digit Pokémon GO trainer code. The code
   remains self-reported; it does not prove ownership of a GO account.
2. The host adds the other registered trainer as a Comms contact. Open
   Battle → Tournaments → New Tournament. Set the name, league, additional rules,
   wins to advance and requested final wins, then create the room.
3. Use Invite Trainers to select contacts and send named invitations. An invited
   trainer finds the room in their Tournaments list, accepts, then checks in.
   The host is initially checked in. A settings change requires fresh check-in.
4. The host starts once at least two trainers have checked in against the current
   settings. Invitations and structural settings then lock. The seeded bracket
   handles byes and concurrent pairings; a bye never creates a battle result.
5. Players select their pairing and mark Ready or Need a Moment. A problem note
   is visible to other room members and clears the sender's readiness. Open
   Pokémon GO starts the installed game; players issue the challenge in GO.
6. After playing, each participant reports I Won, Opponent Won or Draw. The host
   finalizes reciprocal agreed reports. Conflicts remain unfinalized; the host may
   record a played battle with an explicit correction reason. The original
   participant reports remain separate. Each new battle starts with fresh readiness.
7. A completed final shows the champion and event battle history. End Early
   requires a reason and preserves the results without inventing games or a winner.

Left/Right selects LCD actions, A activates, Up/Down selects CRT rows and B returns.
The selected LCD action scrolls into view. Rules have a complete paged view.
The room survives trips into GO; the same event is loaded again from the server
after reconnection or reopening it. Signing out hides that account's event state.

## Scope and ownership

- One authenticated host owns configuration, invitations and bracket advancement.
  The host may also play. Invitations go to registered contacts; there is no join
  code, automatic conversation message, push invitation or public discovery yet.
- This first version caps invited accounts at sixteen. At least two must accept
  and check in before start. Non-entrants and eliminated trainers can still read
  the event; only currently paired players can submit preparation or reports.
- Two-player finals require three wins. Three-player finals require at least two;
  four or more may use single-battle series. Draws count as completed battles but
  award no win. Byes do not count. A normally completed event therefore has at
  least three battles, not necessarily three for every participant.
- Results are explicitly reported competition decisions, not new battle-witness
  testimony. No archive, Timeline, card identity/history or saved team is rewritten.
  Archives are not automatically linked or uploaded in this version.
- Existing Trainer Comms accounts, profiles and contact discovery are reused.
  Event membership is separate because existing conversation membership is fixed.
  No new sign-in service, production account, chat or outgoing message was created
  during development.
- Tournament room envelopes are host-controlled; participant-owned attendance
  and preparation documents are protected separately by Firestore rules. The host
  cannot write another player's response. The host is trusted to administer the
  bracket; server rules do not parse and prove every bracket decision inside its
  serialized payload. Client engine tests verify the supported app's progression.
- Settings revisions prevent old check-ins from approving changed rules. Expected
  battle keys prevent retrying a confirmation from awarding another battle. Host
  transactions read current server responses before making a decision.
- Server acknowledgements determine success. Cached room data is marked read-only;
  failed or timed-out writes ask the player to reconnect/review before retrying.

## Verification

- Final full JVM suite: **1,783 tests / 259 suites**, zero failures, errors or
  skips. Fourteen engine tests include exhaustive completion for every supported
  roster size (2–16), actual-battle minimums, byes, parallel pairings, draws,
  conflicting reports, stale confirmations and settings acknowledgements.
  Fifteen repository tests cover account/listener isolation, acknowledgements,
  failures/timeouts, cached rooms, delayed initial snapshots and recovery.
- Debug app and instrumentation APK assembly passed. The final repository-only
  recovery change was followed by the complete JVM suite and debug APK build.
- Firebase emulators: **40 checks passed**, comprising 22 tournament authorization
  checks and 18 existing Trainer Comms/Auth regressions. The maximum sixteen-member,
  eight-active-pair envelope fits Firestore's rule-expression limits. The existing
  Comms rule body is unchanged.
- API 34 Android emulator: **OK (4 tests)**. Three exercise the production LCD
  action panel with large text, scrolling, disabled/busy controls and replacement
  action lists. One uses the actual Android Firebase SDK and separate disposable
  host, guest and outsider accounts: canonical trainer profiles, named invitation,
  rule-change acknowledgement, three agreed battles, stale-confirmation rejection,
  champion summary, restored room, outsider exclusion and sign-out denial.
- The first Android account run found that emulator HTTP was correctly blocked.
  Debug now permits cleartext only to the Android emulator's `10.0.2.2` loopback
  alias. Release manifest processing passed and its merged manifest contains no
  network-security exception. No production Firebase endpoint uses this exception.
- The pre-feature cloud rules were backed up and verified byte-for-byte against
  the repository baseline before deployment. The tested rules compiled and were
  released to the existing project's default Firestore database. Auth configuration,
  production accounts and existing conversation rules were not changed.
- The final APK was installed on Moondrop and Pixel 4. Host and installed APKs
  share SHA-256 `5bc2a6315ac86ddbdcda996242890be773bdeca4fdcfb95d6476220236c1deaf`.
  Moondrop hardware-button navigation reached Battle → Tournaments and its account
  setup prompt; B returned to the same selected directory entry and A reopened it.
  Its trainer name/code profile is not yet set up, so no production tournament was
  created. The Pixel presentation was not visually accepted while locked.
- Readable before/after backups confirmed unchanged Room table contents,
  calibration/current-team/registration/replay files, and match-archive
  filename/size/mtime inventories on both phones. No phone instrumentation was run
  and no Droidball capture was interrupted. Test emulators were stopped afterward.

The remaining acceptance check is a real two-phone tournament using the user's
own separate accounts and actual Pokémon GO battles. The emulator's reported
results exercise tournament coordination; they do not establish battle recognition
or automatically validated real-world results. The host remains authoritative for
event administration; these prototype rules require further review before broad
public rollout.

Private backups and logs:
`/home/sean/overdex-device-backups/2026-11-05-tournaments/`.
