package com.cityhub.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

/**
 * Tela principal do CityHub Agent.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_CONFIG_CHANGED = "com.cityhub.agent.CONFIG_CHANGED"
    }

    private lateinit var rootView:         View
    private lateinit var btnSettings:     Button
    private lateinit var btnForceRules:   Button
    private lateinit var tvEmptyMsg:      TextView
    private lateinit var tvVersion:       TextView
    private lateinit var tvTitle:         TextView
    private lateinit var tvWelcome:       TextView
    private lateinit var rvHomeApps:      RecyclerView

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkUsageStatsPermission()
            startOverlayService()
        } else {
            Toast.makeText(this, "Permissão de sobreposição necessária.", Toast.LENGTH_LONG).show()
        }
    }

    // Reage a atualizações de config (MDM ou SettingsActivity)
    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applyBackground()
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootView        = findViewById(R.id.rootView)
        btnSettings     = findViewById(R.id.btnSettings)
        btnForceRules   = findViewById(R.id.btnForceRules)
        tvEmptyMsg      = findViewById(R.id.tvEmptyMsg)
        tvVersion       = findViewById(R.id.tvVersion)
        tvTitle         = findViewById(R.id.tvTitle)
        tvWelcome       = findViewById(R.id.tvWelcome)
        rvHomeApps      = findViewById(R.id.rvHomeApps)

        rvHomeApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnForceRules.setOnClickListener {
            forceRules()
        }

        displayVersion()
        applyBackground()
        checkOverlayPermissionAndStartService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_CONFIG_CHANGED) {
            applyBackground()
        }
    }

    private fun checkOverlayPermissionAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            checkUsageStatsPermission()
            startOverlayService()
        }
    }

    private fun checkUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!isUsageStatsPermissionGranted()) {
                Toast.makeText(this, "Por favor, conceda acesso ao uso para bloquear apps.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                configReceiver,
                IntentFilter(ACTION_CONFIG_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(configReceiver, IntentFilter(ACTION_CONFIG_CHANGED))
        }
        applyBackground()
        loadHomeApps()

        // Força a aplicação do wallpaper toda vez que a MainActivity volta ao topo (abertura do app)
        WallpaperHelper.apply(this)
    }

    private fun loadHomeApps() {
        val pm = packageManager
        val homePackageNames = PrefsManager.getHomeApps()
        
        if (homePackageNames.isEmpty()) {
            rvHomeApps.visibility = View.GONE
            tvEmptyMsg.visibility = View.VISIBLE
            tvEmptyMsg.text = "Nenhum app selecionado para a Home.\nVá em Configurações > Gerenciar Apps."
            return
        }

        val homeAppsList = mutableListOf<AppInfo>()
        for (pkg in homePackageNames) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                homeAppsList.add(AppInfo(
                    packageName = pkg,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo)
                ))
            } catch (e: PackageManager.NameNotFoundException) {
                // App desinstalado ou inválido
            }
        }

        rvHomeApps.visibility = View.VISIBLE
        tvEmptyMsg.visibility = View.GONE
        rvHomeApps.adapter = AppAdapter(homeAppsList) { app ->
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Não foi possível abrir o app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(configReceiver)
    }

    // ─── Ações ────────────────────────────────────────────────────────────────

    private fun displayVersion() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            tvVersion.text = "CITYHUB MDM v$version\nrafaantonio"
        } catch (e: Exception) {
            // Fallback se falhar
        }
    }

    private fun forceRules() {
        Toast.makeText(this, "Forçando sincronização MDM...", Toast.LENGTH_SHORT).show()
        PrefsManager.applyMdmConfig(this)
        applyBackground()
        
        // Atualiza o wallpaper logo após aplicar as novas configurações do MDM
        WallpaperHelper.apply(this)
        
        // Reinicia o serviço de overlay para garantir que novas listas de apps sejam aplicadas
        startOverlayService()
    }

    // ─── Fundo da Activity ────────────────────────────────────────────────────

    private fun applyBackground() {
        val welcomeText = PrefsManager.getWelcomeText()
        if (welcomeText.isNotEmpty()) {
            tvWelcome.text = welcomeText
            tvWelcome.visibility = View.VISIBLE
        } else {
            tvWelcome.visibility = View.GONE
        }

        val colorStr = PrefsManager.getBgColor()
        val color = try {
            Color.parseColor(colorStr)
        } catch (e: IllegalArgumentException) {
            Color.parseColor(PrefsManager.DEFAULT_BG_COLOR)
        }
        rootView.background = ColorDrawable(color)

        // Ajusta contraste do texto (preto ou branco) baseado na luminosidade do fundo
        val isDark = isColorDark(color)
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        
        tvTitle.setTextColor(textColor)
        tvWelcome.setTextColor(textColor)
        tvWelcome.alpha = 0.8f
        btnSettings.setTextColor(textColor)
        btnForceRules.setTextColor(textColor)
        tvVersion.setTextColor(textColor)
        
        // Ajusta a borda do botão para ser visível mas discreta
        val strokeColor = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        val btnBg = btnSettings.background as? android.graphics.drawable.GradientDrawable
        btnBg?.setStroke(1 * resources.displayMetrics.density.toInt(), strokeColor)
        (btnForceRules.background as? android.graphics.drawable.GradientDrawable)?.setStroke(
            1 * resources.displayMetrics.density.toInt(), strokeColor
        )
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }
}
