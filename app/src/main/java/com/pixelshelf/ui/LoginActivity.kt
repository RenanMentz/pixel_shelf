package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.AuthRepository
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/** Login com e-mail e senha (contas guardadas no Room). */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)

        binding.btnEntrar.setOnClickListener { tentarLogin() }
        binding.btnCriarConta.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        entrarSeJaLogado()
    }

    /** Se "manter conectado" estava ativo, pula direto para a biblioteca. */
    private fun entrarSeJaLogado() {
        val id = SessaoUsuario.idLogado(this)
        if (id == SessaoUsuario.NENHUM) return
        lifecycleScope.launch {
            val usuario = AppDatabase.get(this@LoginActivity).usuarioDao().buscarPorId(id)
            if (usuario != null) irParaMain() else SessaoUsuario.sair(this@LoginActivity)
        }
    }

    private fun tentarLogin() {
        val email = binding.etEmail.text.toString().trim()
        val senha = binding.etSenha.text.toString()

        binding.tilEmail.error = when {
            email.isEmpty() -> "Informe o e-mail"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "E-mail inválido"
            else -> null
        }
        binding.tilSenha.error = if (senha.isEmpty()) "Informe a senha" else null
        if (binding.tilEmail.error != null || binding.tilSenha.error != null) return

        binding.btnEntrar.isEnabled = false
        lifecycleScope.launch {
            val usuario = auth.autenticar(email, senha)
            binding.btnEntrar.isEnabled = true
            if (usuario == null) {
                // mensagem genérica: não revela se o e-mail existe
                binding.tilSenha.error = "E-mail ou senha incorretos"
                return@launch
            }
            SessaoUsuario.entrar(this@LoginActivity, usuario.id, binding.cbManter.isChecked)
            irParaMain()
        }
    }

    private fun irParaMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
