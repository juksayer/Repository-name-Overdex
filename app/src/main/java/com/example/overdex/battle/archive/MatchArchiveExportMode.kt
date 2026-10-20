package com.example.overdex.battle.archive

/**
 * Chooses how much raw capture material travels with a match archive.
 *
 * [COMPACT_CITED_EVIDENCE] keeps the timeline and only the crop/audio artifacts
 * explicitly cited by retained evidence. [FULL_FORENSIC] keeps every captured
 * artifact for later investigation.
 */
enum class MatchArchiveExportMode {
    /** Every Article, including raw OCR and capture metadata, without PNG/WAV files. */
    TIMELINE_ONLY,
    COMPACT_CITED_EVIDENCE,
    FULL_FORENSIC
}

fun MatchArchive.forExport(mode: MatchArchiveExportMode): MatchArchive = copy(
    articles = MatchArchiveArticleSelector.select(articles, mode),
    // Re-exporting a media-free archive cannot recreate the omitted source files.
    mediaIncluded = mediaIncluded && mode != MatchArchiveExportMode.TIMELINE_ONLY
)
