package com.antigravity.mesh.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * BroadcastReceiver triggered by Android OS when this application's package
 * has been replaced (e.g. after in-app APK self-update).
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = context.getSharedPreferences("antigravity_mesh_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("just_updated", true).apply()
        }
    }
}
