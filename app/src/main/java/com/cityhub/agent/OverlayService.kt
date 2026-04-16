package com.cityhub.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Serviço para manter o app rodando em segundo plano e impor restrições de apps (App Locker).
 */
class OverlayService : Service() {

    companion object {
        private const val CHECK_INTERVAL = 500L // Verifica a cada 0.5 segundos para maior rigor
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkAppsRunnable = object : Runnable {
        override fun run() {
            checkCurrentApp()
            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        handler.post(checkAppsRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "CityHub MDM Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CityHub está ativo")
            .setContentText("Monitorando conformidade do dispositivo")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkAppsRunnable)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private var lastCheckedPackage: String? = null

    private fun checkCurrentApp() {
        val currentPackage = getForegroundPackage() ?: return
        if (currentPackage == packageName) return // Ignora se for o próprio app
        
        // Se o pacote mudou, sincronizamos com o MDM para garantir que temos as restrições mais recentes
        if (currentPackage != lastCheckedPackage) {
            PrefsManager.applyMdmConfig(this)
            lastCheckedPackage = currentPackage
        }

        // 1. Ignora pacotes essenciais de sistema que nunca devem ser bloqueados
        val essentialPackages = listOf("android", "com.android.systemui")
        if (currentPackage in essentialPackages) return

        // 2. Verifica se o bloqueio específico do app de Configurações está ativo
        // Consideramos também variações de fabricantes comuns
        val isSettingsApp = currentPackage == "com.android.settings" || 
                            currentPackage == "com.google.android.settings" ||
                            currentPackage == "com.samsung.android.settings"
                            
        if (isSettingsApp && PrefsManager.getBlockSettings()) {
            blockApp(currentPackage)
            return
        }

        // 3. Verifica filtros gerais (Allowlist / Denylist)
        val mode = PrefsManager.getAppFilterMode()
        if (mode == "none") return

        val set = when (mode) {
            "allowlist" -> PrefsManager.getAllowedApps()
            "denylist" -> PrefsManager.getDeniedApps()
            else -> emptySet()
        }

        val shouldBlock = when (mode) {
            "allowlist" -> currentPackage !in set
            "denylist" -> currentPackage in set
            else -> false
        }

        if (shouldBlock) {
            blockApp(currentPackage)
        }
    }

    private fun blockApp(packageNameToBlock: String) {
        // "Mata" o app enviando o usuário para a Home antes de abrir a tela de bloqueio
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)

        // Abre a BlockedAppActivity
        val launchIntent = Intent(this, BlockedAppActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        startActivity(launchIntent)

        // Tenta matar o processo do app bloqueado (requer KILL_BACKGROUND_PROCESSES)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(packageNameToBlock)
        } catch (e: Exception) {
            // Falha silenciosa
        }
    }

    private fun getForegroundPackage(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return try { am.getRunningTasks(1)?.get(0)?.topActivity?.packageName } catch (e: Exception) { null }
        }

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        // Tenta obter o pacote mais recente dos últimos 10 segundos
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)
        if (!stats.isNullOrEmpty()) {
            return stats.maxByOrNull { it.lastTimeUsed }?.packageName
        }

        // Fallback: busca nos últimos 60 segundos
        val stats60 = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
        return stats60?.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
