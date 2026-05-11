package at.fchtngr.omarchy.intellij

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.nio.file.Files

class OmarchyStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val logger = thisLogger()
        val themeExists = Files.exists(OmarchyPaths.themeJson)
        val hookInstalled = OmarchyHookInstaller.ensureInstalled()
        OmarchyHookInstaller.runSync("startup")
        logger.info("Omarchy Theme Sync startup: watcher path=${OmarchyPaths.baseDir}, themeExists=$themeExists, hookInstalled=$hookInstalled")
        OmarchyNotifications.info(
            "Omarchy Theme Sync loaded",
            if (hookInstalled) "Omarchy hook installed; watcher started for ${OmarchyPaths.baseDir}" else "Watcher started for ${OmarchyPaths.baseDir}"
        )
        OmarchyThemeWatcher.ensureStarted()
        OmarchyThemeRefresher.refresh("startup")
    }
}
