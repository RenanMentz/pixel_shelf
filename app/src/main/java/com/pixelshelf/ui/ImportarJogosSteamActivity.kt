package com.pixelshelf.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pixelshelf.R
import com.pixelshelf.data.ResultadoImportacao
import com.pixelshelf.databinding.ActivityImportarJogosSteamBinding
import com.pixelshelf.databinding.ItemJogoContaBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Escolha dos jogos da conta Steam para importar para a biblioteca. */
class ImportarJogosSteamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportarJogosSteamBinding
    private val viewModel: ImportarJogosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportarJogosSteamBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // não deixa sair no meio da importação (ela continua no ViewModel, mas o usuário perderia o resultado)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.progresso.value != null) {
                    Toast.makeText(this@ImportarJogosSteamActivity, "Aguarde a importação terminar", Toast.LENGTH_SHORT).show()
                } else finish()
            }
        })

        val adapter = JogoContaAdapter(viewModel::alternar)
        binding.rvJogos.layoutManager = LinearLayoutManager(this)
        binding.rvJogos.adapter = adapter

        binding.etFiltro.doAfterTextChanged { viewModel.filtro.value = it?.toString().orEmpty() }
        binding.btnImportar.setOnClickListener { viewModel.importar() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.itens.collect(adapter::submitList) }
                launch {
                    combine(viewModel.estado, viewModel.itens) { e, itens -> e to itens }.collect { (e, itens) ->
                        binding.progresso.visibility =
                            if (e is ImportarJogosViewModel.Estado.Carregando) View.VISIBLE else View.INVISIBLE
                        val mensagem = when (e) {
                            is ImportarJogosViewModel.Estado.Carregando -> "Carregando seus jogos da Steam…"
                            is ImportarJogosViewModel.Estado.Erro -> e.mensagem
                            is ImportarJogosViewModel.Estado.Pronto ->
                                if (itens.isEmpty()) "Nenhum jogo da sua conta com esse nome." else null
                        }
                        binding.tvMensagem.text = mensagem
                        binding.tvMensagem.visibility = if (mensagem == null) View.GONE else View.VISIBLE
                        if (e is ImportarJogosViewModel.Estado.Pronto) {
                            val total = e.jogos.size
                            binding.toolbar.subtitle = "$total jogos na conta • ${e.naBiblioteca.size} já na biblioteca"
                        }
                    }
                }
                launch {
                    combine(viewModel.quantidadeSelecionada, viewModel.progresso) { q, p -> q to p }.collect { (qtd, p) ->
                        val importando = p != null
                        binding.btnImportar.isEnabled = qtd > 0 && !importando
                        binding.btnImportar.text = when {
                            importando -> "Importando…"
                            qtd == 0 -> "Selecione os jogos"
                            qtd == 1 -> "Importar 1 jogo"
                            else -> "Importar $qtd jogos"
                        }
                        binding.tvProgresso.visibility = if (importando) View.VISIBLE else View.GONE
                        binding.barraImportacao.visibility = if (importando) View.VISIBLE else View.GONE
                        binding.etFiltro.isEnabled = !importando
                        if (p != null) {
                            binding.tvProgresso.text = "Importando ${minOf(p.feitos + 1, p.total)} de ${p.total}: ${p.atual}"
                            binding.barraImportacao.max = p.total
                            binding.barraImportacao.setProgressCompat(p.feitos, true)
                        }
                    }
                }
                launch { viewModel.concluido.collect(::terminar) }
            }
        }
    }

    private fun terminar(r: ResultadoImportacao) {
        val partes = buildList {
            add(if (r.importados == 1) "1 jogo importado" else "${r.importados} jogos importados")
            if (r.jaNaBiblioteca > 0) add("${r.jaNaBiblioteca} já estavam na biblioteca")
            if (r.falharam > 0) add("${r.falharam} não puderam ser importados (sem conexão)")
        }
        Toast.makeText(this, partes.joinToString(", "), Toast.LENGTH_LONG).show()
        if (r.falharam == 0) finish() else viewModel.carregar()
    }

    private class JogoContaAdapter(private val onAlternar: (ImportarJogosViewModel.Item) -> Unit) :
        ListAdapter<ImportarJogosViewModel.Item, JogoContaAdapter.VH>(Diff) {

        class VH(val binding: ItemJogoContaBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemJogoContaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            with(holder.binding) {
                tvNome.text = item.jogo.nome
                val horas = when {
                    item.tempoOculto -> "Tempo de jogo oculto na Steam"
                    item.jogo.horas == 0f -> "Nunca jogado"
                    else -> "${Formatos.numero(item.jogo.horas)} h jogadas"
                }
                tvDetalhe.text = if (item.naBiblioteca) "$horas • já na biblioteca" else horas
                ivImagem.load(item.jogo.imagemUrl) {
                    crossfade(true)
                    error(R.drawable.ic_controle)
                }
                cbSelecionado.isChecked = item.selecionado || item.naBiblioteca
                cbSelecionado.isEnabled = !item.naBiblioteca
                root.alpha = if (item.naBiblioteca) 0.5f else 1f
                root.isEnabled = !item.naBiblioteca
                root.setOnClickListener { onAlternar(item) }
            }
        }

        private object Diff : DiffUtil.ItemCallback<ImportarJogosViewModel.Item>() {
            override fun areItemsTheSame(a: ImportarJogosViewModel.Item, b: ImportarJogosViewModel.Item) =
                a.jogo.appId == b.jogo.appId
            override fun areContentsTheSame(a: ImportarJogosViewModel.Item, b: ImportarJogosViewModel.Item) = a == b
        }
    }
}
