package eu.kanade.tachiyomi.ui.reader.setting

import tachiyomi.core.common.preference.PreferenceStore

// KMK -->
class BubblesPanelsPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /** Draw outlines around detected speech balloons and panels. Off by default. */
    fun showDetectionOutlines() = preferenceStore.getBoolean("pref_bubbles_show_outlines", false)

    /** Minimum model confidence (0..1) for a detection to be used. */
    fun detectionConfidence() = preferenceStore.getFloat("pref_bubbles_detection_confidence", 0.5f)

    /** Magnification applied to a balloon when it is tapped. */
    fun balloonZoomFactor() = preferenceStore.getFloat("pref_bubbles_balloon_zoom_factor", 2.0f)
}
// KMK <--
