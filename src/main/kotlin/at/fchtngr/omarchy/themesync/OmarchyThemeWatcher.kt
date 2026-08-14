package at.fchtngr.omarchy.themesync

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
                    val shouldRefresh = key.pollEvents().any {
                        it.context()?.toString() == OmarchyPaths.refreshToken.fileName.toString()
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
