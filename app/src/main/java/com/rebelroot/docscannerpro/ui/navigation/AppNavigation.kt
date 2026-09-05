package com.rebelroot.docscannerpro.ui.navigation
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
    data object Settings : Screen("settings")
}
@Composable
fun AppNavigation(
    documentViewModel: DocumentViewModel = viewModel(),
    scanViewModel: ScanViewModel = viewModel()
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
    fun importImageUri(uri: Uri) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bitmap != null) {
                scanViewModel.captureFrame(bitmap)
                navController.navigate(Screen.ManualCrop.route)
            }
        } catch (_: Exception) {
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
                onImportImage = { uri -> importImageUri(uri) }
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
                onScan = { mode -> startScanning(mode) }
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
                    text = "Doc Scanner uses your camera to capture and detect paper documents, receipts, and ID cards completely offline on your device.",
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
