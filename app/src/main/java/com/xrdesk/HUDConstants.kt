package com.xrdesk

/**
 * Centralized design constants for the HUD Visionary system.
 * Ensures consistent optical mass and hierarchy across all widgets.
 */
object HUDConstants {
    
    // SCALE-RELATIVE RATIOS
    const val RADIUS_RATIO = 0.38f // visionOS style pill radius
    const val HORIZONTAL_PADDING_RATIO = 0.28f
    const val VERTICAL_PADDING_RATIO = 0.12f
    const val ICON_SIZE_RATIO = 0.38f
    
    // FONT SCALE RATIOS
    const val FONT_SIZE_PRIMARY_RATIO = 0.26f
    const val FONT_SIZE_SECONDARY_RATIO = 0.16f
    const val FONT_SIZE_BAR_RATIO = 0.24f

    // DYNAMIC SPACING LIMITS
    const val SPACING_HORIZONTAL_MAX_DP = 24f 
    const val SPACING_HORIZONTAL_MIN_DP = 4f
    const val SPACING_VERTICAL_DP = 12f

    // COMPACT CARD SPECIFICS
    const val CARD_UNIFORM_WIDTH_RATIO = 3.5f
    
    // NOTIFICATIONS (visionOS style)
    const val NOTIF_RADIUS_DP = 24f
    const val NOTIF_WIDTH_DP = 240f
    const val NOTIF_PADDING_H_RATIO = 0.10f
    const val NOTIF_PADDING_V_RATIO = 0.08f
}
