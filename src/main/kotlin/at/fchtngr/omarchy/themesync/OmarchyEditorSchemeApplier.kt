package at.fchtngr.omarchy.themesync

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import org.jdom.input.SAXBuilder

object OmarchyEditorSchemeApplier {
    private val logger = thisLogger()

    fun apply() {
        val manager = EditorColorsManager.getInstance()
        val baseScheme = manager.schemeForCurrentUITheme
        val scheme = EditorColorsSchemeImpl(baseScheme)
        val root = SAXBuilder().build(OmarchyPaths.schemeXml.toFile()).rootElement
        scheme.readExternal(root)
        manager.addColorScheme(scheme)
        manager.setGlobalScheme(scheme)
        logger.info("Applied Omarchy editor scheme: ${scheme.name}")
    }
}
