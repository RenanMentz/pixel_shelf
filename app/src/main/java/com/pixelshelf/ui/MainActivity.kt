package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.pixelshelf.R
import com.pixelshelf.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** Biblioteca de jogos: listagem com capa, pesquisa, filtro por plataforma e ordenação. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = GameAdapter { resumo ->
            startActivity(
                Intent(this, GameDetailActivity::class.java)
                    .putExtra(GameDetailActivity.EXTRA_GAME_ID, resumo.game.id)
            )
        }
        val colunas = if (resources.configuration.screenWidthDp >= 600) 4 else 2
        binding.rvGames.layoutManager = GridLayoutManager(this, colunas)
        binding.rvGames.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, GameFormActivity::class.java))
        }

        configurarMenu()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.plataformas.collect { if (it != null) montarChips(it) } }
                launch {
                    viewModel.biblioteca.collect { b ->
                        if (b == null) return@collect
                        adapter.submitList(b.jogos)
                        binding.toolbar.subtitle = when (b.jogos.size) {
                            1 -> "1 jogo"
                            else -> "${b.jogos.size} jogos"
                        }
                        binding.tvVazio.visibility = if (b.jogos.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvVazio.text = if (b.temJogos)
                            "Nenhum jogo encontrado com esses filtros."
                        else
                            "Nenhum jogo cadastrado ainda.\nToque no + para adicionar."
                    }
                }
            }
        }
    }

    private fun configurarMenu() {
        val toolbar = binding.toolbar
        toolbar.inflateMenu(R.menu.menu_main)

        // Pesquisa por nome
        val itemBusca = toolbar.menu.findItem(R.id.acaoBuscar)
        val searchView = itemBusca.actionView as SearchView
        searchView.queryHint = "Pesquisar por nome"
        val buscaAtual = viewModel.busca.value
        if (buscaAtual.isNotEmpty()) { // restaura a pesquisa após rotação da tela
            itemBusca.expandActionView()
            searchView.setQuery(buscaAtual, false)
            searchView.clearFocus()
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus(); return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.busca.value = newText.orEmpty(); return true
            }
        })
        itemBusca.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.busca.value = ""; return true
            }
        })

        // Ordenação
        val idOrdem = if (viewModel.ordenacao.value == Ordenacao.NOTA) R.id.ordemNota else R.id.ordemNome
        toolbar.menu.findItem(idOrdem).isChecked = true
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.ordemNome -> { viewModel.ordenacao.value = Ordenacao.NOME; item.isChecked = true; true }
                R.id.ordemNota -> { viewModel.ordenacao.value = Ordenacao.NOTA; item.isChecked = true; true }
                else -> false
            }
        }
    }

    /** Recria os chips "Todas" + uma por plataforma que existe na biblioteca. */
    private fun montarChips(plataformas: List<String>) {
        val grupo = binding.chipsPlataforma
        // se a plataforma filtrada deixou de existir (ex.: último jogo excluído), volta para "Todas"
        if (viewModel.plataforma.value !in plataformas) viewModel.plataforma.value = null
        val selecionada = viewModel.plataforma.value

        grupo.setOnCheckedStateChangeListener(null)
        grupo.removeAllViews()
        (listOf<String?>(null) + plataformas).forEach { plat ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = plat ?: "Todas"
                isCheckable = true
                isChecked = plat == selecionada
                tag = plat
            }
            grupo.addView(chip)
        }
        grupo.setOnCheckedStateChangeListener { g, ids ->
            val chip = ids.firstOrNull()?.let { g.findViewById<Chip>(it) }
            viewModel.plataforma.value = chip?.tag as String?
        }
    }
}
