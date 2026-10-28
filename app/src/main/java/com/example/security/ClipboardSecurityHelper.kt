package com.example.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardSecurityHelper {

    /**
     * Copies text to the clipboard with sensitive privacy flags enabled on Android 13+ (Tiramisu).
     * Prevents system clipboard overlay previews from displaying the secret text.
     */
    fun copySensitiveText(
        context: Context,
        label: String,
        text: String,
        showToastNotice: Boolean = true,
        customToastMessage: String? = null
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        clipboard.setPrimaryClip(clip)

        if (showToastNotice) {
            val notice = customToastMessage ?: "$label copied (Sensitive clipboard mode active)"
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies text to clipboard with sensitive privacy flags AND schedules background auto-clearing.
     * After [autoClearSeconds], checks if the clipboard still contains the sensitive text and wipes it.
     */
    fun copySensitiveTextWithAutoClear(
        context: Context,
        scope: CoroutineScope,
        label: String,
        text: String,
        autoClearSeconds: Long = 45
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied! Auto-clearing in ${autoClearSeconds}s.", Toast.LENGTH_SHORT).show()
        DiagnosticsEngine.recordInfo("ClipboardSecurity", "Sensitive content '$label' copied with ${autoClearSeconds}s auto-clear.")

        scope.launch(Dispatchers.Main) {
            delay(autoClearSeconds * 1000)
            try {
                val currentClip = clipboard.primaryClip
                if (currentClip != null && currentClip.itemCount > 0) {
                    val currentText = currentClip.getItemAt(0)?.text?.toString()
                    if (currentText == text) {
                        val emptyClip = ClipData.newPlainText("cleared", "")
                        clipboard.setPrimaryClip(emptyClip)
                        Toast.makeText(context, "VeriCrypt: Sensitive clipboard data automatically wiped.", Toast.LENGTH_SHORT).show()
                        DiagnosticsEngine.recordInfo("ClipboardSecurity", "Sensitive content '$label' automatically wiped from clipboard.")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
