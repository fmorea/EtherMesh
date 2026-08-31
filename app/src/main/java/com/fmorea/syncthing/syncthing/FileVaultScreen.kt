package com.fmorea.syncthing.syncthing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmorea.syncthing.model.Device
import com.fmorea.syncthing.R
import com.fmorea.syncthing.service.Constants
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class FileViewMode {
    GRID, LIST
}

enum class FileSortMode {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC, TYPE
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileVaultScreen(
    viewModel: LinkThingViewModel,
    modifier: Modifier = Modifier,
    initialFile: java.io.File? = null,
    initialCategory: String? = null,
    resetTrigger: Int = 0,
    onShowInChat: (LinkThingMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    var viewMode by remember { 
        mutableStateOf(FileViewMode.valueOf(viewModel.vaultViewMode)) 
    }
    var sortMode by remember { mutableStateOf(FileSortMode.TYPE) }
    var showExtensions by remember { mutableStateOf(true) }

    val labelComm = stringResource(R.string.category_messages)
    val labelNet = stringResource(R.string.category_network)
    val labelMedia = stringResource(R.string.category_media)
    val labelProfile = stringResource(R.string.category_profiles)
    
    // Holo-style colors

    var commSubFilter by remember { mutableStateOf("All") }
    var mediaSubFilter by remember { mutableStateOf("All") }
    
    var searchQuery by remember { mutableStateOf("") }
    var activeCategoryLabel by remember(initialCategory) { mutableStateOf(initialCategory) }
    
    // Auto-reset subfilters when category changes
    LaunchedEffect(activeCategoryLabel) {
        commSubFilter = "All"
        mediaSubFilter = "All"
    }

    var showConnectedDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isRegexSearch by remember { mutableStateOf(false) }
    
    var currentPath by remember { mutableStateOf(viewModel.getRootDir()) }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            activeCategoryLabel = null
            searchQuery = ""
            isSearchExpanded = false
            currentPath = viewModel.getRootDir()
        }
    }
    var highlightedFile by remember { mutableStateOf<File?>(null) }
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    val isSelectionMode = selectedFiles.isNotEmpty()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(initialFile) {
        initialFile?.let {
            if (it.exists()) {
                if (it.isDirectory) {
                    currentPath = it
                    highlightedFile = null
                } else {
                    currentPath = it.parentFile ?: viewModel.getRootDir()
                    highlightedFile = it
                }
                viewMode = FileViewMode.GRID
                searchQuery = ""
            }
        }
    }


    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showAddOptions by remember { mutableStateOf(false) }
    var showToolsMenu by remember { mutableStateOf(false) }

    var selectedDeviceForDetails by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val id = viewModel.getLocalDeviceId()
                    val timestamp = System.currentTimeMillis()
                    var fileName = "file_$timestamp"
                    context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    
                    val destFile = File(currentPath, "${timestamp}_${id}_$fileName")
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    currentPath = File(currentPath.absolutePath)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, context.getString(R.string.copy_exception), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    var editingFile by remember { mutableStateOf<File?>(null) }
    var viewingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isEditorPreviewMode by remember { mutableStateOf(false) }
    var showEditorMetadata by remember { mutableStateOf(false) }
    var editorContentToSave by remember { mutableStateOf("") }
    
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var filesToDelete by remember { mutableStateOf<Set<File>>(emptySet()) }
    
    var clipboardFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var isClipboardMove by remember { mutableStateOf(false) }
    var fileToDetails by remember { mutableStateOf<File?>(null) }

    BackHandler(enabled = editingFile != null || viewingProfile != null || isSearchExpanded || isSelectionMode || activeCategoryLabel != null || currentPath != viewModel.getRootDir()) {
        if (isSelectionMode) {
            selectedFiles = emptySet()
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
        } else if (editingFile != null) {
            editingFile = null
            highlightedFile = null
        } else if (viewingProfile != null) {
            viewingProfile = null
        } else if (activeCategoryLabel != null) {
            activeCategoryLabel = null
            searchQuery = ""
            commSubFilter = "All"
        } else {
            currentPath = currentPath.parentFile ?: viewModel.getRootDir()
        }
    }

    val allFilesOnDisk = remember(currentPath, activeCategoryLabel) {
        val root = viewModel.getRootDir()
        val myId = viewModel.getLocalDeviceId()
        
        val rawList = if (activeCategoryLabel != null) {
            when (activeCategoryLabel) {
                labelNet -> root.listFiles { _, name -> name.lowercase().endsWith(".net") }?.toList() ?: emptyList()
                labelProfile -> root.listFiles { _, name -> name.lowercase().endsWith(".info") }?.toList() ?: emptyList()
                labelComm -> root.walkTopDown().filter { it.isFile && (it.extension.lowercase() == "msg" || it.extension.lowercase() == "ack" || it.extension.lowercase() == "mail") }.toList()
                labelMedia -> {
                    val special = listOf("msg", "ack", "net", "info", "mail")
                    root.walkTopDown().filter { it.isFile && it.extension.lowercase() !in special }.toList()
                }
                else -> root.walkTopDown().filter { it.isFile || it.isDirectory }.toList()
            }
        } else {
            currentPath.listFiles()?.toList() ?: emptyList()
        }

        // PRIVACY FILTER: Only show private messages (.mail) if involving current user
        rawList.filter { file ->
            if (file.name.endsWith(".mail")) {
                val parts = file.name.split("_")
                if (parts.size >= 3) {
                    val senderId = parts[1]
                    val recipientId = parts[2].substringBefore(".")
                    senderId == myId || recipientId == myId
                } else false // Invalid mail filename, hide it
            } else true
        }
    }
    
