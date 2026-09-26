package com.azurpilot.ghio.proot

import android.content.Context

/**
 * 界面选择的持久化（当前实例名）
 *
 * 与 `AzurPilotRunController` 用的 `azurpilot_android` 分开存：那份是「跑哪个配置」的
 * 进程控制选择，这份是「看哪个实例」的浏览选择，两者会不同（比如看着 A 却让 B 在跑）。
 */
class AzurPilotPreferences(context: Context) : AzurPilotPreferenceStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var selectedInstance: String?
        get() = prefs.getString(KEY_INSTANCE, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_INSTANCE) else putString(KEY_INSTANCE, value)
            }.apply()
        }

    private companion object {
        const val PREFS = "azurpilot_browse"
        const val KEY_INSTANCE = "selected_instance"
    }
}
