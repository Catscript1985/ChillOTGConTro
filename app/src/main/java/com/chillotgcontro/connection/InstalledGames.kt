package com.chillotgcontro.connection

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Android package visibility: chỉ truy vấn ứng dụng có màn hình khởi chạy (launcher), không quét dữ liệu riêng tư. */
data class InstalledGame(val label: String, val packageName: String)

object InstalledGames {
    fun list(context: Context): List<InstalledGame> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return activities.asSequence()
            .mapNotNull { resolve ->
                val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (info.packageName == context.packageName || !info.enabled) return@mapNotNull null
                val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
                InstalledGame(label, info.packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch); true }.getOrDefault(false)
    }
}
