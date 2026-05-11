package at.fchtngr.omarchy.intellij

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files

object OmarchyUiThemeApplier {
    private val logger = thisLogger()

    fun apply(manifest: OmarchyThemePayload) {
        Files.newInputStream(OmarchyPaths.themeJson).use { input ->
            val theme = UITheme.Companion.loadTempThemeFromJson(input, manifest.name)
            val laf = UIThemeBasedLookAndFeelInfo(theme)
            val manager = LafManager.getInstance()
            manager.setCurrentUIThemeLookAndFeel(laf)
            manager.updateUI()
            logger.info("Applied Omarchy UI theme: ${manifest.name}")
        }
    }
}
