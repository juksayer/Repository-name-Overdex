#!/usr/bin/env python3
"""Compare saved processing reports; never opens or rewrites source archives."""
import argparse
import json
from collections import defaultdict
from pathlib import Path
from statistics import median


def read_runs(folder, mode):
    grouped = defaultdict(list)
    for path in sorted(folder.glob(f'*-{mode}-*.json')):
        data = json.loads(path.read_text())
        grouped[path.stem.rsplit('-', 2)[0]].append(data)
    return grouped


def stage_cpu(data):
    totals = defaultdict(float)
    for key, value in data['stages'].items():
        totals[key.split('/')[0].split('.')[0]] += value['cpuMs']
    return totals


def median_cpu(runs):
    totals = [stage_cpu(run) for run in runs]
    return {stage: median(row.get(stage, 0) for row in totals)
            for stage in set().union(*totals)}


def inline_rows(run):
    return sum(row['totalInputs'] for key, row in run['rebuilds'].items()
               if not key.startswith('forecast/'))


def compare(folder, baseline):
    cached = read_runs(folder, 'burst')
    controls = read_runs(folder / 'uncached', 'burst')
    lines = ['# Faint-history cache comparison', '',
             'Fresh uncached burst controls and cached bursts use the same inputs and process, after warm-up. '
             'Medians are across three runs. Desktop results do not establish Pixel performance.', '']
    for case, runs in cached.items():
        other = controls[case]
        assert len(runs) == len(other) == 3, 'Incomplete repetitions'
        before = median_cpu(other); after = median_cpu(runs)
        lines += [f'## {case}', '', '| Stage | Uncached CPU ms | Cached CPU ms | Reduction |',
                  '| --- | ---: | ---: | ---: |']
        for stage in sorted(before, key=before.get, reverse=True):
            b = before[stage]; a = after[stage]
            lines.append(f'| {stage} | {b:.3f} | {a:.3f} | {(1-a/b)*100 if b else 0:.1f}% |')
        for label, values in [('Uncached', other), ('Cached', runs)]:
            drains = [r['elapsedMs'] for r in values]
            cpu = [sum(stage_cpu(r).values()) for r in values]
            lines += ['', f'{label}: median drain {median(drains):.3f} ms '
                      f'(range {min(drains):.3f}–{max(drains):.3f}); '
                      f'median total stage CPU {median(cpu):.3f} ms; '
                      f'median inline resolver input rows {median(inline_rows(r) for r in values):,.0f}.']
        lines += ['', 'Cache snapshots at drain (background forecast can differ slightly):']
        for i, r in enumerate(runs, 1):
            c = r['cache']
            lines.append(f'- Run {i}: {c["hits"]} hits, {c["misses"]} misses, '
                         f'{c["entries"]} entries / {c["retainedRows"]} retained rows, '
                         f'{c["evictions"]} evictions, {c["oversized"]} oversized bypasses.')
        for mode in ('burst', 'paced'):
            old = read_runs(baseline, mode).get(case, [])
            new = read_runs(folder, mode).get(case, [])
            if old and new:
                lines += ['', f'Historical {mode} median total CPU: '
                          f'{median(sum(stage_cpu(r).values()) for r in old):.3f} → '
                          f'{median(sum(stage_cpu(r).values()) for r in new):.3f} ms '
                          '(separate experiment; host/JIT conditions differ).']
    (folder / 'cache-comparison.md').write_text('\n'.join(lines) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reports', type=Path)
    parser.add_argument('baseline', type=Path)
    args = parser.parse_args()
    compare(args.reports, args.baseline)
