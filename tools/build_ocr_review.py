#!/usr/bin/env python3
"""Build a local, portable OCR review desk from unmodified .odxmatch evidence.

No recognition is rerun, labels are not guesses, and no archive is rewritten.
Reviewed labels are exported separately for later regression-test curation.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import struct
import zipfile
from zoneinfo import ZoneInfo
from datetime import datetime


def linked_crops(article, by_id):
    pending = list(article.get("evidenceReferences") or []) + article.get("predecessorIds", [])
    seen = set()
    while pending:
        ref = pending.pop(0)
        if ref in seen:
            continue
        seen.add(ref)
        source = by_id.get(ref)
        if not source:
            continue
        if source["payload"]["kind"] == "crop_captured":
            yield source
        else:
            pending.extend(source.get("evidenceReferences") or [])
            pending.extend(source.get("predecessorIds", []))


def collect_cases(archives, per_archive=24, include_empty=False):
    cases, skipped = [], 0
    for path in archives:
        with zipfile.ZipFile(path) as package:
            timeline = json.loads(package.read("timeline.json"))
            articles = timeline["articles"]
            by_id = {article["articleId"]: article for article in articles}
            seen = set()
            candidates = []
            for article in articles:
                payload = article["payload"]
                source = article["sourceId"]
                if payload["kind"] != "raw_text" or "OCR" not in source:
                    continue
                if not any(field in source for field in ("ANNOUNCEMENT", "SPECIES")):
                    continue
                raw = payload["value"]
                if not raw.strip() and not include_empty:
                    continue
                # Keep one representative of each reading per source family.
                family = "announcement" if "ANNOUNCEMENT" in source else "species"
                signature = (family, raw)
                if signature in seen:
                    continue
                found = False
                for crop_article in linked_crops(article, by_id):
                    crop = crop_article["payload"]
                    try:
                        data = package.read(crop["artifactPath"])
                    except KeyError:
                        continue
                    if len(data) != crop["byteCount"] or hashlib.sha256(data).hexdigest() != crop["sha256"]:
                        raise ValueError(f"Source integrity failure: {crop['artifactPath']}")
                    if data[:8] != b"\x89PNG\r\n\x1a\n":
                        continue
                    width, height = struct.unpack(">II", data[16:24])
                    bounds = [crop["cropLeft"], crop["cropTop"], crop["cropRight"], crop["cropBottom"]]
                    is_frame = (width, height) == (crop["sourceWidth"], crop["sourceHeight"])
                    if is_frame:
                        x1, y1, x2, y2 = bounds
                        if not (0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height):
                            raise ValueError("Crop lies outside the retained frame")
                    else:
                        if (width, height) != (bounds[2] - bounds[0], bounds[3] - bounds[1]):
                            raise ValueError("Retained PNG dimensions do not match crop provenance")
                        bounds = [0, 0, width, height]
                    evidence = payload.get("ocr") or {}
                    case = {
                        "id": f"{timeline['matchId']}:{article['articleId']}",
                        "matchId": timeline["matchId"], "articleId": article["articleId"],
                        "archive": str(path.resolve()), "device": path.parent.name,
                        "sourceId": source, "cropArticleId": crop_article["articleId"],
                        "artifactPath": crop["artifactPath"], "sha256": crop["sha256"],
                        "cropName": crop["cropName"], "perceivedAt": article["perceivedAt"],
                        "monotonicTimeNanos": article.get("monotonicTimeNanos"),
                        "rawText": raw, "acceptedText": evidence.get("acceptedText"),
                        "treatment": evidence.get("treatment", "not recorded"),
                        "confidence": article.get("confidence"),
                        "captureBounds": [crop["cropLeft"], crop["cropTop"], crop["cropRight"], crop["cropBottom"]],
                        "sourceFrameWidth": crop["sourceWidth"], "sourceFrameHeight": crop["sourceHeight"],
                        "bounds": bounds, "imageWidth": width, "imageHeight": height,
                        "image": "data:image/png;base64," + base64.b64encode(data).decode(),
                        "timeLabel": datetime.fromtimestamp(article["perceivedAt"] / 1000,
                            ZoneInfo("America/Chicago")).strftime("%b %d · %I:%M:%S %p"),
                    }
                    candidates.append(case)
                    seen.add(signature)
                    found = True
                    break
                if not found:
                    skipped += 1
            # Prefer varied announcement partials; retain a few species labels as controls.
            announcements = [case for case in candidates if "ANNOUNCEMENT" in case["sourceId"]]
            species = [case for case in candidates if "SPECIES" in case["sourceId"]]
            selected = announcements[:max(0, per_archive - min(4, len(species)))]
            selected.extend(species[:per_archive - len(selected)])
            cases.extend(selected)
    return cases, skipped


def build_review(cases, output):
    if not cases:
        raise ValueError("No OCR readings with verified source images found")
    set_id = hashlib.sha256("\n".join(case["id"] + case["sha256"] for case in cases).encode()).hexdigest()[:20]
    data = json.dumps({"schemaVersion": 1, "reviewSetId": set_id, "cases": cases},
                      ensure_ascii=False).replace("<", "\\u003c")
    output.write_text(HTML.replace("__REVIEW_DATA__", data), encoding="utf-8")
    return set_id


HTML = r"""<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Overdex · OCR review desk</title>
<style>
:root{color-scheme:dark;font-family:system-ui,sans-serif;background:#101817;color:#edf5ed}
*{box-sizing:border-box}body{margin:0}main{max-width:1050px;margin:auto;padding:28px 24px 60px}
header{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}
h1{font-size:30px;margin:6px 0 10px}h2{font-size:18px;margin:0 0 12px}
p{line-height:1.55;color:#bfcdc5}small,.muted{color:#a8beb1}button,select,textarea{font:inherit}
button,select{border:1px solid #5c7969;border-radius:7px;padding:10px 14px;background:#24362c;color:inherit;cursor:pointer}
button.primary{background:#b5edc0;color:#122819;border-color:#b5edc0;font-weight:650}
button:disabled{opacity:.35;cursor:default}button:hover:enabled{filter:brightness(1.12)}
button:focus-visible,select:focus-visible,textarea:focus-visible{outline:3px solid #e4c26d;outline-offset:3px}
.bar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.top{border-bottom:1px solid #354b3d;padding-bottom:20px}
.panel{background:#19251e;border:1px solid #3c5344;border-radius:12px;padding:22px;margin-top:20px}
.meta{display:flex;justify-content:space-between;gap:14px;flex-wrap:wrap;margin-bottom:18px}
#counter{font-weight:700;color:#b5edc0}.image-wrap{overflow:auto;background:#080d0a;border-radius:8px;padding:20px}
canvas{display:block;max-width:none;margin:auto}#image-note{margin:12px 0 0;font-size:13px}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:22px;margin-top:22px}
pre{white-space:pre-wrap;overflow-wrap:anywhere;font:18px/1.5 ui-monospace,monospace;padding:16px;background:#0c140f;border-radius:8px;margin:0}
label{display:block;font-weight:600;margin-bottom:9px}textarea{width:100%;resize:vertical;min-height:94px;padding:12px;background:#0c140f;color:#edf5ed;border:1px solid #5c7969;border-radius:7px}
#note{min-height:65px}select{max-width:100%;margin-bottom:16px}.nav{justify-content:space-between;margin-top:22px}
details{margin-top:18px;color:#a8beb1}details pre{font-size:12px;margin-top:10px}#save-state{font-size:13px}
.notice{border-left:3px solid #d5b774;padding-left:12px;font-size:14px}
@media(max-width:700px){main{padding:20px 12px}.grid{grid-template-columns:1fr}.panel{padding:16px}h1{font-size:25px}}
</style>
<main><header class="top"><div><small>OVERDEX / EVIDENCE REVIEW</small><h1>Read what the reader missed.</h1>
<p>Compare a preserved crop with its original OCR reading. Add your reading and move to the next sample.</p></div>
<div class="bar"><button id="import">Load corrections</button><button class="primary" id="export">Save corrections</button>
<input id="import-file" type="file" accept=".json,application/json" hidden></div></header>
<p class="notice">These are the actual captured pixels, enlarged for inspection. If text is cut off or hidden, mark that.
Distinguish letters you can see from a Pokémon GO–informed guess. Corrections stay separate from the match record;
this desk does not train the recognizer automatically.</p>
<section class="panel"><div class="meta"><span id="counter"></span><span id="context"></span><span id="progress"></span></div>
<label for="sample">Choose a sample</label><select id="sample" style="width:100%"></select>
<div class="bar" style="justify-content:space-between;margin-bottom:12px"><h2>Preserved crop</h2>
<button id="zoom">Zoom: Fit</button></div>
<div class="image-wrap"><canvas id="crop" role="img" aria-label="Original retained OCR crop"></canvas></div>
<p id="image-note"></p>
<div class="grid"><div><h2>What OCR read</h2><pre id="raw"></pre>
<p id="accepted" class="muted"></p></div><div>
<label for="basis">How certain is your reading?</label>
<select id="basis"><option value="unreviewed">Not reviewed yet</option>
<option value="visible">I can read this from the pixels</option>
<option value="context">Some of this is inferred from context</option>
<option value="unreadable">Too cropped, covered, or unclear to read</option>
<option value="correct">The OCR reading is already correct</option></select>
<label for="transcript">Your transcription</label><textarea id="transcript" placeholder="Type what the crop actually says."></textarea>
<label for="note" style="margin-top:14px">Notes (optional)</label><textarea id="note" placeholder="For example: left edge clipped; end covered by HUD."></textarea>
<p id="save-state" role="status"></p></div></div>
<div class="bar nav"><button id="previous">← Previous</button><button id="next-unread">Next unreviewed</button><button id="next">Next →</button></div>
<details><summary>Source and evidence references</summary><pre id="provenance"></pre></details></section>
<p>Drafts are saved in this browser when available. Use <strong>Save corrections</strong> to keep a portable copy
and share it back for review. Left/right arrow keys change samples when you are not typing.</p></main>
<script id="review-data" type="application/json">__REVIEW_DATA__</script>
<script>
'use strict';
const data=JSON.parse(document.getElementById('review-data').textContent);
const $=id=>document.getElementById(id), key='overdex-ocr-review:'+data.reviewSetId;
let index=0,zoom=0,answers={},storageOk=true,loadError='';
const validBases=new Set(['unreviewed','visible','context','unreadable','correct']);
const validAnswer=a=>a&&validBases.has(a.basis)&&typeof a.transcript==='string'&&typeof a.note==='string';
try{const saved=JSON.parse(localStorage.getItem(key)||'{}');
 if(saved&&typeof saved==='object'&&!Array.isArray(saved)) answers=Object.fromEntries(
  Object.entries(saved).filter(([id,a])=>data.cases.some(c=>c.id===id)&&validAnswer(a))
 );}catch(e){storageOk=false;loadError='Browser draft storage is unavailable. Save corrections to keep your work.';}
const isComplete=a=>a&&a.basis!=='unreviewed'&&(a.basis==='unreadable'||Boolean(a.transcript.trim()));
const clean=c=>Object.fromEntries(Object.entries(c).filter(([k])=>k!=='image'));
data.cases.forEach((c,i)=>{const option=document.createElement('option');option.value=String(i);
 option.textContent=(i+1)+' · '+c.device+' · '+(c.rawText||'(empty)').replace(/\s+/g,' ').slice(0,90);$('sample').append(option);});
$('sample').onchange=()=>{index=Number($('sample').value);render();};
function persist(){
 const c=data.cases[index], basis=$('basis').value;
 answers[c.id]={basis,transcript:$('transcript').value,note:$('note').value,reviewedAt:new Date().toISOString()};
 try{localStorage.setItem(key,JSON.stringify(answers));storageOk=true;}catch(e){storageOk=false;}
 $('save-state').textContent=storageOk?'Draft saved in this browser.':'Draft is in memory only. Save corrections before closing.';
 if((basis==='visible'||basis==='context')&&!answers[c.id].transcript.trim()) $('save-state').textContent+=' Enter your transcription to complete this sample.';
 updateProgress();
}
function updateProgress(){ $('progress').textContent=data.cases.filter(c=>isComplete(answers[c.id])).length+' / '+data.cases.length+' reviewed'; }
function render(){
 const c=data.cases[index], a=answers[c.id]||{basis:'unreviewed',transcript:'',note:''};
 $('counter').textContent='Sample '+(index+1)+' of '+data.cases.length;
 $('sample').value=String(index);
 $('context').textContent=c.device+' · '+c.timeLabel;
 $('raw').textContent=c.rawText||'(No text returned)';
 $('accepted').textContent='Treatment: '+c.treatment+
  (c.acceptedText!==null&&c.acceptedText!==c.rawText?'\nAfter source filtering: '+(c.acceptedText||'(empty)'):'');
 $('basis').value=a.basis; $('transcript').value=a.transcript; $('note').value=a.note;
 $('save-state').textContent=loadError||(a.basis==='unreviewed'?'Ready for your review.':storageOk?'Draft saved in this browser.':'Save corrections to keep your work.');
 $('provenance').textContent=JSON.stringify(clean(c),null,2);
 $('previous').disabled=index===0; $('next').disabled=index===data.cases.length-1;
 const image=new Image(), current=c.id; image.onload=()=>{
  if(data.cases[index].id!==current)return;
  const [x1,y1,x2,y2]=c.bounds,w=x2-x1,h=y2-y1,canvas=$('crop');
  canvas.width=w;canvas.height=h;canvas.style.width=zoom?w*zoom+'px':'100%';
  canvas.style.maxWidth=zoom?'none':w*2+'px';canvas.style.height=zoom?h*zoom+'px':'auto';
  canvas.getContext('2d').drawImage(image,x1,y1,w,h,0,0,w,h);
  $('image-note').textContent=c.cropName+' · '+w+' × '+h+' source pixels · '+(zoom?'enlarged '+zoom+'×':'fit to view')+' · source SHA-256 verified';
 }; image.onerror=()=>{$('image-note').textContent='Image could not be displayed; leave this sample unreviewed.';};image.src=c.image;
 updateProgress();
}
$('basis').addEventListener('change',()=>{if($('basis').value==='correct')$('transcript').value=data.cases[index].rawText;persist();});
['transcript','note'].forEach(id=>$(id).addEventListener('input',persist));
function go(delta){index=Math.max(0,Math.min(data.cases.length-1,index+delta));render();}
$('previous').onclick=()=>go(-1);$('next').onclick=()=>go(1);
$('next-unread').onclick=()=>{for(let d=1;d<=data.cases.length;d++){const n=(index+d)%data.cases.length;if(!isComplete(answers[data.cases[n].id])){index=n;render();break;}}};
$('zoom').onclick=()=>{zoom=(zoom+1)%4;$('zoom').textContent='Zoom: '+(zoom?zoom+'×':'Fit');render();};
document.addEventListener('keydown',e=>{if(['INPUT','TEXTAREA','SELECT','BUTTON'].includes(e.target.tagName))return;if(e.key==='ArrowLeft'){e.preventDefault();go(-1);}if(e.key==='ArrowRight'){e.preventDefault();go(1);}});
$('export').onclick=()=>{
 const reviewed=data.cases.filter(c=>answers[c.id]&&answers[c.id].basis!=='unreviewed');
 if(reviewed.some(c=>!isComplete(answers[c.id]))){$('save-state').textContent='Please finish the transcription on each reviewed sample, or mark it unreadable.';return;}
 const payload={schemaVersion:1,reviewSetId:data.reviewSetId,exportedAt:new Date().toISOString(),
  usage:'Human review only; source articles unchanged; contextual guesses are not visual ground truth.',
  corrections:reviewed.map(c=>({source:clean(c),review:answers[c.id]}))};
 const blob=new Blob([JSON.stringify(payload,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');
 a.href=url;a.download='overdex-ocr-corrections-'+data.reviewSetId+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 $('save-state').textContent='Corrections exported. Original matches are unchanged.';
};
$('import').onclick=()=>$('import-file').click();
$('import-file').onchange=async e=>{
 const file=e.target.files[0];if(!file)return;
 try{
  const imported=JSON.parse(await file.text());
  if(imported.schemaVersion!==1||imported.reviewSetId!==data.reviewSetId||!Array.isArray(imported.corrections))throw Error('This file belongs to a different review set.');
  const staged={},ids=new Set();
  for(const item of imported.corrections){
   const c=data.cases.find(c=>c.id===item.source?.id),a=item.review;
   if(!c||ids.has(c.id)||item.source.sha256!==c.sha256||item.source.rawText!==c.rawText||!validAnswer(a))throw Error('Invalid correction or mismatched source.');
   ids.add(c.id);staged[c.id]=a;
  }
  answers={...answers,...staged};try{localStorage.setItem(key,JSON.stringify(answers));}catch(e){storageOk=false;}loadError='';render();
  $('save-state').textContent='Loaded '+ids.size+' reviewed samples.';
 }catch(error){$('save-state').textContent='Could not load corrections: '+error.message;}
 e.target.value='';
};
render();
</script></html>"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archives", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--per-archive", type=int, default=24)
    parser.add_argument("--include-empty", action="store_true")
    args = parser.parse_args()
    if args.per_archive < 1:
        parser.error("--per-archive must be positive")
    cases, skipped = collect_cases(args.archives, args.per_archive, args.include_empty)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    set_id = build_review(cases, args.output)
    print(json.dumps({"path": str(args.output), "cases": len(cases),
                      "reviewSetId": set_id, "readingsWithoutMedia": skipped}))


if __name__ == "__main__":
    main()
