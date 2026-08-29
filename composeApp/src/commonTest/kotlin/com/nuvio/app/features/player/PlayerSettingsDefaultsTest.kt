package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSettingsDefaultsTest {
    @Test
    fun defaultUiStatePrefersSpanishSubtitlesAndSixSecondAutoplay() {
        val state = PlayerSettingsUiState()
        assertEquals("es", state.preferredSubtitleLanguage)
        assertEquals(6, state.streamAutoPlayTimeoutSeconds)
    }
}
