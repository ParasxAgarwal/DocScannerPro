package com.rebelroot.docscannerpro.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.docscannerpro.core.qr.QrContent
import com.rebelroot.docscannerpro.core.qr.QrHistoryEntry
import com.rebelroot.docscannerpro.ui.viewmodel.QrResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultSheet(
    result: QrResult,
    history: List<QrHistoryEntry>,
    onDismiss: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(result.item.formatName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                result.content.summaryText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            val actions = result.content.buildActions()
            actions.forEach { action ->
                Button(
                    onClick = { action.run(context) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(action.label, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            OutlinedButton(
                onClick = {
                    copyToClipboard(context, result.content.copyText(), "Scanned code")
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Copy")
            }
            if (result.content is QrContent.Plain) {
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result.content.text)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "Share scan")) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Share")
                }
            }
            TextButton(onClick = { showHistory = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Scan history")
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Scan history") },
            text = {
                if (history.isEmpty()) {
                    Text("No scans saved yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(history, key = { it.id }) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.label, maxLines = 1, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${entry.kind} · ${android.text.format.DateFormat.format("dd MMM HH:mm", entry.timestamp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { copyToClipboard(context, entry.rawValue, "Scanned code") }) {
                                    Text("Copy")
                                }
                                IconButton(onClick = { onDeleteHistory(entry.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) { Text("Clear all") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistory = false }) { Text("Close") }
            }
        )
    }
}

private fun QrContent.summaryText(): String = when (this) {
    is QrContent.Url -> url
    is QrContent.Phone -> number
    is QrContent.Email -> listOfNotNull(address, subject?.let { "Subject: $it" }).joinToString("\n")
    is QrContent.Sms -> listOfNotNull(number, message?.let { "Message: $it" }).joinToString("\n")
    is QrContent.Wifi -> "Network: $ssid" + (encryption?.let { " ($it)" } ?: "") + "\nPassword: ${password ?: "—"}"
    is QrContent.Contact -> listOfNotNull(name, org, phones.firstOrNull(), emails.firstOrNull())
        .joinToString("\n")
    is QrContent.Geo -> "Location: $latitude, $longitude"
    is QrContent.Plain -> text
}

private fun QrContent.copyText(): String = when (this) {
    is QrContent.Url -> url
    is QrContent.Phone -> number
    is QrContent.Email -> address
    is QrContent.Sms -> number
    is QrContent.Wifi -> "WIFI:T:${encryption ?: "WPA"};S:$ssid;P:${password ?: ""};;"
    is QrContent.Contact -> listOfNotNull(
        name?.let { "Name: $it" },
        org?.let { "Organization: $it" },
        phones.joinToString("\n") { "Phone: $it" }.takeIf { it.isNotBlank() },
        emails.joinToString("\n") { "Email: $it" }.takeIf { it.isNotBlank() }
    ).joinToString("\n")
    is QrContent.Geo -> "geo:$latitude,$longitude"
    is QrContent.Plain -> text
}

private data class QrAction(val label: String, val run: (Context) -> Unit)

private fun QrContent.buildActions(): List<QrAction> = when (this) {
    is QrContent.Url -> listOf(
        QrAction("Open link") { ctx ->
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { throwIfFatal(it) }
        }
    )
    is QrContent.Phone -> listOf(
        QrAction("Call ${number.ifBlank { "number" }}") { ctx ->
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
            }.onFailure { throwIfFatal(it) }
        }
    )
    is QrContent.Email -> listOf(
        QrAction("Compose email") { ctx ->
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$address")
                subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                body?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
            runCatching { ctx.startActivity(Intent.createChooser(intent, "Send email")) }
                .onFailure { throwIfFatal(it) }
        }
    )
    is QrContent.Sms -> listOf(
        QrAction("Open in messaging app") { ctx ->
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                message?.let { putExtra("sms_body", it) }
            }
            runCatching { ctx.startActivity(intent) }.onFailure { throwIfFatal(it) }
        }
    )
    is QrContent.Wifi -> listOfNotNull(
        password?.takeIf { it.isNotBlank() }?.let { pw ->
            QrAction("Copy password") { ctx -> copyToClipboard(ctx, pw, "Wi-Fi password") }
        },
        QrAction("Open Wi-Fi settings") { ctx ->
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { throwIfFatal(it) }
        }
    )
    is QrContent.Contact -> listOfNotNull(
        QrAction("Save contact") { ctx ->
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, name)
                putExtra(ContactsContract.Intents.Insert.COMPANY, org)
                putExtra(ContactsContract.Intents.Insert.PHONE, phones.firstOrNull())
                putExtra(ContactsContract.Intents.Insert.EMAIL, emails.firstOrNull())
            }
            runCatching { ctx.startActivity(intent) }.onFailure { throwIfFatal(it) }
        }
    )
    is QrContent.Geo -> listOf(
        QrAction("Open in maps") { ctx ->
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude")))
            }.onFailure { throwIfFatal(it) }
        }
    )
    is QrContent.Plain -> emptyList()
}

private fun throwIfFatal(t: Throwable) {
    if (t is ActivityNotFoundException) return // handled as no-op: no app for this action
    throw t
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}
