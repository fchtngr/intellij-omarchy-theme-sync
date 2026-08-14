package at.fchtngr.omarchy.themesync

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.util.JDOMUtil

object OmarchyEditorSchemeApplier {
    private val logger = thisLogger()

    fun apply() {
        val manager = EditorColorsManager.getInstance()
        val baseScheme = manager.schemeForCurrentUITheme
        val scheme = EditorColorsSchemeImpl(baseScheme)
        val root = JDOMUtil.load(OmarchyPaths.schemeXml)
        scheme.readExternal(root)
        manager.addColorScheme(scheme)
        manager.setGlobalScheme(scheme)
        logger.info("Applied Omarchy editor scheme: ${scheme.name}")
    }
}
