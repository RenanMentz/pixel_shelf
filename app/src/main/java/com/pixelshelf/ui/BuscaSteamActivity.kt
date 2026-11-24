package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pixelshelf.R
import com.pixelshelf.data.steam.ResultadoSteam
import com.pixelshelf.data.steam.SteamRepository
import com.pixelshelf.databinding.ActivityBuscaSteamBinding
import com.pixelshelf.databinding.ItemResultadoSteamBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Busca de jogos na loja Steam (Retrofit). Ao tocar num resultado, devolve o appid para
 * o GameFormActivity, que carrega os detalhes e preenche o formulário.
 */
class BuscaSteamActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TERMO = "TERMO"      // entrada: texto inicial da busca
        const val EXTRA_APP_ID = "APP_ID"    // saída: jogo escolhido
        const val EXTRA_NOME = "NOME"
        private const val MINIMO = 2
    }

    private lateinit var binding: ActivityBuscaSteamBinding
    private val repo = SteamRepository()
    private var busca: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuscaSteamBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = ResultadoAdapter { r ->
            setResult(RESULT_OK, Intent().putExtra(EXTRA_APP_ID, r.appId).putExtra(EXTRA_NOME, r.nome))
            finish()
        }
        binding.rvResultados.layoutManager = LinearLayoutManager(this)
        binding.rvResultados.adapter = adapter

        // busca enquanto digita, esperando uma pausa para não chamar a API a cada letra
        binding.etBusca.doAfterTextChanged { buscar(adapter, esperar = true) }
        binding.etBusca.setOnEditorActionListener { _, acao, _ ->
            if (acao != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            esconderTeclado()
            buscar(adapter, esperar = false)
            true
        }

        if (savedInstanceState == null) {
            val termo = intent.getStringExtra(EXTRA_TERMO).orEmpty()
            if (termo.isNotBlank()) binding.etBusca.setText(termo) // dispara a busca
            else mostrarMensagem("Digite o nome de um jogo para buscar na Steam.")
            binding.etBusca.requestFocus()
        }
        // após girar a tela o Android restaura o texto e o doAfterTextChanged busca de novo
    }

    private fun buscar(adapter: ResultadoAdapter, esperar: Boolean) {
        busca?.cancel()
        val termo = binding.etBusca.text.toString().trim()
        if (termo.length < MINIMO) {
            binding.progresso.visibility = View.INVISIBLE
            adapter.submitList(emptyList())
            mostrarMensagem("Digite pelo menos $MINIMO letras.")
            return
        }
        busca = lifecycleScope.launch {
            if (esperar) delay(400)
            binding.progresso.visibility = View.VISIBLE
            try {
                val resultados = repo.buscar(termo)
                adapter.submitList(resultados)
                if (resultados.isEmpty()) mostrarMensagem("Nenhum jogo encontrado para \"$termo\".")
                else binding.tvMensagem.visibility = View.GONE
            } catch (e: IOException) {
                adapter.submitList(emptyList())
                mostrarMensagem("Sem conexão com a Steam.\nVerifique a internet e tente de novo.")
            } catch (e: retrofit2.HttpException) {
                adapter.submitList(emptyList())
                mostrarMensagem("A Steam não respondeu (erro ${e.code()}).\nTente de novo em instantes.")
            } finally {
                binding.progresso.visibility = View.INVISIBLE
            }
        }
    }

    private fun mostrarMensagem(texto: String) {
        binding.tvMensagem.text = texto
        binding.tvMensagem.visibility = View.VISIBLE
    }

    private fun esconderTeclado() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.etBusca.windowToken, 0)
    }

    private class ResultadoAdapter(private val onEscolher: (ResultadoSteam) -> Unit) :
        ListAdapter<ResultadoSteam, ResultadoAdapter.VH>(Diff) {

        class VH(val binding: ItemResultadoSteamBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemResultadoSteamBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = getItem(position)
            holder.binding.tvNome.text = r.nome
            holder.binding.ivImagem.load(r.imagemUrl) {
                crossfade(true)
                error(R.drawable.ic_controle)
            }
            holder.binding.root.setOnClickListener { onEscolher(r) }
        }

        private object Diff : DiffUtil.ItemCallback<ResultadoSteam>() {
            override fun areItemsTheSame(a: ResultadoSteam, b: ResultadoSteam) = a.appId == b.appId
            override fun areContentsTheSame(a: ResultadoSteam, b: ResultadoSteam) = a == b
        }
    }
}
