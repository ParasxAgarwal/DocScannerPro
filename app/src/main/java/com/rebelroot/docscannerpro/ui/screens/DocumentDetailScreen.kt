package com.rebelroot.docscannerpro.ui.screens
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rebelroot.docscannerpro.core.export.PdfPageSize
import com.rebelroot.docscannerpro.core.export.PdfQuality
import com.rebelroot.docscannerpro.core.model.Document
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.core.ocr.DocumentUnderstanding
import com.rebelroot.docscannerpro.ui.viewmodel.DocumentViewModel
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: String,
    viewModel: DocumentViewModel,
    onBack: () -> Unit,
    onNavigateToOcr: (docId: String, pageId: String) -> Unit,
    onNavigateToAddPage: (docId: String) -> Unit,
    onNavigateToNotes: (docId: String) -> Unit
) {
    val context = LocalContext.current
    val doc by viewModel.currentDocument.collectAsState()
    val pages by viewModel.currentPages.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    var activePageIndex by remember { mutableStateOf(0) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showDeleteDocDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }
    val currentDoc = doc
    if (currentDoc == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val activePage = pages.getOrNull(activePageIndex)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentDoc.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pages.size} pages • ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(currentDoc.updatedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(currentDoc) }) {
                        Icon(
                            imageVector = if (currentDoc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (currentDoc.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        renameText = currentDoc.title
                        showRenameDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { showDeleteDocDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToAddPage(currentDoc.id) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Page")
                    }
                    Button(
                        onClick = { showExportSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_export_dialog")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export / Share")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activePage != null && File(activePage.processedPath).exists()) {
                            AsyncImage(
                                model = File(activePage.processedPath),
                                contentDescription = "Page ${activePageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Page ${activePageIndex + 1} of ${pages.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            if (pages.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        itemsIndexed(pages) { index, page ->
                            val isSelected = index == activePageIndex
                            Box(
                                modifier = Modifier
                                    .size(56.dp, 72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { activePageIndex = index }
                            ) {
                                AsyncImage(
                                    model = File(page.thumbnailPath ?: page.processedPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            activePage?.let { onNavigateToOcr(currentDoc.id, it.id) }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View OCR")
                    }
                    OutlinedButton(
                        onClick = { onNavigateToNotes(currentDoc.id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Attach Note")
                    }
                    if (pages.size > 1 && activePage != null) {
                        IconButton(
                            onClick = { viewModel.deletePage(activePage.id) }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            activePage?.let { page ->
                if (!page.structuredJson.isNullOrBlank()) {
                    item {
                        StructuredDataCard(jsonString = page.structuredJson, type = currentDoc.type)
                    }
                }
                if (!page.ocrText.isNullOrBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Recognized Text", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    IconButton(
                                        onClick = {
                                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clip.setPrimaryClip(android.content.ClipData.newPlainText("OCR", page.ocrText))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = page.ocrText.take(280) + if (page.ocrText.length > 280) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showExportSheet) {
        ExportBottomSheet(
            viewModel = viewModel,
            onDismiss = { showExportSheet = false },
            onShareFile = { uri, mimeType ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Document"))
            }
        )
    }
    if (showDeleteDocDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = false },
            title = { Text("Delete Document?") },
            text = { Text("This will permanently remove this document and all scanned pages from device storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(currentDoc.id) {
                            onBack()
                        }
                        showDeleteDocDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDocDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTitle(currentDoc.id, renameText)
                        showRenameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    viewModel: DocumentViewModel,
    onDismiss: () -> Unit,
    onShareFile: (uri: Uri, mimeType: String) -> Unit
) {
    val isExporting by viewModel.isExporting.collectAsState()
    val progress by viewModel.exportProgress.collectAsState()
    var selectedFormat by remember { mutableStateOf("PDF") }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var isSearchable by remember { mutableStateOf(true) }
    var quality by remember { mutableStateOf(PdfQuality.HIGH) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Export & Share Document", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("PDF", "DOCX", "TXT", "Markdown", "CSV", "JSON")) { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = { selectedFormat = fmt },
                        label = { Text(fmt) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (selectedFormat == "PDF") {
                Text("PDF Options", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Searchable PDF (Embed text layer)")
                    Switch(checked = isSearchable, onCheckedChange = { isSearchable = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = pageSize == PdfPageSize.A4,
                        onClick = { pageSize = PdfPageSize.A4 },
                        label = { Text("A4 Standard") }
                    )
                    FilterChip(
                        selected = pageSize == PdfPageSize.US_LETTER,
                        onClick = { pageSize = PdfPageSize.US_LETTER },
                        label = { Text("US Letter") }
                    )
                    FilterChip(
                        selected = pageSize == PdfPageSize.FIT_IMAGE,
                        onClick = { pageSize = PdfPageSize.FIT_IMAGE },
                        label = { Text("Fit Image") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (isExporting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Generating file... ${(progress * 100).toInt()}%")
                }
            } else {
                Button(
                    onClick = {
                        when (selectedFormat) {
                            "PDF" -> {
                                viewModel.exportPdf(pageSize, isSearchable, quality) { uri, _ ->
                                    onShareFile(uri, "application/pdf")
                                    onDismiss()
                                }
                            }
                            "DOCX" -> {
                                viewModel.exportDocx { uri, _ ->
                                    onShareFile(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                    onDismiss()
                                }
                            }
                            "TXT" -> {
                                viewModel.exportPlainText { uri, _ ->
                                    onShareFile(uri, "text/plain")
                                    onDismiss()
                                }
                            }
                            "Markdown" -> {
                                viewModel.exportMarkdown { uri, _ ->
                                    onShareFile(uri, "text/markdown")
                                    onDismiss()
                                }
                            }
                            "CSV" -> {
                                val doc = viewModel.currentDocument.value
                                val page = viewModel.currentPages.value.firstOrNull()
                                val receipt = DocumentUnderstanding.extractReceipt(page?.ocrText ?: "")
                                viewModel.exportReceiptCsv(receipt) { uri, _ ->
                                    onShareFile(uri, "text/csv")
                                    onDismiss()
                                }
                            }
                            "JSON" -> {
                                viewModel.exportMarkdown { uri, _ ->
                                    onShareFile(uri, "application/json")
                                    onDismiss()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_confirm_export"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate & Share $selectedFormat")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
@Composable
fun StructuredDataCard(jsonString: String, type: DocumentType) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = when (type) {
                    DocumentType.RECEIPT -> "Extracted Receipt Data"
                    DocumentType.BUSINESS_CARD -> "Extracted Contact Data"
                    else -> "Extracted Info"
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            val obj = try { JSONObject(jsonString) } catch (_: Exception) { JSONObject() }
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key)
                if (value.isNotBlank() && value != "null") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = key.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = value,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
