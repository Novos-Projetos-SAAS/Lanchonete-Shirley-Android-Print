package com.lanchoneteshirley.impressao

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanchoneteshirley.impressao.data.PendingReport
import com.lanchoneteshirley.impressao.data.PrintConfigStore
import com.lanchoneteshirley.impressao.security.SecureTokenStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    @Before fun clearBefore() {
        context.getSharedPreferences("print_config", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("print_secret", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun clearAfter() = clearBefore()

    @Test fun credentialAndLeaseAreEncryptedAndNeverCopiedToCommonPreferences() {
        val credential = "credential-plain-text-must-not-leak"
        val lease = "lease-plain-text-must-not-leak"
        val secrets = SecureTokenStore(context)
        val config = PrintConfigStore(context)

        secrets.save(credential)
        secrets.savePendingLease("job-42", lease)
        config.savePendingReport(PendingReport("job-42", "RESULTADO_INDETERMINADO", "rede", true))

        assertEquals(credential, secrets.load())
        assertEquals(lease, secrets.loadPendingLease("job-42"))
        val commonValues = context.getSharedPreferences("print_config", android.content.Context.MODE_PRIVATE).all.values.map(String::valueOf)
        val encryptedValues = context.getSharedPreferences("print_secret", android.content.Context.MODE_PRIVATE).all.values.map(String::valueOf)
        assertTrue(commonValues.none { it.contains(credential) || it.contains(lease) })
        assertTrue(encryptedValues.none { it == credential || it == lease || it.contains(credential) || it.contains(lease) })
    }

    @Test fun requestedShiftDoesNotPretendTheServiceIsRunning() {
        val config = PrintConfigStore(context)
        config.setShiftRequested(true)
        config.setRuntime(false, "INICIANDO")
        assertTrue(config.isShiftRequested())
        assertFalse(config.isShiftActive())
        assertEquals("INICIANDO", config.runtimeStatus())
    }
}
