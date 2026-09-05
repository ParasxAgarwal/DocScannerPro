package com.rebelroot.docscannerpro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rebelroot.docscannerpro.ui.viewmodel.ScanMode

private data class ToolEntry(val title: String, val description: String, val icon: ImageVector, val mode: ScanMode)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ToolsScreen(onBack: () -> Unit, onScan: (ScanMode) -> Unit) {
    val tools = listOf(
        ToolEntry("Document", "Paper, forms, contracts and letters", Icons.Default.Description, ScanMode.DOCUMENT),
        ToolEntry("Book", "Facing pages with spread correction", Icons.Default.MenuBook, ScanMode.BOOK),
        ToolEntry("ID card", "Front and back as one document", Icons.Default.CreditCard, ScanMode.ID_CARD),
        ToolEntry("Receipt", "Narrow receipts with cleanup", Icons.Default.ReceiptLong, ScanMode.RECEIPT),
        ToolEntry("Business card", "Capture contact details from a card", Icons.Default.TextSnippet, ScanMode.BUSINESS_CARD),
        ToolEntry("QR & barcode", "Read codes directly from the camera", Icons.Default.QrCode2, ScanMode.QR_BARCODE)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tools", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text("Capture tools", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(vertical = 10.dp))
                Text("Specialized modes for the documents you handle most.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }
            items(tools) { tool ->
                Surface(onClick = { onScan(tool.mode) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                    ListItem(
                        headlineContent = { Text(tool.title, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                        supportingContent = { Text(tool.description) },
                        leadingContent = {
                            Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Icon(tool.icon, contentDescription = null, modifier = Modifier.size(21.dp))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
