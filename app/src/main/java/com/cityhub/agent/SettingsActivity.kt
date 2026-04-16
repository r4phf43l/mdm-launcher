package com.cityhub.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela de configurações do launcher, protegida por senha admin.
 */
class SettingsActivity : AppCompatActivity() {

    // ─── Views ────────────────────────────────────────────────────────────────
    private lateinit var layoutContent:           View
    private lateinit var etBgColor:               EditText
    private lateinit var etAdminPassword:         EditText
    private lateinit var etAdminPasswordConfirm:  EditText
    private lateinit var spFilterMode:            Spinner
    private lateinit var btnManageApps:           Button
    private lateinit var tvPermissionsStatus:    TextView
    private lateinit var btnRequestPermissions:  Button
    private lateinit var btnApply:                Button
    private lateinit var btnSave:                 Button
    private lateinit var cbBlockSettings:         CheckBox
    private lateinit var cbAutoStart:             CheckBox


    // ─── Lock Screen Wallpaper ────────────────────────────────────────────────
    private lateinit var cbLockWpEnabled:      CheckBox
    private lateinit var tvLockWpApiWarning:   TextView
    private lateinit var tvLockWpImagePath:    TextView
    private lateinit var btnPickLockWpImage:   Button
    private lateinit var btnClearLockWpImage:  Button
    private lateinit var btnApplyLockWpNow:    Button
    private lateinit var ivCurrentLockWp:     ImageView

    private var pickedLockWpUri:    Uri? = null

    // ─── Launchers para substituição do startActivityForResult ────────────────

    private val pickLockImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                pickedLockWpUri = uri
                tvLockWpImagePath.text = uri.toString()
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindViews()
        layoutContent.visibility = View.GONE   // Oculto até autenticação

