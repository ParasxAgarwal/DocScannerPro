package com.rebelroot.docscannerpro.core.qr

import android.net.Uri

/**
 * Typed classification of a decoded QR/barcode payload so the UI can offer
 * meaningful actions instead of only "copy". Pure parsing logic, unit tested.
 */
sealed class QrContent {
    data class Url(val url: String) : QrContent()
    data class Phone(val number: String) : QrContent()
    data class Email(val address: String, val subject: String? = null, val body: String? = null) : QrContent()
    data class Sms(val number: String, val message: String? = null) : QrContent()
    data class Wifi(val ssid: String, val password: String?, val encryption: String?) : QrContent()
    data class Contact(val name: String?, val org: String?, val phones: List<String>, val emails: List<String>) : QrContent()
    data class Geo(val latitude: String, val longitude: String) : QrContent()
    data class Plain(val text: String) : QrContent()

    companion object {
        fun parse(raw: String): QrContent {
            val trimmed = raw.trim()
            return when {
                trimmed.startsWith("WIFI:", true) -> parseWifi(trimmed)
                trimmed.startsWith("BEGIN:VCARD", true) -> parseVCard(trimmed)
                trimmed.startsWith("tel:", true) -> Phone(percentDecode(trimmed.substringAfter(':')).trim())
                trimmed.startsWith("mailto:", true) -> parseMailto(trimmed)
                trimmed.startsWith("smsto:", true) || trimmed.startsWith("sms:", true) -> parseSms(trimmed)
                trimmed.startsWith("geo:", true) -> parseGeo(trimmed)
                trimmed.startsWith("MECARD:", true) -> parseMeCard(trimmed)
                trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> Url(trimmed)
                trimmed.contains("@") && !trimmed.contains(" ") && trimmed.contains(".") -> Email(trimmed)
                else -> Plain(trimmed)
            }
        }

        /** WIFI:T:WPA;S:mynetwork;P:mypass;H:false;; */
        internal fun parseWifi(raw: String): Wifi {
            val body = raw.removePrefix("WIFI:").removePrefix("wifi:")
            var ssid: String? = null
            var password: String? = null
            var encryption: String? = null
            for (field in body.split(';')) {
                val idx = field.indexOf(':')
                if (idx < 1) continue
                val key = field.substring(0, idx).uppercase()
                val value = percentDecode(field.substring(idx + 1))
                when (key) {
                    "S" -> ssid = value
                    "P" -> password = value
                    "T" -> encryption = value
                }
            }
            return Wifi(ssid.orEmpty().trim(), password, encryption)
        }

        /** Minimal vCard 2.1/3.0 reader for the fields scanners actually need. */
        internal fun parseVCard(raw: String): Contact {
            var name: String? = null
            var org: String? = null
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()
            for (line in raw.lines()) {
                val upper = line.uppercase()
                val value = line.substringAfter(':').trim()
                when {
                    upper.startsWith("FN") && value.isNotBlank() -> name = value
                    upper.startsWith("N:") && name == null && value.isNotBlank() ->
                        name = value.split(';').firstOrNull { it.isNotBlank() }?.trim()
                    upper.startsWith("ORG") && value.isNotBlank() -> org = value
                    upper.startsWith("TEL") && value.isNotBlank() -> phones += value
                    upper.startsWith("EMAIL") && value.isNotBlank() -> emails += value
                }
            }
            return Contact(name, org, phones.distinct(), emails.distinct())
        }

        /** MECARD:N:Name;ORG:Company;TEL:123;EMAIL:a@b.c;; */
        internal fun parseMeCard(raw: String): Contact {
            var name: String? = null
            var org: String? = null
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()
            for (field in raw.removePrefix("MECARD:").split(';')) {
                val idx = field.indexOf(':')
                if (idx < 1) continue
                val key = field.substring(0, idx).uppercase()
                val value = percentDecode(field.substring(idx + 1))
                when (key) {
                    "N" -> name = value.replace(",", " ").trim()
                    "ORG" -> org = value
                    "TEL" -> if (value.isNotBlank()) phones += value
                    "EMAIL", "EMA" -> if (value.isNotBlank()) emails += value
                }
            }
            return Contact(name, org, phones.distinct(), emails.distinct())
        }

        /** mailto:addr?subject=...&body=... */
        internal fun parseMailto(raw: String): Email {
            val withoutScheme = raw.substringAfter(':')
            val address = withoutScheme.substringBefore('?').trim()
            var subject: String? = null
            var body: String? = null
            val query = withoutScheme.substringAfter('?', "")
            if (query.isNotEmpty()) {
                for (pair in query.split('&')) {
                    val idx = pair.indexOf('=')
                    if (idx <= 0) continue
                    val key = percentDecode(pair.substring(0, idx)).lowercase()
                    val value = percentDecode(pair.substring(idx + 1))
                    when (key) {
                        "subject" -> subject = value
                        "body" -> body = value
                    }
                }
            }
            return Email(address, subject, body)
        }

        /** smsto:number:message or sms:number?body=... */
        internal fun parseSms(raw: String): Sms {
            val withoutScheme = raw.substringAfter(':')
            return if (withoutScheme.contains('?')) {
                val number = withoutScheme.substringBefore('?').trim()
                var body: String? = null
                val query = withoutScheme.substringAfter('?', "")
                for (pair in query.split('&')) {
                    val idx = pair.indexOf('=')
                    if (idx <= 0) continue
                    if (percentDecode(pair.substring(0, idx)).lowercase() == "body") {
                        body = percentDecode(pair.substring(idx + 1))
                    }
                }
                Sms(number, body)
            } else {
                val parts = withoutScheme.split(':', limit = 2)
                Sms(parts[0].trim(), parts.getOrNull(1)?.trim())
            }
        }

        internal fun parseGeo(raw: String): Geo {
            val coords = raw.substringAfter(':').substringBefore('?').split(',')
            return Geo(coords.getOrNull(0)?.trim().orEmpty(), coords.getOrNull(1)?.trim().orEmpty())
        }

        /** Percent-decoding without android.net.Uri so parsing is unit-testable on the JVM. */
        internal fun percentDecode(value: String): String {
            if (!value.contains('%')) return value
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%' && i + 2 < value.length) {
                    val hex = value.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar())
                        i += 3
                        continue
                    }
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

    }
}
