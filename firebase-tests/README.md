# Trainer Comms and tournament backend checks

These tests use **only** the `demo-overdex-comms` Firebase emulators. They never
create production users or send production messages. Node 20+ and Java 21+ are
required by the current Firebase CLI; this checkout's `.jbr/bin` supplies Java.

From the repository root, with a supported Node on PATH:

```sh
npm --prefix firebase-tests ci
JAVA_HOME="$PWD/.jbr" PATH="$PWD/.jbr/bin:$PATH" \
  npx firebase-tools emulators:exec --only auth,firestore \
  --project demo-overdex-comms 'npm --prefix firebase-tests test'
```

The rules tests cover membership queries, outsider access, spoofed sender IDs,
extra/malformed/oversized data, atomic code claims/rerolls, immutable membership and
messages, denied legacy paths, and retry idempotency. The integration test creates
four local email/password accounts: three group members and an outsider. It tests
trainer-code discovery, group delivery, duplicate retry, sign-out denial, and
restored access after sign-in. The repository's JVM tests separately cover durable
outbox recovery, acknowledgement timing, account/draft isolation, and legacy import.

The tournament rules suite covers authenticated invitation-scoped queries, one-host
settings and bracket ownership, bounded routing data, atomic host check-in, individual
acceptance/decline/readiness/results, settings acknowledgement versions, wrong-pair
and forged-response denial, closed-event immutability, and the maximum sixteen-player
roster with eight simultaneous pairings. Run only that suite against already running
emulators with:

```sh
GCLOUD_PROJECT=demo-overdex-comms node --test firebase-tests/tournament-rules.test.mjs
```

Every rules test clears the **demo project's** Firestore data before its next case.
Do not run this suite concurrently with Android emulator acceptance tests using that
same demo project. The host remains authoritative for tournament settings and audited
result corrections; the rules independently protect member access and who authored
each attendance/result response. Serialized bracket consistency and the minimum of
three played battles are enforced by the shared tournament engine and backend decoder.

UID arrays use a string join/split round trip plus a bounded per-UID regex. The round
trip rejects non-string items and embedded separators, while avoiding Firestore's
1,000-expression limit at the maximum roster size. The methods are documented in the
[Firebase Rules List reference](https://firebase.google.com/docs/reference/rules/rules.List).
Malformed UID types, lengths and embedded separators have negative emulator checks.

Live project configuration is deliberately selected explicitly when deploying:

```sh
npx firebase-tools deploy --only firestore:rules,auth \
  --project com-example-startovermon-6b6ff
```

Deploy only when authorized for the current task, after reviewing the rules and
running the emulator tests. Client cache tests and emulator rules tests do not
establish production-scale security, delivery reliability, or an actual two-phone
conversation. No real credentials belong in these tests.
