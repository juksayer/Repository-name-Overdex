# Trainer Comms conversation foundation — October 20, 2026

Implemented after source checkpoint `474bd5f` on `odx-recovery`.

## Behavior

- Profile always offers Trainer Comms. A Firebase email/password account is
  required. Creation, sign-in, password reset, and sign-out have explicit screens.
- Public identity is a display name and 12-digit Pokemon GO trainer code. Leading
  zeroes are preserved; display groups digits by four. A code can be changed
  without changing the account or conversation IDs. It is a unique self-reported
  claim, not verified ownership of the Pokemon GO account.
- Add trainers by code, choose one for a direct conversation or several for a
  named group. Maximum eight participants including the creator. Members must
  have registered their profiles. Membership and group name are fixed for now.
- CRT history is operated with up/down. Left/right selects LCD actions, A activates,
  B backs out; Select opens/toggles the highlighted row. The message keyboard uses
  the LCD. Credentials and initial account/profile forms use normal Android input
  dialogs outside the CRT GlassShield.
- Each account/conversation has saved history, drafts, and a durable outbox.
  Pending means no acknowledgement. Sent means saved online; it is not a read
  receipt. Failed sends retain text and ID for retry. A restarted pending send is
  marked unconfirmed until a server record or explicit retry resolves it.
- Firebase transactions make retries idempotent. Snapshot listeners reject local
  pending-write echoes as proof of delivery. Listener replacement and account
  generation checks keep previous conversations/accounts from contaminating new ones.
- Legacy preferences are preserved. Their messages and shared Pokemon summaries
  are copied into read-only local history once, for the first signed-in account.
  Original sender IDs/times survive, while missing recipients and delivery stay
  unknown. Corrupt legacy data fails visibly and is not overwritten.

## Cloud configuration

Existing project: `com-example-startovermon-6b6ff`; Firestore `(default)`, STANDARD
edition. No new project/database or Storage bucket was created.

Before this change, the only deployed rule expired on August 10, 2026, and Firebase
Authentication returned CONFIGURATION_NOT_FOUND. Old ChatTransport did not await
its write Task and fell back to a no-op mock after configuration errors.

Deployed `firebase.json` email/password configuration and `firestore.rules` after
local validation. New profiles contain only the intentionally shared trainer name,
code and account ID; account email/password remain in Firebase Authentication.
Only conversation members can read/write its messages; the sender must match the
signed-in UID and registered display name. Schema/type/size validation applies to
all allowed mutations. Code registration and reroll use atomic profile/index
changes. Legacy cloud chat paths and unrelated paths remain denied.

These are prototype rules with tested access boundaries. Review and harden them
before broad release, especially invitations, blocking/reporting, account deletion,
code-ownership disputes, rate limits, and member lifecycle management.

## Validation

- Full Android JVM suite: **979 tests**, zero failures/errors/skips (176 suites).
- Debug APK assembled successfully.
- Firebase emulator suite: **18 tests** passed. Includes adversarial access/schema
  checks and four real local Auth accounts (three group members plus one outsider).
- Local tests cover interrupted sends, timeout/late acknowledgement, stable retry
  IDs, disk-write failure before network send, per-conversation drafts, cross-account
  isolation, code reroll, and retained legacy records.
- Firebase cloud rules compiler accepted the file and deployment reported success.
- The Android Firebase client configuration matches the project's registered app
  configuration. No key/credential values were printed or changed.
- No production accounts, password reset emails, or trainer messages were created
  by development tests.

Logs: `/tmp/overdex-comms-final-build.log`, `/tmp/overdex-comms-firebase-final.log`,
`/tmp/overdex-comms-deploy.log`, `/tmp/overdex-comms-install.log`.

Live read-back confirmed email/password enabled, password required, anonymous
sign-in disabled, and the deployed rules content identical to `firestore.rules`.
Ruleset: `9f3ff77d-35a7-4293-aa40-d85a40c191a2`.

Moondrop `MDPH00124112500414` was idle (no Droidball service) and unlocked by the
user. The debug APK was installed with `adb install -r`, without clearing app data.
Physical navigation: Directory Down to Profile → A; Profile Down to Trainer Comms
→ A; Comms Right to Create Account → A. The sign-in gate, native account form,
password fields, and action reachability with the keyboard open were inspected.
No credentials were entered or account created. The user-provided trainer code
replaced an old invalid five-digit local Profile value; the existing local trainer
UUID and linked-partner identity were retained. The phone was left on Create
Account for the user's private entry.

Phone screenshots: `/tmp/overdex-comms-signin-gate.png` and
`/tmp/overdex-comms-account-form.png`. No instrumentation suite was run on the
user's phone. Signed-in conversation UI and real-device message exchange remain
part of the acceptance check below.

## Next acceptance boundary

After each tester creates their own account and registers their own trainer code,
exercise a real two-phone conversation and a three-member group: background one
phone, disconnect/reconnect networking, retry an unconfirmed send, and sign back
in. Verify that each message appears once in the correct room. Do not use the
emulator result as a claim that this real-phone test has already passed.

`.odxmatch` attachments are step 2; portable clip/compilation sharing is step 3.
No archive format, recognition witness, calibration, owned card, or battle timeline
was changed by this feature.
