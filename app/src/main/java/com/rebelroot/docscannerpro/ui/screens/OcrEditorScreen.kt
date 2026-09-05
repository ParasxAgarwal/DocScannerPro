package com.rebelroot.docscannerpro.ui.screens
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.docscannerpro.ui.viewmodel.DocumentViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrEditorScreen(
    documentId: String,
    pageId: String,
    viewModel: DocumentViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pages by viewModel.currentPages.collectAsState()
    val page = pages.find { it.id == pageId }
    var textContent by remember(page?.ocrText) { mutableStateOf(page?.ocrText ?: "") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recognized text", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", textContent))
                        }
                    ) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy text") }
                    IconButton(
                        onClick = {
                            viewModel.updatePageOcr(pageId, textContent)
                            onBack()
                        },
                        modifier = Modifier.testTag("btn_save_ocr")
                    ) { Icon(Icons.Default.Save, contentDescription = "Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit the text recognized from this page.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .testTag("ocr_text_input"),
                placeholder = { Text("No text recognized") },
                shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = {
                    viewModel.updatePageOcr(pageId, textContent)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save changes")
            }
        }
    }
}
