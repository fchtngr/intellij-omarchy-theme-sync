package at.fchtngr.omarchy.intellij

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import org.jdom.input.SAXBuilder
import java.io.StringReader
import java.nio.file.Files

object OmarchyEditorSchemeApplier {
    private val logger = thisLogger()

    fun apply(manifest: OmarchyThemePayload) {
        val manager = EditorColorsManager.getInstance()
        val baseScheme = manager.globalScheme
        val scheme = EditorColorsSchemeImpl(baseScheme)
        val root = SAXBuilder().build(StringReader(Files.readString(OmarchyPaths.schemeXml))).rootElement
        scheme.readExternal(root)
        manager.addColorScheme(scheme)
        manager.setGlobalScheme(scheme)
        logger.info("Applied Omarchy editor scheme: ${manifest.name}")
    }
}
