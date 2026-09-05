package com.rebelroot.docscannerpro.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rebelroot.docscannerpro.core.model.Document
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.ui.viewmodel.CategoryFilter
import com.rebelroot.docscannerpro.ui.viewmodel.DocumentViewModel
import com.rebelroot.docscannerpro.ui.viewmodel.ScanMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibrarySort { RECENT, OLDEST, NAME, PAGES }
private enum class LibraryView { LIST, GRID }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    viewModel: DocumentViewModel,
    onNavigateToScan: (mode: ScanMode) -> Unit,
    onNavigateToDetail: (docId: String) -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onImportImages: (List<Uri>) -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isVaultUnlocked by viewModel.vaultManager.isUnlocked.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var renameDocTarget by remember { mutableStateOf<Document?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(LibrarySort.RECENT) }
    var viewMode by remember { mutableStateOf(LibraryView.LIST) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris -> if (uris.isNotEmpty()) onImportImages(uris) }

    val visibleDocuments = remember(documents, sortMode) {
        when (sortMode) {
            LibrarySort.RECENT -> documents.sortedByDescending { it.updatedAt }
            LibrarySort.OLDEST -> documents.sortedBy { it.updatedAt }
            LibrarySort.NAME -> documents.sortedBy { it.title.lowercase(Locale.getDefault()) }
            LibrarySort.PAGES -> documents.sortedByDescending { it.pageCount }
        }
    }

    val title = when (selectedCategory) {
        CategoryFilter.ALL, CategoryFilter.DOCUMENTS -> "Documents"
        CategoryFilter.RECEIPTS -> "Receipts"
        CategoryFilter.ID_CARDS -> "IDs & Cards"
        CategoryFilter.BOOKS -> "Books"
        CategoryFilter.FAVORITES -> "Favorites"
        CategoryFilter.VAULT -> "Private Vault"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        if (searchQuery.isNotBlank()) {
                            Text(
                                "${visibleDocuments.size} ${if (visibleDocuments.size == 1) "result" else "results"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("search_button")) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { viewMode = if (viewMode == LibraryView.LIST) LibraryView.GRID else LibraryView.LIST }) {
                        Icon(
                            if (viewMode == LibraryView.LIST) Icons.Default.GridView else Icons.Default.List,
                            contentDescription = if (viewMode == LibraryView.LIST) "Grid view" else "List view"
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Most recent") }, onClick = { sortMode = LibrarySort.RECENT; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Oldest first") }, onClick = { sortMode = LibrarySort.OLDEST; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Name") }, onClick = { sortMode = LibrarySort.NAME; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Most pages") }, onClick = { sortMode = LibrarySort.PAGES; showSortMenu = false })
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        bottomBar = {
            AppBottomNavigation(
                selectedCategory = selectedCategory,
                onNavigateToDocuments = onNavigateToDocuments,
                onNavigateToTools = onNavigateToTools,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        floatingActionButton = {
            FloatingScanButton(onClick = { onNavigateToScan(ScanMode.DOCUMENT) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(searchOpen) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("search_bar"),
                    singleLine = true,
                    placeholder = { Text("Search your documents") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = { viewModel.setSearchQuery("") }) { Text("Clear") }
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            QuickActions(
                onScan = { onNavigateToScan(ScanMode.DOCUMENT) },
                onImport = {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onId = { onNavigateToScan(ScanMode.ID_CARD) },
                onReceipt = { onNavigateToScan(ScanMode.RECEIPT) },
                onMore = { showToolsSheet = true }
            )

            CategoryTabs(
                selected = selectedCategory,
                isVaultUnlocked = isVaultUnlocked,
                onSelect = { category ->
                    if (category == CategoryFilter.VAULT && !isVaultUnlocked) showPinDialog = true
                    else viewModel.setCategory(category)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        searchQuery.isNotBlank() -> "Search results"
                        selectedCategory == CategoryFilter.FAVORITES -> "Saved for quick access"
                        else -> "Recent"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${visibleDocuments.size} ${if (visibleDocuments.size == 1) "item" else "items"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (visibleDocuments.isEmpty()) {
                EmptyLibrary(
                    hasQuery = searchQuery.isNotBlank(),
                    category = selectedCategory,
                    onScan = { onNavigateToScan(ScanMode.DOCUMENT) },
                    onImport = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else if (viewMode == LibraryView.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 104.dp)
                ) {
                    items(visibleDocuments, key = { it.id }) { doc ->
                        ProfessionalDocumentRow(
                            doc = doc,
                            onClick = { onNavigateToDetail(doc.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(doc) },
                            onTogglePinned = { viewModel.togglePinned(doc) },
                            onToggleVault = { viewModel.toggleVault(doc) },
                            onRename = { renameDocTarget = doc; renameText = doc.title },
                            onDelete = { viewModel.deleteDocument(doc.id) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(visibleDocuments, key = { it.id }) { doc ->
                        DocumentGridItem(doc, onClick = { onNavigateToDetail(doc.id) })
                    }
                }
            }
        }
    }

    if (showToolsSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showToolsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Scan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Choose the capture type that fits the page in front of you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToolRow("Document", "Paper, forms, contracts and letters", Icons.Default.Description) { showToolsSheet = false; onNavigateToScan(ScanMode.DOCUMENT) }
                ToolRow("Book", "Two-page capture with spread correction", Icons.Default.MenuBook) { showToolsSheet = false; onNavigateToScan(ScanMode.BOOK) }
                ToolRow("ID card", "Front and back as one document", Icons.Default.CreditCard) { showToolsSheet = false; onNavigateToScan(ScanMode.ID_CARD) }
                ToolRow("Receipt", "Narrow receipts with cleanup", Icons.Default.ReceiptLong) { showToolsSheet = false; onNavigateToScan(ScanMode.RECEIPT) }
                ToolRow("Business card", "Capture and extract contact details", Icons.Default.TextSnippet) { showToolsSheet = false; onNavigateToScan(ScanMode.BUSINESS_CARD) }
                ToolRow("QR & barcode", "Read codes directly from camera", Icons.Default.Assignment) { showToolsSheet = false; onNavigateToScan(ScanMode.QR_BARCODE) }
                ToolRow("Import photo", "Use an existing image", Icons.Default.PhotoLibrary) {
                    showToolsSheet = false
                    photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(if (viewModel.vaultManager.isPinSet()) "Unlock Private Vault" else "Create Private Vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (viewModel.vaultManager.isPinSet()) "Enter your PIN to view private documents." else "Create a PIN that stays on this device.")
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit) },
                        singleLine = true,
                        isError = pinError,
                        label = { Text("PIN") }
                    )
                    if (pinError) Text("PIN must be at least 4 digits.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length < 4) pinError = true
                    else {
                        if (viewModel.vaultManager.isPinSet()) viewModel.vaultManager.verifyPin(pinInput)
                        else viewModel.vaultManager.setPin(pinInput)
                        showPinDialog = false
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
        )
    }

    renameDocTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameDocTarget = null },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Document name") })
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateTitle(target.id, renameText); renameDocTarget = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameDocTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuickActions(
    onScan: () -> Unit,
    onImport: () -> Unit,
    onId: () -> Unit,
    onReceipt: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onScan,
            modifier = Modifier.height(44.dp),
            contentPadding = PaddingValues(horizontal = 18.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan")
        }
        CompactAction(Icons.Default.PhotoLibrary, "Import", onImport)
        CompactAction(Icons.Default.CreditCard, "ID", onId)
        CompactAction(Icons.Default.ReceiptLong, "Receipt", onReceipt)
        CompactAction(Icons.Default.MoreVert, "More", onMore)
    }
}

@Composable
private fun CompactAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp).clickable(onClick = onClick)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryTabs(
    selected: CategoryFilter,
    isVaultUnlocked: Boolean,
    onSelect: (CategoryFilter) -> Unit
) {
    val categories = listOf(
        CategoryFilter.ALL to "All",
        CategoryFilter.DOCUMENTS to "Documents",
        CategoryFilter.RECEIPTS to "Receipts",
        CategoryFilter.ID_CARDS to "IDs & cards",
        CategoryFilter.BOOKS to "Books",
        CategoryFilter.FAVORITES to "Favorites",
        CategoryFilter.VAULT to "Private"
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        items(categories) { (category, label) ->
            val active = selected == category
            val locked = category == CategoryFilter.VAULT && !isVaultUnlocked
            Column(
                modifier = Modifier.clickable { onSelect(category) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (locked) Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (locked) Spacer(Modifier.width(3.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (active) 24.dp else 0.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun AppBottomNavigation(
    selectedCategory: CategoryFilter,
    onNavigateToDocuments: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val selectedDocuments = selectedCategory != CategoryFilter.FAVORITES
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem("Documents", Icons.Default.Home, selectedDocuments, onNavigateToDocuments)
        BottomNavItem("Tools", Icons.Default.Assignment, false, onNavigateToTools)
        BottomNavItem("Favorites", Icons.Default.Favorite, selectedCategory == CategoryFilter.FAVORITES, onNavigateToFavorites)
        BottomNavItem("Settings", Icons.Default.Settings, false, onNavigateToSettings)
    }
}

@Composable
private fun BottomNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(78.dp).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun FloatingScanButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.testTag("fab_scan")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyLibrary(hasQuery: Boolean, category: CategoryFilter, onScan: () -> Unit, onImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 48.dp)) {
            Box(
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (hasQuery) "No matching documents" else when (category) {
                    CategoryFilter.FAVORITES -> "No favorites yet"
                    CategoryFilter.RECEIPTS -> "No receipts yet"
                    CategoryFilter.ID_CARDS -> "No IDs or cards yet"
                    CategoryFilter.BOOKS -> "No books yet"
                    CategoryFilter.VAULT -> "Private vault is empty"
                    else -> "Your library is empty"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                if (hasQuery) "Try another title or OCR phrase." else when (category) {
                    CategoryFilter.FAVORITES -> "Tap the heart on a document to keep it here."
                    CategoryFilter.RECEIPTS -> "Scan a receipt to keep it organized on this device."
                    CategoryFilter.ID_CARDS -> "Scan a card and keep the copy on this device."
                    CategoryFilter.BOOKS -> "Use Book mode for facing pages."
                    CategoryFilter.VAULT -> "Private documents stay on this device."
                    else -> "Scan paper or import a photo to get started."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!hasQuery && category != CategoryFilter.FAVORITES) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onScan) { Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Scan") }
                    OutlinedButton(onClick = onImport) { Text("Import") }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfessionalDocumentRow(
    doc: Document,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleVault: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val date = remember(doc.updatedAt) { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(doc.updatedAt)) }
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            DocumentThumbnail(doc, Modifier.size(width = 56.dp, height = 70.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (doc.isPinned) { Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
                    if (doc.isVaultLocked) { Icon(Icons.Default.Lock, contentDescription = "Private", modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(doc.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("$date · ${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(prettyType(doc.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(if (doc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = if (doc.isFavorite) "Remove favorite" else "Favorite")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text(if (doc.isPinned) "Unpin" else "Pin to top") }, onClick = { menuExpanded = false; onTogglePinned() })
                    DropdownMenuItem(text = { Text(if (doc.isVaultLocked) "Remove from vault" else "Move to private vault") }, onClick = { menuExpanded = false; onToggleVault() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@Composable
private fun DocumentGridItem(doc: Document, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            DocumentThumbnail(doc, Modifier.fillMaxWidth().height(188.dp))
            Column(modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)) {
                Text(doc.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"} · ${prettyType(doc.type)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DocumentThumbnail(doc: Document, modifier: Modifier) {
    val file = doc.thumbnailPath?.let(::File)
    if (file != null && file.exists()) {
        AsyncImage(model = file, contentDescription = doc.title, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(12.dp)))
    } else {
        Surface(modifier = modifier.clip(RoundedCornerShape(12.dp)), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    when (doc.type) {
                        DocumentType.RECEIPT -> Icons.Default.ReceiptLong
                        DocumentType.ID_CARD, DocumentType.BUSINESS_CARD -> Icons.Default.CreditCard
                        DocumentType.BOOK -> Icons.Default.MenuBook
                        DocumentType.NOTE -> Icons.Default.Description
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun prettyType(type: DocumentType): String = when (type) {
    DocumentType.DOCUMENT -> "Document"
    DocumentType.RECEIPT -> "Receipt"
    DocumentType.BUSINESS_CARD -> "Business card"
    DocumentType.BOOK -> "Book"
    DocumentType.ID_CARD -> "ID card"
    DocumentType.NOTE -> "Note"
    DocumentType.WHITEBOARD -> "Whiteboard"
    DocumentType.QR_BARCODE -> "QR / barcode"
}
