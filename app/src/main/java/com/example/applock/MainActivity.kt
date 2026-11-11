package com.example.applock

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private lateinit var store: LockStore
    private lateinit var adapter: AppAdapter
    private lateinit var etSearch: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnEnable: Button
    private var all: List<AppItem> = emptyList()

    // true while a system settings screen is open (so App Lock does not lock itself)
    private var leavingToSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_main)
        store = LockStore(this)

        etSearch = findViewById(R.id.etSearch)
        tvStatus = findViewById(R.id.tvStatus)
        btnEnable = findViewById(R.id.btnEnable)
        val rv = findViewById<RecyclerView>(R.id.rvApps)

        adapter = AppAdapter { item, on -> store.setLocked(item.pkg, on) }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        etSearch.doAfterTextChanged { applyFilter() }
        findViewById<Button>(R.id.btnLock).setOnClickListener { showLockSettings() }
        btnEnable.setOnClickListener { showEnableHelp() }

        loadApps()
    }

    override fun onStart() {
        super.onStart()
        if (!AppLockState.sessionUnlocked) {
            startActivity(Intent(this, GateActivity::class.java))
            finish()
            return
        }
        leavingToSettings = false
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !leavingToSettings) AppLockState.sessionUnlocked = false
    }

    // ---------------- App list ----------------

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val locked = store.lockedPackages()
            val list = pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != packageName }
                .mapNotNull { pkg ->
                    try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        AppItem(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai), pkg in locked)
                    } catch (e: Exception) {
                        null
                    }
                }
                .sortedWith(compareByDescending<AppItem> { it.locked }.thenBy { it.label.lowercase() })
            runOnUiThread {
                all = list
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val q = etSearch.text.toString().trim().lowercase()
        adapter.submit(all.filter { q.isEmpty() || it.label.lowercase().contains(q) })
    }

    // ---------------- Protection (accessibility service) status ----------------

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, GuardService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    private fun updateStatus() {
        if (serviceEnabled()) {
            tvStatus.text = "Protection is ON"
            btnEnable.visibility = View.GONE
        } else {
            tvStatus.text = "Protection is OFF. Locks will not work until you turn it on."
            btnEnable.visibility = View.VISIBLE
        }
    }

    private fun showEnableHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Turn on protection")
            .setMessage(
                "1. Tap \"Open accessibility settings\".\n" +
                        "2. Find \"App Lock\" (maybe under Installed apps or Downloaded apps) and switch it on.\n\n" +
                        "If the switch is greyed out or says \"Restricted setting\": tap \"Open app info\", " +
                        "tap the 3 dots at the top right, choose \"Allow restricted settings\", then try again."
            )
            .setPositiveButton("Open accessibility settings") { _, _ ->
                leavingToSettings = true
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNeutralButton("Open app info") { _, _ ->
                leavingToSettings = true
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null))
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- Lock settings ----------------

    private fun showLockSettings() {
        val v = layoutInflater.inflate(R.layout.dialog_lock, null)
        val rg = v.findViewById<RadioGroup>(R.id.rgType)
        val tilNew = v.findViewById<TextInputLayout>(R.id.tilNewLock)
        val etNew = v.findViewById<EditText>(R.id.etNewLock)
        val tilConfirm = v.findViewById<TextInputLayout>(R.id.tilNewConfirm)
        val etConfirm = v.findViewById<EditText>(R.id.etNewConfirm)
        val tilCur = v.findViewById<TextInputLayout>(R.id.tilCurrent)
        val etCur = v.findViewById<EditText>(R.id.etCurrent)
        val swFinger = v.findViewById<MaterialSwitch>(R.id.swFinger)

        var type = store.lockType()
        rg.check(if (type == LockStore.PIN) R.id.rbPin else R.id.rbPassword)

        if (biometricAvailable(this)) {
            swFinger.isChecked = store.fingerprintEnabled()
        } else {
            swFinger.visibility = View.GONE
        }

        fun applyType() {
            val pin = type == LockStore.PIN
            val inputType = if (pin) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etNew.inputType = inputType
            etConfirm.inputType = inputType
            etNew.transformationMethod = PasswordTransformationMethod.getInstance()
            etConfirm.transformationMethod = PasswordTransformationMethod.getInstance()
            tilNew.hint = if (pin) "New PIN (4-8 digits)" else "New password (min 6)"
            tilConfirm.hint = if (pin) "Confirm new PIN" else "Confirm new password"
            tilNew.error = null
            tilConfirm.error = null
        }
        applyType()
        rg.setOnCheckedChangeListener { _, id ->
            type = if (id == R.id.rbPin) LockStore.PIN else LockStore.PASSWORD
            etNew.setText("")
            etConfirm.setText("")
            applyType()
        }

        val d = MaterialAlertDialogBuilder(this)
            .setTitle("Lock settings")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        d.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        d.show()

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            tilCur.error = null
            tilNew.error = null
            tilConfirm.error = null

            val wait = store.remainingLockMs()
            if (wait > 0) {
                tilCur.error = "Too many attempts. Try again in ${(wait + 999) / 1000}s"
                return@setOnClickListener
            }
            if (!store.attempt(etCur.text.toString())) {
                val w = store.remainingLockMs()
                tilCur.error = if (w > 0) {
                    "Too many attempts. Try again in ${(w + 999) / 1000}s"
                } else {
                    "Wrong current PIN / password"
                }
                return@setOnClickListener
            }

            val np = etNew.text.toString()
            if (np.isEmpty()) {
                if (type != store.lockType()) {
                    tilNew.error = "Enter a new " + if (type == LockStore.PIN) "PIN" else "password"
                    return@setOnClickListener
                }
            } else {
                val err = LockStore.secretError(type, np)
                if (err != null) {
                    tilNew.error = err
                    return@setOnClickListener
                }
                if (np != etConfirm.text.toString()) {
                    tilConfirm.error = "Do not match"
                    return@setOnClickListener
                }
                store.setLock(type, np)
            }
            if (swFinger.visibility == View.VISIBLE) {
                store.setFingerprintEnabled(swFinger.isChecked)
            }
            Toast.makeText(this, "Lock settings saved", Toast.LENGTH_LONG).show()
            d.dismiss()
        }
    }
}
