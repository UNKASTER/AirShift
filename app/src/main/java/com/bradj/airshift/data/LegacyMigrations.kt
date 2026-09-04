package com.bradj.airshift.data

import android.content.Context
import androidx.core.content.edit

/**
 * 一次性清理早期版本遗留的存储项。
 *
 * 只在 [MainActivity] 启动时执行一次，用 `migration_version` 记录已完成的版本；
 * 此前这段清理写在 [RosterStore] 与 [VariFlightApiKeyStore] 的构造里，
 * 导致每次小组件重绘、每条 MUC 通知都要走一遍 Keystore IPC 和 SharedPreferences 写入。
 */
internal object LegacyMigrations {
    private const val KEY_MIGRATION_VERSION = "migration_version"
    private const val CURRENT_VERSION = 1

    private const val KEY_LEGACY_SUPPLEMENT = "roster_supplement"
    private const val KEY_LEGACY_GATEWAY_URL = "gateway_url"

    fun runOnce(context: Context) {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(RosterStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_MIGRATION_VERSION, 0) >= CURRENT_VERSION) return
        preferences.edit {
            remove(KEY_LEGACY_SUPPLEMENT)
            remove(KEY_LEGACY_GATEWAY_URL)
        }
        VariFlightApiKeyStore.clearLegacyGatewayCredential(appContext)
        preferences.edit { putInt(KEY_MIGRATION_VERSION, CURRENT_VERSION) }
    }

    /** 仅供测试读取。 */
    internal fun completedVersion(context: Context): Int = context.applicationContext
        .getSharedPreferences(RosterStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_MIGRATION_VERSION, 0)
}
