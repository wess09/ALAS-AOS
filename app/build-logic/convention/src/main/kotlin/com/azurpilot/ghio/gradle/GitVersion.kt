package com.azurpilot.ghio.gradle

import org.gradle.api.Project
import java.io.File

/**
 * A standalone checkout versions itself. When this checkout is a submodule, walk through any
 * nested superprojects and version from the outermost repository instead.
 */
private fun Project.versionGitWorkingDir(): File {
    var workingDir = rootProject.projectDir
    while (true) {
        val superproject = providers.exec {
            workingDir(workingDir)
            commandLine("git", "rev-parse", "--show-superproject-working-tree")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
        if (superproject.isEmpty()) return workingDir
        workingDir = File(superproject)
    }
}

private fun Project.gitCommitTime(workingDir: File, rev: String): Long = providers.exec {
    workingDir(workingDir)
    commandLine("git", "log", "-1", "--format=%ct", rev)
}.standardOutput.asText.get().trim().toLong()

/**
 * APK 版本号只取宿主 HEAD 的提交时间。AP 独立更新不会改变 APK 版本。
 *
 * APP_VERSION_CODE 仍是最高优先级的逃生口（发布事故时可手工钉版）。
 */
internal fun Project.gitVersionCode(): Int {
    System.getenv("APP_VERSION_CODE")?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return it.toInt().also { code -> require(code > 0) { "APP_VERSION_CODE must be positive" } }
    }
    val own = gitCommitTime(versionGitWorkingDir(), "HEAD")
    return own.toInt()
}

/** 版本名的主干；外壳自身的语义版本线，与内置运行时无关 / The version-name stem; the shell's own semver line, independent of the bundled runtime. */
private const val VERSION_BASE = "1.1"

/**
 * X 的提交基线：注释完善与版本升位两个提交之前的那一个（193e386，计 131）。
 * X 钉在基线计数、不随后续提交增长；下次升位时同时更新这两个常量。
 *
 * The commit baseline for X: the commit before the KDoc pass and this bump
 * (193e386, 131 commits). X is pinned to the baseline count and does not grow
 * with later commits; bump both constants together on the next minor bump.
 */
private const val VERSION_BASELINE_REV = "193e386892b89a3bd4bdc86ddfab0da69ac7390b"

/** 版本名 = `1.1.<基线提交数>`；运行时版本由 rootfs 清单独立记录。 / Version name = `1.1.<baseline commit count>`; the runtime version is tracked independently by the rootfs manifest. */
internal fun Project.gitVersionName(): String {
    System.getenv("APP_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val commits = providers.exec {
        workingDir(versionGitWorkingDir())
        commandLine("git", "rev-list", "--count", VERSION_BASELINE_REV)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "0" }

    return "$VERSION_BASE.$commits"
}
