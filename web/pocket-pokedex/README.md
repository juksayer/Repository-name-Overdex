# Overdex Pocket

A small, keypad-first Pokédex prototype for a Nokia 2760 Flip (N139DL, KaiOS 3.1).
It is a separate web companion; it does not modify the Android application.

- All 1,025 national-dex species, with local sprites, types, and a short entry.
- Pokémon GO base Attack, Defense, and Stamina, explicitly labeled as GO stats.
- Search by name or exact number; accents, punctuation, and leading zeros are handled.
- Saved favorites and the last opened species, when browser storage is available.
- No external fonts, libraries, accounts, analytics, or runtime data services.
- Only the visible list rows and the current portrait are rendered.

## Controls

| Key | Index / search | Pokémon |
| --- | --- | --- |
| Up / Down | Select a row; Down leaves the search field | Down opens the entry; Up/Down scroll its text |
| Left / Right | Previous / next page (edits the cursor in the search field) | Previous / next national-dex species |
| Center / Enter | Open the selected Pokémon | Switch between portrait and entry |
| Left softkey / desktop F1 | Find / clear search | Save or remove favorite |
| Right softkey / desktop F2 | Saved/all on the index; back from search | Back |
| Back / desktop Escape | Back; root Back remains available to the browser | Back |
| Number key | Start a number search | Start a number search |
| # | Search | Search |
| * | Toggle saved index | Toggle favorite |

The search field uses the phone/browser's own text entry. Backspace still deletes
text while that field has focus. The on-screen softkeys also work with a mouse.

## Build and preview

From this directory, with Python 3:

```sh
python3 build.py
python3 -m http.server 8765 --bind 0.0.0.0 --directory dist
```

Open `http://localhost:8765/` on the computer. For a phone on the same Wi-Fi, use
the computer's LAN address, for example `http://192.168.1.10:8765/`. This preview
requires the computer/server to remain running. It is not a public deployment.

The build also produces `dist/overdex-pocket.html`: one self-contained file,
including every sprite and the catalogue. Once that file is opened locally in a
compatible browser, it needs no network. `dist/overdex-pocket.zip` contains this
file, the sprite license, and this readme. This is a portable web page, **not** a
signed KaiOS installer. Whether the N139DL's file manager can open a downloaded
HTML file, and whether its browser persists a saved page, require a device check.
The LAN browser preview is the first verification route. No offline service-worker
claim is made for an ordinary HTTP LAN origin.

Desktop test controls: arrows, Enter, F1/F2, Escape. Test at 240×320 and at a shorter
viewport to account for browser chrome. Keypad input and performance must still be
checked on the physical Nokia; desktop checks cannot establish those.

## Reference data

The build reads existing assets; it never edits their sources:

- `app/src/main/assets/sprites/pokemon/{id}.png`: ordinary national-dex portraits.
- `battle_haiku.json`: species names (excluding Overdex's additional mascot entry).
- `pokemon_species.json`: category and short Pokédex description.
- `gamemaster.json`: types and Pokémon GO base stats, with its source date retained.

One ordinary form is shown per dex number. Explicit form mappings in `build.py`
prevent a Shadow, regional, or Mega variant from being selected simply because
it is first in the reference file. The entry names the specific stat reference.
Unreleased species' GO stats are marked provisional. There are no main-series
stat claims and no automatic claim that a move or species is currently released.

The complete source sprite license is included in each build. Pokémon imagery
belongs to its respective owners. Build coverage, source hashes, and selected
reference forms are written to `dist/build-report.json`. Generated copies are
ignored by Git; the Android sources remain the source of truth.

## Verification

`build.py` validates all species IDs, default-form choices, types, stats, and PNG
coverage. Browser regression checks are in `tests/browser.cjs`; they require
Node.js 20 or later, Playwright as a development-only tool, and a running local preview:

```sh
PLAYWRIGHT_PATH=/path/to/node_modules/playwright node tests/browser.cjs
```

Set `POCKET_DEX_URL` to change the default `http://127.0.0.1:8765/`.
