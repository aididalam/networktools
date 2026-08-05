package com.nexdevlab.networktools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexdevlab.networktools.data.SpeedTestPolicyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeedTestPolicyStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: SpeedTestPolicyStore

    @Before fun setUp() {
        context.getSharedPreferences(SpeedTestPolicyStore.PreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        store = SpeedTestPolicyStore(context)
    }
    @After fun tearDown() {
        context.getSharedPreferences(SpeedTestPolicyStore.PreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun consentIsVersionedAndExplicit() {
        assertFalse(store.hasCurrentConsent())
        store.acceptConsent()
        assertTrue(store.hasCurrentConsent())
    }

    @Test fun appRateLimitStopsAtTwentyStartsInRollingDay() {
        val now = 2_000_000_000L
        repeat(20) { assertTrue(store.recordStart(now + it)) }
        assertFalse(store.recordStart(now + 21))
        assertEquals(0, store.remainingTests(now + 22))
        assertEquals(20, store.remainingTests(now + 24L * 60L * 60L * 1_000L + 22))
    }
}
