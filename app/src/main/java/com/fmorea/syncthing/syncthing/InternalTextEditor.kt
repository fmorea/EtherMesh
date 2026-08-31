package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmorea.syncthing.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

data class FileMetadata(
    val timestamp: Long? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    val introducerId: String? = null,
    val introducedId: String? = null,
    val originalTimestamp: Long? = null,
    val originalSender: String? = null,
    val type: String,
    val profile: UserProfile? = null
)

class MarkdownVisualTransformation(
    val boldColor: Color,
    val italicColor: Color,
    val codeColor: Color,
    val searchHighlightColor: Color,
    val searchQuery: String,
    val extension: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = if (extension in listOf("msg", "ack", "mail", "md")) {
            highlightMarkdown(text.text)
        } else if (extension in listOf("json", "xml", "html", "net")) {
            highlightCode(text.text, extension)
        } else {
            AnnotatedString(text.text)
        }

        // Apply search highlights
        val finalResult = if (searchQuery.isNotBlank()) {
            buildAnnotatedString {
                append(highlighted)
                var index = highlighted.text.indexOf(searchQuery, ignoreCase = true)
                while (index >= 0) {
                    addStyle(
                        style = SpanStyle(background = searchHighlightColor),
                        start = index,
                        end = index + searchQuery.length
                    )
                    index = highlighted.text.indexOf(searchQuery, index + 1, ignoreCase = true)
                }
            }
        } else highlighted

        return TransformedText(finalResult, androidx.compose.ui.text.input.OffsetMapping.Identity)
    }

    private fun highlightMarkdown(content: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < content.length) {
                when {
                    content.startsWith("**", i) -> {
                        val end = content.indexOf("**", i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                                append(content.substring(i + 2, end))
                            }
                            i = end + 2
                        } else { append(content[i]); i++ }
                    }
                    content.startsWith("_", i) -> {
                        val end = content.indexOf("_", i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = italicColor)) {
                                append(content.substring(i + 1, end))
                            }
                            i = end + 1
                        } else { append(content[i]); i++ }
                    }
                    content.startsWith("`", i) -> {
                        val end = content.indexOf("`", i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeColor.copy(alpha = 0.1f), color = codeColor)) {
                                append(content.substring(i + 1, end))
                            }
                            i = end + 1
                        } else { append(content[i]); i++ }
                    }
                    else -> { append(content[i]); i++ }
                }
            }
        }
    }

    private fun highlightCode(content: String, ext: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = content.split("\n")
            lines.forEachIndexed { index, line ->
                when (ext) {
                    "json" -> {
                        var i = 0
                        while (i < line.length) {
                            val char = line[i]
                            when {
                                char == '"' -> {
                                    val end = line.indexOf('"', i + 1)
                                    if (end != -1) {
                                        val isKey = line.substring(end + 1).trimStart().startsWith(":")
                                        withStyle(SpanStyle(color = if (isKey) Color(0xFF2196F3) else Color(0xFF4CAF50))) {
                                            append(line.substring(i, end + 1))
                                        }
                                        i = end + 1
                                    } else { append(char); i++ }
                                }
                                char.isDigit() -> {
                                    withStyle(SpanStyle(color = Color(0xFFFF9800))) {
                                        append(char)
                                    }
                                    i++
                                }
                                else -> { append(char); i++ }
                            }
                        }
                    }
                    "xml", "html" -> {
                        var i = 0
                        while (i < line.length) {
                            val char = line[i]
                            if (char == '<') {
                                val end = line.indexOf('>', i)
                                if (end != -1) {
                                    withStyle(SpanStyle(color = Color(0xFFE91E63))) {
                                        append(line.substring(i, end + 1))
                                    }
                                    i = end + 1
                                } else { append(char); i++ }
                            } else { append(char); i++ }
                        }
                    }
                    else -> append(line)
                }
                if (index < lines.size - 1) append("\n")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalTextEditor(
    file: File,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onShowInChat: ((LinkThingMessage) -> Unit)? = null,
    searchQuery: String = "",
    isPreviewMode: Boolean = false,
    showMetadata: Boolean = false,
    onPreviewModeChange: (Boolean) -> Unit = {},
    onMetadataToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Simple Undo/Redo Stack
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    
    fun pushUndo(content: String) {
        if (undoStack.isEmpty() || undoStack.last() != content) {
            undoStack.add(content)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
        }
    }

    val metadata = remember(file) {
        val name = file.name
        val ext = file.extension.lowercase()
        val parts = name.removeSuffix(".$ext").split("_")
        
        when {
            ext == "msg" -> {
                if (parts.size >= 4) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        senderId = parts[1],
                        originalTimestamp = parts[2].toLongOrNull(),
                        originalSender = parts[3],
                        type = context.getString(R.string.type_msg_reply)
                    )
                } else if (parts.size >= 2) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        senderId = parts[1],
                        type = context.getString(R.string.type_msg)
                    )
                } else FileMetadata(type = context.getString(R.string.type_msg_unknown))
            }
            ext == "ack" -> {
                if (parts.size >= 3) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        originalSender = parts[1],
                        receiverId = parts[2],
                        type = context.getString(R.string.type_ack)
                    )
                } else FileMetadata(type = context.getString(R.string.type_ack_unknown))
            }
            ext == "net" -> {
                if (parts.size >= 3) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        introducerId = parts[1],
                        introducedId = parts[2],
                        type = "Discovery"
                    )
                } else FileMetadata(type = "Discovery Unknown")
            }
            else -> FileMetadata(type = ext.uppercase())
        }
    }

    val keywordColor = Color(0xFF2196F3)
    val boldColor = Color(0xFFFF9800)
    val italicColor = Color(0xFF9C27B0)
    val codeColor = Color(0xFFE91E63)

    val markdownTransformation = remember(searchQuery, file.extension, keywordColor, boldColor, italicColor, codeColor) {
        MarkdownVisualTransformation(
            boldColor = boldColor,
            italicColor = italicColor,
            codeColor = codeColor,
            searchHighlightColor = Color.Yellow.copy(alpha = 0.5f),
            searchQuery = searchQuery,
            extension = file.extension.lowercase()
        )
    }

    LaunchedEffect(file) {
        isLoading = true
        try {
            val content = file.readText()
            textValue = TextFieldValue(content)
            undoStack.clear()
            redoStack.clear()
            undoStack.add(content)
        } catch (e: Exception) {
            textValue = TextFieldValue(context.getString(R.string.error_loading_file, e.message))
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isLoading && !isPreviewMode) {
            Surface(tonalElevation = 4.dp, shadowElevation = 2.dp) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }

                        IconButton(onClick = { /* Files switch logic */ }) {
                            Icon(Icons.Default.FilterNone, "Files", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("edit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        
                        IconButton(onClick = { 
                            onSave(textValue.text)
                            Toast.makeText(context, R.string.toast_file_saved, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Save, "Save", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }

                        if (file.extension.lowercase() in listOf("msg", "ack") && onShowInChat != null) {
                            IconButton(onClick = { 
                                // Simplified LinkThingMessage creation for jumping to chat
                                val msg = LinkThingMessage(
                                    fileName = file.name,
                                    timestamp = metadata.timestamp ?: 0L,
                                    deviceId = metadata.senderId ?: "",
                                    content = textValue.text,
                                    file = file
                                )
                                onShowInChat(msg)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Chat, "Chat", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Jump to Start/End
                        IconButton(onClick = { textValue = textValue.copy(selection = TextRange(0)) }) {
                            Icon(Icons.Default.VerticalAlignTop, "Start", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { textValue = textValue.copy(selection = TextRange(textValue.text.length)) }) {
                            Icon(Icons.Default.VerticalAlignBottom, "End", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = {
                            val newText = "**${textValue.text.substring(textValue.selection.start, textValue.selection.end)}**"
                            textValue = textValue.copy(
                                text = textValue.text.replaceRange(textValue.selection.start, textValue.selection.end, newText),
                                selection = TextRange(textValue.selection.start + 2, textValue.selection.start + 2 + (textValue.selection.end - textValue.selection.start))
                            )
                        }) {
                            Icon(Icons.Default.FormatBold, "Bold", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            val newText = "_${textValue.text.substring(textValue.selection.start, textValue.selection.end)}_"
                            textValue = textValue.copy(
                                text = textValue.text.replaceRange(textValue.selection.start, textValue.selection.end, newText),
                                selection = TextRange(textValue.selection.start + 1, textValue.selection.start + 1 + (textValue.selection.end - textValue.selection.start))
                            )
                        }) {
                            Icon(Icons.Default.FormatItalic, "Italic", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            val newText = "`${textValue.text.substring(textValue.selection.start, textValue.selection.end)}`"
                            textValue = textValue.copy(
                                text = textValue.text.replaceRange(textValue.selection.start, textValue.selection.end, newText),
                                selection = TextRange(textValue.selection.start + 1, textValue.selection.start + 1 + (textValue.selection.end - textValue.selection.start))
                            )
                        }) {
                            Icon(Icons.Default.Code, "Code", modifier = Modifier.size(18.dp))
                        }
                        
                        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                        
                        IconButton(onClick = {
                            if (undoStack.size > 1) {
                                val current = undoStack.removeAt(undoStack.size - 1)
                                redoStack.add(current)
                                textValue = TextFieldValue(undoStack.last())
                            }
                        }, enabled = undoStack.size > 1) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                val target = redoStack.removeAt(redoStack.size - 1)
                                undoStack.add(target)
                                textValue = TextFieldValue(target)
                            }
                        }, enabled = redoStack.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Redo, "Redo", modifier = Modifier.size(18.dp))
                        }
                        
                        Spacer(Modifier.weight(1f))
                        
                        IconButton(onClick = { onMetadataToggle() }) {
                            Icon(if (showMetadata) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Metadata", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (showMetadata) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = metadata.type,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        metadata.timestamp?.let { 
                            MetadataRow(stringResource(R.string.metadata_creation_date), SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it)))
                        }
                        metadata.senderId?.let { MetadataRow(stringResource(R.string.metadata_sender_id), it) }
                        metadata.receiverId?.let { MetadataRow(stringResource(R.string.metadata_receiver_id), it) }
                        metadata.introducerId?.let { MetadataRow(stringResource(R.string.metadata_introducer_id), it) }
                        metadata.introducedId?.let { MetadataRow(stringResource(R.string.metadata_introduced_node_id), it) }
                        
                        metadata.profile?.let { p ->
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
                            Spacer(Modifier.height(8.dp))
                            MetadataRow(stringResource(R.string.profile_first_name), p.firstName.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_last_name), p.lastName.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_address), p.address.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_country), p.country.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_gender), p.gender.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_height), p.height.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_device_id), p.deviceId.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_declared_by), p.discloserId.ifBlank { "-" })
                        }
                    }
                }
                HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                if (isPreviewMode) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        MarkdownText(
                            text = textValue.text,
                            style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                        )
                    }
                } else {
                    val scrollState = rememberScrollState()
                    val lines = textValue.text.split("\n")
                    val lineCount = lines.size.coerceAtLeast(1)
                    val lineHeightDp = with(density) { 20.sp.toDp() }

                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val minHeight = maxHeight
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .heightIn(min = minHeight)
                            ) {
                                // Line Numbers
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(40.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                        .padding(top = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    repeat(lineCount) { index ->
                                        Text(
                                            text = (index + 1).toString(),
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color.Gray.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.height(lineHeightDp)
                                        )
                                    }
                                }

                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    color = Color.Black.copy(alpha = 0.05f)
                                )

                                Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState())) {
                                    BasicTextField(
                                        value = textValue,
                                        onValueChange = { 
                                            pushUndo(it.text)
                                            textValue = it 
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 12.dp, top = 16.dp, end = 16.dp, bottom = 64.dp),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        ),
                                        visualTransformation = markdownTransformation,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                    )
                                    
                                    // Character and Line Count Overlay
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Lines: $lineCount | Chars: ${textValue.text.length}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun MarkdownText(text: String, style: TextStyle) {
    // Basic markdown renderer for preview mode
    Text(
        text = text,
        style = style
    )
}
