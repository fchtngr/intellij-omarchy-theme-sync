package at.fchtngr.omarchy.intellij

import kotlinx.serialization.Serializable

@Serializable
data class OmarchyThemePayload(
    val schemaVersion: Int,
    val name: String,
    val generatedAt: String,
    val dark: Boolean,
    val themeFile: String = "theme.json",
    val schemeFile: String = "omarchy.xml"
)
