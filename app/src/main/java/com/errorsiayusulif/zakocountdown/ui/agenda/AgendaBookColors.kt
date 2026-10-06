// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/agenda/AgendaBookColors.kt
package com.errorsiayusulif.zakocountdown.ui.agenda

/**
 * 日程本可选配色。
 *
 * 从 [AddEditAgendaBookFragment] 里抽出来共享 —— 默认本（全部 / 重点）的
 * 设置对话框也要用同一套色板，两处若各写一份，以后加颜色必然只改一处、
 * 另一处悄悄漂移（本工程已经因为「两条路径各写一份」踩过坑）。
 */
val AGENDA_BOOK_COLORS: List<String> = listOf(
    // MD3 Primary / Secondary Tones
    "#6750A4", // Purple 40 (M3 Default)
    "#9C27B0", // Purple
    "#E91E63", // Pink
    "#B58392", // M3 Pink-ish
    "#B3261E", // M3 Error/Red
    "#F44336", // Red
    "#9C4146", // M3 Brick Red
    "#7D5260", // M3 Rose

    // Warm Tones
    "#9A4058", // M3 Maroon
    "#FF9800", // Orange
    "#FFB300", // Amber
    "#E65100", // Deep Orange
    "#825500", // M3 Gold/Olive

    // Cool Tones
    "#0061A4", // M3 Blue
    "#2196F3", // Blue
    "#03A9F4", // Light Blue
    "#006493", // M3 Deep Blue
    "#386A20", // M3 Green
    "#4CAF50", // Green
    "#006D42", // M3 Teal-Green
    "#009688", // Teal
    "#006064", // Deep Teal

    // Neutral / Earthy Tones
    "#795548", // Brown
    "#605D62", // M3 Neutral
    "#424242", // Grey
    "#000000"  // Black
)
