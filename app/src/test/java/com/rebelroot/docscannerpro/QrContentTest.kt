package com.rebelroot.docscannerpro

import com.rebelroot.docscannerpro.core.qr.QrContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrContentTest {

    @Test
    fun `parses wifi credentials`() {
        val content = QrContent.parse("WIFI:T:WPA;S:HomeNet;P:secret123;H:false;;")
        assertTrue(content is QrContent.Wifi)
        val wifi = content as QrContent.Wifi
        assertEquals("HomeNet", wifi.ssid)
        assertEquals("secret123", wifi.password)
        assertEquals("WPA", wifi.encryption)
    }

    @Test
    fun `parses wifi without password`() {
        val content = QrContent.parse("WIFI:T:nopass;S:FreeWifi;;")
        assertTrue(content is QrContent.Wifi)
        val wifi = content as QrContent.Wifi
        assertEquals("FreeWifi", wifi.ssid)
        assertEquals(null, wifi.password)
    }

    @Test
    fun `parses vcard with name org phone email`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            N:Lovelace;Ada;;;
            ORG:Analytical Engines Ltd
            TEL:+441234567890
            EMAIL:ada@example.com
            END:VCARD
        """.trimIndent()
        val content = QrContent.parse(vcard)
        assertTrue(content is QrContent.Contact)
        val contact = content as QrContent.Contact
        assertEquals("Ada Lovelace", contact.name)
        assertEquals("Analytical Engines Ltd", contact.org)
        assertEquals(listOf("+441234567890"), contact.phones)
        assertEquals(listOf("ada@example.com"), contact.emails)
    }

    @Test
    fun `parses mecard`() {
        val content = QrContent.parse("MECARD:N:Lee,Mina;ORG:Acme;TEL:5550100;EMAIL:mina@acme.io;;")
        assertTrue(content is QrContent.Contact)
        val contact = content as QrContent.Contact
        assertEquals("Lee Mina", contact.name)
        assertEquals("Acme", contact.org)
        assertEquals(listOf("5550100"), contact.phones)
    }

    @Test
    fun `parses tel url`() {
        val content = QrContent.parse("tel:+15551234567")
        assertEquals(QrContent.Phone("+15551234567"), content)
    }

    @Test
    fun `parses mailto with subject`() {
        val content = QrContent.parse("mailto:hi@example.org?subject=Hello&body=World")
        assertTrue(content is QrContent.Email)
        val email = content as QrContent.Email
        assertEquals("hi@example.org", email.address)
        assertEquals("Hello", email.subject)
        assertEquals("World", email.body)
    }

    @Test
    fun `parses smsto with message`() {
        val content = QrContent.parse("smsto:+15550001111:Vote YES")
        assertTrue(content is QrContent.Sms)
        val sms = content as QrContent.Sms
        assertEquals("+15550001111", sms.number)
        assertEquals("Vote YES", sms.message)
    }

    @Test
    fun `parses geo point`() {
        val content = QrContent.parse("geo:37.7749,-122.4194")
        assertTrue(content is QrContent.Geo)
        assertEquals("37.7749", (content as QrContent.Geo).latitude)
        assertEquals("-122.4194", content.longitude)
    }

    @Test
    fun `detects http url`() {
        val content = QrContent.parse("https://example.com/page?x=1")
        assertTrue(content is QrContent.Url)
    }

    @Test
    fun `falls back to plain text`() {
        val content = QrContent.parse("just some words here")
        assertTrue(content is QrContent.Plain)
        assertEquals("just some words here", (content as QrContent.Plain).text)
    }

    @Test
    fun `bare email address becomes email content`() {
        val content = QrContent.parse("someone@example.com")
        assertNotNull(content)
        assertTrue(content is QrContent.Email)
    }
}
