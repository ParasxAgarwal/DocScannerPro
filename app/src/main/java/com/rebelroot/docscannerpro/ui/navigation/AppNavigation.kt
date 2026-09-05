package com.rebelroot.docscannerpro.ui.navigation
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rebelroot.docscannerpro.ui.screens.DocumentDetailScreen
import com.rebelroot.docscannerpro.ui.screens.FilterAndEditScreen
import com.rebelroot.docscannerpro.ui.screens.HomeScreen
import com.rebelroot.docscannerpro.ui.screens.ManualCropScreen
import com.rebelroot.docscannerpro.ui.screens.NotesScreen
import com.rebelroot.docscannerpro.ui.screens.PdfToolScreen
import com.rebelroot.docscannerpro.ui.screens.ToolsScreen
import com.rebelroot.docscannerpro.ui.screens.OcrEditorScreen
import com.rebelroot.docscannerpro.ui.screens.ScannerScreen
import com.rebelroot.docscannerpro.ui.screens.SettingsScreen
import com.rebelroot.docscannerpro.ui.viewmodel.DocumentViewModel
import com.rebelroot.docscannerpro.ui.viewmodel.ScanMode
import com.rebelroot.docscannerpro.ui.viewmodel.ScanViewModel
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Scanner : Screen("scanner/{mode}") {
        fun createRoute(mode: ScanMode) = "scanner/${mode.name}"
    }
    data object ManualCrop : Screen("manual_crop")
    data object FilterEdit : Screen("filter_edit")
    data object Detail : Screen("detail/{docId}") {
        fun createRoute(docId: String) = "detail/$docId"
    }
    data object Ocr : Screen("ocr/{docId}/{pageId}") {
        fun createRoute(docId: String, pageId: String) = "ocr/$docId/$pageId"
    }
    data object Notes : Screen("notes?docId={docId}") {
        fun createRoute(docId: String? = null) = if (docId != null) "notes?docId=$docId" else "notes"
    }
    data object Tools : Screen("tools")
    data object PdfTool : Screen("pdf_tool/{tool}") {
        fun createRoute(tool: com.rebelroot.docscannerpro.ui.viewmodel.PdfToolType) = "pdf_tool/${tool.name}"
    }
    data object Settings : Screen("settings")
}
@Composable
fun AppNavigation(
    documentViewModel: DocumentViewModel = viewModel(),
    scanViewModel: ScanViewModel = viewModel(),
    pdfToolsViewModel: com.rebelroot.docscannerpro.ui.viewmodel.PdfToolsViewModel = viewModel(),
    quickMode: ScanMode? = null,
    onQuickModeConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var pendingScanMode by remember { mutableStateOf(ScanMode.DOCUMENT) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            scanViewModel.setScanMode(pendingScanMode)
            navController.navigate(Screen.Scanner.createRoute(pendingScanMode))
        }
    }
    fun startScanning(mode: ScanMode) {
        pendingScanMode = mode
        if (hasCameraPermission) {
            scanViewModel.setScanMode(mode)
            navController.navigate(Screen.Scanner.createRoute(mode))
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    androidx.compose.runtime.LaunchedEffect(quickMode) {
        if (quickMode != null) {
            startScanning(quickMode)
            onQuickModeConsumed()
        }
    }
    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // Switch mode and enter the scanner FIRST: setScanMode() resets the
        // processing state, so it must run before the import coroutine starts.
        // The scanner gives visible progress and error banners during import.
        scanViewModel.setScanMode(ScanMode.DOCUMENT)
        navController.navigate(Screen.Scanner.createRoute(ScanMode.DOCUMENT))
        scanViewModel.importImages(uris) { drafts ->
            if (drafts.size == 1) {
                // Single image behaves like a captured page: open the crop editor.
                scanViewModel.editPage(drafts.first())
                navController.navigate(Screen.ManualCrop.route)
            }
        }
    }

    fun importPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scanViewModel.setScanMode(ScanMode.DOCUMENT)
        navController.navigate(Screen.Scanner.createRoute(ScanMode.DOCUMENT))
        scanViewModel.importPdfs(uris) { drafts ->
            if (drafts.size == 1) {
                scanViewModel.editPage(drafts.first())
                navController.navigate(Screen.ManualCrop.route)
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = documentViewModel,
                onNavigateToScan = { mode -> startScanning(mode) },
                onNavigateToDetail = { docId -> navController.navigate(Screen.Detail.createRoute(docId)) },
                onNavigateToDocuments = { documentViewModel.setCategory(com.rebelroot.docscannerpro.ui.viewmodel.CategoryFilter.ALL) },
                onNavigateToTools = { navController.navigate(Screen.Tools.route) },
                onNavigateToFavorites = { documentViewModel.setCategory(com.rebelroot.docscannerpro.ui.viewmodel.CategoryFilter.FAVORITES) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onImportImages = { uris -> importImages(uris) },
                onImportPdfs = { uris -> importPdfs(uris) }
            )
        }
        composable(
            route = Screen.Scanner.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = ScanMode.DOCUMENT.name })
        ) { backStackEntry ->
            val modeStr = backStackEntry.arguments?.getString("mode") ?: ScanMode.DOCUMENT.name
            val mode = try { ScanMode.valueOf(modeStr) } catch (_: Exception) { ScanMode.DOCUMENT }
            if (!hasCameraPermission) {
                CameraPermissionRationale(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onBack = { navController.popBackStack() }
                )
            } else {
                ScannerScreen(
                    viewModel = scanViewModel,
                    onClose = { navController.popBackStack() },
                    onNavigateToCrop = { navController.navigate(Screen.ManualCrop.route) },
                    onBatchFinished = { docId ->
                        navController.navigate(Screen.Detail.createRoute(docId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }
        }
        composable(Screen.ManualCrop.route) {
            ManualCropScreen(
                viewModel = scanViewModel,
                onBack = { navController.popBackStack() },
                onProceedToFilter = { navController.navigate(Screen.FilterEdit.route) }
            )
        }
        composable(Screen.FilterEdit.route) {
            FilterAndEditScreen(
                viewModel = scanViewModel,
                onBackToCrop = { navController.popBackStack() },
                onSaved = { docId ->
                    navController.navigate(Screen.Detail.createRoute(docId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("docId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId") ?: ""
            DocumentDetailScreen(
                documentId = docId,
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToOcr = { dId, pId -> navController.navigate(Screen.Ocr.createRoute(dId, pId)) },
                onNavigateToAddPage = { _ -> startScanning(ScanMode.DOCUMENT) },
                onNavigateToNotes = { dId -> navController.navigate(Screen.Notes.createRoute(dId)) }
            )
        }
        composable(
            route = Screen.Ocr.route,
            arguments = listOf(
                navArgument("docId") { type = NavType.StringType },
                navArgument("pageId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId") ?: ""
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""
            OcrEditorScreen(
                documentId = docId,
                pageId = pageId,
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Notes.route,
            arguments = listOf(navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId")
            NotesScreen(
                attachedDocId = docId,
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Tools.route) {
            ToolsScreen(
                onBack = { navController.popBackStack() },
                onScan = { mode -> startScanning(mode) },
                onOpenPdfTool = { tool ->
                    navController.navigate(Screen.PdfTool.createRoute(tool))
                }
            )
        }
        composable(
            route = Screen.PdfTool.route,
            arguments = listOf(navArgument("tool") { type = NavType.StringType })
        ) { backStackEntry ->
            val toolName = backStackEntry.arguments?.getString("tool") ?: ""
            val tool = try {
                com.rebelroot.docscannerpro.ui.viewmodel.PdfToolType.valueOf(toolName)
            } catch (_: Exception) {
                com.rebelroot.docscannerpro.ui.viewmodel.PdfToolType.MERGE
            }
            PdfToolScreen(
                tool = tool,
                viewModel = pdfToolsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = documentViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
@Composable
fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Doc Scanner Pro uses your camera to capture and detect paper documents, receipts, and ID cards completely offline on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRequestPermission) {
                    Text("Grant Camera Access")
                }
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.TextButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
    }
}
