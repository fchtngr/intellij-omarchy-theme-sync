package at.fchtngr.omarchy.themesync

import java.nio.file.Path

object OmarchyPaths {
    val baseDir: Path = Path.of(System.getProperty("user.home"), ".config", "omarchy-intellij")
    val themeJson: Path = baseDir.resolve("theme.json")
    val schemeXml: Path = baseDir.resolve("omarchy.xml")
    val refreshToken: Path = baseDir.resolve("refresh.token")
}
