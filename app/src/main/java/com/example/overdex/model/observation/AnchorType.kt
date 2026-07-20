package com.example.overdex.model.observation

/**
 * Enumeration of stable UI elements that can serve as visual anchors for coordinate normalization.
 */
enum class AnchorType {
    /** A generic move icon container. */
    MoveIcon,
    /** The specific icon for a fast move. */
    FastMoveIcon,
    /** The specific icon for a charged move. */
    ChargedMoveIcon,
    /** A Pokémon type badge (e.g., Fire, Water). */
    TypeBadge,
    /** Any other identifiable UI element used for alignment. */
    Other
}
