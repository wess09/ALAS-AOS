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
 * 内置运行时的上游 commit（短 SHA 或全 SHA），由构建环境注入。
 * CI 烘 rootfs 时钉的就是这个 commit；本地不设则版本名里不带这一段。
 */
private fun runtimeCommit(): String =
    System.getenv("APP_RUNTIME_COMMIT").orEmpty().trim()

/**
 * 内置运行时那个上游 commit 的提交时间（秒），由构建环境注入。
 * 取不到时按 0 处理，等价于「只看本仓提交时间」。
 */
private fun runtimeCommitTime(): Long =
    System.getenv("APP_RUNTIME_COMMIT_TIME").orEmpty().trim().toLongOrNull() ?: 0L

/**
 * 版本号 = max(本仓 HEAD 提交时间, 内置运行时的上游提交时间)
 *
 * 两个输入都是**提交时间**，与构建时刻无关：同一份（本仓代码 + 上游 commit）无论在哪台机器、
 * 哪个时刻构建，永远得到同一个 versionCode；任一输入前进则单调递增。
 * 旧实现里 CI 用 `date +%s` 覆盖，导致同一份代码每次构建版本号都不同、且与本地不一致。
 *
 * APP_VERSION_CODE 仍是最高优先级的逃生口（发布事故时可手工钉版）。
 */
internal fun Project.gitVersionCode(): Int {
    System.getenv("APP_VERSION_CODE")?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return it.toInt().also { code -> require(code > 0) { "APP_VERSION_CODE must be positive" } }
    }
    val own = gitCommitTime(versionGitWorkingDir(), "HEAD")
    return maxOf(own, runtimeCommitTime()).toInt()
}

/** 版本名的主干；外壳自身的语义版本线，与内置运行时无关 */
private const val VERSION_BASE = "1.0"

/**
 * 版本名 = `1.0.<提交数>[.<内置运行时的上游短 SHA>]`
 *
 * 第三段取本仓的提交数：随每次提交自动单调递增，无需人工改文件，且同一份代码在任何机器上
 * 都得到同一个值（可复现）。第四段是烘进 APK 的那个上游 commit 的短 SHA，由构建环境注入；
 * 本地构建没有运行时注入时省略该段。
 *
 * 于是「这一版的壳 + 里面的运行时」由版本名唯一确定：`1.0.83.1841cb1941`。
 *
 * APP_VERSION_NAME 仍是最高优先级的逃生口。
 */
internal fun Project.gitVersionName(): String {
    System.getenv("APP_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val commits = providers.exec {
        workingDir(versionGitWorkingDir())
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "0" }

    val runtime = runtimeCommit()
    return if (runtime.isEmpty()) "$VERSION_BASE.$commits"
    else "$VERSION_BASE.$commits.${runtime.take(10)}"
}
