package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleRequestExtrasTest {
    @Test
    fun omitsExtraPathWhenHintsAreMissing() {
        assertNull(buildSubtitleExtraPathSegment())
        assertNull(buildSubtitleExtraPathSegment(videoHash = "  ", videoSize = 0L, filename = ""))
    }

    @Test
    fun buildsVideoHashSizeAndFilenameSegment() {
        assertEquals(
            "https://addon.example/subtitles/movie/tt123/videoSize=10&filename=a.mkv.json",
            com.nuvio.app.features.addons.buildAddonResourceUrl(
                manifestUrl = "https://addon.example/manifest.json",
                resource = "subtitles",
                type = "movie",
                id = "tt123",
                extraPathSegment = buildSubtitleExtraPathSegment(videoSize = 10L, filename = "a.mkv"),
            ),
        )
    }
}
