package com.cityhub.agent

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BlockedAppActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_app)

        val btnBack = findViewById<Button>(R.id.btnBackToHome)

        btnBack.setOnClickListener {
            // Volta para a Home (Launcher padrão)
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        // Impede o usuário de voltar para o app bloqueado
        super.onBackPressed()
        finish()
    }
}