    val filteredFiles = remember(allFilesOnDisk, sortMode, searchQuery, isRegexSearch, activeCategoryLabel, commSubFilter, mediaSubFilter) {
        val filtered = allFilesOnDisk.filter { file ->
            // ... (keep existing category and search filter logic)
            val matchesCategory = when (activeCategoryLabel) {
                labelComm -> {
                    val isMsg = file.extension.lowercase() == "msg"
                    val isAck = file.extension.lowercase() == "ack"
                    if (!isMsg && !isAck) return@filter false
                    
                    val isReply = isMsg && file.name.split("_").size >= 4
                    val isDirectMsg = isMsg && !isReply
                    
                    when (commSubFilter) {
                        "Messages" -> isDirectMsg
                        "Replies" -> isReply
                        "Acks" -> isAck
                        else -> true
                    }
                }
                labelNet -> file.extension.lowercase() == "net"
                labelMedia -> {
                    val ext = file.extension.lowercase()
                    val special = listOf("msg", "ack", "net", "info")
                    val isMedia = ext !in special && !file.isDirectory
                    if (!isMedia) return@filter false
                    
                    if (mediaSubFilter == "All") true
                    else ext == mediaSubFilter.removePrefix(".")
                }
                labelProfile -> file.extension.lowercase() == "info"
                else -> true
            }
            if (!matchesCategory) return@filter false

            if (searchQuery.isBlank()) true
            else {
                val queries = searchQuery.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
                val (excludeQueries, includeQueries) = queries.partition { it.startsWith("-") }
                
                if (excludeQueries.any { 
                    val extToExclude = it.removePrefix("-")
                    file.extension.equals(extToExclude, ignoreCase = true) 
                }) return@filter false
                
                if (includeQueries.isEmpty()) true
                else if (includeQueries.size > 1 && !isRegexSearch) {
                    includeQueries.any { q -> file.name.contains(q, ignoreCase = true) }
                } else if (isRegexSearch) {
                    try { Regex(searchQuery, RegexOption.IGNORE_CASE).containsMatchIn(file.name) } catch (e: Exception) { file.name.contains(searchQuery, ignoreCase = true) }
                } else {
                    file.name.contains(includeQueries[0], ignoreCase = true)
                }
            }
        }
        
        filtered.sortedWith { f1, f2 ->
            if (f1.isDirectory && !f2.isDirectory) return@sortedWith -1
            if (!f1.isDirectory && f2.isDirectory) return@sortedWith 1
            
            when (sortMode) {
                FileSortMode.TYPE -> {
                    val res = f1.extension.lowercase().compareTo(f2.extension.lowercase())
                    if (res != 0) res else f1.name.lowercase().compareTo(f2.name.lowercase())
                }
                FileSortMode.NAME_ASC -> f1.name.lowercase().compareTo(f2.name.lowercase())
                FileSortMode.NAME_DESC -> f2.name.lowercase().compareTo(f1.name.lowercase())
                FileSortMode.DATE_ASC -> f1.lastModified().compareTo(f2.lastModified())
                FileSortMode.DATE_DESC -> f2.lastModified().compareTo(f1.lastModified())
                FileSortMode.SIZE_ASC -> f1.length().compareTo(f2.length())
                FileSortMode.SIZE_DESC -> f2.length().compareTo(f1.length())
            }
        }
    }

