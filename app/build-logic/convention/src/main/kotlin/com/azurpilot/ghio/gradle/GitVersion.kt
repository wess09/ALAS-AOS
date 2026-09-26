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
private const val VERSION_BASE = "1.2"

/**
 * X 从版本方案切换的那个提交起算：该提交是本仓第 134 个（X=0 → 1.2.0），
 * 此后每个提交 +1。
 *
 * X counts commits from the commit that switched the version scheme — that
 * commit is #134 in this repository (X=0 → 1.2.0), and each later commit
 * increments X by one.
 */
private const val VERSION_SCHEME_START_COUNT = 134L

/** 版本名 = `1.2.<X>`；运行时版本由 rootfs 清单独立记录。 / Version name = `1.2.<X>`; the runtime version is tracked independently by the rootfs manifest. */
internal fun Project.gitVersionName(): String {
    System.getenv("APP_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val commits = providers.exec {
        workingDir(versionGitWorkingDir())
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toLongOrNull() ?: 0L
    val x = (commits - VERSION_SCHEME_START_COUNT).coerceAtLeast(0L)

    return "$VERSION_BASE.$x"
}
