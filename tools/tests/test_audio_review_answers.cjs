'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

const html = fs.readFileSync(path.join(__dirname, '../templates/fast_move_audio_review.html'), 'utf8');
const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]
  .map(match => match[1]).find(body => body.includes('function validateImport('));
assert.ok(script);
const prefixEnd = script.indexOf('  function stopPlayers()');
const merge = script.match(/      staged\.forEach\(\(review,id\)=>\{[\s\S]*?\n      \}\);/);
assert.ok(prefixEnd > 0 && merge, 'Use the actual validation and import merge code');
const T0 = '2026-10-29T12:00:00.000Z', T1 = '2026-10-29T12:01:00.000Z', T2 = '2026-10-29T12:02:00.000Z';
const clone = value => JSON.parse(JSON.stringify(value));

function fixture() {
  const cases = ['one', 'two'].map(id => ({id, archiveName:'match.zip', matchId:'match',
    sourceSha256:'archive-hash', articleId:id, recording:{sha256:'recording-hash'}, relativeSeconds:2,
    timeOrigin:'Match Start', candidates:[{code:'A', reference:{sha256:'reference-hash'},
      matchingOffsetSeconds:0.144, referenceOffsetSeconds:0.128, matchingDurationSeconds:0.416}]}));
  const data = {version:'fast-move-audio-review-v1', reviewSetId:'same-set', cases};
  const context = {document:{getElementById:id => id === 'review-data' ? {textContent:JSON.stringify(data)} : {}}};
  vm.runInNewContext(script.slice(0, prefixEnd) + `
    function importAnswers(value) { const staged = validateImport(value); ${merge[0]} }
    globalThis.api = {validateImport, sourceFor, reviews, importAnswers};
  })();`, context);
  const api = context.api;
  const answer = (changes = {}, id = 'one') => ({id, source:clone(api.sourceFor(cases.find(item => item.id === id))),
    review:{choice:'A', confidence:'sure', notes:'Heard a match', updatedAtUtc:T1,
      reviewedAtUtc:T1, revealedAtUtc:null, reviewedWithContext:false, ...changes}});
  const envelope = (...reviews) => ({version:'fast-move-audio-review-answers-v1',
    datasetVersion:data.version, reviewSetId:data.reviewSetId, reviews});
  return {api, answer, envelope};
}

test('legacy completed blind answer remains reviewed without names', () => {
  const {api, answer, envelope} = fixture();
  const value = api.validateImport(envelope(answer())).get('one');
  assert.equal(value.reviewedAtUtc, T1);
  assert.equal(value.namesShownAtUtc, null);
  assert.equal(value.reviewedWithNames, false);
});

test('legacy context-reviewed answer inherits name exposure and its date', () => {
  const {api, answer, envelope} = fixture();
  const value = api.validateImport(envelope(answer({revealedAtUtc:T0, reviewedWithContext:true}))).get('one');
  assert.equal(value.namesShownAtUtc, T0);
  assert.equal(value.reviewedWithNames, true);
});

test('legacy reveal after a blind answer records exposure without rewriting the judgment', () => {
  const {api, answer, envelope} = fixture();
  const value = api.validateImport(envelope(answer({revealedAtUtc:T2}))).get('one');
  assert.equal(value.namesShownAtUtc, T2);
  assert.equal(value.reviewedAtUtc, T1);
  assert.equal(value.reviewedWithNames, false);
});

test('new fields survive validation and importing preserves the earliest exposure dates', () => {
  const {api, answer, envelope} = fixture();
  api.importAnswers(envelope(answer({namesShownAtUtc:T0, reviewedWithNames:true,
    revealedAtUtc:T0, reviewedWithContext:true})));
  let value = api.reviews.get('one');
  assert.equal(value.reviewedWithNames, true);
  assert.equal(value.namesShownAtUtc, T0);
  api.importAnswers(envelope(answer({namesShownAtUtc:T2, reviewedWithNames:false,
    revealedAtUtc:T2, reviewedWithContext:false})));
  value = api.reviews.get('one');
  assert.equal(value.namesShownAtUtc, T0);
  assert.equal(value.revealedAtUtc, T0);
  assert.equal(value.reviewedWithNames, false); // Preserve the imported answer's actual basis.
  api.importAnswers(envelope(answer({namesShownAtUtc:null, reviewedWithNames:false})));
  assert.equal(api.reviews.get('one').namesShownAtUtc, T0);
  assert.equal(api.reviews.get('one').revealedAtUtc, T0);
});

test('invalid new exposure types, dates and missing required name date are rejected', () => {
  const {api, answer, envelope} = fixture();
  for (const changes of [{namesShownAtUtc:42}, {namesShownAtUtc:'not a date'},
    {reviewedWithNames:'yes'}, {reviewedWithNames:1}, {reviewedWithNames:true, namesShownAtUtc:null}]) {
    assert.throws(() => api.validateImport(envelope(answer(changes))), /invalid answer/);
  }
});

test('a later altered source rejects the entire import without changing existing answers', () => {
  const {api, answer, envelope} = fixture();
  api.importAnswers(envelope(answer()));
  const before = JSON.stringify([...api.reviews]);
  for (const alter of [source => {source.recordingSha256 = 'different';},
    source => {source.candidates[0].code = 'B';},
    source => {source.candidates[0].matchingOffsetSeconds = 0.2;}]) {
    const changed = answer({}, 'two'); alter(changed.source);
    assert.throws(() => api.importAnswers(envelope(answer({notes:'Do not partially apply'}), changed)), /does not match/);
    assert.equal(JSON.stringify([...api.reviews]), before);
  }
});
