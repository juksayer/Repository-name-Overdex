# Recorded Cramorant HP reference ordering

The eleven Articles in `articles.json` are unchanged objects from the Moondrop
match `b508a605-132a-48a8-a4e1-67514c729fc1`. The original archive is retained at:

`/home/sean/overdex-device-backups/2026-11-11-moondrop-2113-review/2026-11-11_21-13-35_b508a605-132a-48a8-a4e1-67514c729fc1.odxmatch.zip`

Archive SHA-256:
`97027bc36e6d3c5e59718307aa708ffca6c29f8179d98a83a1254b2300a45050`.

`provenance.json` maps test roles to exact Article IDs, records original-source
and fixture hashes, and hashes each original Article's canonical field values.
All transitive Article references are present. Referenced PNGs are not duplicated:
this is a source-time ownership test, not a pixel-reader or OCR test.

| Role | Exact monotonic timestamp | Relative to Match Start | Original Article ID |
| --- | ---: | ---: | --- |
| Earlier positive opponent HP | 18565005307029 | +121.701159161 s | `36bc3d67-a041-4026-9fe7-8052b38556c6` |
| First Cramorant species badge | 18569834423029 | +126.530275161 s | `a1633907-0236-400e-bc5c-34f615df3ea8` |
| Last normal opponent HP | 18574090407953 | +130.786260085 s | `71bd7d45-2d95-401a-a353-3a640a31abe7` |
| Final accepted player QTE | 18579026798337 | +135.722650469 s | `ef4ab365-9c6e-4acf-8e16-ca20925cbbfa` |
| QTE crop | 18579026798337 | +135.722650469 s | `e70d4cff-38bb-4771-99e8-0a12d5c17d74` |
| Original failed preparation diagnostic | 18579026798337 | +135.722650469 s | `67b62448-18d6-4bef-add2-8fce3d93e6d5` |

Match Start source timestamp is `18443304147868`. Recognition completion is
`18581445169184` (+138.141021316 s), taken from the original scheduling diagnostic.
The latest normal HP is 4,936,390,384 ns before the QTE capture, within five
seconds. Its measured fraction is 0.7619048, not zero.

`RecordedCramorantHpReferenceTest` deliberately supplies both possible delivery
orders: newer captured HP before delayed species recognition, and recognition
before that HP measurement. Both should retain the same safe reference under
`appearanceBoundary(side, atNanos)`. Earlier positive HP is tested in both delivery
orders and must not authorize this later appearance.

These are counterfactual delivery-order tests using original captured evidence.
The archive does not timestamp the Live HP subscriber's precise seed/reset order.
Its original diagnostic remains `NO_REFERENCE`; this fixture does not rewrite it,
assert that the tested order caused that failure, or supply the absent final
charged HP, zero-HP, or Cramorant faint.
