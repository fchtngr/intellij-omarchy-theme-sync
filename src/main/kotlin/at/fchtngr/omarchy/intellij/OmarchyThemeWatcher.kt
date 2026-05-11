package at.fchtngr.omarchy.intellij

import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object OmarchyThemeWatcher {
    private val started = AtomicBoolean(false)

    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return

        thread(name = "omarchy-theme-watcher", isDaemon = true) {
            val logger = thisLogger()
            try {
                OmarchyPaths.baseDir.toFile().mkdirs()
                val watchService = FileSystems.getDefault().newWatchService()
                OmarchyPaths.baseDir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )
                logger.info("Omarchy watcher started for ${OmarchyPaths.baseDir}")
                while (true) {
                    val key = watchService.take()
                    var shouldRefresh = false
                    for (event in key.pollEvents()) {
                        val name = event.context()?.toString() ?: continue
                        if (
                            name == OmarchyPaths.manifestJson.fileName.toString() ||
                            name == OmarchyPaths.themeJson.fileName.toString() ||
                            name == OmarchyPaths.schemeXml.fileName.toString() ||
                            name == OmarchyPaths.refreshToken.fileName.toString()
                        ) {
                            shouldRefresh = true
                        }
                    }
                    key.reset()
                    if (shouldRefresh) {
                        OmarchyThemeRefresher.refresh("file-watch")
                    }
                }
            } catch (t: Throwable) {
                logger.warn("Omarchy watcher failed", t)
            }
        }
    }
}
