package com.avrora.protection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Что означает каждая опасная возможность простыми словами.
 * weight — сколько баллов риска добавляет это разрешение (0..100 в сумме).
 */
data class RiskRule(
    val title: String,
    val explanation: String,
    val weight: Int
)

data class PermissionFinding(
    val permission: String,
    val rule: RiskRule
)

enum class Verdict { GREEN, YELLOW, RED }

data class ScanReport(
    val fileName: String,
    val sha256: String,
    val packageName: String?,
    val versionName: String?,
    val signerSha256: String?,
    val findings: List<PermissionFinding>,
    val otherPermissions: List<String>,
    val notes: List<String>,
    val score: Int
) {
    val verdict: Verdict
        get() = when {
            score >= 60 -> Verdict.RED
            score >= 25 -> Verdict.YELLOW
            else -> Verdict.GREEN
        }
}

object ApkAnalyzer {

    private val riskRules: Map<String, RiskRule> = mapOf(
        "android.permission.READ_SMS" to RiskRule(
            "Читает SMS",
            "Может читать входящие сообщения, в том числе коды подтверждения из банков.",
            25
        ),
        "android.permission.SEND_SMS" to RiskRule(
            "Отправляет SMS",
            "Может отправлять платные сообщения без твоего ведома.",
            25
        ),
        "android.permission.RECEIVE_SMS" to RiskRule(
            "Получает SMS",
            "Может перехватывать входящие сообщения раньше тебя.",
            15
        ),
        "android.permission.REQUEST_INSTALL_PACKAGES" to RiskRule(
            "Может ставить другие приложения",
            "Может сам скачать и установить ещё одно приложение, минуя твоё согласие.",
            20
        ),
        "android.permission.SYSTEM_ALERT_WINDOW" to RiskRule(
            "Рисует поверх других окон",
            "Может показывать окна поверх банковских или других приложений. Так часто воруют пароли.",
            20
        ),
        "android.permission.BIND_DEVICE_ADMIN" to RiskRule(
            "Права администратора устройства",
            "Может менять настройки телефона и блокировать его. Сложно удалить обычным способом.",
            30
        ),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to RiskRule(
            "Доступ ко всем файлам",
            "Может читать и менять любые файлы на телефоне: фото, документы, загрузки.",
            15
        ),
        "android.permission.RECORD_AUDIO" to RiskRule(
            "Пишет звук с микрофона",
            "Может слушать, что происходит рядом с телефоном.",
            15
        ),
        "android.permission.CAMERA" to RiskRule(
            "Использует камеру",
            "Может снимать тебя или то, что вокруг, без явного запуска.",
            10
        ),
        "android.permission.READ_CONTACTS" to RiskRule(
            "Читает контакты",
            "Видит список твоих знакомых и их номера.",
            10
        ),
        "android.permission.READ_CALL_LOG" to RiskRule(
            "Читает журнал звонков",
            "Видит, кому и когда ты звонил.",
            10
        ),
        "android.permission.ACCESS_FINE_LOCATION" to RiskRule(
            "Знает точное местоположение",
            "Может отслеживать, где ты находишься.",
            10
        ),
        "android.permission.READ_PHONE_STATE" to RiskRule(
            "Видит данные о телефоне",
            "Узнаёт номер телефона и уникальные идентификаторы устройства.",
            5
        ),
        "android.permission.RECEIVE_BOOT_COMPLETED" to RiskRule(
            "Запускается сам",
            "Стартует сразу после включения телефона, даже если ты его не открывал.",
            5
        )
    )

    /**
     * Анализирует APK-файл. Не запускает приложение и не ставит его.
     * Вызывать из фонового потока.
     */
    fun analyze(context: Context, apk: File): ScanReport {
        val notes = mutableListOf<String>()
        val sha256 = sha256Of(apk)

        val info = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        )

        if (info == null) {
            notes += "Файл не похож на APK или повреждён. Установка такого файла не пройдёт."
            return ScanReport(
                fileName = apk.name,
                sha256 = sha256,
                packageName = null,
                versionName = null,
                signerSha256 = null,
                findings = emptyList(),
                otherPermissions = emptyList(),
                notes = notes,
                score = 100
            )
        }

        val requested = info.requestedPermissions?.toList().orEmpty()
        val findings = requested
            .mapNotNull { perm -> riskRules[perm]?.let { PermissionFinding(perm, it) } }
        val others = requested.filter { it !in riskRules }

        val signerHash = signerSha256(info.signingInfo)
        if (signerHash == null) {
            notes += "У файла нет подписи сертификатом. Непонятно, кто его создал."
        }

        if (info.packageName == null) {
            notes += "Не найдено имя пакета. Это нетипично для нормального приложения."
        }

        val score = (findings.sumOf { it.rule.weight } + if (signerHash == null) 30 else 0)
            .coerceAtMost(100)

        if (findings.isEmpty()) {
            notes += "Опасных разрешений не найдено. Это не гарантия безопасности."
        }

        return ScanReport(
            fileName = apk.name,
            sha256 = sha256,
            packageName = info.packageName,
            versionName = info.versionName,
            signerSha256 = signerHash,
            findings = findings,
            otherPermissions = others,
            notes = notes,
            score = score
        )
    }

    private fun signerSha256(signingInfo: android.content.pm.SigningInfo?): String? {
        if (signingInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val signers = signingInfo.apkContentsSigners ?: return null
        val first = signers.firstOrNull() ?: return null
        return sha256Bytes(first.toByteArray())
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }
}
