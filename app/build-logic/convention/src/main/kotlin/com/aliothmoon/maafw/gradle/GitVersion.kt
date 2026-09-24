package com.aliothmoon.maafw.gradle

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

/** Unix commit time is monotonic across normal builds and leaves room for AP-triggered APK builds. */
internal fun Project.gitVersionCode(): Int {
    System.getenv("APP_VERSION_CODE")?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return it.toInt().also { code -> require(code > 0) { "APP_VERSION_CODE must be positive" } }
    }
    val gitWorkingDir = versionGitWorkingDir()
    return providers.exec {
        workingDir(gitWorkingDir)
        commandLine("git", "log", "-1", "--format=%ct", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}

/**
 * A tag on HEAD gives x.y.z; a tag further back bumps patch by one and appends alpha.<distance>
 * A describe output that does not match degrades to itself instead of blocking the build
 */
internal fun Project.gitVersionName(): String {
    System.getenv("APP_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val gitWorkingDir = versionGitWorkingDir()
    val desc = providers.exec {
        workingDir(gitWorkingDir)
        commandLine("git", "describe", "--tags", "--always")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
    val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(\d+)-g[0-9a-f]+)?$""").matchEntire(desc)
        ?: return desc.removePrefix("v").ifEmpty { "0.0.0-dev" }
    val (major, minor, patch, distance) = match.destructured
    return if (distance.isEmpty()) "$major.$minor.$patch"
    else "$major.$minor.${patch.toInt() + 1}-alpha.$distance"
}
