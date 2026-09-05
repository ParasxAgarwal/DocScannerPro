package com.rebelroot.docscannerpro.ui.screens
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.docscannerpro.core.model.QuadCorners
import com.rebelroot.docscannerpro.ui.viewmodel.ScanViewModel
import kotlin.math.hypot
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualCropScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onProceedToFilter: () -> Unit
) {
    val pageDraft by viewModel.editingPage.collectAsState()
    val bitmap = pageDraft?.originalBitmap
    if (bitmap == null) {
        onBack()
        return
    }
    var corners by remember { mutableStateOf(pageDraft?.corners ?: QuadCorners.defaultQuad(1f, 1f)) }
    var activeDraggingCorner by remember { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust Borders", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.rotateEditingPage()
                        }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                    }
                    IconButton(
                        onClick = {
                            corners = QuadCorners(
                                topLeft = PointF(0.01f, 0.01f),
                                topRight = PointF(0.99f, 0.01f),
                                bottomRight = PointF(0.99f, 0.99f),
                                bottomLeft = PointF(0.01f, 0.99f)
                            )
                        }
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = "Full Image")
                    }
                    IconButton(
                        onClick = {
                            corners = QuadCorners.defaultQuad(1f, 1f, 0.08f)
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Corners")
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Drag corners to de-skew",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            viewModel.updateEditingPageCorners(corners)
                            onProceedToFilter()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_apply_crop")
                    ) {
                        Text("Apply Crop")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF020617))
        ) {
            val canvasW = constraints.maxWidth.toFloat()
            val canvasH = constraints.maxHeight.toFloat()
            val scale = minOf(canvasW / bitmap.width.toFloat(), canvasH / bitmap.height.toFloat())
            val imgW = bitmap.width * scale
            val imgH = bitmap.height * scale
            val offsetX = (canvasW - imgW) / 2f
            val offsetY = (canvasH - imgH) / 2f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val hitRadius = 40.dp.toPx()
                                val tl = Offset(offsetX + corners.topLeft.x * imgW, offsetY + corners.topLeft.y * imgH)
                                val tr = Offset(offsetX + corners.topRight.x * imgW, offsetY + corners.topRight.y * imgH)
                                val br = Offset(offsetX + corners.bottomRight.x * imgW, offsetY + corners.bottomRight.y * imgH)
                                val bl = Offset(offsetX + corners.bottomLeft.x * imgW, offsetY + corners.bottomLeft.y * imgH)
                                activeDraggingCorner = when {
                                    hypot(offset.x - tl.x, offset.y - tl.y) < hitRadius -> 0
                                    hypot(offset.x - tr.x, offset.y - tr.y) < hitRadius -> 1
                                    hypot(offset.x - br.x, offset.y - br.y) < hitRadius -> 2
                                    hypot(offset.x - bl.x, offset.y - bl.y) < hitRadius -> 3
                                    else -> null
                                }
                            },
                            onDragEnd = {
                                activeDraggingCorner = null
                            },
                            onDragCancel = {
                                activeDraggingCorner = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val cornerIdx = activeDraggingCorner ?: return@detectDragGestures
                                val normDx = dragAmount.x / imgW
                                val normDy = dragAmount.y / imgH
                                corners = when (cornerIdx) {
                                    0 -> corners.copy(
                                        topLeft = PointF(
                                            (corners.topLeft.x + normDx).coerceIn(0f, corners.topRight.x - 0.1f),
                                            (corners.topLeft.y + normDy).coerceIn(0f, corners.bottomLeft.y - 0.1f)
                                        )
                                    )
                                    1 -> corners.copy(
                                        topRight = PointF(
                                            (corners.topRight.x + normDx).coerceIn(corners.topLeft.x + 0.1f, 1f),
                                            (corners.topRight.y + normDy).coerceIn(0f, corners.bottomRight.y - 0.1f)
                                        )
                                    )
                                    2 -> corners.copy(
                                        bottomRight = PointF(
                                            (corners.bottomRight.x + normDx).coerceIn(corners.bottomLeft.x + 0.1f, 1f),
                                            (corners.bottomRight.y + normDy).coerceIn(corners.topRight.y + 0.1f, 1f)
                                        )
                                    )
                                    3 -> corners.copy(
                                        bottomLeft = PointF(
                                            (corners.bottomLeft.x + normDx).coerceIn(0f, corners.bottomRight.x - 0.1f),
                                            (corners.bottomLeft.y + normDy).coerceIn(corners.topLeft.y + 0.1f, 1f)
                                        )
                                    )
                                    else -> corners
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val imgBitmap = bitmap.asImageBitmap()
                    drawImage(
                        image = imgBitmap,
                        dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = IntSize(imgW.toInt(), imgH.toInt())
                    )
                    val p1 = Offset(offsetX + corners.topLeft.x * imgW, offsetY + corners.topLeft.y * imgH)
                    val p2 = Offset(offsetX + corners.topRight.x * imgW, offsetY + corners.topRight.y * imgH)
                    val p3 = Offset(offsetX + corners.bottomRight.x * imgW, offsetY + corners.bottomRight.y * imgH)
                    val p4 = Offset(offsetX + corners.bottomLeft.x * imgW, offsetY + corners.bottomLeft.y * imgH)
                    val quadPath = Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        lineTo(p4.x, p4.y)
                        close()
                    }
                    drawPath(
                        path = quadPath,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawPerspectiveGrid(p1, p2, p3, p4)
                    val handleRadius = 14.dp.toPx()
                    listOf(p1, p2, p3, p4).forEachIndexed { i, p ->
                        val isHighlighted = activeDraggingCorner == i
                        drawCircle(
                            color = Color.White,
                            radius = if (isHighlighted) handleRadius * 1.3f else handleRadius,
                            center = p
                        )
                        drawCircle(
                            color = Color(0xFF0284C7),
                            radius = if (isHighlighted) handleRadius * 0.9f else handleRadius * 0.65f,
                            center = p
                        )
                    }
                }
                activeDraggingCorner?.let { cornerIdx ->
                    val activePoint = when (cornerIdx) {
                        0 -> corners.topLeft
                        1 -> corners.topRight
                        2 -> corners.bottomRight
                        else -> corners.bottomLeft
                    }
                    val loupeOnRight = activePoint.x < 0.5f
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .align(if (loupeOnRight) Alignment.TopEnd else Alignment.TopStart)
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(3.dp, Color(0xFF38BDF8), CircleShape)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cropRadius = (bitmap.width * 0.12f).toInt().coerceAtLeast(30)
                            val centerPxX = (activePoint.x * bitmap.width).toInt().coerceIn(cropRadius, bitmap.width - cropRadius)
                            val centerPxY = (activePoint.y * bitmap.height).toInt().coerceIn(cropRadius, bitmap.height - cropRadius)
                            val srcRect = IntOffset(centerPxX - cropRadius, centerPxY - cropRadius)
                            val srcSize = IntSize(cropRadius * 2, cropRadius * 2)
                            drawImage(
                                image = bitmap.asImageBitmap(),
                                srcOffset = srcRect,
                                srcSize = srcSize,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                            drawLine(
                                color = Color(0xFF38BDF8),
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFF38BDF8),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
    }
}
private fun DrawScope.drawPerspectiveGrid(p1: Offset, p2: Offset, p3: Offset, p4: Offset) {
    val gridColor = Color.White.copy(alpha = 0.25f)
    val stroke = Stroke(width = 1.dp.toPx())
    for (step in 1..2) {
        val f = step / 3f
        val top = Offset(p1.x + (p2.x - p1.x) * f, p1.y + (p2.y - p1.y) * f)
        val bot = Offset(p4.x + (p3.x - p4.x) * f, p4.y + (p3.y - p4.y) * f)
        drawLine(color = gridColor, start = top, end = bot, strokeWidth = stroke.width)
        val left = Offset(p1.x + (p4.x - p1.x) * f, p1.y + (p4.y - p1.y) * f)
        val right = Offset(p2.x + (p3.x - p2.x) * f, p2.y + (p3.y - p2.y) * f)
        drawLine(color = gridColor, start = left, end = right, strokeWidth = stroke.width)
    }
}
