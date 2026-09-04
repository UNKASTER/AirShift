package com.bradj.airshift.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LegacyMigrationsInstrumentedTest {
    private lateinit var targetContext: Context
    private lateinit var isolatedContext: Context
    private val isolatedPreferenceNames = mutableSetOf<String>()

    @Before
    fun isolatePreferences() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "legacy_migrations_test_${UUID.randomUUID()}"
        isolatedContext = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolatedName = "${prefix}_$name"
                isolatedPreferenceNames += isolatedName
                return super.getSharedPreferences(isolatedName, mode)
            }
        }
    }

    @After
    fun removeOnlyIsolatedTestPreferences() {
        isolatedPreferenceNames.forEach { name ->
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            targetContext.deleteSharedPreferences(name)
        }
    }

    private fun roster(): SharedPreferences = isolatedContext.getSharedPreferences("air_shift", Context.MODE_PRIVATE)

    private fun secrets(): SharedPreferences =
        isolatedContext.getSharedPreferences("air_shift_secrets", Context.MODE_PRIVATE)

    @Test
    fun legacyKeysAreRemovedAndTheCompletedVersionIsRecorded() {
        roster().edit().putString("roster_supplement", "x").putString("gateway_url", "https://old").commit()
        secrets().edit().putString("gateway_token_iv", "iv").putString("gateway_token_ciphertext", "ct").commit()

        LegacyMigrations.runOnce(isolatedContext)

        assertFalse(roster().contains("roster_supplement"))
        assertFalse(roster().contains("gateway_url"))
        assertFalse(secrets().contains("gateway_token_iv"))
        assertFalse(secrets().contains("gateway_token_ciphertext"))
        assertEquals(1, LegacyMigrations.completedVersion(isolatedContext))
    }

    @Test
    fun aCompletedMigrationDoesNotRunAgain() {
        LegacyMigrations.runOnce(isolatedContext)
        roster().edit().putString("gateway_url", "https://again").commit()

        LegacyMigrations.runOnce(isolatedContext)

        assertTrue(roster().contains("gateway_url"))
    }

    @Test
    fun constructingTheStoreNoLongerTouchesLegacyKeys() {
        // 清理只在启动时跑一次；小组件重绘、MUC 通知等路径构造 RosterStore 不得再做 Keystore/偏好写入。
        roster().edit().putString("gateway_url", "https://old").commit()

        RosterStore(isolatedContext).loadSnapshot()

        assertTrue(roster().contains("gateway_url"))
        assertEquals(0, LegacyMigrations.completedVersion(isolatedContext))
    }
}
