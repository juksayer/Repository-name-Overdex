# Vaporeon and Water Pulse overlap: offline study

Using the original 13-second retained match-audio clip, the separately
bundled Vaporeon cry and Water Pulse references, and the previously identified
hypothesized offsets, an offline least-squares test projects a scaled Water Pulse
reference from the mixture. On the selected 600 ms Vaporeon reference fragment,
correlation is 0.8919 before subtraction and 0.9867 after a locally fitted
Water Pulse gain. The two aligned reference fragments have correlation 0.0067.
These values are correlations, not probabilities. The clip was not modified;
subtraction operates on an in-memory copy. Both offsets and the selected segment
were informed by this same case, and the gain is fitted from that same interval.
The result is not a blind test, a general recognition threshold, or proof that
all sound in the residual is Vaporeon.

Limited controls compared the same fragment to synthetic Water Pulse alone,
synthetic Water Pulse plus a Swampert reference, the original ending at ±600 ms,
and a 600 ms sample from an independently reviewed living Swampert return.
Absolute correlations after the fit were at most 0.102 in those controls.
These checks do not cover an exhaustive pool of species, sounds, devices,
codecs, scaling, missed entries or real shielded nonlethal cases. In particular,
the living-return control is one sample and does not validate faint inference.
The full signal comparison and control scripts and numeric outputs are retained
at `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-15-vaporeon-unmix/`.

The production archived-audio loader and Replay were also run on the preserved
match: they still recovered no cries or final player Fast Move and made no KO
association. The existing association has separate gates for source-timed move,
recipient, faint, competitors and appearance. The enhanced correlation alone
is not a production recovery and cannot create a move or prove cry purpose.
Focused audio, saved-match and living-switch association checks passed (15
tests). No source recording, archive, installed app or runtime code was
changed. SHA-256 checks confirmed the source recording and archive still match
those listed in `2026-11-15-vaporeon-shield-incinerate.md`.

Next evaluation should select and align the competing effect without using the
known answer, measure independent fragments and simultaneous competing
references, and run nonlethal shielded cases and living re-entries. Once a
separately supported Incinerate and a resolved target faint are available, test
the pending hit across the charge; do not add a KO link from this fit alone.
