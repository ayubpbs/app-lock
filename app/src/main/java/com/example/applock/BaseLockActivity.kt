package com.example.applock

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** The PIN / password / fingerprint screen. Used both for App Lock itself and for locked apps. */
abstract class BaseLockActivity : AppCompatActivity() {

    protected lateinit var store: LockStore
    private var type = LockStore.PASSWORD
    private lateinit var tvTitle: TextView
    private lateinit var ivIcon: ImageView

    protected abstract fun onUnlocked()
    protected open fun headerTitle(): String = "App Lock"
    protected open fun headerIcon(): Drawable? = null

    protected fun refreshHeader() {
        tvTitle.text = headerTitle()
        val icon = headerIcon()
        ivIcon.setImageDrawable(icon)
        ivIcon.visibility = if (icon != null) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_lock)
        store = LockStore(this)

        tvTitle = findViewById(R.id.tvTitle)
        ivIcon = findViewById(R.id.ivIcon)
        refreshHeader()

        val tvSub = findViewById<TextView>(R.id.tvSub)
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleType)
        val tilPw = findViewById<TextInputLayout>(R.id.tilPassword)
        val etPw = findViewById<TextInputEditText>(R.id.etPassword)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirm)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirm)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        val btnFinger = findViewById<Button>(R.id.btnFinger)

        val setup = !store.hasLock()
        type = if (setup) LockStore.PASSWORD else store.lockType()

        fun applyType() {
            val pin = type == LockStore.PIN
            val inputType = if (pin) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPw.inputType = inputType
            etConfirm.inputType = inputType
            etPw.transformationMethod = PasswordTransformationMethod.getInstance()
            etConfirm.transformationMethod = PasswordTransformationMethod.getInstance()
            tilPw.hint = if (pin) "PIN" else "Password"
            tilConfirm.hint = if (pin) "Confirm PIN" else "Confirm password"
            tilPw.error = null
            tilConfirm.error = null
        }
        applyType()

        if (setup) {
            tvSub.text = "Choose how to lock your apps"
            toggle.visibility = View.VISIBLE
            toggle.check(R.id.btnTypePw)
            toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    type = if (checkedId == R.id.btnTypePin) LockStore.PIN else LockStore.PASSWORD
                    etPw.setText("")
                    etConfirm.setText("")
                    applyType()
                }
            }
            tilConfirm.visibility = View.VISIBLE
            btnUnlock.text = "Set lock"
            btnFinger.visibility = View.GONE
        } else {
            tvSub.text = if (type == LockStore.PIN) "Enter your PIN" else "Enter your password"
        }

        btnUnlock.setOnClickListener {
            val p = etPw.text.toString()
            tilPw.error = null
            tilConfirm.error = null
            if (setup) {
                val err = LockStore.secretError(type, p)
                when {
                    err != null -> tilPw.error = err
                    p != etConfirm.text.toString() -> tilConfirm.error = "Do not match"
                    else -> {
                        store.setLock(type, p)
                        onUnlocked()
                    }
                }
            } else {
                val wait = store.remainingLockMs()
                if (wait > 0) {
                    tilPw.error = "Too many attempts. Try again in ${(wait + 999) / 1000}s"
                    return@setOnClickListener
                }
                if (store.attempt(p)) {
                    onUnlocked()
                } else {
                    val w = store.remainingLockMs()
                    tilPw.error = if (w > 0) {
                        "Too many attempts. Try again in ${(w + 999) / 1000}s"
                    } else {
                        if (type == LockStore.PIN) "Wrong PIN" else "Wrong password"
                    }
                }
            }
        }

        btnFinger.setOnClickListener { showBiometric() }

        if (!setup) {
            if (store.fingerprintEnabled() && biometricAvailable(this)) {
                btnFinger.visibility = View.VISIBLE
                showBiometric()
            } else {
                btnFinger.visibility = View.GONE
            }
        }
    }

    private fun showBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    store.resetFails()
                    onUnlocked()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock")
            .setNegativeButtonText(if (type == LockStore.PIN) "Use PIN" else "Use password")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
}
