package com.adulthub.tv.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build

data class CodecInfo(
    val name: String,
    val isSupported: Boolean,
    val isHardware: Boolean,
)

data class DeviceDiagnostics(
    val deviceModel: String,
    val androidVersion: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val codecs: List<CodecInfo>,
)

object DeviceDiagnosticsHelper {

    fun getDiagnostics(context: Context): DeviceDiagnostics {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availRamMb = memoryInfo.availMem / (1024 * 1024)

        val targetMimes = listOf(
            "video/avc" to "AVC (H.264)",
            "video/hevc" to "HEVC (H.265)",
            "video/av01" to "AV1",
            "video/x-vnd.on2.vp9" to "VP9",
        )

        val codecList = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS) }.getOrNull()
        val allDecoders = codecList?.codecInfos.orEmpty().filter { !it.isEncoder }

        val codecs = targetMimes.map { (mime, displayName) ->
            val decodersForMime = allDecoders.filter { info ->
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            val isSupported = decodersForMime.isNotEmpty()
            val isHardware = decodersForMime.any { info ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    val n = info.name.lowercase()
                    !n.startsWith("c2.android.") && !n.startsWith("omx.google.")
                }
            }
            CodecInfo(
                name = displayName,
                isSupported = isSupported,
                isHardware = isHardware,
            )
        }

        return DeviceDiagnostics(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            codecs = codecs,
        )
    }
}
