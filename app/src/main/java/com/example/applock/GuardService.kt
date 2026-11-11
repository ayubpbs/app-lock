package com.example.applock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/** Watches which app comes to the front and shows the lock screen over locked apps. */
class GuardService : AccessibilityService() {

    private val store by lazy { LockStore(this) }
    private var foregroundPkg: String? = null
    private var imePkgs: Set<String> = emptySet()
    private var imeStamp = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> AppLockState.unlockedPkg = null
                // Screen is back and the phone is unlocked: lock the app that was open
                Intent.ACTION_USER_PRESENT -> foregroundPkg?.let { maybeLock(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, f)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (shouldIgnore(pkg)) return

        foregroundPkg = pkg
        // Moving to another app ends the "unlocked" period of the previous one
        if (AppLockState.unlockedPkg != pkg) AppLockState.unlockedPkg = null
        maybeLock(pkg)
    }

    private fun shouldIgnore(pkg: String): Boolean =
        pkg == packageName || pkg == "com.android.systemui" || pkg in imePackages()

    private fun imePackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - imeStamp > 60_000L) {
            imeStamp = now
            imePkgs = try {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .enabledInputMethodList.map { it.packageName }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }
        return imePkgs
    }

    private fun maybeLock(pkg: String) {
        if (!store.hasLock()) return
        if (pkg !in store.lockedPackages()) return
        if (AppLockState.unlockedPkg == pkg) return

        val now = System.currentTimeMillis()
        if (AppLockState.guardingPkg == pkg && now - AppLockState.guardingSince < 1500L) return
        AppLockState.guardingPkg = pkg
        AppLockState.guardingSince = now

        startActivity(
            Intent(this, GuardActivity::class.java)
                .putExtra(GuardActivity.EXTRA_PKG, pkg)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
        )
    }
}
