#!/usr/bin/env python3
"""Summarize the opt-in MatchProcessingArchiveProfileTest reports (stdlib only)."""
import argparse
import csv
import json
import zipfile
from collections import defaultdict
from pathlib import Path
from statistics import median


def summarize(folder):
    runs = []
    stages = []
    backlog = []
    for path in sorted(folder.glob('*.json')):
        if not any(f'-{mode}-' in path.stem for mode in ('burst', 'paced')):
            continue
        data = json.loads(path.read_text())
        if 'stages' not in data:
            continue
        totals = defaultdict(lambda: [0, 0., 0., 0., 0.])
        for key, timing in data['stages'].items():
            stage = key.split('/')[0].split('.')[0]
            row = totals[stage]
            row[0] += timing['calls']
            row[1] += timing['elapsedMs']
            row[2] += timing['cpuMs']
            row[3] = max(row[3], timing['maxMs'])
            row[4] = max(row[4], timing.get('maxCpuMs', 0))
        for stage, values in totals.items():
            stages.append([path.stem, stage, *values])
        samples = data['progress']
        # An operational definition, not a game rule: three consecutive 1 s
        # samples with work pending and the oldest item already >=100 ms old.
        first = None
        for i in range(len(samples) - 2):
            if all(s['pending'] > 0 and s['oldestPendingMs'] >= 100 for s in samples[i:i+3]):
                first = samples[i]['elapsedMs']
                break
        runs.append([path.stem, data['mode'], data['elapsedMs'],
                     max((s['pending'] for s in samples), default=0),
                     max((s['oldestPendingMs'] for s in samples), default=0), first])
        for sample in samples:
            backlog.append([path.stem, *[sample.get(k) for k in
                ('elapsedMs', 'accepted', 'completed', 'pending', 'oldestPendingMs',
                 'activeSequence', 'lastCompletedSequence', 'activeStage', 'activeAgeMs')]])
    for name, header, rows in [
        ('stage-times.csv', ['run', 'stage', 'calls', 'elapsedMs', 'cpuMs', 'maxMs', 'maxCpuMs'], stages),
        ('runs.csv', ['run', 'mode', 'drainMs', 'maxSampledPending', 'maxSampledOldestMs', 'firstSustainedBacklogMs'], runs),
        ('backlog.csv', ['run', 'elapsedMs', 'accepted', 'completed', 'pending', 'oldestPendingMs',
                         'activeSequence', 'lastCompletedSequence', 'activeStage', 'activeAgeMs'], backlog),
    ]:
        with (folder / name).open('w', newline='') as output:
            writer = csv.writer(output); writer.writerow(header); writer.writerows(rows)
    grouped = defaultdict(list)
    for run, stage, calls, elapsed, cpu, maximum, maximum_cpu in stages:
        case, mode, repetition = run.rsplit('-', 2)
        grouped[case, mode, stage].append((elapsed, cpu))
    lines = ['# Desktop processing measurements', '',
             'Medians across measured repetitions. Warm-up and uninstrumented control are excluded.', '',
             '| Match | Mode | Stage | Median elapsed ms | Median CPU ms | Repetitions |',
             '| --- | --- | --- | ---: | ---: | ---: |']
    for (case, mode, stage), values in sorted(grouped.items(), key=lambda x: (x[0][:2], -median(v[0] for v in x[1]))):
        lines.append(f'| {case} | {mode} | {stage} | {median(v[0] for v in values):.3f} | {median(v[1] for v in values):.3f} | {len(values)} |')
    (folder / 'stage-summary.md').write_text('\n'.join(lines) + '\n')
    archive_rows = []
    for archive in sorted(folder.parent.glob('*.odxmatch.zip')):
        with zipfile.ZipFile(archive) as source:
            articles = json.loads(source.read('timeline.json'))['articles']
        origin = min(a['monotonicTimeNanos'] for a in articles
                     if a.get('sequenceNumber') is not None and a.get('monotonicTimeNanos') is not None)
        summaries = sorted((a['payload'] for a in articles
                            if a['payload']['kind'] == 'hp_observation_pipeline_measured'),
                           key=lambda p: p['windowEndedAtNanos'])
        for p in summaries:
            archive_rows.append([archive.name, (p['windowEndedAtNanos'] - origin) / 1e9,
                                 p['pendingSubmissions'], (p['oldestPendingAgeNanos'] or 0) / 1e9,
                                 sum(p['cumulative'][side]['pendingEvicted'] for side in ('player', 'opponent')),
                                 *[(p['window'][side]['submissionToTimeline']['meanNanos'] or 0) / 1e9
                                   for side in ('player', 'opponent')]])
    with (folder / 'archive-hp-backlog.csv').open('w', newline='') as output:
        writer = csv.writer(output)
        writer.writerow(['archive', 'captureOffsetSeconds', 'pendingHp', 'oldestTrackedAgeSeconds',
                         'cumulativeDiagnosticEvictions', 'playerWindowSubmissionToTimelineMeanSeconds',
                         'opponentWindowSubmissionToTimelineMeanSeconds'])
        writer.writerows(archive_rows)
    print(f'{len(runs)} measured runs; wrote stage-times.csv, runs.csv, backlog.csv, stage-summary.md, archive-hp-backlog.csv')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reports', type=Path)
    summarize(parser.parse_args().reports)
