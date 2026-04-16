package com.cityhub.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Receiver para iniciar o agente automaticamente após o boot do sistema.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // Inicializa o PrefsManager caso o App.kt ainda não tenha sido instanciado
        PrefsManager.init(context)

        // Se o onboarding não foi feito, abre a UI para configuração obrigatória
        if (!PrefsManager.isOnboardingDone()) {
            val launchIntent = Intent(context, OnboardingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
            return
        }

        // Se já está configurado, decide se inicia silenciosamente
        if (PrefsManager.getAutoStart() || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Inicia apenas o serviço de monitoramento em segundo plano
            val serviceIntent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Força a aplicação das regras do MDM e Wallpaper no boot/update
            PrefsManager.applyMdmConfig(context)
            WallpaperHelper.apply(context)
        }
    }
}
