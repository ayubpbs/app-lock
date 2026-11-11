package com.example.applock

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.OnBackPressedCallback

/** Covers a locked app until the right PIN / password / fingerprint is given. */
class GuardActivity : BaseLockActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
    }

    private var unlockedOk = false
    private val targetPkg: String get() = intent.getStringExtra(EXTRA_PKG) ?: ""

    override fun headerTitle(): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPkg, 0)).toString()
    } catch (e: Exception) {
        "App Lock"
    }

    override fun headerIcon(): Drawable? = try {
        packageManager.getApplicationIcon(targetPkg)
    } catch (e: Exception) {
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back must not reveal the locked app: go to the home screen instead
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshHeader()
    }

    override fun onUnlocked() {
        AppLockState.unlockedPkg = targetPkg
        unlockedOk = true
        finishAndRemoveTask()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAndRemoveTask()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the lock screen without unlocking: close it, the app stays locked
        if (!unlockedOk) finishAndRemoveTask()
    }
}
