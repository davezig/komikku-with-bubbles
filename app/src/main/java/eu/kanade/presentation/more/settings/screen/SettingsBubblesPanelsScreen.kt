package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.setting.BubblesPanelsPreferences
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
object SettingsBubblesPanelsScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsBubblesPanelsScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_bubbles_panels

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<BubblesPanelsPreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.showDetectionOutlines(),
                title = stringResource(KMR.strings.pref_bubbles_show_outlines),
                subtitle = stringResource(KMR.strings.pref_bubbles_show_outlines_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = prefs.detectionConfidence(),
                entries = persistentMapOf(
                    0.1f to "0.1",
                    0.2f to "0.2",
                    0.3f to "0.3",
                    0.4f to "0.4",
                    0.5f to "0.5",
                    0.6f to "0.6",
                    0.7f to "0.7",
                    0.8f to "0.8",
                    0.9f to "0.9",
                ),
                title = stringResource(KMR.strings.pref_bubbles_detection_confidence),
                subtitle = stringResource(KMR.strings.pref_bubbles_detection_confidence_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = prefs.balloonZoomFactor(),
                entries = persistentMapOf(
                    1.5f to "1.5×",
                    2.0f to "2.0×",
                    2.5f to "2.5×",
                    3.0f to "3.0×",
                ),
                title = stringResource(KMR.strings.pref_bubbles_balloon_zoom_factor),
                subtitle = stringResource(KMR.strings.pref_bubbles_balloon_zoom_factor_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.panelViewFullPageAfterLast(),
                title = stringResource(KMR.strings.pref_bubbles_panel_view_full_page_after),
                subtitle = stringResource(KMR.strings.pref_bubbles_panel_view_full_page_after_summary),
            ),
        )
    }
}
// KMK <--
