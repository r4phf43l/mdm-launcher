package com.cityhub.agent

import android.annotation.TargetApi
import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit

/**
 * Gerenciador central de configurações.
 */
object PrefsManager {

    // ─── Chaves ─────────────
    const val KEY_BG_COLOR        = "bg_color"
    const val KEY_ADMIN_PASSWORD  = "admin_password"
    const val KEY_APP_FILTER_MODE = "app_filter_mode"
    const val KEY_ALLOWED_APPS    = "allowed_apps"
    const val KEY_DENIED_APPS     = "denied_apps"
    const val KEY_HOME_APPS       = "home_apps"

    const val KEY_LOCK_WP_ENABLED = "lock_wallpaper_enabled"
    const val KEY_LOCK_WP_URI     = "lock_wallpaper_uri"
    const val KEY_BLOCK_SETTINGS    = "block_settings"
    const val KEY_AUTO_START        = "auto_start"
    const val KEY_WELCOME_TEXT      = "welcome_text"
    const val KEY_CONFIG_JSON       = "config_json"

    // ─── Defaults ─────────────────────────────────────────────────────────────
    const val DEFAULT_BG_COLOR        = "#1A1A2E"
    const val DEFAULT_ADMIN_PASSWORD  = "admin123"
    const val DEFAULT_APP_FILTER_MODE = "none"

    private const val PREFS_NAME = "cityhub_launcher_prefs"
    const val PREFS_INTERNAL = "cityhub_internal_state"

