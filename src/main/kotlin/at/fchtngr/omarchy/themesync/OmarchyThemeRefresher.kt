package at.fchtngr.omarchy.themesync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files

object OmarchyThemeRefresher {
    private val logger = thisLogger()
    private var lastAppliedStamp: String? = null

    fun refresh(reason: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!Files.exists(OmarchyPaths.themeJson) || !Files.exists(OmarchyPaths.schemeXml) || !Files.exists(OmarchyPaths.refreshToken)) {
                logger.info("Omarchy refresh skipped ($reason): runtime files missing in ${OmarchyPaths.baseDir}")
                OmarchyNotifications.warning(
                    "Omarchy theme missing",
                    "Expected theme.json, omarchy.xml, and refresh.token under ${OmarchyPaths.baseDir}"
                )
                return@invokeLater
            }

            val stamp = runCatching { Files.readString(OmarchyPaths.refreshToken).trim() }
                .onFailure { logger.warn("Failed reading Omarchy refresh token", it) }
                .getOrNull() ?: return@invokeLater

            val firstLoad = lastAppliedStamp == null
            if (lastAppliedStamp == stamp) {
                logger.info("Omarchy refresh skipped ($reason): payload already applied at $stamp")
                return@invokeLater
            }

            logger.info("Omarchy refresh triggered by $reason")

            val themeName = runCatching {
                val name = OmarchyUiThemeApplier.apply()
                OmarchyEditorSchemeApplier.apply()
                lastAppliedStamp = stamp
                name
            }.getOrElse {
                logger.warn("Failed applying Omarchy theme", it)
                OmarchyNotifications.warning(
                    "Omarchy apply failed",
                    "Failed applying Omarchy theme: ${it.message ?: it::class.simpleName.orEmpty()}"
                )
                return@invokeLater
            }

            OmarchyNotifications.info(
                if (firstLoad) "Omarchy theme loaded" else "Omarchy theme refreshed",
                "Applied $themeName from ${OmarchyPaths.baseDir.fileName} ($reason)"
            )
        }
    }
}
