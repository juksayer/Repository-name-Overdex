"""Source-time listening context; this never labels or reattributes the audio.

``humanContext`` may contain player/opponent dictionaries with speciesName,
moveName and basis, plus activityNote. These are explicit trusted user reports,
not guessed from candidate scores. Overrides only apply to the matching active
species; a recording of the idle side must not inherit the opponent's move.
"""

import math


def _name(value):
    return "".join(c for c in str(value or "").casefold() if c.isalnum())


def _text(value):
    return value.strip() if isinstance(value, str) and value.strip() else None


def _time(article):
    value = article.get("monotonicTimeNanos")
    return value if isinstance(value, int) and not isinstance(value, bool) else None


def _ids(*articles):
    return list(dict.fromkeys(a["articleId"] for a in articles
                             if a and _text(a.get("articleId"))))


def _same_species(left, right):
    # Keep regional/form text: ordinary Raichu is not Alolan Raichu.
    return bool(_name(left)) and _name(left) == _name(right)


def _snapshot(articles, at):
    active = {}
    identified = {}
    configurations = {}
    confirmation = None
    for article in articles:
        if _time(article) > at:
            break
        p = article.get("payload") or {}
        kind, side = p.get("kind"), p.get("side")
        if kind == "player_team_roster_confirmation" and p.get("confirmed") is True:
            confirmation = article
            # A new accepted scan establishes a fresh team, even when names match.
            configurations.clear()
            active.pop("PLAYER", None)
            identified.pop("PLAYER", None)
        elif kind == "player_team_slot_configured":
            slot = p.get("slot")
            if isinstance(slot, int) and not isinstance(slot, bool) and slot in (1, 2, 3):
                # Same-clock saves cannot prove that they follow a confirmation.
                if confirmation is None or _time(article) > _time(confirmation):
                    configurations[slot] = article
        elif kind == "active_pokemon_species_witnessed" and side in ("PLAYER", "OPPONENT"):
            if not _text(p.get("speciesName")):
                continue
            prior = active.get(side)
            prior_p = prior["payload"] if prior else {}
            changed = (not _same_species(prior_p.get("speciesName"), p["speciesName"])
                       or (p.get("playerTeamSlot") is not None
                           and p.get("playerTeamSlot") != prior_p.get("playerTeamSlot"))
                       or (p.get("appearanceStartedAtNanos") is not None
                           and p.get("appearanceStartedAtNanos") != prior_p.get("appearanceStartedAtNanos")))
            if changed:
                identified.pop(side, None)
            active[side] = article
        elif kind == "active_pokemon_fainted_witnessed" and side in active:
            if not p.get("speciesName") or _same_species(p["speciesName"], active[side]["payload"].get("speciesName")):
                active.pop(side, None)
                identified.pop(side, None)
        elif kind == "fast_move_identified" and side in active:
            if _same_species(p.get("speciesName"), active[side]["payload"].get("speciesName")):
                identified[side] = article
    return active, identified, configurations, confirmation


