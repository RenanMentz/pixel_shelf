package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pixelshelf.data.AuthRepository
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

/** Cadastro de conta: nome, e-mail, senha e confirmação, com validações. */
class RegisterActivity : AppCompatActivity() {

    private companion object { const val TAMANHO_MINIMO_SENHA = 6 }

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)

        binding.btnCriarConta.setOnClickListener { criarConta() }
        binding.btnJaTenhoConta.setOnClickListener { finish() }
    }

    private fun criarConta() {
        val nome = binding.etNome.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val senha = binding.etSenha.text.toString()
        val confirmar = binding.etConfirmar.text.toString()

        binding.tilNome.error = if (nome.length < 2) "Informe seu nome" else null
        binding.tilEmail.error = when {
            email.isEmpty() -> "Informe o e-mail"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "E-mail inválido"
            else -> null
        }
        binding.tilSenha.error =
            if (senha.length < TAMANHO_MINIMO_SENHA) "Mínimo de $TAMANHO_MINIMO_SENHA caracteres" else null
        binding.tilConfirmar.error = if (confirmar != senha) "As senhas não conferem" else null

        if (listOf(binding.tilNome, binding.tilEmail, binding.tilSenha, binding.tilConfirmar)
                .any { it.error != null }
        ) return

        binding.btnCriarConta.isEnabled = false
        lifecycleScope.launch {
            val usuario = auth.cadastrar(nome, email, senha)
            binding.btnCriarConta.isEnabled = true
            if (usuario == null) {
                binding.tilEmail.error = "Já existe uma conta com esse e-mail"
                return@launch
            }
            // Conta criada: já entra no app e mantém conectado
            SessaoUsuario.entrar(this@RegisterActivity, usuario.id, manterConectado = true)
            startActivity(
                Intent(this@RegisterActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }
}
