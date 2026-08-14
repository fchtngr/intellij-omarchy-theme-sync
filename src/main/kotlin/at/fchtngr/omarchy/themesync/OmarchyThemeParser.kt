package at.fchtngr.omarchy.themesync

import kotlinx.serialization.json.Json

object OmarchyThemeParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseManifest(raw: String): OmarchyThemePayload = json.decodeFromString(raw)
}
