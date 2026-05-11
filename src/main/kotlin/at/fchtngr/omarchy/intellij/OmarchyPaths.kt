package at.fchtngr.omarchy.intellij

import java.nio.file.Path
import java.nio.file.Paths

object OmarchyPaths {
    val baseDir: Path = Paths.get(System.getProperty("user.home"), ".config", "omarchy-intellij")
    val manifestJson: Path = baseDir.resolve("manifest.json")
    val themeJson: Path = baseDir.resolve("theme.json")
    val schemeXml: Path = baseDir.resolve("omarchy.xml")
    val refreshToken: Path = baseDir.resolve("refresh.token")
}
