package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pixelshelf.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEntrar.setOnClickListener { tentarLogin() }
    }

    // Login simulado (sem backend): usuario "admin" e senha "1234"
    private fun tentarLogin() {
        val usuario = binding.etUsuario.text.toString().trim()
        val senha = binding.etSenha.text.toString()

        binding.tilUsuario.error = if (usuario.isEmpty()) "Informe o usuário" else null
        binding.tilSenha.error = if (senha.isEmpty()) "Informe a senha" else null
        if (usuario.isEmpty() || senha.isEmpty()) return

        if (usuario == "admin" && senha == "1234") {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            Toast.makeText(this, "Usuário ou senha inválidos", Toast.LENGTH_SHORT).show()
        }
    }
}
