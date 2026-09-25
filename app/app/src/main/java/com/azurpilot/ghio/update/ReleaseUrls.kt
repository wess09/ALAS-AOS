package com.azurpilot.ghio.update

/** Only the project's fixed GitHub release may be routed through the selected mirror. */
internal object ReleaseUrls {
    const val BASE = "https://github.com/wess09/AzurPilot-for-Android/releases/download/azurpilot-android-latest/"
    const val INDEX = "${BASE}latest.json"
    private const val MIRROR = "https://ghproxy.net/"

    fun selected(url: String, useMirror: Boolean): String {
        require(url.startsWith(BASE)) { "Invalid release URL" }
        return if (useMirror) "$MIRROR$url" else url
    }
}
