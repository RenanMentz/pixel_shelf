package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.pixelshelf.R
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.Conquista
import com.pixelshelf.data.Game
import com.pixelshelf.databinding.ActivityGameDetailBinding
import kotlinx.coroutines.launch

class GameDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_GAME_ID = "GAME_ID" }

    private lateinit var binding: ActivityGameDetailBinding
    private var game: Game? = null
    private var excluindo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.get(this)
        val id = intent.getIntExtra(EXTRA_GAME_ID, -1)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.acaoEditar -> {
                    startActivity(
                        Intent(this, GameFormActivity::class.java)
                            .putExtra(GameFormActivity.EXTRA_GAME_ID, id)
                    )
                    true
                }
                R.id.acaoExcluir -> { confirmarExclusao(); true }
                else -> false
            }
        }

        val adapter = ConquistaAdapter { c ->
            lifecycleScope.launch { db.conquistaDao().definirObtida(c.id, !c.obtida) }
        }
        binding.rvConquistas.layoutManager = LinearLayoutManager(this)
        binding.rvConquistas.adapter = adapter

        // Observa o jogo: ao voltar da edição a tela já mostra os dados atualizados
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    db.gameDao().observarPorId(id).collect { g ->
                        if (g == null) { if (!excluindo) finish(); return@collect }
                        game = g
                        mostrar(g)
                    }
                }
                launch {
                    db.conquistaDao().observarDoJogo(id).collect { lista ->
                        adapter.submitList(lista)
                        mostrarProgresso(lista)
                    }
                }
            }
        }
    }

    private fun mostrar(g: Game) {
        with(binding) {
            ivCapa.load(g.capaUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_capa_placeholder)
                error(R.drawable.ic_capa_placeholder)
            }
            tvTitulo.text = g.titulo
            tvPlataforma.text = g.plataforma
            tvStatus.text = g.status
            tvNota.text = "★ ${Formatos.nota(g.nota)}"
            tvHoras.text = "${Formatos.numero(g.horasJogadas)} h jogadas"

            val info = listOfNotNull(
                Formatos.data(g.dataLancamento)?.let { "Lançamento: $it" },
                g.desenvolvedora?.let { "Desenvolvedora: $it" },
                g.genero?.let { "Gênero: $it" }
            )
            tvInfo.text = info.joinToString("\n")
            tvInfo.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE
            tvDescricao.text = g.descricao
            tvDescricao.visibility = if (g.descricao.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun mostrarProgresso(lista: List<Conquista>) {
        binding.secaoConquistas.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        if (lista.isEmpty()) return
        val obtidas = lista.count { it.obtida }
        val pct = obtidas * 100 / lista.size
        binding.tvProgressoConquistas.text =
            "$obtidas de ${lista.size} desbloqueadas ($pct%)" + if (pct == 100) " 🏆" else ""
        binding.progressoConquistas.setProgressCompat(pct, true)
    }

    private fun confirmarExclusao() {
        val g = game ?: return
        AlertDialog.Builder(this)
            .setTitle("Excluir jogo")
            .setMessage("Remover \"${g.titulo}\" da biblioteca? As conquistas dele também serão apagadas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                excluindo = true
                lifecycleScope.launch {
                    AppDatabase.get(this@GameDetailActivity).gameDao().excluir(g)
                    Capas.apagarSeLocal(this@GameDetailActivity, g.capaUrl)
                    finish()
                }
            }
            .show()
    }
}
