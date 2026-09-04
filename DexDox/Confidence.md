Confidence
Confidence describes how strongly the available evidence supports an interpretation.
Confidence belongs to reasoning.
It is not a property of an Article.
It is not a property of a phenomenon.
It is not a property of a Crop.
It is not a property of a measurement.
A measurement records what Overdex encountered.
An interpretation expresses what Overdex presently believes that measurement, together with other available evidence, means.
Confidence expresses the strength of support for that interpretation.
Measurement Is Not Confidence
A Crop may produce a measurement such as:
SpeciesName
"SNEASEL"
That measurement does not need to claim:
confidence = 1.0
The measurement does not know whether it is correct.
It does not compare itself with other measurements.
It does not reason about its own significance.
It records the best available measurement of the incoming signal.
The measurement enters the Timeline before any interpretation is performed.
Confidence Belongs to Interpretation
Reasoning may later receive preserved measurements such as:
SpeciesName
"SNEASEL"

TypeIcon
"DARK"

BattleCry
matching Sneasel cry

FastMove
"SHADOW CLAW"
These remain distinct measurements.
Reasoning may determine that together they support the interpretation:
Opponent Species A = Sneasel
That interpretation may have confidence.
The measurements themselves do not.
Primary and Supplemental Evidence
Some interpretations may have a preferred primary source of evidence.
For example:
SpeciesName
"SNEASEL"
may provide the primary basis for a species interpretation.
Other preserved measurements may provide supplemental evidence:
BattleCry
TypeIcon
FastMove
MoveAudio
SuperEffective
If primary evidence provides sufficient support, interpretation may proceed.
If it does not, supplemental evidence may increase or decrease the support for that interpretation.
Conceptually:
primary evidence
      ↓
assessment
      │
      ├── sufficient → interpretation
      │
      └── insufficient
               ↓
       supplemental evidence
               ↓
          reassessment
               ↓
          interpretation
Measurements never perform this combination.
They remain independent records.
Reasoning evaluates their relationship.
Confidence Is Not Certainty
Confidence expresses how strongly the evidence currently available supports an interpretation.
It does not transform an interpretation into an immutable fact.
Additional evidence may:
- increase confidence
- decrease confidence
- reveal conflict
- invalidate an interpretation
- support a different interpretation
The original measurements remain unchanged.
A later interpretation does not overwrite an earlier interpretation.
Both become part of the Timeline.
Confidence Changes Are History
When the confidence associated with an interpretation changes, that change itself exists and shall be preserved.
For example:
T1
Interpretation:
Opponent Species A = Sneasel
Confidence: 0.62
Later:
T2
new measurement arrives
Then:
T3
Interpretation:
Opponent Species A = Sneasel
Confidence: 0.91
The 0.62 interpretation is not edited into 0.91.
The Timeline preserves:
measurement
interpretation at 0.62
new measurement
reassessment
interpretation at 0.91
The history of confidence is itself part of the history of Overdex's understanding.
Confidence Does Not Belong Upstream
No component responsible only for acquiring or preserving a measurement shall assign interpretive confidence to that measurement.
A recognizer may report properties of its measurement process when those properties are themselves measurements, but it shall not convert those properties into institutional belief.
For example, a recognizer may legitimately preserve:
candidate:
"SNEASEL"

match score:
0.84
if 0.84 is the measured output of the recognition process.
That number is not automatically Confidence.
It is evidence available to later reasoning.
This distinction matters enormously.
Otherwise:
recognizer score
      ↓
renamed "confidence"
      ↓
treated as belief
quietly turns a measurement into a conclusion before the rest of the evidence has even arrived. Groucho Marx glasses, now available in software architecture.
Confidence and the Timeline
The order is:
phenomenon
    ↓
Crop
    ↓
measurement
    ↓
Timeline
    ↓
reasoning
    ↓
interpretation + confidence
    ↓
Timeline
Confidence therefore never determines whether the original measurement deserves preservation.
The measurement is already preserved.
Low-confidence evidence is still evidence.
Conflicting evidence is still evidence.
Evidence whose meaning is unknown is still evidence.
Constitutional Principle
Confidence is the strength of support that available evidence provides for an interpretation.

The measurement tells Overdex what it encountered.
Reasoning determines what that evidence may mean.
Confidence expresses how strongly the evidence supports that interpretation.
