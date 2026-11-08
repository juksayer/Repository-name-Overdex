# Foreground nearby field-note exchange

November 8, 2026. Follows the portable journal format and explicit import preview.

`FieldNotesNearbyScreen` uses the existing LCD controls and protected CRT. It
looks up the local identity and the QR-linked partner; there is no Firebase
account or Trainer Comms outbox in this path. One trainer chooses Receive and
the other Find. Both compare the same four-digit code before confirming. Only
then can either explicitly send its shareable journal snapshot. Received bytes
are checked against that peer's reported trainer ID and opened as a preview,
without automatically saving or favoriting anything.

The transport uses Nearby Connections point-to-point BYTES packets, capped at
32 KiB. Larger snapshots use the existing file exchange. Only one peer, one
outgoing transfer and one pending incoming preview are retained. Unknown peers,
non-byte payloads, oversized data and sender mismatches do not reach import.
Endpoint trainer IDs and poem author fields are self-declared metadata; the
human code comparison authenticates the connection, not historical authorship.

Closing or backgrounding the screen stops hosting, discovery, transfers and
connections. Returning does not automatically advertise again. Session and
payload relevance guards prevent callbacks from an old connection or failed
send from changing a later attempt. A send Task means queued; transfer SUCCESS
means delivered to the connected transport, not saved in the recipient's journal.

Runtime permissions are requested only after Receive or Find. Unavailable Play
services and denied permissions produce an explicit failure. Wi-Fi and Bluetooth
hardware remain optional so this feature does not exclude devices using other
Overdex workflows. The dependency is
pinned to Nearby 19.3.0: direct artifact inspection confirms its Kotlin 1.9
metadata and all Java APIs used here. Nearby 19.5.1 uses Kotlin 2.4 metadata and
does not compile with this checkout's Kotlin 2.2 toolchain. The pin avoids an
unrelated compiler/toolchain migration or skipped metadata verification.

Packet contents travel directly between the devices, with no Overdex journal
server. Google's Nearby SDK separately collects usage/performance analytics as
described in its [overview](https://developers.google.com/nearby/connections/overview).
API and permission behavior were checked against Google's
[connection guide](https://developers.google.com/nearby/connections/android/manage-connections),
[data guide](https://developers.google.com/nearby/connections/android/exchange-data)
and [setup guide](https://developers.google.com/nearby/connections/android/get-started),
plus the exact 19.3.0 artifact interfaces.

## Validation

- Full unit suite: **2,106 tests, zero failures/errors/skips** on the combined
  journal/calibration working tree. Debug app and instrumentation APKs build.
- API 34 disposable emulator: **OK (10 tests)**. Six state-machine tests inject
  a radio-free ConnectionsClient proxy but drive production callbacks and actual
  Google Tasks. They cover confirmation, queue/delivery separation, late failure
  after retry, closed/restarted sessions, incoming limits/preview retention and
  service/permission boundaries. Four journal UI checks include the new
  journal → exchange → nearby → same-page return through physical B controls.
- These are distinct from real Bluetooth/Wi-Fi discovery. No phone
  instrumentation, real journal transmission or partner relinking was performed.

The phones' pre-update backups show that each already has a saved partner, and
neither partner identity points to the other phone. Those links are preserved.
A real paired handoff requires an explicitly linked test pair and code
confirmation; compilation, proxy tests and launch checks cannot prove it.
