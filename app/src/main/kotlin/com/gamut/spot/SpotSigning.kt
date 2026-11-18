package com.gamut.spot

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

@Suppress("DEPRECATION")
internal fun updateChannel(context: Context): String = runCatching {
    val signatures = if (Build.VERSION.SDK_INT >= 28) context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    val hash = signatures?.firstOrNull()?.toByteArray()?.let { bytes -> MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
    when (hash) {
        "c9ae87a69f11c3e4852f64aaac4c32d7bb2891b09fc1883ea2d378314576a0e0" -> "Retained local signing key"
        "bab3f8d16bf11feee9b15791faac0909e1e9840b27fb79bf21e07a22e3fdce4b" -> "Legacy GitHub alpha 8 key"
        "3f0a72ca6c311dde6d59795d12a0f963ab768981ffa6d1ab08a7ea519d1e58bf" -> "Legacy GitHub alpha 6 key"
        "e8bda239e396367523f0ff46af9a7ff0fe42acc6dc53a93331750d5aeee9cff9" -> "Legacy GitHub alpha 5 key"
        else -> "Custom build · ${hash?.take(12).orEmpty()}"
    }
}.getOrDefault("Custom build")