    LaunchedEffect(highlightedFile, filteredFiles) {
        if (highlightedFile != null) {
            val index = filteredFiles.indexOfFirst { it.absolutePath == highlightedFile?.absolutePath }
            if (index >= 0) {
                if (viewMode == FileViewMode.GRID) {
                    gridState.animateScrollToItem(index)
                } else if (viewMode == FileViewMode.LIST) {
                    listState.animateScrollToItem(index)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FileVaultTopBar(
                currentPath = currentPath,
                rootDir = viewModel.getRootDir(),
                isDashboard = false,
                viewMode = viewMode,
                searchQuery = searchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                isRegexSearch = isRegexSearch,
                onSearchQueryChange = { 
                    searchQuery = it
                    if (it.isEmpty()) activeCategoryLabel = null
                },
                onToggleRegex = { isRegexSearch = !isRegexSearch },
                onBack = { 
                    if (isSearchExpanded) { 
                        isSearchExpanded = false
                        searchQuery = "" 
                        activeCategoryLabel = null
                    } else if (editingFile != null) {
                        editingFile = null
                        highlightedFile = null
                    } else if (highlightedFile != null) {
                        highlightedFile = null
                    } else if (currentPath == viewModel.getRootDir()) { 
                        // Stay in browser mode at root
                    } else { 
                        currentPath = currentPath.parentFile ?: viewModel.getRootDir() 
                    }
                },
                onHomeClick = { 
                    searchQuery = ""
                    activeCategoryLabel = null
                    isSearchExpanded = false
                    currentPath = viewModel.getRootDir()
                },
                activeCategoryLabel = activeCategoryLabel,
                onClearCategory = { 
                    activeCategoryLabel = null
                    searchQuery = ""
                },
                onPathEdit = { newVirtualPath ->
                    val targetFile = if (newVirtualPath == "/" || newVirtualPath.isBlank()) {
                        viewModel.getRootDir()
                    } else if (newVirtualPath.startsWith("linkthing://drive?path=")) {
                        val path = newVirtualPath.removePrefix("linkthing://drive?path=")
                        File(viewModel.getRootDir(), path)
                    } else {
                        val cleanPath = newVirtualPath.trim().removePrefix("/")
                        File(viewModel.getRootDir(), cleanPath)
                    }
                    
                    if (targetFile.exists()) {
                        if (targetFile.isDirectory) {
                            currentPath = targetFile
                        } else {
                            handleFileClick(targetFile, context, viewModel, { currentPath = it }, { editingFile = it })
                        }
                    } else {
                        val msg = context.getString(R.string.invalid_path)
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigateTo = { folder ->
                    currentPath = folder
                },
                friends = friends,
                onConnectedClick = { showConnectedDialog = true },
                editingFile = editingFile,
                isEditorPreviewMode = isEditorPreviewMode,
                showEditorMetadata = showEditorMetadata,
                onToggleEditorPreview = { isEditorPreviewMode = !isEditorPreviewMode },
                onToggleEditorMetadata = { showEditorMetadata = !showEditorMetadata },
                onSaveEditor = {
                    editingFile?.let { file ->
                        try {
                            file.writeText(editorContentToSave)
                            android.widget.Toast.makeText(context, context.getString(R.string.toast_file_saved), android.widget.Toast.LENGTH_SHORT).show()
                            currentPath = File(currentPath.absolutePath)
                        } catch (e: Exception) {
                            val msg = context.getString(R.string.error_saving)
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onShowEditorInChat = {
                    editingFile?.let { file ->
                        val msg = viewModel.findMessageForFile(file)
                        if (msg != null) onShowInChat(msg)
                    }
                },
                syncStatus = viewModel.syncStatus.collectAsStateWithLifecycle().value,
                onSyncClick = { viewModel.forceSync() },
                onPaste = {
                    scope.launch {
                        try {
                            clipboardFiles.forEach { source ->
                                val dest = File(currentPath, source.name)
                                if (isClipboardMove) {
                                    source.renameTo(dest)
                                } else {
                                    if (source.isDirectory) source.copyRecursively(dest, overwrite = true)
                                    else source.copyTo(dest, overwrite = true)
                                }
                            }
                            if (isClipboardMove) clipboardFiles = emptySet()
                            currentPath = File(currentPath.absolutePath)
                            android.widget.Toast.makeText(context, "Operazione completata", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Errore durante l'operazione", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                hasClipboard = clipboardFiles.isNotEmpty(),
                showExtensions = showExtensions,
                onToggleExtensions = { showExtensions = !showExtensions },
                onToggleView = { 
                    viewMode = if (viewMode == FileViewMode.GRID) FileViewMode.LIST else FileViewMode.GRID 
                    viewModel.vaultViewMode = viewMode.name
                },
                onSort = { sortMode = it },
                onShowId = { viewModel.showMyId() },
                onOpenWebGui = { viewModel.openWebGui() },
                onOpenChess = {
                    val gameFile = viewModel.shareChessGame()
                    if (gameFile != null) {
                        val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", gameFile
                            )
                            data = uri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                }
            )
        },
        bottomBar = {
            if (editingFile == null) {
                if (isSelectionMode) {
                    SelectionToolbar(
                        selectedCount = selectedFiles.size,
                        onCopy = {
                            clipboardFiles = selectedFiles
                            isClipboardMove = false
                            selectedFiles = emptySet()
                        },
                        onCut = {
                            clipboardFiles = selectedFiles
                            isClipboardMove = true
                            selectedFiles = emptySet()
                        },
                        onDelete = {
                            filesToDelete = selectedFiles
                        },
                        onShare = {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND_MULTIPLE
                                val uris = ArrayList(selectedFiles.filter { !it.isDirectory }.map {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.provider", it
                                    )
                                })
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                type = "*/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                            selectedFiles = emptySet()
                        },
                        onClear = { selectedFiles = emptySet() }
                    )
                } else {
                    FileVaultBottomToolbar(
                        onNew = { showAddOptions = true },
                        onSearch = { isSearchExpanded = true },
                        onRefresh = { viewModel.forceSync() },
                        onView = { 
                            viewMode = if (viewMode == FileViewMode.GRID) FileViewMode.LIST else FileViewMode.GRID 
                            viewModel.vaultViewMode = viewMode.name
                        },
                        onTools = { showToolsMenu = true }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = if (editingFile != null) "EDITOR" to editingFile else "BROWSER" to null,
                transitionSpec = {
                    if (!DevicePerformance.useHeavyAnimations) {
                        return@AnimatedContent fadeIn() togetherWith fadeOut()
                    }
                    val ease = FastOutSlowInEasing
                    if (targetState.first == "EDITOR") {
                        (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(350, easing = ease)) + fadeIn(tween(350))).togetherWith(
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(350, easing = ease)) + fadeOut(tween(350))
                        )
                    } else if (initialState.first == "EDITOR") {
                        (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(350, easing = ease)) + fadeIn(tween(350))).togetherWith(
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(350, easing = ease)) + fadeOut(tween(350))
                        )
                    } else {
                        (fadeIn(animationSpec = tween(250, easing = ease)) + scaleIn(initialScale = 0.92f, animationSpec = tween(250, easing = ease))).togetherWith(
                            fadeOut(animationSpec = tween(250, easing = ease)) + scaleOut(targetScale = 0.92f, animationSpec = tween(250, easing = ease))
                        )
                    }
                },
                label = "VaultContentTransition"
            ) { (targetState, targetFile) ->
                when (targetState) {
                    "EDITOR" -> {
                        InternalTextEditor(
                            file = targetFile!!,
                            onDismiss = { 
                                if (highlightedFile != null) {
                                    highlightedFile = null
                                }
                                editingFile = null 
                            },
                            onSave = { editorContentToSave = it },
                            onShowInChat = { msg ->
                                editingFile = null
                                onShowInChat(msg)
                            },
                            searchQuery = searchQuery,
                            isPreviewMode = isEditorPreviewMode,
                            showMetadata = showEditorMetadata,
                            onPreviewModeChange = { isEditorPreviewMode = it },
                            onMetadataToggle = { showEditorMetadata = !showEditorMetadata }
                        )
                    }
                    "BROWSER" -> {
                        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            FileVaultListHeader(
                                currentPath = currentPath,
                                rootDir = viewModel.getRootDir(),
                                viewMode = viewMode,
                                onToggleView = { 
                                    viewMode = if (viewMode == FileViewMode.GRID) FileViewMode.LIST else FileViewMode.GRID 
                                    viewModel.vaultViewMode = viewMode.name
                                },
                                sortMode = sortMode,
                                onSort = { sortMode = it },
                                onGoUp = {
                                    if (currentPath != viewModel.getRootDir()) {
                                        currentPath = currentPath.parentFile ?: viewModel.getRootDir()
                                    }
                                },
                                horizontalScrollState = horizontalScrollState
                            )
                            
                            if (activeCategoryLabel == labelComm) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val filters = listOf(
                                        stringResource(R.string.none) to "All",
                                        stringResource(R.string.category_messages) to "Messages",
                                        stringResource(R.string.category_replies) to "Replies",
                                        stringResource(R.string.category_acks) to "Acks"
                                    )
                                    filters.forEach { (label, value) ->
                                        FilterChip(
                                            selected = commSubFilter == value,
                                            onClick = { commSubFilter = value },
                                            label = { Text(label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }

                            if (activeCategoryLabel == labelMedia) {
                                val availableExtensions = remember(allFilesOnDisk) {
                                    val special = listOf("msg", "ack", "net", "info")
                                    allFilesOnDisk.filter { it.isFile && it.extension.lowercase() !in special }
                                        .map { "." + it.extension.lowercase() }
                                        .distinct()
                                        .sorted()
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = mediaSubFilter == "All",
                                        onClick = { mediaSubFilter = "All" },
                                        label = { Text(stringResource(R.string.none)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                    availableExtensions.forEach { ext ->
                                        FilterChip(
                                            selected = mediaSubFilter == ext,
                                            onClick = { mediaSubFilter = ext },
                                            label = { Text(ext) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                            
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                AnimatedContent(
                                    targetState = when {
                                        activeCategoryLabel == labelNet -> "NET"
                                        activeCategoryLabel == labelProfile -> "RUBRICA"
                                        filteredFiles.isEmpty() -> "EMPTY"
                                        else -> "LIST_${currentPath.absolutePath}_${viewMode.name}"
                                    },
                                    transitionSpec = {
                                        fadeIn(tween(250, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(250, easing = FastOutSlowInEasing))
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "BrowserContentTransition"
                                ) { browserState ->
                                    when {
                                        browserState == "NET" -> {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                item(contentType = "topology") {
                                                    Box(modifier = Modifier.height(400.dp).fillMaxWidth()) {
                                                        NetworkGraphView(
                                                            viewModel = viewModel, 
                                                            modifier = Modifier.fillMaxSize(),
                                                            onNodeClick = { selectedDeviceForDetails = it }
                                                        )
                                                    }
                                                }
                                                item(contentType = "explanation") {
                                                    CliqueExplanationBanner()
                                                }
                                                item(contentType = LinkThingContentTypes.DATE_HEADER) {
                                                    SectionHeader("File di Rete (.net)")
                                                }
                                                items(
                                                    filteredFiles, 
                                                    key = { it.absolutePath },
                                                    contentType = { LinkThingContentTypes.ATTACHMENT }
                                                ) { file ->
                                                    val isSelected = selectedFiles.contains(file)
                                                    val isHighlighted = highlightedFile?.absolutePath == file.absolutePath
                                                    FileVaultItem(
                                                        file = file,
                                                        viewMode = FileViewMode.LIST,
                                                        highlighted = isHighlighted,
                                                        selected = isSelected,
                                                        showExtension = showExtensions,
                                                        onTap = { 
                                                            if (isSelectionMode) {
                                                                selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                                                            } else {
                                                                highlightedFile = null
                                                                handleFileClick(file, context, viewModel, { currentPath = it }, { editingFile = it }) 
                                                            }
                                                        },
                                                        onLongClick = {
                                                            selectedFiles = selectedFiles + file
                                                        },
                                                        onRename = { fileToRename = it; renameValue = it.name },
                                                        onDelete = { filesToDelete = setOf(it) },
                                                        onCopy = { clipboardFiles = setOf(it); isClipboardMove = false },
                                                        onCut = { clipboardFiles = setOf(it); isClipboardMove = true },
                                                        onShowInChat = { target ->
                                                            val msg = viewModel.findMessageForFile(target)
                                                            if (msg != null) onShowInChat(msg)
                                                            else {
                                                                val errorMsg = context.getString(R.string.message_not_found)
                                                                android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        onDetails = { fileToDetails = it },
                                                        rootDir = viewModel.getRootDir(),
                                                        horizontalScrollState = horizontalScrollState
                                                    )
                                                }
                                                item {
                                                    Spacer(modifier = Modifier.height(32.dp))
                                                }
                                            }
                                        }
                                        browserState == "RUBRICA" -> {
                                            val uniqueIdentities = remember(filteredFiles) {
                                                val validProfiles = filteredFiles.mapNotNull { file ->
                                                    try {
                                                        val profile = UserProfile.loadFromFile(file)
                                                        if (profile.deviceId.isNotBlank()) profile.deviceId to (file to profile)
                                                        else null
                                                    } catch (e: Exception) { null }
                                                }
                                                
                                                if (validProfiles.isEmpty()) emptyList<Pair<File, UserProfile>>()
                                                else {
                                                    validProfiles.groupBy { it.first }
                                                        .mapNotNull { group -> 
                                                            group.value.maxByOrNull { it.second.first.lastModified() }?.second 
                                                        }
                                                        .sortedBy { it.second.getDisplayName().lowercase() }
                                                }
                                            }

                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                item(contentType = LinkThingContentTypes.DATE_HEADER) {
                                                    Text(
                                                        "Identità verificate",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                }
                                                items(
                                                    uniqueIdentities, 
                                                    key = { it.second.deviceId },
                                                    contentType = { "profile" }
                                                ) { (file, profile) ->
                                                    RubricaCard(
                                                        file = file,
                                                        onClick = { viewingProfile = profile },
                                                        onDelete = { filesToDelete = setOf(file) }
                                                    )
                                                }
                                            }
                                        }
                                        browserState == "EMPTY" -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                    Text(stringResource(R.string.no_files_found), color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }
                                        else -> {
                                        if (viewMode == FileViewMode.GRID) {
                                            LazyVerticalGrid(
                                                state = gridState,
                                                columns = GridCells.Fixed(4),
                                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                                contentPadding = PaddingValues(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                items(
                                                    filteredFiles, 
                                                    key = { it.absolutePath },
                                                    contentType = { if (it.isDirectory) "folder" else LinkThingContentTypes.ATTACHMENT }
                                                ) { file ->
                                                    FileVaultItem(
                                                        file = file,
                                                        viewMode = viewMode,
                                                        highlighted = highlightedFile?.absolutePath == file.absolutePath,
                                                        selected = selectedFiles.contains(file),
                                                        showExtension = showExtensions,
                                                        onTap = { 
                                                            if (isSelectionMode) {
                                                                selectedFiles = if (selectedFiles.contains(file)) selectedFiles - file else selectedFiles + file
                                                            } else {
                                                                highlightedFile = null
                                                                handleFileClick(file, context, viewModel, { currentPath = it }, { editingFile = it }) 
                                                            }
                                                        },
                                                        onLongClick = {
                                                            selectedFiles = selectedFiles + file
                                                        },
                                                        onRename = { fileToRename = it; renameValue = it.name },
                                                        onDelete = { filesToDelete = setOf(it) },
                                                        onCopy = { clipboardFiles = setOf(it); isClipboardMove = false },
                                                        onCut = { clipboardFiles = setOf(it); isClipboardMove = true },
                                                        onShowInChat = { target ->
                                                            val msg = viewModel.findMessageForFile(target)
                                                            if (msg != null) onShowInChat(msg)
                                                        },
                                                        onDetails = { fileToDetails = it },
                                                        rootDir = viewModel.getRootDir(),
                                                        horizontalScrollState = horizontalScrollState
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                state = listState,
                                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                                            ) {
                                                items(
                                                    filteredFiles,
                                                    key = { it.absolutePath },
                                                    contentType = { if (it.isDirectory) "folder" else LinkThingContentTypes.ATTACHMENT }
                                                ) { file ->
                                                    FileVaultItem(
                                                        file = file,
                                                        viewMode = viewMode,
                                                        highlighted = highlightedFile?.absolutePath == file.absolutePath,
                                                        selected = selectedFiles.contains(file),
                                                        showExtension = showExtensions,
                                                        onTap = { 
                                                            if (isSelectionMode) {
                                                                selectedFiles = if (selectedFiles.contains(file)) selectedFiles - file else selectedFiles + file
                                                            } else {
                                                                highlightedFile = null
                                                                handleFileClick(file, context, viewModel, { currentPath = it }, { editingFile = it }) 
                                                            }
                                                        },
                                                        onLongClick = {
                                                            selectedFiles = selectedFiles + file
                                                        },
                                                        onRename = { fileToRename = it; renameValue = it.name },
                                                        onDelete = { filesToDelete = setOf(it) },
                                                        onCopy = { clipboardFiles = setOf(it); isClipboardMove = false },
                                                        onCut = { clipboardFiles = setOf(it); isClipboardMove = true },
                                                        onShowInChat = { target ->
                                                            val msg = viewModel.findMessageForFile(target)
                                                            if (msg != null) onShowInChat(msg)
                                                        },
                                                        onDetails = { fileToDetails = it },
                                                        rootDir = viewModel.getRootDir(),
                                                        horizontalScrollState = horizontalScrollState
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    }
                                }

                                // Access Tab (deduced function: summary/status panel)
                                if (!isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                            .clickable { showConnectedDialog = true }
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.access_tab_up),
                                            contentDescription = "Status",
                                            modifier = Modifier.size(width = 64.dp, height = 24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showAddOptions) {
        AlertDialog(
            onDismissRequest = { showAddOptions = false },
            title = { Text(stringResource(R.string.add)) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.new_folder)) },
                        leadingContent = { Icon(Icons.Default.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { 
                            showAddOptions = false
                            showCreateFolderDialog = true 
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.new_file)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { 
                            showAddOptions = false
                            showCreateFileDialog = true 
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.attach_file)) },
                        leadingContent = { Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { 
                            showAddOptions = false
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptions = false }) {
                    Text(stringResource(R.string.cancel_title))
                }
            }
        )
    }

    if (showToolsMenu) {
        AlertDialog(
            onDismissRequest = { showToolsMenu = false },
            title = { Text("Strumenti") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Seleziona Tutto") },
                        leadingContent = { Icon(painterResource(R.drawable.toolbar_edit_selectall), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
                        modifier = Modifier.clickable { 
                            selectedFiles = filteredFiles.toSet()
                            showToolsMenu = false 
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Deseleziona Tutto") },
                        leadingContent = { Icon(painterResource(R.drawable.toolbar_edit_selectnone), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
                        modifier = Modifier.clickable { 
                            selectedFiles = emptySet()
                            showToolsMenu = false 
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Analisi Memoria") },
                        leadingContent = { Icon(painterResource(R.drawable.toolbar_analyse), null, tint = Color.Gray, modifier = Modifier.size(28.dp)) },
                        modifier = Modifier.clickable { 
                            showToolsMenu = false 
                            // Analysis logic...
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showToolsMenu = false }) {
                    Text(stringResource(R.string.cancel_title))
                }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(stringResource(R.string.new_file)) },
            text = { 
                TextField(
                    value = newFileName, 
                    onValueChange = { newFileName = it }, 
                    placeholder = { Text(stringResource(R.string.filename_hint)) }, 
                    singleLine = true 
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFileName.isNotBlank()) {
                        val id = viewModel.getLocalDeviceId()
                        val timestamp = System.currentTimeMillis()
                        val finalName = if (newFileName.contains(".")) newFileName else "$newFileName.msg"
                        val file = File(currentPath, "${timestamp}_${id}_$finalName")
                        try {
                            file.createNewFile()
                            currentPath = File(currentPath.absolutePath)
                            editingFile = file
                            showCreateFileDialog = false
                            newFileName = ""
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.file_creation_error), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateFileDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)) { Text(stringResource(R.string.cancel_title)) } }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = { TextField(value = newFolderName, onValueChange = { newFolderName = it }, placeholder = { Text(stringResource(R.string.folder_name_hint)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        File(currentPath, newFolderName).mkdirs()
                        currentPath = File(currentPath.absolutePath) 
                        showCreateFolderDialog = false
                        newFolderName = ""
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)) { Text(stringResource(R.string.cancel_title)) } }
        )
    }

    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { TextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val dest = File(fileToRename!!.parentFile, renameValue)
                    if (fileToRename!!.renameTo(dest)) {
                        currentPath = File(currentPath.absolutePath)
                        fileToRename = null
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = { TextButton(onClick = { fileToRename = null }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)) { Text(stringResource(R.string.cancel_title)) } }
        )
    }

    if (showConnectedDialog) {
        val connectedFriends = friends.filter { it.numConnections > 0 }
        AlertDialog(
            onDismissRequest = { showConnectedDialog = false },
            title = { Text(stringResource(R.string.online_users)) },
            text = {
                Column {
                    if (connectedFriends.isEmpty()) {
                        Text(stringResource(R.string.no_users_online), color = MaterialTheme.colorScheme.outline)
                    } else {
                        connectedFriends.forEach { friend ->
                            ListItem(
                                headlineContent = { Text(friend.getDisplayName()) },
                                leadingContent = { Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                supportingContent = { Text("${stringResource(R.string.connected)} • ${friend.deviceID.take(8)}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectedDialog = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    if (filesToDelete.isNotEmpty()) {
        val isProfile = filesToDelete.size == 1 && filesToDelete.first().extension.lowercase() == "info"
        val myId = viewModel.getLocalDeviceId()
        val isBootstrapper = Constants.isBootstrapId(myId)
        
        AlertDialog(
            onDismissRequest = { filesToDelete = emptySet() },
            title = { Text(if (isProfile && isBootstrapper) "Banna Utente" else stringResource(R.string.delete)) },
            text = { 
                if (isProfile && isBootstrapper) {
                    Text("Stai eliminando l'identità di un altro utente come moderatore. Questo creerà un file di BAN per escluderlo permanentemente dalla rete. Confermi?")
                } else {
                    Text(if (filesToDelete.size == 1) stringResource(R.string.delete_confirm_msg, filesToDelete.first().name) else "Sei sicuro di voler eliminare ${filesToDelete.size} elementi?") 
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isProfile) {
                            val profile = UserProfile.loadFromFile(filesToDelete.first())
                            viewModel.deleteIdentity(profile.deviceId, profile.discloserId)
                        } else {
                            filesToDelete.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
                        }
                        currentPath = File(currentPath.absolutePath)
                        filesToDelete = emptySet()
                        selectedFiles = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isProfile && isBootstrapper) "BANNA E ELIMINA" else stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { filesToDelete = emptySet() }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)) { Text(stringResource(R.string.cancel_title)) } }
        )
    }

    if (viewingProfile != null) {
        val myId = viewModel.getLocalDeviceId()
        EditProfileDialog(
            profile = viewingProfile!!,
            isMe = viewingProfile!!.deviceId == myId,
            onDismiss = { viewingProfile = null },
            onSave = { 
                if (viewingProfile!!.deviceId == myId) viewModel.updateMyProfile(it)
                else viewModel.updateFriendProfile(viewingProfile!!.deviceId, it)
            },
            onPhotoSelected = { viewModel.updateMyPhoto(it) }
        )
    }

    if (fileToDetails != null) {
        val file = fileToDetails!!
        var currentPermissions by remember(file) { 
            mutableStateOf(if (file.canRead()) "r" else "-")
        }
        LaunchedEffect(file) {
            val r = if (file.canRead()) "r" else "-"
            val w = if (file.canWrite()) "w" else "-"
            val x = if (file.canExecute()) "x" else "-"
            currentPermissions = "$r$w$x"
        }

        AlertDialog(
            onDismissRequest = { fileToDetails = null },
            title = { Text("Proprietà File") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("Nome") },
                        supportingContent = { Text(file.name) },
                        leadingContent = { Icon(Icons.Default.Description, null, tint = Color(0xFF0099CC)) }
                    )
                    ListItem(
                        headlineContent = { Text("Percorso") },
                        supportingContent = { Text(file.absolutePath) },
                        leadingContent = { Icon(Icons.Default.Folder, null, tint = Color(0xFF03A9F4)) }
                    )
                    ListItem(
                        headlineContent = { Text("Dimensione") },
                        supportingContent = { Text(formatSize(file.length())) },
                        leadingContent = { Icon(painterResource(R.drawable.toolbar_analyse), null, tint = Color.Gray) }
                    )
                    ListItem(
                        headlineContent = { Text("Ultima Modifica") },
                        supportingContent = { Text(SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.lastModified()))) },
                        leadingContent = { Icon(Icons.Default.Schedule, null, tint = Color.Gray) }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        "Permessi Unix",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PermissionToggle("Lettura", file.canRead()) { file.setReadable(it) }
                        PermissionToggle("Scrittura", file.canWrite()) { file.setWritable(it) }
                        PermissionToggle("Esecuzione", file.canExecute()) { file.setExecutable(it) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fileToDetails = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    DeviceDetailsDialog(
        selectedDeviceForDetails = selectedDeviceForDetails,
        onDismiss = { selectedDeviceForDetails = null },
        friends = friends,
        viewModel = viewModel
    )
}

@Composable
fun PermissionToggle(label: String, active: Boolean, onToggle: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(active) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Checkbox(checked = checked, onCheckedChange = { 
            onToggle(it)
            checked = it
        })
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

fun dummyFunctionJustToHelpSplit() {}

@Composable
fun DeviceDetailsDialog(
    selectedDeviceForDetails: String?,
    onDismiss: () -> Unit,
    friends: List<com.fmorea.syncthing.model.Device>,
    viewModel: LinkThingViewModel
) {
    if (selectedDeviceForDetails != null) {
        val deviceId = selectedDeviceForDetails!!
        val device = friends.find { it.deviceID == deviceId } ?: if (deviceId == viewModel.getLocalDeviceId()) viewModel.localDevice.collectAsStateWithLifecycle().value else null
        val profile = viewModel.friendProfiles.collectAsStateWithLifecycle().value[deviceId] ?: if (deviceId == viewModel.getLocalDeviceId()) viewModel.userProfile.collectAsStateWithLifecycle().value else null
        
        val qrBitmap = remember(deviceId) {
            try {
                val size = 512
                val bitMatrix = MultiFormatWriter().encode(deviceId, BarcodeFormat.QR_CODE, size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                bitmap
            } catch (e: Exception) {
                null
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(profile?.getDisplayName() ?: device?.getDisplayName() ?: deviceId.take(8)) },
            text = {
                // ... (content remains same)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(240.dp).padding(8.dp)
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code Device ID",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(150.dp), tint = Color.Black)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = deviceId, 
                        style = MaterialTheme.typography.bodySmall, 
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (profile != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.associated_profile), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${stringResource(R.string.profile_first_name)}: ${profile.firstName} ${profile.lastName}", style = MaterialTheme.typography.bodyMedium)
                                if (profile.address.isNotBlank()) Text("${stringResource(R.string.profile_address)}: ${profile.address}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.action_close)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileVaultTopBar(
    currentPath: File,
    rootDir: File,
    isDashboard: Boolean,
    viewMode: FileViewMode,
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    isRegexSearch: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleRegex: () -> Unit,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    onPathEdit: (String) -> Unit,
    onNavigateTo: (File) -> Unit,
    activeCategoryLabel: String? = null,
    onClearCategory: () -> Unit = {},
    friends: List<com.fmorea.syncthing.model.Device> = emptyList(),
    onConnectedClick: () -> Unit = {},
    editingFile: File? = null,
    isEditorPreviewMode: Boolean = false,
    showEditorMetadata: Boolean = false,
    onToggleEditorPreview: () -> Unit = {},
    onToggleEditorMetadata: () -> Unit = {},
    onSaveEditor: () -> Unit = {},
    onShowEditorInChat: () -> Unit = {},
    syncStatus: String = "",
    onSyncClick: () -> Unit = {},
    onPaste: () -> Unit = {},
    hasClipboard: Boolean = false,
    showExtensions: Boolean = true,
    onToggleExtensions: () -> Unit = {},
    onToggleView: () -> Unit = {},
    onSort: (FileSortMode) -> Unit = {},
    onShowId: () -> Unit = {},
    onOpenWebGui: () -> Unit = {},
    onOpenChess: () -> Unit = {}
) {
    var isPathEditing by remember { mutableStateOf(false) }
    var pathEditValue by remember { 
        mutableStateOf(
            if (currentPath == rootDir) "/" 
            else "/" + currentPath.absolutePath.removePrefix(rootDir.absolutePath).removePrefix(File.separator).replace(File.separator, "/")
        ) 
    }

    // Auto-update pathEditValue when currentPath changes
    LaunchedEffect(currentPath) {
        pathEditValue = if (currentPath == rootDir) "/" 
        else "/" + currentPath.absolutePath.removePrefix(rootDir.absolutePath).removePrefix(File.separator).replace(File.separator, "/")
    }

    val displayPath = remember(currentPath, rootDir) {
        val absoluteRoot = rootDir.absolutePath
        val absoluteCurrent = currentPath.absolutePath
        if (absoluteCurrent == absoluteRoot) {
            "/ > sdcard"
        } else if (absoluteCurrent.startsWith(absoluteRoot)) {
            "/ > sdcard" + absoluteCurrent.substring(absoluteRoot.length).replace(File.separator, " > ")
        } else {
            absoluteCurrent.replace(File.separator, " > ")
        }
    }

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        border = BorderStroke(0.5.dp, Color.LightGray)
    ) {
        if (isSearchExpanded) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSearchExpandedChange(false); onSearchQueryChange("") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                }
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.search_hint), color = Color.Gray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onHomeClick) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.menu_operating), 
                        null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            
                TextField(
                    value = pathEditValue,
                    onValueChange = { pathEditValue = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color(0xFF0078D7),
                        unfocusedIndicatorColor = Color.LightGray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    trailingIcon = {
                        if (pathEditValue != (if (currentPath == rootDir) "/" else "/" + currentPath.absolutePath.removePrefix(rootDir.absolutePath).removePrefix(File.separator).replace(File.separator, "/"))) {
                            IconButton(onClick = { onPathEdit(pathEditValue) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF0078D7), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                IconButton(onClick = { onSearchExpandedChange(true) }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.toolbar_search), 
                        null, 
                        tint = MaterialTheme.colorScheme.onSurface, 
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (hasClipboard) {
                    IconButton(onClick = onPaste) {
                        Icon(
                            painter = painterResource(R.drawable.toolbar_paste),
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onBack() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.toolbar_close), 
                            null, 
                            modifier = Modifier.size(18.dp), 
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionToolbar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.toolbar_close), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            }
            Text("$selectedCount selezionati", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            BottomToolbarItem(R.drawable.toolbar_edit_copy, "Copia", onCopy)
            BottomToolbarItem(R.drawable.toolbar_edit_cut, "Taglia", onCut)
            BottomToolbarItem(R.drawable.toolbar_edit_share, "Condividi", onShare)
            BottomToolbarItem(R.drawable.toolbar_edit_delete, "Elimina", onDelete)
        }
    }
}

@Composable
fun FileVaultBottomToolbar(
    onNew: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onView: () -> Unit,
    onTools: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomToolbarItem(R.drawable.toolbar_new, "Nuovo", onNew)
            BottomToolbarItem(R.drawable.toolbar_search, "Cerca", onSearch)
            BottomToolbarItem(R.drawable.toolbar_refresh, "Aggiorna", onRefresh)
            BottomToolbarItem(R.drawable.toolbar_view, "Vista", onView)
            BottomToolbarItem(R.drawable.toolbar_tool, "Strumenti", onTools)
        }
    }
}

@Composable
fun BottomToolbarItem(iconRes: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes), 
            null, 
            tint = MaterialTheme.colorScheme.onSurface, 
            modifier = Modifier.size(24.dp)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun FileVaultListHeader(
    currentPath: File,
    rootDir: File,
    viewMode: FileViewMode,
    onToggleView: () -> Unit,
    sortMode: FileSortMode,
    onSort: (FileSortMode) -> Unit,
    title: String? = null,
    showGoUp: Boolean = true,
    onGoUp: () -> Unit = {},
    horizontalScrollState: ScrollState = rememberScrollState()
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showGoUp && currentPath != rootDir) {
                    IconButton(onClick = onGoUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF0099CC), modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    text = title ?: currentPath.name.ifEmpty { "EtherMesh" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(start = if (showGoUp && currentPath != rootDir) 4.dp else 8.dp)
                )
            }
            
            Row {
                IconButton(onClick = onToggleView, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (viewMode == FileViewMode.GRID) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                        contentDescription = null,
                        tint = Color(0xFF0099CC),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.toolbar_sort), 
                            contentDescription = null, 
                            tint = Color(0xFF0099CC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.toolbar_sort_name_ascending), null, modifier = Modifier.size(18.dp)) },
                            text = { Text(stringResource(R.string.sort_name_asc), style = MaterialTheme.typography.bodySmall) }, 
                            onClick = { onSort(FileSortMode.NAME_ASC); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.toolbar_sort_time_descending), null, modifier = Modifier.size(18.dp)) },
                            text = { Text(stringResource(R.string.sort_date_desc), style = MaterialTheme.typography.bodySmall) }, 
                            onClick = { onSort(FileSortMode.DATE_DESC); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.toolbar_sort_size_descending), null, modifier = Modifier.size(18.dp)) },
                            text = { Text(stringResource(R.string.sort_size_desc), style = MaterialTheme.typography.bodySmall) }, 
                            onClick = { onSort(FileSortMode.SIZE_DESC); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.toolbar_sort_type_ascending), null, modifier = Modifier.size(18.dp)) },
                            text = { Text(stringResource(R.string.sort_type), style = MaterialTheme.typography.bodySmall) }, 
                            onClick = { onSort(FileSortMode.TYPE); showSortMenu = false }
                        )
                    }
                }
            }
        }

        if (viewMode == FileViewMode.LIST) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp
            val isWideScreen = screenWidth > 600

            Surface(
                color = Color(0xFFF0F0F0),
                border = BorderStroke(0.5.dp, Color.LightGray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (!isWideScreen) it.horizontalScroll(horizontalScrollState) else it }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(48.dp))
                    Text(
                        text = "Name", 
                        modifier = Modifier
                            .then(if (isWideScreen) Modifier.weight(1f) else Modifier.width(200.dp))
                            .padding(start = 8.dp), 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                        color = Color.Black
                    )
                    Text("Date modified", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                    Text("Type", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                    Text("Size", modifier = Modifier.width(70.dp).padding(end = 8.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black, textAlign = TextAlign.End)
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun getFileTypeDescription(file: File): String {
    if (file.isDirectory) return "Cartella"
    val ext = file.extension.lowercase()
    return when (ext) {
        "msg" -> "Messaggio Mesh"
        "ack" -> "Conferma Ricezione"
        "mail" -> "Email Privata"
        "net" -> "Topologia Rete"
        "info" -> "Profilo Utente"
        "cal" -> "Evento Calendario"
        "chess" -> "Partita Scacchi"
        "jpg", "jpeg", "png", "webp", "gif" -> "Immagine"
        "mp3", "m4a", "wav", "ogg" -> "Audio"
        "mp4", "mkv", "avi" -> "Video"
        "pdf" -> "Documento PDF"
        "txt", "md" -> "Testo"
        "zip", "rar", "7z", "tar", "gz" -> "Archivio"
        "apk" -> "App Android"
        "" -> "File"
        else -> "${ext.uppercase()} File"
    }
}

@Composable
fun CategoryItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.1f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun handleFileClick(
    file: File, 
    context: android.content.Context, 
    viewModel: LinkThingViewModel,
    onPathChange: (File) -> Unit,
    onEditFile: (File) -> Unit
) {
    if (file.isDirectory) {
        onPathChange(file)
    } else {
        val ext = file.extension.lowercase()
        val textExtensions = listOf("msg", "ack", "net", "chess", "mail", "txt", "log", "md", "json", "xml", "html")
        
        if (ext in textExtensions) {
            onEditFile(file)
        } else {
            com.fmorea.syncthing.util.FileUtils.openFile(context, file.absolutePath)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileVaultItem(
    file: File, 
    viewMode: FileViewMode,
    highlighted: Boolean = false,
    selected: Boolean = false,
    showExtension: Boolean = true,
    onTap: () -> Unit, 
    onLongClick: () -> Unit = {},
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onShowInChat: (File) -> Unit,
    onCopy: (File) -> Unit = {},
    onCut: (File) -> Unit = {},
    onDetails: (File) -> Unit = {},
    rootDir: File? = null,
    horizontalScrollState: ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }


    val defaultFolderIcons = mapOf(
        "Download" to R.drawable.logo_download,
        "kindle" to R.drawable.home_book,
        "Movies" to R.drawable.logo_video,
        "Music" to R.drawable.logo_music,
        "Notifications" to R.drawable.ic_stat_notify,
        "Pictures" to R.drawable.logo_pictures,
        "Podcasts" to R.drawable.icon_app_musicplayer,
        "Ringtones" to R.drawable.logo_ringtones,
        "DCIM" to R.drawable.logo_dcim,
        "Backups" to R.drawable.logo_backups,
        "Android" to R.drawable.logo_android
    )

    if (viewMode == FileViewMode.GRID) {
        Box {
            Column(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = { 
                            onLongClick()
                            showContextMenu = true 
                        }
                    )
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    if (file.isDirectory) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            null,
                            modifier = Modifier.size(58.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        defaultFolderIcons[file.name]?.let { iconRes ->
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(iconRes),
                                null,
                                modifier = Modifier.size(22.dp).offset(y = 4.dp),
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            null,
                            modifier = Modifier.size(52.dp),
                            tint = Color.Gray.copy(alpha = 0.6f)
                        )
                    }
                    
                    if (selected) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0099CC),
                            modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                val displayName = remember(file.name, showExtension) {
                    if (showExtension || file.isDirectory) file.name 
                    else file.name.substringBeforeLast(".", file.name)
                }
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black
                )
            }
            FileItemContextMenu(showContextMenu, { showContextMenu = it }, file, onShowInChat, onRename, onCopy, onCut, rootDir, context, onDelete, onDetails)
        }
    } else {
        // LIST VIEW
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isWideScreen = configuration.screenWidthDp > 600

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { 
                        onLongClick()
                        showContextMenu = true 
                    }
                ),
            color = if (selected) Color(0xFFCCE8FF) else Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (!isWideScreen) it.horizontalScroll(horizontalScrollState) else it }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).padding(start = 8.dp)) {
                    if (file.isDirectory) {
                        Icon(Icons.Filled.Folder, null, modifier = Modifier.size(24.dp), tint = Color(0xFF0099CC))
                    } else {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(24.dp), tint = Color(0xFF0099CC))
                    }
                    if (selected) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(12.dp).align(Alignment.TopEnd), tint = Color(0xFF0078D7))
                    }
                }
                
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .then(if (isWideScreen) Modifier.weight(1f) else Modifier.width(200.dp))
                        .padding(start = 16.dp),
                    color = Color.Black
                )

                Text(
                    text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.width(130.dp)
                )

                Text(
                    text = getFileTypeDescription(file),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.width(110.dp)
                )

                Text(
                    text = if (file.isDirectory) "" else formatSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.width(70.dp).padding(end = 8.dp),
                    textAlign = TextAlign.End
                )

                FileItemContextMenu(showContextMenu, { showContextMenu = it }, file, onShowInChat, onRename, onCopy, onCut, rootDir, context, onDelete, onDetails)
            }
        }
    }
}

@Composable
fun FileItemContextMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    file: File,
    onShowInChat: (File) -> Unit,
    onRename: (File) -> Unit,
    onCopy: (File) -> Unit,
    onCut: (File) -> Unit,
    rootDir: File?,
    context: android.content.Context,
    onDelete: (File) -> Unit,
    onDetails: (File) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.view_in_chat)) },
            onClick = {
                onExpandedChange(false)
                onShowInChat(file)
            },
            leadingIcon = { Icon(painterResource(R.drawable.menu_operating), null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename)) },
            onClick = {
                onExpandedChange(false)
                onRename(file)
            },
            leadingIcon = { Icon(painterResource(R.drawable.toolbar_edit_rename), null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text("Copia") },
            onClick = {
                onExpandedChange(false)
                onCopy(file)
            },
            leadingIcon = { Icon(painterResource(R.drawable.toolbar_edit_copy), null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text("Taglia") },
            onClick = {
                onExpandedChange(false)
                onCut(file)
            },
            leadingIcon = { Icon(painterResource(R.drawable.toolbar_edit_cut), null, modifier = Modifier.size(20.dp)) }
        )
        DropdownMenuItem(
            text = { Text("Crea Link") },
            onClick = {
                onExpandedChange(false)
                val virtualPath = if (rootDir != null && file.absolutePath.startsWith(rootDir.absolutePath)) {
                    file.absolutePath.substring(rootDir.absolutePath.length).replace(File.separator, "/")
                } else {
                    "/" + file.name
                }
                val link = "linkthing://drive?path=$virtualPath"
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("EtherMesh Link", link)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Link copiato negli appunti", android.widget.Toast.LENGTH_SHORT).show()
            },
            leadingIcon = { Icon(painterResource(R.drawable.toolbar_associate), null, modifier = Modifier.size(20.dp)) }
        )
        if (!file.isDirectory) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = {
                    onExpandedChange(false)
                    val intent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        type = context.contentResolver.getType(uri) ?: "*/*"
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, null))
                },
                leadingIcon = { Icon(painterResource(R.drawable.toolbar_edit_share), null, modifier = Modifier.size(20.dp)) }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            onClick = {
                onExpandedChange(false)
                onDelete(file)
            },
            leadingIcon = { Icon(painterResource(R.drawable.toolbar_edit_delete), null, modifier = Modifier.size(20.dp)) },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error
            )
        )
        DropdownMenuItem(
            text = { Text("Dettagli") },
            onClick = {
                onExpandedChange(false)
                onDetails(file)
            },
            leadingIcon = { Icon(painterResource(R.drawable.menu_property), null, modifier = Modifier.size(20.dp)) }
        )
    }
}

@Composable
fun RubricaCard(file: File, onClick: () -> Unit, onDelete: () -> Unit) {
    val profile = remember(file) { UserProfile.loadFromFile(file) }
    val context = LocalContext.current
    val myId = remember(context) {
        (context.applicationContext as? com.fmorea.syncthing.SyncthingApp)?.let { 
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(it).getString(Constants.PREF_LOCAL_DEVICE_ID, "") 
        } ?: ""
    }
    val isBootstrapper = Constants.isBootstrapId(myId)
    val isMine = profile.discloserId == myId

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).border(1.dp, Color(0xFF0099CC).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(deviceId = profile.deviceId, profile = profile, size = 64)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = profile.deviceId.take(16) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF0099CC),
                    fontFamily = FontFamily.Monospace
                )
                
                if (profile.country.isNotBlank() || profile.address.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(profile.country.ifBlank { null }, profile.address.ifBlank { null }).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                val discloserLabel = if (profile.discloserId == profile.deviceId) "Auto-dichiarato" else "Segnalato da: ${profile.discloserId.take(8)}"
                Text(
                    text = discloserLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }

            if (isBootstrapper || isMine) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = if (isBootstrapper && !isMine) Icons.Default.Gavel else Icons.Default.Delete, 
                        contentDescription = "Rimuovi", 
                        tint = if (isBootstrapper && !isMine) Color(0xFF0099CC) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun CliqueExplanationBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(1.dp, Color(0xFF0099CC).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Hub,
                contentDescription = null,
                tint = Color(0xFF0099CC),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Mesh Discovery (Clique)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0099CC)
                )
                Text(
                    text = "Questa rete tende a una Clique (Grafo Completo). Ogni nodo dichiara i propri vicini tramite file .net. In una rete di N nodi, a convergenza troverai N*(N-1) file, garantendo che ogni partecipante si connetta automaticamente a tutti gli altri senza server centrali.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun InfoFilePreview(file: File) {
    val profile = remember(file) {
        UserProfile.loadFromFile(file)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.size(64.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = profile.getDisplayName(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )
        }
    }
}
