#!/usr/bin/env python3
"""Audit recorded height measurements against an explicitly supplied counted-energy checkpoint.
Never rewrites an archive or chooses a layer using the expected energy.
"""
import argparse
import json
import zipfile
from inspect_charge_fill_layers import energy_hypotheses, intersect_hypotheses


def compare_pair(left, right, costs, counted_energy):
    hypotheses = []
    for p, cost in zip((left, right), costs):
        if not p or p.get('status') != 'EXPERIMENTAL_SEPARATOR':
            hypotheses.append([])
            continue
        hypotheses.append(energy_hypotheses(
            p['separatorY'] - p['centerY'], p['radius'], cost,
            uncertainty_px=p['pixelTolerance']))
    joint = intersect_hypotheses(*hypotheses)
    status = ('UNKNOWN' if not all(hypotheses) else
              'BUTTONS_DISAGREE' if not joint else
              'COMPATIBLE' if any(v['low'] <= counted_energy <= v['high'] for v in joint) else
              'COUNT_DISAGREES')
    return dict(status=status, candidates=joint, counted_energy=counted_energy,
                note='Experimental geometry tolerance only; compatible does not prove the move count.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive')
    parser.add_argument('--costs', nargs=2, type=int, required=True,
                        help='Verified left and right button costs for this checkpoint')
    parser.add_argument('--counted-energy', type=int, required=True)
    parser.add_argument('--start-nanos', type=int, required=True)
    parser.add_argument('--end-nanos', type=int, required=True)
    args = parser.parse_args()
    if not all(1 <= c <= 100 for c in args.costs) or not 0 <= args.counted_energy <= 100:
        parser.error('Costs must be 1..100 and energy must be 0..100')
    if args.start_nanos > args.end_nanos:
        parser.error('Checkpoint start must precede end')
    with zipfile.ZipFile(args.archive) as z:
        timeline = json.loads(z.read('timeline.json'))
    pairs = {}
    for article in timeline['articles']:
        p = article.get('payload', {})
        if p.get('kind') != 'player_charge_button_fill_height_measured':
            continue
        at = article.get('monotonicTimeNanos')
        if at is None or not args.start_nanos <= at <= args.end_nanos:
            continue
        pairs.setdefault(at, {})[p['slot']] = (article['articleId'], p)
    results = []
    for at, pair in sorted(pairs.items()):
        result = compare_pair(pair.get(1, (None, None))[1], pair.get(2, (None, None))[1],
                              args.costs, args.counted_energy)
        results.append(dict(monotonicTimeNanos=at, source_articles=[a[0] for a in pair.values()], **result))
    print(json.dumps(dict(results=results, status='MEASUREMENTS' if results else 'NO_MEASUREMENTS'), indent=2))

if __name__ == '__main__':
    main()
