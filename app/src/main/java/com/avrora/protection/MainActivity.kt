package com.avrora.protection

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.avrora.protection.ui.VerdictGreen
import com.avrora.protection.ui.VerdictRed
import com.avrora.protection.ui.VerdictYellow
import com.avrora.protection.ui.hudFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuroraTheme {
                ScannerScreen()
            }
        }
    }
}

@Composable
private fun ScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            report = withContext(Dispatchers.IO) {
                // Копируем файл в кэш: PackageManager читает APK только с обычного пути.
                val copy = File(context.cacheDir, "scan.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copy.outputStream().use { input.copyTo(it) }
                }
                ApkAnalyzer.analyze(context, copy)
            }
            busy = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AuroraBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AVRORA PROTECTION",
                color = AuroraCyan,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Проверка APK до установки. Файл никуда не отправляется.",
                color = AuroraMuted,
                fontSize = 13.sp
            )

            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = AuroraOrange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Выбрать APK для проверки")
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AuroraCyan)
                    Text("  Сканирую...", color = AuroraText)
                }
            }

            report?.let { ReportView(it) }
        }
    }
}

@Composable
private fun ReportView(report: ScanReport) {
    val verdictColor = when (report.verdict) {
        Verdict.GREEN -> VerdictGreen
        Verdict.YELLOW -> VerdictYellow
        Verdict.RED -> VerdictRed
    }
    val auroraLine = when (report.verdict) {
        Verdict.GREEN -> "Вроде чисто. Но всё равно смотри, откуда файл."
        Verdict.YELLOW -> "Есть вопросы. Прочитай пункты ниже, прежде чем ставить."
        Verdict.RED -> "Стоп. Не ставь, пока не разберёшься с каждым пунктом."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hudFrame(verdictColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ОЦЕНКА РИСКА: ${report.score} / 100",
            color = verdictColor,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(text = auroraLine, color = AuroraText, fontSize = 14.sp)
        Text(
            text = "Это оценка по разрешениям и подписи, а не окончательный вердикт «вирус».",
            color = AuroraMuted,
            fontSize = 12.sp
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
                Text(
                    text = "• ${f.rule.title}",
                    color = AuroraOrange,
                    fontSize = 14.sp
                )
                Text(
                    text = f.rule.explanation,
                    color = AuroraMuted,
                    fontSize = 12.sp
                )
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
        Text(
            text = value,
            color = AuroraText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Start
        )
    }
}
