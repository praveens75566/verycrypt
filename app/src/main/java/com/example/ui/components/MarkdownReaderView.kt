package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SecurityGreen
import kotlin.math.ceil

/**
 * Text Readiness Reader Component.
 * Supports typography scaling, high contrast, reading metrics, and formatted markdown rendering.
 */
@Composable
fun MarkdownReaderView(
    text: String,
    modifier: Modifier = Modifier,
    initialFontSizeSp: Int = 15,
    initialHighContrast: Boolean = false,
    initialLineWrap: Boolean = true,
    showControls: Boolean = true,
    onCopyCleanText: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var fontSizeSp by remember { mutableIntStateOf(initialFontSizeSp) }
    var isHighContrast by remember { mutableStateOf(initialHighContrast) }
    var isLineWrap by remember { mutableStateOf(initialLineWrap) }
    var readerMode by remember { mutableIntStateOf(0) } // 0: Formatted Reader, 1: Monospace Code / Line Numbers
    var hasCopied by remember { mutableStateOf(false) }

    // Reading metrics calculation
    val metrics = remember(text) {
        val trimmed = text.trim()
        val charCount = trimmed.length
        val words = if (trimmed.isEmpty()) emptyList() else trimmed.split("\\s+".toRegex())
        val wordCount = words.size
        val minutesToRead = ceil(wordCount / 200.0).toInt().coerceAtLeast(1)
        val lineCount = if (trimmed.isEmpty()) 0 else trimmed.lines().size
        Triple(wordCount, charCount, Pair(minutesToRead, lineCount))
    }

    val (wordCount, charCount, lineAndMinutes) = metrics
    val (minutesToRead, lineCount) = lineAndMinutes

    Column(modifier = modifier.fillMaxWidth()) {
        // Text Readiness Metrics & Quick Toolbar
        if (showControls) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reader_metrics_toolbar"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Row 1: Metrics Badge & Readability Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Word & Reading Time Badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "$wordCount words",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = "• $minutesToRead min read • $lineCount lines",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Text controls: A- / A+, Contrast, Copy
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Text Size Down
                            IconButton(
                                onClick = { if (fontSizeSp > 12) fontSizeSp -= 2 },
                                enabled = fontSizeSp > 12,
                                modifier = Modifier.size(28.dp).testTag("reader_font_size_down")
                            ) {
                                Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Text Size Up
                            IconButton(
                                onClick = { if (fontSizeSp < 24) fontSizeSp += 2 },
                                enabled = fontSizeSp < 24,
                                modifier = Modifier.size(28.dp).testTag("reader_font_size_up")
                            ) {
                                Text("A+", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // High Contrast Toggle
                            IconButton(
                                onClick = { isHighContrast = !isHighContrast },
                                modifier = Modifier.size(28.dp).testTag("reader_toggle_contrast")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contrast,
                                    contentDescription = "Toggle High Contrast",
                                    tint = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Line Wrap Toggle
                            IconButton(
                                onClick = { isLineWrap = !isLineWrap },
                                modifier = Modifier.size(28.dp).testTag("reader_toggle_line_wrap")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WrapText,
                                    contentDescription = "Toggle Line Wrap",
                                    tint = if (isLineWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // One-Tap Copy
                            IconButton(
                                onClick = {
                                    if (onCopyCleanText != null) {
                                        onCopyCleanText()
                                    } else {
                                        clipboardManager.setText(AnnotatedString(text))
                                    }
                                    hasCopied = true
                                    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp).testTag("reader_copy_button")
                            ) {
                                Icon(
                                    imageVector = if (hasCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy Clean Text",
                                    tint = if (hasCopied) SecurityGreen else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Mode Segmented Chips (Formatted Reader vs Monospace Inspector)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = readerMode == 0,
                            onClick = { readerMode = 0 },
                            label = { Text("Formatted Reader", fontSize = 11.sp) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = readerMode == 1,
                            onClick = { readerMode = 1 },
                            label = { Text("Monospace Lines", fontSize = 11.sp) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Reader Body Container
        val textColor = when {
            isHighContrast -> if (MaterialTheme.colorScheme.background == Color.Black) Color.White else MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }

        val containerBg = when {
            isHighContrast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isHighContrast) 1.5.dp else 1.dp,
                    color = if (isHighContrast) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp)
                )
                .background(containerBg)
                .padding(14.dp)
        ) {
            if (readerMode == 0) {
                // Formatted Markdown Reader
                FormattedMarkdownContent(
                    text = text,
                    fontSizeSp = fontSizeSp.sp,
                    textColor = textColor,
                    isHighContrast = isHighContrast
                )
            } else {
                // Monospace Line-Numbered Inspector
                MonospaceNumberedContent(
                    text = text,
                    fontSizeSp = (fontSizeSp - 2).coerceAtLeast(11).sp,
                    textColor = textColor,
                    isLineWrap = isLineWrap
                )
            }
        }
    }
}

@Composable
private fun FormattedMarkdownContent(
    text: String,
    fontSizeSp: TextUnit,
    textColor: Color,
    isHighContrast: Boolean
) {
    val lines = remember(text) { text.lines() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var inCodeBlock = false
        val currentCodeBlock = remember { mutableListOf<String>() }

        lines.forEach { line ->
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Close code block
                    CodeBlockCard(code = currentCodeBlock.joinToString("\n"), isHighContrast = isHighContrast)
                    currentCodeBlock.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
            } else if (inCodeBlock) {
                currentCodeBlock.add(line)
            } else when {
                // Header 1 (# Title)
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        fontSize = (fontSizeSp.value * 1.35f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                // Header 2 (## Title)
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## ").trim(),
                        fontSize = (fontSizeSp.value * 1.2f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                // Header 3 (### Title)
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### ").trim(),
                        fontSize = (fontSizeSp.value * 1.1f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                // Checkbox checked [x]
                trimmed.startsWith("[x] ", ignoreCase = true) -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckBox, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(trimmed.substring(4)),
                            fontSize = fontSizeSp,
                            color = textColor.copy(alpha = 0.7f),
                            fontWeight = if (isHighContrast) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
                // Checkbox unchecked [ ]
                trimmed.startsWith("[ ] ") -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(trimmed.substring(4)),
                            fontSize = fontSizeSp,
                            color = textColor,
                            fontWeight = if (isHighContrast) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
                // Bullet points (- or • or *)
                trimmed.startsWith("- ") || trimmed.startsWith("• ") || (trimmed.startsWith("* ") && !trimmed.endsWith("*")) -> {
                    Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                        Text(
                            text = "•",
                            fontSize = fontSizeSp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(trimmed.substring(2)),
                            fontSize = fontSizeSp,
                            color = textColor,
                            lineHeight = (fontSizeSp.value * 1.45f).sp
                        )
                    }
                }
                // Blockquote (> Quote)
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = trimmed.removePrefix("> ").trim(),
                            fontSize = fontSizeSp,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.9f)
                        )
                    }
                }
                // Blank line spacer
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // Standard Paragraph Text
                else -> {
                    Text(
                        text = parseInlineMarkdown(line),
                        fontSize = fontSizeSp,
                        color = textColor,
                        lineHeight = (fontSizeSp.value * 1.45f).sp,
                        fontWeight = if (isHighContrast) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        if (inCodeBlock && currentCodeBlock.isNotEmpty()) {
            CodeBlockCard(code = currentCodeBlock.joinToString("\n"), isHighContrast = isHighContrast)
        }
    }
}

@Composable
private fun CodeBlockCard(code: String, isHighContrast: Boolean) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CODE SNIPPET",
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMonoFamily,
                    color = Color(0xFF8B949E),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy snippet",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = Color(0xFFE6EDF3),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun MonospaceNumberedContent(
    text: String,
    fontSizeSp: TextUnit,
    textColor: Color,
    isLineWrap: Boolean
) {
    val lines = remember(text) { text.lines() }

    val contentModifier = if (isLineWrap) Modifier.fillMaxWidth()
    else Modifier.horizontalScroll(rememberScrollState())

    Column(modifier = contentModifier) {
        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Line Number
                Text(
                    text = "${index + 1}".padStart(3, ' ') + "  ",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = fontSizeSp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold
                )

                // Line Text
                Text(
                    text = if (line.isEmpty()) " " else line,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = fontSizeSp,
                    color = textColor,
                    lineHeight = (fontSizeSp.value * 1.4f).sp
                )
            }
        }
    }
}

/**
 * Parses bold (**text**) and inline code (`code`) into an AnnotatedString.
 */
private fun parseInlineMarkdown(line: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            when {
                // Bold (**text**)
                line.startsWith("**", i) -> {
                    val end = line.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(line.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append(line[i])
                        i++
                    }
                }
                // Inline code (`code`)
                line.startsWith("`", i) -> {
                    val end = line.indexOf("`", i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFamily,
                                background = Color(0x33888888),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(" " + line.substring(i + 1, end) + " ")
                        pop()
                        i = end + 1
                    } else {
                        append(line[i])
                        i++
                    }
                }
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }
}
