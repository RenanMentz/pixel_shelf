package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.pixelshelf.R
import com.pixelshelf.data.Usuario
import com.pixelshelf.databinding.ActivityContaSteamBinding
import kotlinx.coroutines.launch

/**
 * Vinculação da conta Steam do usuário (pelo link do perfil, sem senha), importação dos
 * jogos da conta e sincronização das conquistas.
 */
class ContaSteamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContaSteamBinding
    private val viewModel: ContaSteamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContaSteamBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (!viewModel.temChave) {
            binding.tvSemChave.visibility = View.VISIBLE
            return
        }

        binding.btnVincular.setOnClickListener { vincular() }
        binding.etPerfil.setOnEditorActionListener { _, acao, _ ->
            if (acao == EditorInfo.IME_ACTION_DONE) { vincular(); true } else false
        }
        binding.etPerfil.doAfterTextChanged { binding.tilPerfil.error = null }
        binding.btnImportar.setOnClickListener {
            startActivity(Intent(this, ImportarJogosSteamActivity::class.java))
        }
        binding.btnSincronizar.setOnClickListener { viewModel.sincronizar() }
        binding.btnDesvincular.setOnClickListener { confirmarDesvinculo() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.usuario.collect { it?.let(::mostrar) } }
                launch {
                    viewModel.perfil.collect { p ->
                        if (p == null) return@collect
                        binding.tvNomeSteam.text = p.nome
                        binding.ivAvatar.load(p.avatarUrl) { crossfade(true) }
                    }
                }
                launch {
                    viewModel.ocupado.collect { ocupado ->
                        binding.progresso.visibility = if (ocupado) View.VISIBLE else View.INVISIBLE
                        listOf(binding.btnVincular, binding.btnImportar, binding.btnSincronizar, binding.btnDesvincular)
                            .forEach { it.isEnabled = !ocupado }
                    }
                }
                launch {
                    viewModel.eventos.collect { e ->
                        when (e) {
                            is ContaSteamViewModel.Evento.ErroCampo -> binding.tilPerfil.error = e.texto
                            is ContaSteamViewModel.Evento.Mensagem ->
                                Snackbar.make(binding.root, e.texto, Snackbar.LENGTH_LONG).show()
                            is ContaSteamViewModel.Evento.Sincronizado -> {
                                binding.tvResultado.visibility = View.VISIBLE
                                binding.tvResultado.text = e.texto
                                binding.tvResultado.setTextColor(ContextCompat.getColor(
                                    this@ContaSteamActivity,
                                    if (e.texto.startsWith("✓")) R.color.dourado else R.color.texto
                                ))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mostrar(u: Usuario) {
        val vinculada = u.steamId != null
        binding.secaoVincular.visibility = if (vinculada) View.GONE else View.VISIBLE
        binding.secaoVinculada.visibility = if (vinculada) View.VISIBLE else View.GONE
        if (vinculada) {
            // limpa o link digitado: ao desvincular, o campo não pode reaproveitar o perfil antigo
            binding.etPerfil.text?.clear()
            binding.tilPerfil.error = null
            binding.tvSteamId.text = "SteamID ${u.steamId}"
            if (binding.tvNomeSteam.text.isNullOrEmpty()) binding.tvNomeSteam.text = "Carregando…"
            viewModel.carregarPerfil(u.steamId!!)
        } else {
            binding.tvNomeSteam.text = null
            binding.tvResultado.visibility = View.GONE
        }
    }

    private fun vincular() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.etPerfil.windowToken, 0)
        viewModel.vincular(binding.etPerfil.text.toString())
    }

    private fun confirmarDesvinculo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Desvincular conta Steam")
            .setMessage("Os jogos e conquistas que já estão na biblioteca continuam salvos. Deseja desvincular?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Desvincular") { _, _ -> viewModel.desvincular() }
            .show()
    }
}
