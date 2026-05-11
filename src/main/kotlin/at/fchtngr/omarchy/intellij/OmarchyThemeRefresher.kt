package at.fchtngr.omarchy.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

object OmarchyThemeRefresher {
    private val logger = thisLogger()
    private val lastAppliedStamp = AtomicReference<String?>(null)

    fun refresh(reason: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!Files.exists(OmarchyPaths.manifestJson) || !Files.exists(OmarchyPaths.themeJson) || !Files.exists(OmarchyPaths.schemeXml)) {
                logger.info("Omarchy refresh skipped ($reason): runtime files missing in ${OmarchyPaths.baseDir}")
                OmarchyNotifications.warning(
                    "Omarchy theme missing",
                    "Expected manifest.json, theme.json, and omarchy.xml under ${OmarchyPaths.baseDir}"
                )
                return@invokeLater
            }

            val rawManifest = runCatching { Files.readString(OmarchyPaths.manifestJson) }
                .onFailure { logger.warn("Failed reading Omarchy manifest", it) }
                .getOrNull() ?: return@invokeLater

            val manifest = runCatching { OmarchyThemeParser.parseManifest(rawManifest) }
                .onFailure { logger.warn("Failed parsing Omarchy manifest", it) }
                .getOrElse {
                    OmarchyNotifications.warning(
                        "Omarchy manifest parse failed",
                        "Could not parse ${OmarchyPaths.manifestJson.fileName}: ${it.message ?: it::class.simpleName.orEmpty()}"
                    )
                    return@invokeLater
                }

            val stamp = manifest.generatedAt.ifBlank { rawManifest.hashCode().toString() }
            val firstLoad = lastAppliedStamp.get() == null
            if (lastAppliedStamp.get() == stamp) {
                logger.info("Omarchy refresh skipped ($reason): payload already applied at $stamp")
                return@invokeLater
            }

            logger.info("Omarchy refresh triggered by $reason")
            logger.info("Parsed Omarchy manifest: ${manifest.name}, dark=${manifest.dark}, schema=${manifest.schemaVersion}")

            runCatching {
                OmarchyUiThemeApplier.apply(manifest)
                OmarchyEditorSchemeApplier.apply(manifest)
                lastAppliedStamp.set(stamp)
            }.onFailure {
                logger.warn("Failed applying Omarchy theme", it)
                OmarchyNotifications.warning(
                    "Omarchy apply failed",
                    "Failed applying ${manifest.name}: ${it.message ?: it::class.simpleName.orEmpty()}"
                )
                return@invokeLater
            }

            OmarchyNotifications.info(
                if (firstLoad) "Omarchy theme loaded" else "Omarchy theme refreshed",
                "Applied ${manifest.name} from ${OmarchyPaths.baseDir.fileName} ($reason)"
            )
        }
    }
}
