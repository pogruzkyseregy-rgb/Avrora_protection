package com.avrora.protection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.protection.ui.AuroraBackground
import com.avrora.protection.ui.AuroraCyan
import com.avrora.protection.ui.AuroraMuted
import com.avrora.protection.ui.AuroraOrange
import com.avrora.protection.ui.AuroraTheme
import com.avrora.protection.ui.AuroraText
import com.avrora.protection.ui.HudTag
import com.avrora.protection.ui.RadialGauge
import com.avrora.protection.ui.SegmentBar
import com.avrora.protection.ui.VerdictGreen
import com.avrora.protection.ui.VerdictRed
import com.avrora.protection.ui.VerdictYellow
import com.avrora.protection.ui.hudFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    // APK, который пришёл извне: из «Открыть с помощью» или «Поделиться».
    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            AuroraTheme {
                AppScreen(
                    incomingUri = incomingUri,
                    onConsumed = { incomingUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) incomingUri = uri
    }
}

/** Копирует APK в кэш и анализирует. Возвращает null, если файл не открылся. */
private fun scanUri(context: Context, uri: Uri): ScanReport? = runCatching {
    val copy = File(context.cacheDir, "scan.apk")
    context.contentResolver.openInputStream(uri)?.use { src ->
        copy.outputStream().use { dst -> src.copyTo(dst) }
    } ?: return@runCatching null
    ApkAnalyzer.analyze(context, copy)
}.getOrNull()

@Composable
private fun AppScreen(incomingUri: Uri?, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("avrora", Context.MODE_PRIVATE) }

    var folderUri by remember {
        mutableStateOf(prefs.getString("folder", null)?.let { Uri.parse(it) })
    }
    var apks by remember { mutableStateOf<List<ApkItem>>(emptyList()) }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun scan(uri: Uri) {
        scope.launch {
            busy = true
            error = null
            val result = withContext(Dispatchers.IO) { scanUri(context, uri) }
            if (result == null) error = "Не удалось открыть файл." else report = result
            busy = false
        }
    }

    // Внешний APK сразу уходит на проверку.
    LaunchedEffect(incomingUri) {
        incomingUri?.let {
            scan(it)
            onConsumed()
        }
    }

    // Список APK обновляется при выборе папки.
    LaunchedEffect(folderUri) {
        val folder = folderUri
        apks = if (folder == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { ApkFolderScanner.list(context, folder) }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs.edit().putString("folder", uri.toString()).apply()
            folderUri = uri
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scan(uri)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AuroraBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HudTag("AVRORA PROTECTION")
                Text(
                    text = "Проверка APK до установки. Файлы никуда не отправляются.",
                    color = AuroraMuted,
                    fontSize = 13.sp
                )
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AuroraCyan)
                    Text("  Сканирую...", color = AuroraText)
                }
            }

            val current = report
            if (current != null) {
                OutlinedButton(
                    onClick = { report = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("← К списку", color = AuroraCyan)
                }
                ReportView(current)
            } else {
                Button(
                    onClick = { folderPicker.launch(null) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать папку загрузок")
                }

                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить один файл", color = AuroraCyan)
                }

                error?.let { Text(text = it, color = VerdictRed, fontSize = 13.sp) }

                ApkListSection(
                    folder = folderUri,
                    apks = apks,
                    enabled = !busy,
                    onScan = { scan(it) }
                )
            }
        }
    }
}

@Composable
private fun ApkListSection(
    folder: Uri?,
    apks: List<ApkItem>,
    enabled: Boolean,
    onScan: (Uri) -> Unit
) {
    Section("НАЙДЕННЫЕ APK") {
        when {
            folder == null -> Text(
                text = "Выбери папку загрузок, и приложение покажет все APK из неё.",
                color = AuroraText,
                fontSize = 13.sp
            )
            apks.isEmpty() -> Text(
                text = "В этой папке APK-файлов нет.",
                color = AuroraText,
                fontSize = 13.sp
            )
            else -> apks.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.name, color = AuroraText, fontSize = 13.sp)
                        Text(
                            text = formatSize(item.sizeBytes),
                            color = AuroraMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Button(
                        onClick = { onScan(item.uri) },
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraCyan)
                    ) {
                        Text("Проверить", color = AuroraBackground)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportView(report: ScanReport) {
    val color = when (report.verdict) {
        Verdict.GREEN -> VerdictGreen
        Verdict.YELLOW -> VerdictYellow
        Verdict.RED -> VerdictRed
    }
    val tag = when (report.verdict) {
        Verdict.GREEN -> "ЧИСТО"
        Verdict.YELLOW -> "ВНИМАНИЕ"
        Verdict.RED -> "ОПАСНО"
    }
    val auroraLine = when (report.verdict) {
        Verdict.GREEN -> "Вроде чисто. Но всё равно смотри, откуда файл."
        Verdict.YELLOW -> "Есть вопросы. Прочитай пункты ниже, прежде чем ставить."
        Verdict.RED -> "Стоп. Не ставь, пока не разберёшься с каждым пунктом."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hudFrame(color)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HudTag(tag, color = color)
        RadialGauge(score = report.score, color = color)
        SegmentBar(progress = report.score / 100f, color = color)
        Text(
            text = auroraLine,
            color = AuroraText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Оценка по разрешениям и подписи, а не окончательный вердикт «вирус».",
            color = AuroraMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }

    Section("ОСНОВНОЕ") {
        Line("Файл", report.fileName)
        Line("Пакет", report.packageName ?: "не определён")
        Line("Версия", report.versionName ?: "не определена")
        Line("SHA-256", report.sha256)
        Line("Подпись", report.signerSha256 ?: "нет подписи")
    }

    if (report.findings.isNotEmpty()) {
        Section("ОПАСНЫЕ ВОЗМОЖНОСТИ") {
            report.findings.forEach { f ->
                Text(text = "• ${f.rule.title}", color = AuroraOrange, fontSize = 14.sp)
                Text(text = f.rule.explanation, color = AuroraMuted, fontSize = 12.sp)
                Text(
                    text = "  Право: ${f.permission}",
                    color = AuroraMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    if (report.otherPermissions.isNotEmpty()) {
        Section("ОСТАЛЬНЫЕ РАЗРЕШЕНИЯ") {
            report.otherPermissions.forEach { perm ->
                Text(text = perm, color = AuroraMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }

    if (report.notes.isNotEmpty()) {
        Section("ЗАМЕТКИ") {
            report.notes.forEach { note ->
                Text(text = "• $note", color = AuroraText, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hudFrame()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = title, color = AuroraCyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column {
        Text(text = label, color = AuroraMuted, fontSize = 11.sp)
        Text(text = value, color = AuroraText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) "%.1f МБ".format(bytes / 1024.0 / 1024.0)
    else "%.0f КБ".format(bytes / 1024.0)
