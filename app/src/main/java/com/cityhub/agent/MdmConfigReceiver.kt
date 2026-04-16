package com.cityhub.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/**
 * Recebe ACTION_APPLICATION_RESTRICTIONS_CHANGED enviado pelo MDM
 * sempre que o servidor atualizar as Managed Configurations deste app.
 *
 * ⚠ RestrictionsManager existe desde API 18.
 *   Em API 19 (nosso minSdk) o broadcast pode ser recebido normalmente.
 */
class MdmConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) return

        // Aplica novo payload MDM no PrefsManager
        PrefsManager.applyMdmConfig(context)

        // Aplica o wallpaper imediatamente em segundo plano (forçado via MDM)
        WallpaperHelper.apply(context, force = true)

        // Notifica a MainActivity via Broadcast caso ela esteja aberta (onResume/onPause cuidam do registro)
        val configIntent = Intent(MainActivity.ACTION_CONFIG_CHANGED).apply {
            // Em Android 14+ broadcasts implícitos precisam ser tratados com cautela, 
            // mas como é interno e registrado via código, funciona.
            setPackage(context.packageName)
        }
        context.sendBroadcast(configIntent)

        // Reinicia o serviço de bloqueio para aplicar novas regras imediatamente em segundo plano
        val serviceIntent = Intent(context, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // REMOVIDO: startActivity/getLaunchIntent que forçava a abertura da UI.
        // Agora o receiver é totalmente silencioso e apenas atualiza o estado interno e o serviço.
    }
}
