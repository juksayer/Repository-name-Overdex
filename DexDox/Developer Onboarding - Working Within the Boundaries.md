     OVERDEX

The repository contains several generations of Overdex architecture. Existing code is evidence
of architectural history, not automatically the current architectural authority.

Some concepts in 
Overdex exist in multiple places, and there is not always an obvious reason why.

Sometimes this is intentional: different parts of the system may need the same underlying equipment
to perform different jobs.

Sometimes it is historical: early naming decisions caused the same concept to appear under 
different names.

Sometimes it is architectural debris: accidents, abandoned approaches, and incomplete refactors
left multiple versions of the same idea behind.

Never assume that duplicated or apparently unused code is meaningless. It may still have active
consumers, represent an unfinished architectural direction, or be waiting to be renamed,
reconnected, or untangled.

Example: Regions

Overdex contains CaptureRegion, ObservationRegion, and battle-specific calibrated regions. 
They are not interchangeable merely because they all contain coordinates. CaptureRegion belongs
to the capture-template/registration system; ObservationRegion belongs to the Observation
architecture; battle calibration represents runtime battle configuration. Before modifying
any of them, determine which architectural responsibility the requested change belongs to.

Never choose an architectural owner for a new concept solely because an existing class has a
similar name or shape. First establish whether that class still represents a surviving
architectural concept.

Before removing, replacing, or consolidating something, determine why it exists and whether 
anything depends on it.
│
├── Mental Model
│   └── What the layers mean
│
└── Developer Onboarding
├── How to read the existing architecture
|
|   When ownership is ambiguous, research the boundary; do not choose the nearest existing class
| merely because it can hold the data.
|
├── Responsibility boundaries
|
├── Geography vs calibration
|
├── Evidence vs recognition vs testimony
|
├── Active paths vs archaeological foundations
|
├── How to resolve competing implementations
|
├── How to investigate before changing code
|
|  Before implementing a new concept, determine whether the concept already exists under another 
| name, exists in multiple generations, or is genuinely missing
| 
└── When to stop and ask

Refactors are high-rish. Do not delete or overwrite anything without express permission.