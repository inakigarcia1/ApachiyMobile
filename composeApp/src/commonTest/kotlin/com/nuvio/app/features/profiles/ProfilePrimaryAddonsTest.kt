package com.nuvio.app.features.profiles

import kotlin.test.Test
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
}
