package com.cityhub.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Tela de primeiro uso — exibida apenas uma vez.
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val RC_PERMISSIONS = 200
    }

    private var currentStep = 0

    private data class Step(val iconRes: Int, val title: String, val description: String)

    private val steps by lazy {
        listOf(
            Step(
                R.drawable.ic_onboard_launcher,
                "Bem-vindo ao CityHub Agent",
                "Um portal de aplicativos gerenciável via MDM, desenvolvido para dispositivos institucionais."
            ),
            Step(
                R.drawable.ic_onboard_apps,
                "Controle de aplicativos",
                "Defina quais apps aparecem na tela inicial usando listas de permissão (allowlist) ou bloqueio (denylist). Ideal para quiosques e totens."
            ),
            Step(
                R.drawable.ic_onboard_permission,
                "Permissões necessárias",
                "Para que o admin possa escolher uma imagem de fundo da galeria, precisamos de acesso à mídia do dispositivo. Nenhum dado é enviado para fora do dispositivo."
            )
        )
    }

    private lateinit var ivIcon:       ImageView
    private lateinit var tvTitle:      TextView
    private lateinit var tvDesc:       TextView
    private lateinit var layoutDots:   LinearLayout
    private lateinit var btnBack:      Button
    private lateinit var btnNext:      Button
    private lateinit var layoutPermStatus: LinearLayout
    private lateinit var tvPermOverlay:   TextView
    private lateinit var tvPermUsage:     TextView
    private lateinit var tvPermBattery:   TextView
    private lateinit var tvPermStorage:   TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se o onboarding já foi feito, decide se abre a UI ou fica em background
        if (PrefsManager.isOnboardingDone()) {
            if (isTaskRoot) {
                // Se foi aberto pelo ícone (TaskRoot), vai para a Main
                goToMain()
            } else {
                // Se foi aberto via BOOT ou UPDATE (não é TaskRoot), apenas inicia os serviços e encerra
                startServicesAndFinish()
            }
            return
        }

        setContentView(R.layout.activity_onboarding)

        ivIcon     = findViewById(R.id.ivOnboardIcon)
        tvTitle    = findViewById(R.id.tvOnboardTitle)
        tvDesc     = findViewById(R.id.tvOnboardDesc)
        layoutDots = findViewById(R.id.layoutDots)
        btnBack    = findViewById(R.id.btnOnboardBack)
        btnNext    = findViewById(R.id.btnOnboardNext)
        layoutPermStatus = findViewById(R.id.layoutPermissionStatus)
        tvPermOverlay    = findViewById(R.id.tvPermOverlay)
        tvPermUsage      = findViewById(R.id.tvPermUsage)
        tvPermBattery    = findViewById(R.id.tvPermBattery)
        tvPermStorage    = findViewById(R.id.tvPermStorage)

        buildDots()
        renderStep(0)

        btnBack.setOnClickListener {
            if (currentStep > 0) renderStep(currentStep - 1)
        }

        btnNext.setOnClickListener {
            if (currentStep < steps.lastIndex) {
                renderStep(currentStep + 1)
            } else {
                requestRequiredPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Se estivermos no último passo, re-valida o texto do botão caso o usuário tenha voltado das configurações
        if (currentStep == steps.lastIndex) {
            renderStep(currentStep)
        }
    }

    private fun renderStep(index: Int) {
        currentStep = index
        val step = steps[index]

        ivIcon.setImageResource(step.iconRes)
        tvTitle.text = step.title
        tvDesc.text  = step.description

        for (i in 0..steps.lastIndex) {
            val dot = layoutDots.getChildAt(i)
            dot?.alpha = if (i == index) 1f else 0.3f
        }

        btnBack.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        
        if (index == steps.lastIndex) {
            layoutPermStatus.visibility = View.VISIBLE
            updatePermissionList()
            btnNext.text = if (allCriticalPermissionsGranted()) "Finalizar Configuração" else "Conceder permissões"
        } else {
            layoutPermStatus.visibility = View.GONE
            btnNext.text = "Próximo →"
        }
    }

    private fun updatePermissionList() {
        // Overlay
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        tvPermOverlay.text = if (hasOverlay) "✓ Sobreposição: Concedida" else "• Sobreposição: Pendente"
        tvPermOverlay.setTextColor(if (hasOverlay) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())

        // Usage
        val hasUsage = hasUsageStatsPermission()
        tvPermUsage.text = if (hasUsage) "✓ Acesso ao Uso: Concedido" else "• Acesso ao Uso: Pendente"
        tvPermUsage.setTextColor(if (hasUsage) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())

        // Battery
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) powerManager.isIgnoringBatteryOptimizations(packageName) else true
        tvPermBattery.text = if (isIgnoringBattery) "✓ Otimização Bateria: Ilimitada" else "• Otimização Bateria: Pendente"
        tvPermBattery.setTextColor(if (isIgnoringBattery) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())

        // Storage
        val hasStorage = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else true
        
        tvPermStorage.text = if (hasStorage) "✓ Armazenamento: Concedido" else "• Armazenamento: Pendente"
        tvPermStorage.setTextColor(if (hasStorage) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
    }

    private fun buildDots() {
        layoutDots.removeAllViews()
        steps.forEachIndexed { i, _ ->
            val dot = View(this).apply {
                val size = (8 * resources.displayMetrics.density).toInt()
                val lp   = LinearLayout.LayoutParams(size, size)
                lp.setMargins(6, 0, 6, 0)
                layoutParams = lp
                setBackgroundResource(R.drawable.bg_dot)
                alpha = if (i == 0) 1f else 0.3f
            }
            layoutDots.addView(dot)
        }
    }

    private fun requestRequiredPermissions() {
        // 1. Sobreposição (Overlay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Por favor, conceda a permissão de sobreposição.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        // 2. Acesso a Uso (Usage Stats)
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Por favor, conceda o acesso aos dados de uso.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            return
        }

        // 3. Bateria (Ignorar Otimizações)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Por favor, permita que o app ignore otimizações de bateria.", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            return
        }

        // 4. Armazenamento (Runtime permissions tradicionais)
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES))
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE))
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (needed.isEmpty()) {
            finishOnboarding()
        } else {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), RC_PERMISSIONS)
        }
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

    private fun allCriticalPermissionsGranted(): Boolean {
        // Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return false
        
        // Usage Stats
        if (!hasUsageStatsPermission()) return false

        // Bateria
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) return false

        // Armazenamento
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) return false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) return false
        }

        return true
    }

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERMISSIONS) {
            if (allCriticalPermissionsGranted()) {
                finishOnboarding()
            } else {
                Toast.makeText(this, "Permissões de armazenamento são necessárias.", Toast.LENGTH_SHORT).show()
                renderStep(currentStep)
            }
        }
    }

    private fun finishOnboarding() {
        PrefsManager.setOnboardingDone(true)
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun startServicesAndFinish() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        // Garante aplicação do wallpaper e config no start silencioso
        PrefsManager.applyMdmConfig(this)
        WallpaperHelper.apply(this)
        finish()
    }

    override fun onBackPressed() {
        if (currentStep > 0) {
            renderStep(currentStep - 1)
        } else {
            super.onBackPressed()
        }
    }
}
