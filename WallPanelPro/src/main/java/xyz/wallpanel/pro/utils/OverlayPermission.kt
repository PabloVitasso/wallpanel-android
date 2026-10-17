/*
 * Copyright (c) 2022 WallPanel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.wallpanel.pro.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import timber.log.Timber
import xyz.wallpanel.pro.R

/**
 * The "display over other apps" permission. From API 29 Android blocks an application in the
 * background from starting an activity, and a relaunch after a crash or a restart is such a
 * start; holding this permission exempts it. Without it the browser only comes back through
 * the full screen intent, which Android honours on a locked or sleeping screen alone.
 */
object OverlayPermission {

    /** Whether the device restricts background activity starts at all. */
    fun isNeeded(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun isGranted(context: Context): Boolean {
        return !isNeeded() || Settings.canDrawOverlays(context)
    }

    /** Opens the system screen where the user grants it. */
    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Some cut down builds, Android Go and TV among them, have no such screen.
            Timber.w(e, "No screen to grant display over other apps")
            Toast.makeText(context, R.string.toast_overlay_settings_missing, Toast.LENGTH_LONG).show()
        }
    }
}