    private lateinit var prefs: SharedPreferences
    private lateinit var internalPrefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        internalPrefs = context.getSharedPreferences(PREFS_INTERNAL, Context.MODE_PRIVATE)
    }

    // ─── Leitura ──────────────────────────────────────────────────────────────

    fun getBgColor()       = prefs.getString(KEY_BG_COLOR,        DEFAULT_BG_COLOR)        ?: DEFAULT_BG_COLOR
    fun getAdminPassword() = prefs.getString(KEY_ADMIN_PASSWORD,  DEFAULT_ADMIN_PASSWORD)  ?: DEFAULT_ADMIN_PASSWORD
    fun getAppFilterMode() = prefs.getString(KEY_APP_FILTER_MODE, DEFAULT_APP_FILTER_MODE) ?: DEFAULT_APP_FILTER_MODE

    fun getAllowedApps(): Set<String> = parseSet(prefs.getString(KEY_ALLOWED_APPS, "") ?: "")
    fun getDeniedApps():  Set<String> = parseSet(prefs.getString(KEY_DENIED_APPS,  "") ?: "")
    fun getHomeApps():    Set<String> = parseSet(prefs.getString(KEY_HOME_APPS,    "") ?: "")

    fun getLockWpEnabled(): Boolean = prefs.getBoolean(KEY_LOCK_WP_ENABLED, true)
    fun getLockWpUri():     String? = prefs.getString(KEY_LOCK_WP_URI, null)
    fun getBlockSettings(): Boolean = prefs.getBoolean(KEY_BLOCK_SETTINGS, false)
    fun getAutoStart(): Boolean     = prefs.getBoolean(KEY_AUTO_START, true)
    fun getWelcomeText(): String    = prefs.getString(KEY_WELCOME_TEXT, "") ?: ""

    // ─── Escrita ──────────────────────────────────────────────────────────────

    fun setBgColor(v: String)       { prefs.edit().putString(KEY_BG_COLOR, v).apply() }
    fun setAdminPassword(v: String) { prefs.edit().putString(KEY_ADMIN_PASSWORD, v).apply() }
    fun setAppFilterMode(v: String) { prefs.edit().putString(KEY_APP_FILTER_MODE, v).apply() }

    fun setAllowedApps(set: Set<String>) {
        prefs.edit().putString(KEY_ALLOWED_APPS, set.joinToString(",")).apply()
    }
    fun setDeniedApps(set: Set<String>) {
        prefs.edit().putString(KEY_DENIED_APPS, set.joinToString(",")).apply()
    }
    fun setHomeApps(set: Set<String>) {
        prefs.edit().putString(KEY_HOME_APPS, set.joinToString(",")).apply()
    }

    fun setLockWpEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_LOCK_WP_ENABLED, v).apply() }
    fun setLockWpUri(v: String?) {
        val oldUri = getLockWpUri()
        prefs.edit().putString(KEY_LOCK_WP_URI, v).apply()
        
        // Se a URI mudou e não é nula, marcamos que precisa de atualização imediata
        if (v != null && v != oldUri) {
            setPendingWpUpdate(true)
        }
    }

    fun setPendingWpUpdate(pending: Boolean) {
        internalPrefs.edit().putBoolean("pending_wp_update", pending).apply()
    }

    fun isPendingWpUpdate(): Boolean {
        return internalPrefs.getBoolean("pending_wp_update", false)
    }
    fun setBlockSettings(v: Boolean) { prefs.edit().putBoolean(KEY_BLOCK_SETTINGS, v).apply() }
    fun setAutoStart(v: Boolean)     { prefs.edit().putBoolean(KEY_AUTO_START, v).apply() }
    fun setWelcomeText(v: String)    { prefs.edit().putString(KEY_WELCOME_TEXT, v).apply() }

    // ─── Internal State ──────────────────────────────────────────────────────

    fun setOnboardingDone(done: Boolean) {
        internalPrefs.edit().putBoolean("onboarding_done", done).apply()
    }

    fun isOnboardingDone(): Boolean {
        return internalPrefs.getBoolean("onboarding_done", false)
    }

    // ─── MDM ─────────────────────────────────────────────────────────────────

    fun applyMdmConfig(context: Context) {
        // RestrictionsManager is available from API 18. minSdk is 19.
        applyMdmConfigInternal(context)
    }

    private fun applyMdmConfigInternal(context: Context) {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager ?: return
        val restrictions = rm.applicationRestrictions
        if (restrictions.isEmpty) return

        prefs.edit {
            // 1. Processa JSON se existir (sobrescreve campos individuais)
            getSafeString(restrictions, KEY_CONFIG_JSON)?.let { jsonStr ->
                try {
                    val json = org.json.JSONObject(jsonStr)
                    if (json.has(KEY_BG_COLOR))       putString(KEY_BG_COLOR,        json.getString(KEY_BG_COLOR))
                    if (json.has(KEY_ADMIN_PASSWORD)) putString(KEY_ADMIN_PASSWORD,  json.getString(KEY_ADMIN_PASSWORD))
                    if (json.has(KEY_APP_FILTER_MODE)) putString(KEY_APP_FILTER_MODE, json.getString(KEY_APP_FILTER_MODE))
                    if (json.has(KEY_ALLOWED_APPS))    putString(KEY_ALLOWED_APPS,    json.getString(KEY_ALLOWED_APPS))
                    if (json.has(KEY_DENIED_APPS))     putString(KEY_DENIED_APPS,     json.getString(KEY_DENIED_APPS))
                    if (json.has(KEY_HOME_APPS))       putString(KEY_HOME_APPS,       json.getString(KEY_HOME_APPS))
                    if (json.has(KEY_LOCK_WP_ENABLED)) putBoolean(KEY_LOCK_WP_ENABLED, json.getBoolean(KEY_LOCK_WP_ENABLED))
                    if (json.has(KEY_LOCK_WP_URI))     putString(KEY_LOCK_WP_URI,     json.getString(KEY_LOCK_WP_URI))
                    if (json.has(KEY_BLOCK_SETTINGS))  putBoolean(KEY_BLOCK_SETTINGS, json.getBoolean(KEY_BLOCK_SETTINGS))
                    if (json.has(KEY_AUTO_START))     putBoolean(KEY_AUTO_START,     json.getBoolean(KEY_AUTO_START))
                    if (json.has(KEY_WELCOME_TEXT))   putString(KEY_WELCOME_TEXT,    json.getString(KEY_WELCOME_TEXT))
                    Unit
                } catch (e: Exception) {
                    android.util.Log.e("PrefsManager", "Erro ao processar config_json: ${e.message}")
                }
                Unit
            }

            // 2. Processa campos individuais (MDM tradicional)
            getSafeString(restrictions, KEY_BG_COLOR)?.let        { putString(KEY_BG_COLOR,        it) }
            getSafeString(restrictions, KEY_ADMIN_PASSWORD)?.let  { putString(KEY_ADMIN_PASSWORD,  it) }
            getSafeString(restrictions, KEY_APP_FILTER_MODE)?.let { putString(KEY_APP_FILTER_MODE, it) }
            getSafeString(restrictions, KEY_ALLOWED_APPS)?.let    { putString(KEY_ALLOWED_APPS,    it) }
            getSafeString(restrictions, KEY_DENIED_APPS)?.let     { putString(KEY_DENIED_APPS,     it) }
            getSafeString(restrictions, KEY_HOME_APPS)?.let       { putString(KEY_HOME_APPS,       it) }

            if (restrictions.containsKey(KEY_LOCK_WP_ENABLED)) {
                putBoolean(KEY_LOCK_WP_ENABLED, restrictions.getBoolean(KEY_LOCK_WP_ENABLED))
            }
            getSafeString(restrictions, KEY_LOCK_WP_URI)?.let { 
                putString(KEY_LOCK_WP_URI, it) 
            }
            if (restrictions.containsKey(KEY_BLOCK_SETTINGS)) {
                putBoolean(KEY_BLOCK_SETTINGS, restrictions.getBoolean(KEY_BLOCK_SETTINGS))
            }
            if (restrictions.containsKey(KEY_AUTO_START)) {
                putBoolean(KEY_AUTO_START, restrictions.getBoolean(KEY_AUTO_START))
            }
            getSafeString(restrictions, KEY_WELCOME_TEXT)?.let {
                putString(KEY_WELCOME_TEXT, it)
            }
        }
    }

    private fun getSafeString(bundle: android.os.Bundle, key: String): String? {
        @Suppress("DEPRECATION")
        val value = bundle.get(key) ?: return null
        return value.toString()
    }

    private fun parseSet(raw: String): Set<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
