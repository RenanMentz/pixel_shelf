package com.pixelshelf.ui

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.snackbar.Snackbar
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
    private var platinadoAntes: Boolean? = null // null = ainda não carregou as conquistas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.get(this)
        val id = intent.getIntExtra(EXTRA_GAME_ID, -1)

        // fundo do cabeçalho desfocado (RenderEffect só existe a partir do Android 12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.ivFundo.setRenderEffect(RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP))
        }

        // a toolbar começa transparente sobre a capa e vai ficando preta conforme a tela rola
        val preto = ContextCompat.getColor(this, R.color.preto)
        val limite = resources.displayMetrics.density * 180
        binding.rolagem.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, _ ->
            val alfa = (y / limite).coerceIn(0f, 1f)
            binding.toolbar.setBackgroundColor(ColorUtils.setAlphaComponent(preto, (alfa * 255).toInt()))
        })

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
                R.id.acaoFavorito -> { alternarFavorito(); true }
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
            ivFundo.load(g.capaUrl) { crossfade(true) }
            tvTitulo.text = g.titulo
            toolbar.menu.findItem(R.id.acaoFavorito)?.apply {
                setIcon(if (g.favorito) R.drawable.ic_favorito else R.drawable.ic_favorito_borda)
                title = if (g.favorito) "Remover dos favoritos" else "Favoritar"
            }
            tvPlataforma.text = g.plataforma
            tvStatus.text = g.status
            tvNota.text = if (g.nota == null) "—" else "★ ${Formatos.nota(g.nota)}"
            tvHoras.text = "${Formatos.numero(g.horasJogadas)} h"

            val info = listOfNotNull(
                Formatos.data(g.dataLancamento)?.let { "Lançamento: $it" },
                g.desenvolvedora?.let { "Desenvolvedora: $it" },
                g.genero?.let { "Gênero: $it" }
            )
            tvInfo.text = info.joinToString("\n")
            tvInfo.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE
            tvDescricao.text = g.descricao
            tvDescricao.visibility = if (g.descricao.isNullOrBlank()) View.GONE else View.VISIBLE
            secaoSobre.visibility =
                if (info.isEmpty() && g.descricao.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun mostrarProgresso(lista: List<Conquista>) {
        binding.secaoConquistas.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        if (lista.isEmpty()) return
        val obtidas = lista.count { it.obtida }
        val pct = obtidas * 100 / lista.size
        val platinado = obtidas == lista.size
        // o texto sempre ocupa uma linha, assim a lista não "pula" ao marcar/desmarcar
        binding.tvProgressoConquistas.text = if (platinado)
            "🏆 Platina conquistada! $obtidas de ${lista.size} (100%)"
        else "$obtidas de ${lista.size} desbloqueadas ($pct%)"
        binding.tvProgressoConquistas.setTextColor(
            ContextCompat.getColor(this, if (platinado) R.color.dourado else R.color.texto_secundario)
        )
        binding.progressoConquistas.setProgressCompat(pct, true)
        binding.tvPlatina.visibility = if (platinado) View.VISIBLE else View.GONE

        // comemora só quando a platina acontece agora (não ao abrir um jogo já platinado)
        if (platinadoAntes == false && platinado) {
            Snackbar.make(binding.root, "🏆 Parabéns! Você platinou ${game?.titulo ?: "o jogo"}!", Snackbar.LENGTH_LONG).show()
        }
        platinadoAntes = platinado
    }

    private fun alternarFavorito() {
        val g = game ?: return
        val agora = !g.favorito
        lifecycleScope.launch {
            AppDatabase.get(this@GameDetailActivity).gameDao().definirFavorito(g.id, agora)
            val msg = if (agora) "Adicionado aos favoritos" else "Removido dos favoritos"
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmarExclusao() {
        val g = game ?: return
        MaterialAlertDialogBuilder(this)
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
