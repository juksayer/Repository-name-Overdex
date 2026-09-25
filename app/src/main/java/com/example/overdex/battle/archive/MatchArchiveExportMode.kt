package com.example.overdex.battle.archive

/**
 * Chooses how much raw capture material travels with a match archive.
 *
 * [COMPACT_CITED_EVIDENCE] keeps the timeline and only the crop/audio artifacts
 * explicitly cited by retained evidence. [FULL_FORENSIC] keeps every captured
 * artifact for later investigation.
 */
enum class MatchArchiveExportMode {
    COMPACT_CITED_EVIDENCE,
    FULL_FORENSIC
}
