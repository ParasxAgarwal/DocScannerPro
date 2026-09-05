package com.rebelroot.docscannerpro.ui.screens
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.docscannerpro.core.model.FilterType
import com.rebelroot.docscannerpro.ui.viewmodel.CaptureState
import com.rebelroot.docscannerpro.ui.viewmodel.ScanViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterAndEditScreen(
    viewModel: ScanViewModel,
    onBackToCrop: () -> Unit,
    onSaved: (documentId: String) -> Unit
) {
    val pageDraft by viewModel.editingPage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val captureState by viewModel.captureState.collectAsState()
    val activeFilter = pageDraft?.filterType ?: FilterType.AUTO_ENHANCE
    val filters = listOf(
        FilterType.AUTO_ENHANCE to "Auto",
        FilterType.DOCUMENT_BW to "B&W Doc",
        FilterType.ORIGINAL to "Original",
        FilterType.GRAYSCALE to "Grayscale",
        FilterType.COLOR_BOOST to "Vivid",
        FilterType.RECEIPT_CONTRAST to "Receipt"
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filters & Color", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackToCrop) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to crop")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.rotateEditingPage() }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(vertical = 12.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(filters) { (type, label) ->
                            val isSelected = activeFilter == type
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color.White.copy(alpha = 0.12f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)) else null,
                                modifier = Modifier
                                    .clickable(enabled = !isProcessing) { viewModel.applyFilterToEditingPage(type) }
                                    .testTag("filter_chip_${type.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.finishBatchAndSave { docId ->
                                    onSaved(docId)
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_save_document")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving…")
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Document", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF020617)),
            contentAlignment = Alignment.Center
        ) {
            val processed = pageDraft?.processedBitmap
            if (processed != null) {
                Image(
                    bitmap = processed.asImageBitmap(),
                    contentDescription = "Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
            when (val state = captureState) {
                is CaptureState.Failed -> Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF7F1D1D).copy(alpha = 0.94f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.dismissCaptureFailure() }) {
                            Text("OK")
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
