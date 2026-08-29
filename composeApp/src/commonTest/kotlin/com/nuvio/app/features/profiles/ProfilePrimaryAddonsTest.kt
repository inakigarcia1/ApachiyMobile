package com.nuvio.app.features.profiles

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfilePrimaryAddonsTest {
    @Test
    fun newProfilesDefaultToPrimaryAddons() {
        assertTrue(NuvioProfile().usesPrimaryAddons)
        assertTrue(
            ProfilePushPayload(
                profileIndex = 2,
                name = "Kids",
                avatarColorHex = "#000000",
            ).usesPrimaryAddons,
        )
    }

    @Test
    fun pullDecodesNumericAndStringProfileIds() {
        val json = Json { ignoreUnknownKeys = true }
        val numeric = json.decodeFromString<NuvioProfile>(
            """{"id":2,"user_id":"f73a607e-5f91-478a-9c11-aaaaaaaaaaaa","profile_index":1,"name":"Casa"}""",
        )
        val quoted = json.decodeFromString<NuvioProfile>(
            """{"id":"2","user_id":"f73a607e-5f91-478a-9c11-aaaaaaaaaaaa","profile_index":1,"name":"Casa"}""",
        )
        assertEquals("2", numeric.id)
        assertEquals("2", quoted.id)
        assertEquals(1, numeric.profileIndex)
    }
}
