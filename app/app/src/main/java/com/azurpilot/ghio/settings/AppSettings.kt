package com.azurpilot.ghio.settings

import com.aliothmoon.preferences.PrefKey
import com.aliothmoon.preferences.PrefSchema

/** Shizuku 官方包名，manifest 的 `<queries>` 里声明的就是它 */
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/**
 * app 级设置，与 `UserConfiguration` 分开存
 *
 * `UserConfiguration` 是运行配置的聚合根，走 schemaVersion 信封 + 版本不符即重置；
 * 提权后端这类设置不该跟着运行配置一起被重置，所以另起一个 Preferences DataStore
 *
 * 字段一律声明成 String：`@PrefSchema` 生成的 key 按字段类型选 preferencesKey，
 * 枚举与布尔都以文本落盘，改默认值不会让老数据变成非法值（见 [AppSettingsManager] 的解析）
 */
@PrefSchema
data class AppSettings(
    /** [com.azurpilot.ghio.domain.RemoteBackend] 的 name */
    @PrefKey(default = "SHIZUKU")
    val startupBackend: String = "SHIZUKU",

    /** 用户在引导弹窗上点过「不再提醒」 */
    @PrefKey(default = "false")
    val skipShizukuCheck: String = "false",

    /** Shizuku 管理器的包名；有 ROM 内置了自己的分发，允许指到别处 */
    @PrefKey(default = SHIZUKU_PACKAGE)
    val shizukuLaunchPackage: String = SHIZUKU_PACKAGE,

    /** [com.azurpilot.ghio.domain.RunMode] 的 name */
    @PrefKey(default = "BACKGROUND")
    val runMode: String = "BACKGROUND",

    /** [com.azurpilot.ghio.domain.OverlayControlMode] 的 name；仅前台模式生效 */
    @PrefKey(default = "FLOAT_BALL")
    val overlayControlMode: String = "FLOAT_BALL",

    /** 后台模式运行期是否自动盖上屏保；默认关，盖住整块屏幕这种事要用户先点头 */
    @PrefKey(default = "false")
    val screenSaverEnabled: String = "false",

    /** 冷启动自动清理过期日志（AzurPilot 7 天前日志、过期 session.log 截尾）；默认开 */
    @PrefKey(default = "true")
    val autoCleanLogs: String = "true",

)