        showPasswordDialog()
    }

    // ─── Gate de senha ────────────────────────────────────────────────────────

    private fun showPasswordDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Senha admin"
        }

        // Melhora o alinhamento adicionando um container com margens
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        val marginHorizontal = (24 * resources.displayMetrics.density).toInt()
        params.setMargins(marginHorizontal, 0, marginHorizontal, 0)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Acesso restrito")
            .setMessage("Digite a senha de administrador:")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Entrar") { _, _ ->
                if (input.text.toString() == PrefsManager.getAdminPassword()) {
                    unlockSettings()
                } else {
                    Toast.makeText(this, "Senha incorreta.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .show()
    }

    private fun unlockSettings() {
        layoutContent.visibility = View.VISIBLE
        populateFields()
        updatePermissionsStatus()
    }

    // ─── Bind ─────────────────────────────────────────────────────────────────

    private fun bindViews() {
        layoutContent           = findViewById(R.id.layoutContent)
        etBgColor               = findViewById(R.id.etBgColor)
        etAdminPassword         = findViewById(R.id.etAdminPassword)
        etAdminPasswordConfirm  = findViewById(R.id.etAdminPasswordConfirm)
        spFilterMode            = findViewById(R.id.spFilterMode)
        btnManageApps           = findViewById(R.id.btnManageApps)
        tvPermissionsStatus     = findViewById(R.id.tvPermissionsStatus)
        btnRequestPermissions   = findViewById(R.id.btnRequestPermissions)
        btnApply                = findViewById(R.id.btnApply)
        btnSave                 = findViewById(R.id.btnSave)
        cbBlockSettings         = findViewById(R.id.cbBlockSettings)
        cbAutoStart             = findViewById(R.id.cbAutoStart)

        ArrayAdapter.createFromResource(
            this, R.array.filter_mode_options, android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                  spFilterMode.adapter = it }

        btnManageApps.setOnClickListener  {
            startActivity(Intent(this, AppManagerActivity::class.java))
        }
        btnRequestPermissions.setOnClickListener { requestNextPermission() }
        btnApply.setOnClickListener       { saveSettings(false) }
        btnSave.setOnClickListener        { saveSettings(true) }

        // ─── Lock Screen ──────────────────────────────────────────────────
        cbLockWpEnabled     = findViewById(R.id.cbLockWpEnabled)
        tvLockWpApiWarning  = findViewById(R.id.tvLockWpApiWarning)
        tvLockWpImagePath   = findViewById(R.id.tvLockWpImagePath)
        btnPickLockWpImage  = findViewById(R.id.btnPickLockWpImage)
        btnClearLockWpImage = findViewById(R.id.btnClearLockWpImage)
        btnApplyLockWpNow   = findViewById(R.id.btnApplyLockWpNow)
        ivCurrentLockWp     = findViewById(R.id.ivCurrentLockWp)

        // Aviso para Android < 7.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            tvLockWpApiWarning.visibility = View.VISIBLE
        }

        btnPickLockWpImage.setOnClickListener  { openLockImagePicker() }
        btnClearLockWpImage.setOnClickListener {
            pickedLockWpUri = null
            tvLockWpImagePath.text = "(usando cor de fundo)"
        }
        btnApplyLockWpNow.setOnClickListener   { applyLockWallpaperNow() }
    }


    // ─── Permissões ───────────────────────────────────────────────────────────

    private fun updatePermissionsStatus() {
        val status = StringBuilder()

        // 1. Overlay (Sobreposição)
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else true
        status.append(if (hasOverlay) "✅ Sobreposição concedida\n" else "❌ Sobreposição pendente\n")

        // 2. Usage Stats (Acesso a Uso)
        val hasUsage = hasUsageStatsPermission()
        status.append(if (hasUsage) "✅ Acesso a uso concedido\n" else "❌ Acesso a uso pendente\n")

        // 4. Armazenamento (para ler/definir wallpaper)
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        status.append(if (hasStorage) "✅ Acesso a arquivos concedido" else "❌ Acesso a arquivos pendente")

        // 5. Bateria (Autoinício)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else true
        status.append(if (isIgnoringBattery) "\n✅ Autoinício/Bateria ok" else "\n❌ Otimização de bateria ativa")

        tvPermissionsStatus.text = status.toString()
    }

    private fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return true
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun requestNextPermission() {
        // Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        // Usage Stats
        if (!hasUsageStatsPermission()) {
            val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            return
        }

        // Armazenamento
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 102)
                return
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 102)
                return
            }
        }

        // Bateria
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback para a tela geral se a intent direta falhar
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
                return
            }
        }

        Toast.makeText(this, "Todas as permissões críticas foram verificadas.", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (layoutContent.visibility == View.VISIBLE) {
            updatePermissionsStatus()
            updateWallpaperThumbnail()
        }
    }

    private fun populateFields() {
        etBgColor.setText(PrefsManager.getBgColor())

        // Lock screen wallpaper
        cbLockWpEnabled.isChecked = PrefsManager.getLockWpEnabled()
        val lockUri = PrefsManager.getLockWpUri()
        pickedLockWpUri = lockUri?.let { Uri.parse(it) }
        tvLockWpImagePath.text = lockUri ?: "(usando cor de fundo)"

        // Campos de senha ficam em branco por segurança
        etAdminPassword.setText("")
        etAdminPasswordConfirm.setText("")

        val filterValues = resources.getStringArray(R.array.filter_mode_values)
        val filterIdx    = filterValues.indexOf(PrefsManager.getAppFilterMode()).coerceAtLeast(0)
        spFilterMode.setSelection(filterIdx)

        cbBlockSettings.isChecked = PrefsManager.getBlockSettings()
        cbAutoStart.isChecked     = PrefsManager.getAutoStart()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun updateWallpaperThumbnail() {
        try {
            val wm = android.app.WallpaperManager.getInstance(this)
            
            // Força a limpeza do cache interno do WallpaperManager para garantir dados novos
            @Suppress("DEPRECATION")
            wm.forgetLoadedWallpaper()

            // No Android 7.0+, tentamos obter o arquivo específico da Lock Screen.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pfd = try {
                    wm.getWallpaperFile(android.app.WallpaperManager.FLAG_LOCK)
                } catch (e: SecurityException) {
                    null
                }

                if (pfd != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                    ivCurrentLockWp.setImageBitmap(bitmap)
                    pfd.close()
                    return
                }
            }

            // Fallback para o wallpaper padrão (que o sistema usa se a lock não for customizada)
            ivCurrentLockWp.setImageDrawable(wm.drawable)
        } catch (e: Exception) {
            ivCurrentLockWp.setImageResource(android.R.drawable.ic_menu_report_image)
        }
    }

    private fun openLockImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        pickLockImageLauncher.launch(intent)
    }

    private fun applyLockWallpaperNow() {
        PrefsManager.setLockWpEnabled(cbLockWpEnabled.isChecked)
        PrefsManager.setLockWpUri(pickedLockWpUri?.toString())
        
        // Também aplicamos o bloqueio de configurações se alterado manualmente
        PrefsManager.setBlockSettings(cbBlockSettings.isChecked)

        // Reinicia o serviço de overlay para garantir que o bloqueio (ou desbloqueio) seja aplicado
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        WallpaperHelper.apply(this) { success, message ->
            runOnUiThread {
                val prefix = if (success) "✅ " else "❌ "
                Toast.makeText(this, prefix + message, Toast.LENGTH_LONG).show()
                // Pequeno delay para dar tempo ao WallpaperManager de atualizar o descritor de arquivo
                ivCurrentLockWp.postDelayed({ updateWallpaperThumbnail() }, 800)
            }
        }
    }

    private fun saveSettings(finishActivity: Boolean) {
        // Cor de fundo
        val colorStr = etBgColor.text.toString().trim()
        if (colorStr.isNotEmpty()) {
            try {
                android.graphics.Color.parseColor(colorStr)
                PrefsManager.setBgColor(colorStr)
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, "Cor inválida. Use o formato #RRGGBB.", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Senha admin
        val newPass     = etAdminPassword.text.toString()
        val confirmPass = etAdminPasswordConfirm.text.toString()
        if (newPass.isNotEmpty()) {
            if (newPass != confirmPass) {
                Toast.makeText(this, "As senhas não coincidem.", Toast.LENGTH_SHORT).show()
                return
            }
            if (newPass.length < 4) {
                Toast.makeText(this, "Senha mínima: 4 caracteres.", Toast.LENGTH_SHORT).show()
                return
            }
            PrefsManager.setAdminPassword(newPass)
        }

        // Modo de filtro de apps
        val filterValues = resources.getStringArray(R.array.filter_mode_values)
        PrefsManager.setAppFilterMode(filterValues[spFilterMode.selectedItemPosition])

        // Bloqueio de configurações
        PrefsManager.setBlockSettings(cbBlockSettings.isChecked)

        // Início Automático
        PrefsManager.setAutoStart(cbAutoStart.isChecked)

        // Reinicia o serviço de overlay para aplicar a nova regra imediatamente
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Notificar MainActivity
        sendBroadcast(Intent(MainActivity.ACTION_CONFIG_CHANGED))

        // Lock screen wallpaper
        PrefsManager.setLockWpEnabled(cbLockWpEnabled.isChecked)
        PrefsManager.setLockWpUri(pickedLockWpUri?.toString())
        if (cbLockWpEnabled.isChecked) {
            WallpaperHelper.apply(this) { _, _ -> }
        } else {
            WallpaperHelper.clear(this, true)
        }

        Toast.makeText(this, "Configurações aplicadas.", Toast.LENGTH_SHORT).show()
        if (finishActivity) finish()
    }
}
