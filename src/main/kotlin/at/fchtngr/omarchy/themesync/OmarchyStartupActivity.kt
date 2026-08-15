package at.fchtngr.omarchy.themesync

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class OmarchyStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        OmarchyThemeWatcher.ensureStarted()
        OmarchyThemeRefresher.refresh("startup")
    }
}
