package at.fchtngr.omarchy.intellij

import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

object OmarchyHookInstaller {
    private val logger = thisLogger()
    private const val START_MARKER = "# >>> omarchy-intellij-plugin >>>"
    private const val END_MARKER = "# <<< omarchy-intellij-plugin <<<"
    private val installDir: Path = Paths.get(System.getProperty("user.home"), ".local", "share", "omarchy-intellij")
    private val syncScript: Path = installDir.resolve("omarchy-intellij-theme-sync.py")
    private val hookPath: Path = Paths.get(System.getProperty("user.home"), ".config", "omarchy", "hooks", "theme-set")

    fun ensureInstalled(): Boolean {
        return runCatching {
            installSyncScript()
            installHook()
            true
        }.onFailure { logger.warn("Failed installing Omarchy IntelliJ hook", it) }.getOrDefault(false)
    }

    fun runSync(reason: String) {
        runCatching {
            if (!Files.isExecutable(syncScript)) installSyncScript()
            val process = ProcessBuilder("python3", syncScript.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                logger.warn("Omarchy IntelliJ sync timed out ($reason)")
                return
            }
            if (process.exitValue() == 0) {
                logger.info("Omarchy IntelliJ sync completed ($reason): $output")
            } else {
                logger.info("Omarchy IntelliJ sync skipped/failed ($reason, exit=${process.exitValue()}): $output")
            }
        }.onFailure { logger.info("Could not run Omarchy IntelliJ sync ($reason): ${it.message}") }
    }

    private fun installSyncScript() {
        Files.createDirectories(installDir)
        val resource = OmarchyHookInstaller::class.java.getResourceAsStream("/bin/omarchy-intellij-theme-sync.py")
            ?: error("Missing bundled sync script")
        resource.use { input ->
            Files.copy(input, syncScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        syncScript.toFile().setExecutable(true, false)
    }

    private fun installHook() {
        Files.createDirectories(hookPath.parent)
        val body = "\"$syncScript\" \"\$@\""
        val block = "$START_MARKER\n$body\n$END_MARKER"
        val existing = if (Files.exists(hookPath)) Files.readString(hookPath) else "#!/usr/bin/env bash\nset -euo pipefail\n"
        val withShebang = if (existing.startsWith("#!/usr/bin/env bash")) existing else "#!/usr/bin/env bash\nset -euo pipefail\n$existing"
        val updated = if (withShebang.contains(START_MARKER) && withShebang.contains(END_MARKER)) {
            val before = withShebang.substringBefore(START_MARKER).trimEnd()
            val after = withShebang.substringAfter(END_MARKER)
            "$before\n\n$block$after"
        } else {
            withShebang.trimEnd() + "\n\n" + block + "\n"
        }
        Files.writeString(hookPath, updated, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        hookPath.toFile().setExecutable(true, false)
    }
}
