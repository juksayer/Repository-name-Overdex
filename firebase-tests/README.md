# Trainer Comms backend checks

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

Live project configuration is deliberately selected explicitly when deploying:

```sh
npx firebase-tools deploy --only firestore:rules,auth \
  --project com-example-startovermon-6b6ff
```

Deploy only when authorized for the current task, after reviewing the rules and
running the emulator tests. Client cache tests and emulator rules tests do not
establish production-scale security, delivery reliability, or an actual two-phone
conversation. No real credentials belong in these tests.
