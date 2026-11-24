package com.pixelshelf.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.Estatisticas
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.databinding.ActivityEstatisticasBinding
import com.pixelshelf.databinding.ItemPlataformaEstatisticaBinding
import kotlinx.coroutines.launch

/**
 * Estatísticas pessoais: total de jogos, zerados, platinados, média das notas,
 * plataforma com mais jogos e percentual da biblioteca concluída.
 * Observa o banco, então os números se atualizam sozinhos.
 */
class EstatisticasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEstatisticasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEstatisticasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val dao = AppDatabase.get(this).gameDao()
        val usuarioId = SessaoUsuario.idLogado(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.listarResumos(usuarioId).collect { mostrar(Estatisticas.calcular(it)) }
            }
        }
    }

    private fun mostrar(e: Estatisticas) {
        val vazio = e.total == 0
        binding.tvVazio.visibility = if (vazio) View.VISIBLE else View.GONE
        binding.conteudo.visibility = if (vazio) View.GONE else View.VISIBLE
        if (vazio) return

        binding.tvPercentual.text = "${e.percentualConcluido}%"
        binding.barraConcluida.setProgressCompat(e.percentualConcluido, false)
        binding.tvConcluidosLegenda.text =
            "${e.concluidos} de ${jogos(e.total)} zerados ou platinados"

        binding.tvTotal.text = e.total.toString()
        binding.tvZerados.text = e.zerados.toString()
        binding.tvZeradosLegenda.text = "${percentual(e.zerados, e.total)}% da biblioteca"
        binding.tvPlatinados.text = e.platinados.toString()

        binding.tvMedia.text = e.mediaNotas?.let { Formatos.numero(it) } ?: "—"
        binding.tvMediaLegenda.text = when (e.jogosAvaliados) {
            0 -> "nenhum jogo avaliado"
            1 -> "de 1 jogo avaliado"
            else -> "de ${e.jogosAvaliados} jogos avaliados"
        }

        val lideres = e.plataformasLideres
        val qtdLider = e.porPlataforma.first().second
        binding.tvPlataformaLider.text = lideres.joinToString(" e ")
        binding.tvPlataformaLiderLegenda.text =
            if (lideres.size > 1) "empatadas com ${jogos(qtdLider)} cada" else jogos(qtdLider)

        // uma barra por plataforma, proporcional à que tem mais jogos
        binding.listaPlataformas.removeAllViews()
        e.porPlataforma.forEach { (plataforma, qtd) ->
            val linha = ItemPlataformaEstatisticaBinding.inflate(
                layoutInflater, binding.listaPlataformas, true
            )
            linha.tvPlataforma.text = plataforma
            linha.tvQuantidade.text = qtd.toString()
            linha.barra.max = qtdLider
            linha.barra.setProgressCompat(qtd, false)
        }
    }

    private fun jogos(qtd: Int) = if (qtd == 1) "1 jogo" else "$qtd jogos"

    private fun percentual(parte: Int, total: Int) =
        if (total == 0) 0 else Math.round(parte * 100f / total)
}
