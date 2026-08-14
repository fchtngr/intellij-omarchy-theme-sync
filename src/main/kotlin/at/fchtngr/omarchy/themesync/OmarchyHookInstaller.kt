package at.fchtngr.omarchy.themesync

import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

object OmarchyHookInstaller {
    private val logger = thisLogger()
    private val installDir: Path = Path.of(System.getProperty("user.home"), ".local", "share", "omarchy-intellij")
    private val syncScript: Path = installDir.resolve("omarchy-intellij-theme-sync.py")
    private val hookPath: Path = Path.of(
        System.getProperty("user.home"),
        ".config", "omarchy", "hooks", "theme-set.d", "omarchy-intellij-theme-sync",
    )

    fun ensureInstalled() {
        runCatching {
            installSyncScript()
            installHook()
        }.onFailure { logger.warn("Failed installing Omarchy IntelliJ hook", it) }
    }

    fun runSync() {
        runCatching {
            if (!Files.isExecutable(syncScript)) installSyncScript()
            val process = ProcessBuilder("python3", syncScript.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                logger.warn("Omarchy IntelliJ sync timed out")
                return
            }
            if (process.exitValue() == 0) {
                logger.info("Omarchy IntelliJ sync completed: $output")
            } else {
                logger.info("Omarchy IntelliJ sync skipped/failed (exit=${process.exitValue()}): $output")
            }
        }.onFailure { logger.info("Could not run Omarchy IntelliJ sync: ${it.message}") }
    }

    private fun installSyncScript() {
        Files.createDirectories(installDir)
        val resource = OmarchyHookInstaller::class.java.getResourceAsStream("/scripts/omarchy-intellij-theme-sync.py")
            ?: error("Missing bundled sync script")
        resource.use { input ->
            Files.copy(input, syncScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        syncScript.toFile().setExecutable(true, false)
    }

    private fun installHook() {
        Files.createDirectories(hookPath.parent)
        val body = "#!/bin/bash\n\"$syncScript\" \"\$@\"\n"
        Files.writeString(hookPath, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        hookPath.toFile().setExecutable(true, false)
    }
}
