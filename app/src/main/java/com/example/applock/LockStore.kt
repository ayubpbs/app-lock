package com.example.applock

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

fun biometricAvailable(ctx: Context): Boolean =
    BiometricManager.from(ctx)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

/** State shared between the service and the screens (same process). */
object AppLockState {
    /** Is App Lock's own screen unlocked? Memory only. */
    @Volatile
    var sessionUnlocked = false

    /** The locked app the user has just unlocked, until they leave it. */
    @Volatile
    var unlockedPkg: String? = null

    @Volatile
    var guardingPkg: String? = null

    @Volatile
    var guardingSince = 0L
}

class LockStore(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("lock", Context.MODE_PRIVATE)

    companion object {
        const val PIN = "pin"
        const val PASSWORD = "password"

        /** Returns an error text, or null when the PIN / password is acceptable. */
        fun secretError(type: String, s: String): String? =
            if (type == PIN) {
                if (!s.matches(Regex("\\d{4,8}"))) "PIN must be 4 to 8 digits" else null
            } else {
                if (s.length < 6) "At least 6 characters" else null
            }
    }

    // ---------------- Lock (PIN / password / fingerprint) ----------------

    fun hasLock() = sp.contains("hash")

    fun lockType(): String = sp.getString("lockType", PASSWORD) ?: PASSWORD

    fun fingerprintEnabled(): Boolean = sp.getBoolean("fingerprint", true)

    fun setFingerprintEnabled(on: Boolean) {
        sp.edit().putBoolean("fingerprint", on).apply()
    }

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 120_000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(f.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    fun setLock(type: String, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("lockType", type)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(secret, salt))
            .putInt("fails", 0)
            .putLong("lockedUntil", 0L)
            .apply()
    }

    private fun verify(secret: String): Boolean {
        val salt = Base64.decode(sp.getString("salt", "") ?: return false, Base64.NO_WRAP)
        val expected = sp.getString("hash", null) ?: return false
        return MessageDigest.isEqual(expected.toByteArray(), hash(secret, salt).toByteArray())
    }

    // ---- Wrong-attempt lockout: after 5 wrong tries wait 30s, then longer ----

    fun remainingLockMs(): Long =
        (sp.getLong("lockedUntil", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun resetFails() {
        sp.edit().putInt("fails", 0).putLong("lockedUntil", 0L).apply()
    }

    /** Checks the PIN/password and keeps count of wrong tries. */
    fun attempt(secret: String): Boolean {
        if (remainingLockMs() > 0) return false
        if (verify(secret)) {
            resetFails()
            return true
        }
        val fails = sp.getInt("fails", 0) + 1
        val e = sp.edit().putInt("fails", fails)
        if (fails >= 5) {
            val wait = (30_000L shl (fails - 5).coerceAtMost(5)).coerceAtMost(15 * 60_000L)
            e.putLong("lockedUntil", System.currentTimeMillis() + wait)
        }
        e.apply()
        return false
    }

    // ---------------- Which apps are locked ----------------

    fun lockedPackages(): Set<String> =
        HashSet(sp.getStringSet("apps", emptySet()) ?: emptySet())

    fun setLocked(pkg: String, on: Boolean) {
        val s = lockedPackages().toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp.edit().putStringSet("apps", s).apply()
    }
}
