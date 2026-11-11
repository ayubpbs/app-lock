package com.example.applock

import android.content.Intent

/** Unlocks App Lock itself, or sets the first PIN / password. */
class GateActivity : BaseLockActivity() {
    override fun onUnlocked() {
        AppLockState.sessionUnlocked = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
