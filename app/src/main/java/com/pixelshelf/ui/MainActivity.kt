package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayoutMediator
import com.pixelshelf.R
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Biblioteca de jogos: pesquisa, filtro por plataforma e ordenação (na toolbar/chips)
 * e uma aba por status (Todos, Jogando, Zerado, Na fila), cada uma um GameListFragment.
 */
class MainActivity : AppCompatActivity() {

    private companion object { const val TAG = "PixelShelf-Ciclo" }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")

        // Sem usuário logado, volta para o login
        val usuarioId = SessaoUsuario.idLogado(this)
        if (usuarioId == SessaoUsuario.NENHUM) { irParaLogin(); return }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Abas por status: ViewPager2 + TabLayout, cada página é um Fragment
        binding.pager.adapter = StatusPagerAdapter(this)
        TabLayoutMediator(binding.tabs, binding.pager) { tab, posicao ->
            tab.text = StatusPagerAdapter.ABAS[posicao].titulo
        }.attach()

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, GameFormActivity::class.java))
        }

        configurarMenu()
        carregarUsuario(usuarioId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.plataformas.collect { if (it != null) montarChips(it) } }
                launch {
                    viewModel.biblioteca.collect { b ->
                        if (b == null) return@collect
                        val jogos = if (b.jogos.size == 1) "1 jogo" else "${b.jogos.size} jogos"
                        val platinas = b.jogos.count { it.platinado }
                        binding.toolbar.subtitle = when (platinas) {
                            0 -> jogos
                            1 -> "$jogos • 1 platina"
                            else -> "$jogos • $platinas platinas"
                        }
                        // Quantidade de jogos em cada aba, respeitando pesquisa e plataforma
                        StatusPagerAdapter.ABAS.forEachIndexed { i, aba ->
                            val qtd = b.jogos.count(aba::aceita)
                            binding.tabs.getTabAt(i)?.text = "${aba.titulo} ($qtd)"
                        }
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); Log.d(TAG, "MainActivity onStart") }
    override fun onResume() { super.onResume(); Log.d(TAG, "MainActivity onResume") }
    override fun onPause() { Log.d(TAG, "MainActivity onPause"); super.onPause() }
    override fun onStop() { Log.d(TAG, "MainActivity onStop"); super.onStop() }
    override fun onDestroy() { Log.d(TAG, "MainActivity onDestroy"); super.onDestroy() }

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
                R.id.acaoSair -> { confirmarSaida(); true }
                else -> false
            }
        }
    }

    private var nomeUsuario = ""

    /** Personaliza o título com o nome do usuário logado. */
    private fun carregarUsuario(usuarioId: Int) {
        lifecycleScope.launch {
            val usuario = AppDatabase.get(this@MainActivity).usuarioDao().buscarPorId(usuarioId)
            if (usuario == null) { // conta não existe mais: encerra a sessão
                SessaoUsuario.sair(this@MainActivity)
                irParaLogin()
                return@launch
            }
            nomeUsuario = usuario.nome
            binding.toolbar.title = "Biblioteca de ${usuario.nome.substringBefore(' ')}"
        }
    }

    private fun confirmarSaida() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sair da conta")
            .setMessage(if (nomeUsuario.isEmpty()) "Deseja sair?" else "Sair da conta de $nomeUsuario?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sair") { _, _ ->
                SessaoUsuario.sair(this)
                irParaLogin()
            }
            .show()
    }

    private fun irParaLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
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
