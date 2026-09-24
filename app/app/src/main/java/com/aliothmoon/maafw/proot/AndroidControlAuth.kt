package com.aliothmoon.maafw.proot

import android.content.Context
import java.util.UUID

/** App 私有的本机控制口令，WebUI 的普通会话不使用。 */
object AndroidControlAuth {
    private const val PREFS = "azurpilot_android_control"
    private const val KEY = "token"

    @Synchronized
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).commit() }
    }
}
