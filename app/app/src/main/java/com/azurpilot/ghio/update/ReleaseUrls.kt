package com.azurpilot.ghio.update

/**
 * Release 资源地址与下载源解析。只有本项目的固定 GitHub Release 允许走镜像。
 *
 * 镜像均为 ghproxy 形态：`前缀 + 完整 GitHub URL`（如 `https://ghproxy.net/https://github.com/…`）。
 * 镜像站时效性强，内置列表只是当前可用的常用快照；某个站挂了或太慢时，用户可在设置里
 * 换一个或填自定义前缀（同样要求 ghproxy 形态）。
 */
internal object ReleaseUrls {
    const val BASE = "https://github.com/wess09/AzurPilot-for-Android/releases/download/azurpilot-android-latest/"
    const val INDEX = "${BASE}latest.json"

    /** 设置存储值：直连 GitHub */
    const val DIRECT = "direct"

    /** 设置存储值：自定义前缀（实际前缀取自定义输入） */
    const val CUSTOM = "custom"

    /**
     * 内置镜像前缀；列表项本身就是设置存储值。
     * 顺序即展示顺序：第一个（ghproxy.net）也是旧版布尔开关迁移的落点。
     */
    val MIRRORS: List<String> = listOf(
        "https://ghproxy.net/",
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://gh.ddlc.top/",
        "https://gh-proxy.net/",
    )

    /** 自定义前缀规范化：去空白、补尾斜杠；非法输入一律回落直连，不让解析环节抛异常 */
    fun normalizeCustom(raw: String): String =
        raw.trim()
            .let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
            .takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: ""

    /** 由设置值算出实际前缀；direct 与非法值都返回空串（直连） */
    fun mirrorPrefix(id: String, customRaw: String): String = when (id) {
        DIRECT -> ""
        CUSTOM -> normalizeCustom(customRaw)
        else -> if (id in MIRRORS) id else ""
    }

    /** 给 Release 资源地址套上前缀；前缀为空即直连 */
    fun selected(url: String, prefix: String): String {
        require(url.startsWith(BASE)) { "Invalid release URL" }
        return if (prefix.isEmpty()) url else "$prefix$url"
    }

    /** 镜像选择器的展示名：去掉协议与尾斜杠，只剩主机名 */
    fun displayLabel(mirror: String): String =
        mirror.removePrefix("https://").removePrefix("http://").trimEnd('/')
}
