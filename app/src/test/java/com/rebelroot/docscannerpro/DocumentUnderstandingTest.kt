package com.rebelroot.docscannerpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.core.ocr.DocumentUnderstanding
import com.rebelroot.docscannerpro.core.security.VaultManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentUnderstandingTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Doc Scanner Pro", appName)
    }

    @Test
    fun `receipt understanding extracts total and merchant`() {
        val sampleReceiptText = """
            COFFEE BEAN & TEA LEAF
            123 Market St, Suite 100
            09/04/2026 10:30 AM

            1 Latte            $5.50
            1 Croissant        $4.00

            Subtotal           $9.50
            Tax                $0.85
            Total             $10.35

            Thank you for visiting!
        """.trimIndent()

        val receipt = DocumentUnderstanding.extractReceipt(sampleReceiptText)
        assertEquals("COFFEE BEAN & TEA LEAF", receipt.merchant)
        assertNotNull(receipt.total)
        assertTrue(receipt.total?.contains("10.35") == true)
        assertTrue(receipt.tax?.contains("0.85") == true)
    }

    @Test
    fun `business card understanding extracts contact details`() {
        val sampleCard = """
            Alex Morgan
            Lead Systems Architect
            Apex Robotics Inc
            alex.morgan@apexrobotics.io
            (555) 234-5678
            https://apexrobotics.io
        """.trimIndent()

        val contact = DocumentUnderstanding.extractContact(sampleCard)
        assertEquals("alex.morgan@apexrobotics.io", contact.email)
        assertEquals("(555) 234-5678", contact.phone)
        assertEquals("Alex Morgan", contact.name)
    }

    @Test
    fun `vault manager PIN verification and lock state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = VaultManager(context)


        assertTrue(vault.setPin("1234"))
        assertTrue(vault.isPinSet())
        assertTrue(vault.isUnlocked.value)


        vault.lock()
        assertFalse(vault.isUnlocked.value)


        assertFalse(vault.verifyPin("9999"))
        assertFalse(vault.isUnlocked.value)


        assertTrue(vault.verifyPin("1234"))
        assertTrue(vault.isUnlocked.value)
    }
}
