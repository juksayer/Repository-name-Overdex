Organizer creates tournament.
Players join with a short code.
Tournament restrictions are distributed automatically.
Overdex assists with match tracking.
Results update standings.
Players earn persistent tournament records and trophies

# Tournament Domain Model (Draft)

## Core Definitions

### Match

A Match is the complete record of a competitive engagement between two participants.

A Match begins when the Pokémon GO countdown reaches:

3

2

1

A Match ends when one of the following occurs:

* Victory
* Defeat
* Abandonment

Each completed Match produces a single immutable ArchivedBattle.

---

### Round

A Round is a predetermined collection of Matches.

Rounds are created by the Organizer before the tournament begins.

---

### Tournament

A Tournament is an organized collection of Rounds.

Tournament presets may include:

* Neighborhood
* Regional
* National
* Global
* Custom

Tournament presets may configure participant limits, pairing style, scheduling, and number of rounds.

---

## Match Validation

Both participants independently record the battle.

After completion, both ArchivedBattles are compared.

Examples of validation:

* Opposing leads match.
* Teams observed by both participants agree.
* Winner and loser agree.
* Timeline consistency.

If sufficient disagreement exists, the Match enters a **Contested** state.

Contested Matches require Organizer review.

---

## Player Profile

A Player Profile represents a user's competitive history.

Possible profile attributes include:

* Battles Completed
* Tournament Wins
* Trophies
* Achievements
* Favorite League
* Favorite Lead
* Most Used Pokémon
* Play Style Metrics
* Contested Results

Players may choose which profile information is publicly visible.

---

## Trophies

Trophies preserve historical accomplishments.

Each Trophy records:

* Tournament Name
* Date
* Tournament Style
* Final Placement
* Winning Team

---

## Roles

### Participant

Participants may view:

* Tournament Rules
* Schedule
* Opponent Profile
* Current Standings

Participants may not view:

* Other participants' teams
* Organizer controls

---

### Organizer

Organizers may manage:

* Participants
* Pairings
* Round progression
* Ready status
* Online status
* Match validation
* Contested Matches
* Tournament completion

Organizer tools are intentionally separate from the participant experience.