def _side_context(side, snapshot, human, notes):
    active, identified, configurations, confirmation = snapshot
    species_article = active.get(side)
    result = {"speciesName": None, "speciesId": None, "moveName": None, "status": "unknown",
              "basis": "No active species recorded before this audio window.", "sourceArticleIds": []}
    if species_article:
        p = species_article["payload"]
        species = p["speciesName"]
        result.update(speciesName=species, speciesId=p.get("speciesId"), basis="No equipped Fast Move recorded for this active Pokémon.",
                      sourceArticleIds=_ids(species_article))
        if species_article.get("sourceId") == "TEAM_SELECT_LEAD_INFERENCE":
            result["speciesBasis"] = "Lead inferred from the confirmed Team Select roster."
        else:
            result["speciesBasis"] = "Latest recorded active-species evidence before this window."
        configured = [a for a in configurations.values()
                      if _same_species(a["payload"].get("speciesName"), species)
                      and _text(a["payload"].get("fastMoveName"))] if side == "PLAYER" else []
        if p.get("playerTeamSlot") is not None:
            configured = [a for a in configured if a["payload"].get("slot") == p["playerTeamSlot"]]
        # Repeated species can be separate owned Pokémon with different moves.
        moves = {_name(a["payload"]["fastMoveName"]) for a in configured}
        if configured and len(moves) == 1:
            result.update(moveName=configured[-1]["payload"]["fastMoveName"], status="configured",
                          basis="Species-matched team configuration; equipped move, not proof this sound is audible.",
                          sourceArticleIds=_ids(species_article, *configured, confirmation))
        else:
            identity = identified.get(side)
            if identity and not (identity["payload"].get("basis") or "").startswith("CONFIGURED_PLAYER_TEAM"):
                move = _text(identity["payload"].get("moveName"))
                if move:
                    result.update(moveName=move, status="inferred",
                                  basis="Recorded inference: " + (identity["payload"].get("basis") or "basis not supplied") + ". Not user-confirmed.",
                                  sourceArticleIds=_ids(species_article, identity))
            if len(moves) > 1:
                notes.append("Player has multiple configured Pokémon of this species with different Fast Moves; the active slot is unresolved.")
    if human:
        human_species, human_move = _text(human.get("speciesName")), _text(human.get("moveName"))
        if human_move and _same_species(human_species, result["speciesName"]):
            if result["moveName"] and _name(human_move) != _name(result["moveName"]):
                notes.append(f"{side.title()} user report of {human_move} differs from recorded {result['status']} {result['moveName']}; the user report is shown separately from the unchanged Articles.")
            result.update(moveName=human_move, status="user_confirmed",
                          basis=_text(human.get("basis")) or "User-confirmed equipped Fast Move; not an automatic sound label.")
        elif human_move:
            notes.append(f"{side.title()} user-reported {human_species or 'unknown species'} / {human_move} does not match the recorded active species; it was not applied.")
    return result


def listening_guide(articles, startNanos, durationSeconds, humanContext=None):
    """Build context at the AudioCaptured source start, never analysis delivery.

    The returned status qualifies the move's provenance. ``inferred`` is not a
    known equipped move. A source-time Article may have been delivered later;
    this is reconstructed review context, not a claim about live availability.
    Changes *inside* the recording are noted without replacing its start context.
    """
    if isinstance(startNanos, bool) or not isinstance(startNanos, int):
        raise ValueError("startNanos must be an integer source timestamp")
    if isinstance(durationSeconds, bool) or not isinstance(durationSeconds, (int, float)) or not math.isfinite(durationSeconds) or durationSeconds <= 0:
        raise ValueError("durationSeconds must be finite and positive")
    ordered = sorted((a for a in articles if isinstance(a, dict) and _time(a) is not None), key=_time)
    snapshot = _snapshot(ordered, startNanos)
    notes = []
    human = humanContext or {}
    result = {side.lower(): _side_context(side, snapshot, human.get(side.lower()), notes)
              for side in ("PLAYER", "OPPONENT")}
    end = startNanos + round(durationSeconds * 1_000_000_000)
    initial = {side: a["payload"].get("speciesName") for side, a in snapshot[0].items()}
    for a in ordered:
        if not startNanos < _time(a) < end:
            continue
        p = a.get("payload") or {}
        side = p.get("side")
        if side not in ("PLAYER", "OPPONENT"):
            continue
        elapsed = (_time(a) - startNanos) / 1_000_000_000
        if p.get("kind") == "active_pokemon_species_witnessed" and _text(p.get("speciesName")):
            if not _same_species(initial.get(side), p["speciesName"]):
                notes.append(f"{side.title()} species changes to {p['speciesName']} at +{elapsed:.3f} s within this recording; start context does not apply to the entire clip.")
                initial[side] = p["speciesName"]
        elif p.get("kind") == "active_pokemon_fainted_witnessed" and initial.get(side):
            if not p.get("speciesName") or _same_species(initial[side], p["speciesName"]):
                notes.append(f"{side.title()} {initial[side]} faint is recorded at +{elapsed:.3f} s within this recording.")
                initial.pop(side, None)
    if snapshot[3] and any(a["payload"].get("kind") == "player_team_slot_configured" and _time(a) <= _time(snapshot[3]) for a in ordered):
        notes.append("Team configurations from before the latest confirmed roster were excluded.")
    result.update(activityNote=_text(human.get("activityNote")), contextNotes=list(dict.fromkeys(notes)),
                  captureNote=f"Only {durationSeconds:g} s was saved in this recording. Longer original sound cannot be recovered from this clip.")
    return result
