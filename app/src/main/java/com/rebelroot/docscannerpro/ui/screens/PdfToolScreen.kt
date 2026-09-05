package com.rebelroot.docscannerpro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.docscannerpro.core.export.PdfPageSize
import com.rebelroot.docscannerpro.core.export.PdfQuality
import com.rebelroot.docscannerpro.core.pdf.PdfToolsEngine
import com.rebelroot.docscannerpro.ui.viewmodel.PdfToolType
import com.rebelroot.docscannerpro.ui.viewmodel.PdfToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolScreen(
    tool: PdfToolType,
    viewModel: PdfToolsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val splitMode by viewModel.splitMode.collectAsState()
    val rangeFrom by viewModel.rangeFrom.collectAsState()
    val rangeTo by viewModel.rangeTo.collectAsState()
    val compressQuality by viewModel.compressQuality.collectAsState()
    val password by viewModel.password.collectAsState()

    androidx.compose.runtime.LaunchedEffect(tool) {
        viewModel.open(tool)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris -> if (uris.isNotEmpty()) viewModel.setSelection(uris) }

    val singlePdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.setSelection(listOf(uri)) }

    val multiPdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.setSelection(uris) }

    fun share(files: List<java.io.File>) {
        val uris = ArrayList(files.map { viewModel.uriFor(it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                if (files.first().extension == "jpg") type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(tool.title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.resultFiles.isNotEmpty()) {
                    SuccessView(
                        files = state.resultFiles,
                        onShare = { share(state.resultFiles) },
                        onRunAgain = { viewModel.resetResults() }
                    )
                } else {
                    Text(
                        tool.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (state.selectedUris.isEmpty()) "No file selected"
                                else "${state.selectedUris.size} file(s) selected",
                                fontWeight = FontWeight.Medium
                            )
                            state.pageCountHint?.let {
                                Text(
                                    "$it pages",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    when {
                                        tool.needsImages -> imagePicker.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                        tool == PdfToolType.MERGE -> multiPdfPicker.launch(arrayOf("application/pdf"))
                                        else -> singlePdfPicker.launch(arrayOf("application/pdf"))
                                    }
                                }) {
                                    Icon(
                                        if (tool.needsImages) Icons.Default.PhotoLibrary else Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        when {
                                            state.selectedUris.isNotEmpty() && tool.needsImages -> "Change images"
                                            state.selectedUris.isNotEmpty() -> "Change file"
                                            tool.needsImages -> "Select images"
                                            else -> "Select PDF"
                                        }
                                    )
                                }
                                if (tool == PdfToolType.MERGE && state.selectedUris.size >= 2) {
                                    OutlinedButton(onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) }) {
                                        Text("Add more")
                                    }
                                }
                            }
                        }
                    }

                    when (tool) {
                        PdfToolType.IMAGES_TO_PDF -> {
                            OptionsCard("Page size") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = pageSize == PdfPageSize.FIT_IMAGE,
                                        onClick = { viewModel.pageSize.value = PdfPageSize.FIT_IMAGE },
                                        label = { Text("Fit image") }
                                    )
                                    FilterChip(
                                        selected = pageSize == PdfPageSize.A4,
                                        onClick = { viewModel.pageSize.value = PdfPageSize.A4 },
                                        label = { Text("A4") }
                                    )
                                    FilterChip(
                                        selected = pageSize == PdfPageSize.US_LETTER,
                                        onClick = { viewModel.pageSize.value = PdfPageSize.US_LETTER },
                                        label = { Text("US Letter") }
                                    )
                                }
                            }
                        }
                        PdfToolType.SPLIT -> {
                            OptionsCard("How to split") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = splitMode == PdfToolsEngine.SplitMode.EVERY_PAGE,
                                        onClick = { viewModel.splitMode.value = PdfToolsEngine.SplitMode.EVERY_PAGE },
                                        label = { Text("Every page") }
                                    )
                                    FilterChip(
                                        selected = splitMode == PdfToolsEngine.SplitMode.EXTRACT_RANGE,
                                        onClick = { viewModel.splitMode.value = PdfToolsEngine.SplitMode.EXTRACT_RANGE },
                                        label = { Text("Page range") }
                                    )
                                }
                                if (splitMode == PdfToolsEngine.SplitMode.EXTRACT_RANGE) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = rangeFrom,
                                            onValueChange = { viewModel.rangeFrom.value = it.filter(Char::isDigit).take(4) },
                                            label = { Text("From") },
                                            singleLine = true,
                                            modifier = Modifier.width(110.dp)
                                        )
                                        Text("to")
                                        OutlinedTextField(
                                            value = rangeTo,
                                            onValueChange = { viewModel.rangeTo.value = it.filter(Char::isDigit).take(4) },
                                            label = { Text("To") },
                                            singleLine = true,
                                            modifier = Modifier.width(110.dp)
                                        )
                                    }
                                }
                            }
                        }
                        PdfToolType.COMPRESS -> {
                            OptionsCard("Output quality") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = compressQuality == PdfQuality.HIGH,
                                        onClick = { viewModel.compressQuality.value = PdfQuality.HIGH },
                                        label = { Text("High") }
                                    )
                                    FilterChip(
                                        selected = compressQuality == PdfQuality.MEDIUM,
                                        onClick = { viewModel.compressQuality.value = PdfQuality.MEDIUM },
                                        label = { Text("Balanced") }
                                    )
                                    FilterChip(
                                        selected = compressQuality == PdfQuality.COMPACT,
                                        onClick = { viewModel.compressQuality.value = PdfQuality.COMPACT },
                                        label = { Text("Smallest") }
                                    )
                                }
                            }
                        }
                        PdfToolType.PROTECT -> {
                            OptionsCard("Password") {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { viewModel.password.value = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "The PDF will ask for this password when opened. Keep a copy — it cannot be recovered.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        PdfToolType.MERGE -> {
                            Text(
                                "Pages are combined in the order you pick the files. Password-protected PDFs are not supported.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PdfToolType.PDF_TO_IMAGES -> Unit
                    }

                    state.error?.let { message ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.run() },
                        enabled = state.selectedUris.isNotEmpty() && !state.isRunning,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(
                            when (tool) {
                                PdfToolType.IMAGES_TO_PDF -> "Create PDF"
                                PdfToolType.MERGE -> "Merge PDFs"
                                PdfToolType.SPLIT -> "Split"
                                PdfToolType.PDF_TO_IMAGES -> "Export images"
                                PdfToolType.COMPRESS -> "Compress"
                                PdfToolType.PROTECT -> "Protect"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF0F172A).copy(alpha = 0.96f)) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                state.statusText.ifBlank { "Working…" },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                color = Color(0xFF5AA7FF),
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            content(this)
        }
    }
}

@Composable
private fun SuccessView(
    files: List<java.io.File>,
    onShare: () -> Unit,
    onRunAgain: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
            Text("Done!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (files.size == 1) files.first().name else "${files.size} files created",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (files.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(files) { file ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(onClick = onRunAgain) { Text("Run again") }
            }
        }
    }
}
