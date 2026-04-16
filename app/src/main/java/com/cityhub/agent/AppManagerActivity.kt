package com.cityhub.agent

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Tela para selecionar apps na allowlist ou denylist.
 *
 * Duas abas: "Permitidos" e "Negados".
 * O modo de filtragem (que lista fica ativa) é configurado em SettingsActivity.
 *
 * Compatível com Android 4.4+ (API 19).
 */
class AppManagerActivity : AppCompatActivity() {

    private lateinit var rvApps:      RecyclerView
    private lateinit var etSearch:    EditText
    private lateinit var tvMode:      TextView
    private lateinit var btnTabAllow: Button
    private lateinit var btnTabDeny:  Button
    private lateinit var btnTabHome:  Button
    private lateinit var btnSave:     Button

    // Listas com estado de seleção independente por tab
    private val allowApps = mutableListOf<AppInfo>()
    private val denyApps  = mutableListOf<AppInfo>()
    private val homeApps  = mutableListOf<AppInfo>()

    private var currentTab = "allowlist"

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manager)

        rvApps      = findViewById(R.id.rvApps)
        etSearch    = findViewById(R.id.etSearch)
        tvMode      = findViewById(R.id.tvCurrentMode)
        btnTabAllow = findViewById(R.id.btnTabAllow)
        btnTabDeny  = findViewById(R.id.btnTabDeny)
        btnTabHome  = findViewById(R.id.btnTabHome)
        btnSave     = findViewById(R.id.btnSaveApps)

        rvApps.layoutManager = LinearLayoutManager(this)

        tvMode.text = "Modo de filtro ativo: ${PrefsManager.getAppFilterMode()}"

        loadApps()
        showTab("allowlist")

        btnTabAllow.setOnClickListener { showTab("allowlist") }
        btnTabDeny.setOnClickListener  { showTab("denylist")  }
        btnTabHome.setOnClickListener  { showTab("home")      }
        btnSave.setOnClickListener     { saveAndExit()        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = renderList(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    // ─── Carregamento ─────────────────────────────────────────────────────────

    private fun loadApps() {
        val pm = packageManager
        val queryIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Flag 0 compatível com API 19 (MATCH_ALL é API 23+)
        @Suppress("DEPRECATION")
        val installed = pm.queryIntentActivities(queryIntent, 0)
            .map { ri ->
                AppInfo(
                    packageName = ri.activityInfo.packageName,
                    label       = ri.loadLabel(pm).toString(),
                    icon        = ri.loadIcon(pm)
                )
            }
            .filter { it.packageName != packageName }
            .sortedBy { it.label.lowercase() }

        val savedAllowed = PrefsManager.getAllowedApps()
        val savedDenied  = PrefsManager.getDeniedApps()
        val savedHome     = PrefsManager.getHomeApps()

        allowApps.clear(); denyApps.clear(); homeApps.clear()
        installed.forEach { base ->
            allowApps.add(base.copy(isSelected = base.packageName in savedAllowed))
            denyApps.add(base.copy(isSelected  = base.packageName in savedDenied))
            homeApps.add(base.copy(isSelected  = base.packageName in savedHome))
        }
    }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private fun showTab(tab: String) {
        currentTab = tab
        
        // Estilização das abas (Simulando Material You)
        val activeColor = androidx.core.content.ContextCompat.getColor(this, R.color.settings_accent)
        val inactiveColor = androidx.core.content.ContextCompat.getColor(this, R.color.settings_text_secondary)

        btnTabAllow.setTextColor(if (tab == "allowlist") activeColor else inactiveColor)
        btnTabDeny.setTextColor(if (tab == "denylist") activeColor else inactiveColor)
        btnTabHome.setTextColor(if (tab == "home") activeColor else inactiveColor)

        // No Android 14+, abas costumam ter um peso visual maior. 
        // Aqui apenas ajustamos o texto por simplicidade e compatibilidade.

        renderList(etSearch.text.toString())
    }

    private fun renderList(query: String) {
        val source = when (currentTab) {
            "allowlist" -> allowApps
            "denylist"  -> denyApps
            else        -> homeApps
        }
        val display = if (query.isBlank()) source
        else source.filter {
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
        rvApps.adapter = AppAdapter(display, showCheckbox = true) {}
    }

    // ─── Persistir ────────────────────────────────────────────────────────────

    private fun saveAndExit() {
        // AppInfo.isSelected é mutado diretamente pelo AppAdapter via CheckBox
        PrefsManager.setAllowedApps(allowApps.filter { it.isSelected }.map { it.packageName }.toSet())
        PrefsManager.setDeniedApps(denyApps.filter   { it.isSelected }.map { it.packageName }.toSet())
        PrefsManager.setHomeApps(homeApps.filter     { it.isSelected }.map { it.packageName }.toSet())

        // Reinicia o serviço de overlay para aplicar as novas regras imediatamente
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        sendBroadcast(Intent(MainActivity.ACTION_CONFIG_CHANGED))
        Toast.makeText(this, "Listas salvas com sucesso.", Toast.LENGTH_SHORT).show()
        finish()
    }
}
