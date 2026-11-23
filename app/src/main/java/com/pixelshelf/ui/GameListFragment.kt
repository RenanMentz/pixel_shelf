package com.pixelshelf.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.pixelshelf.databinding.FragmentGameListBinding
import kotlinx.coroutines.launch

/**
 * Lista de jogos de um status (Jogando, Zerado, Na fila) ou de todos (status = null).
 *
 * Os filtros da biblioteca (pesquisa, plataforma e ordenação) ficam no MainViewModel,
 * compartilhado com a MainActivity: cada aba só aplica, por cima, o filtro do seu status.
 */
class GameListFragment : Fragment() {

    companion object {
        private const val TAG = "PixelShelf-Ciclo"
        private const val ARG_STATUS = "status"

        fun newInstance(status: String?) = GameListFragment().apply {
            arguments = bundleOf(ARG_STATUS to status)
        }
    }

    private val viewModel: MainViewModel by activityViewModels()

    // O binding só vale entre onCreateView e onDestroyView; por isso é anulado ao destruir a view.
    private var _binding: FragmentGameListBinding? = null
    private val binding get() = _binding!!

    private val status: String? get() = arguments?.getString(ARG_STATUS)

    private fun log(evento: String) = Log.d(TAG, "GameListFragment[${status ?: "Todos"}] $evento")

    override fun onAttach(context: Context) {
        super.onAttach(context)
        log("onAttach")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        log("onCreateView")
        _binding = FragmentGameListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        log("onViewCreated")

        val adapter = GameAdapter { resumo ->
            startActivity(
                Intent(requireContext(), GameDetailActivity::class.java)
                    .putExtra(GameDetailActivity.EXTRA_GAME_ID, resumo.game.id)
            )
        }
        val colunas = if (resources.configuration.screenWidthDp >= 600) 4 else 2
        binding.rvGames.layoutManager = GridLayoutManager(requireContext(), colunas)
        binding.rvGames.adapter = adapter

        // Coleta atrelada ao ciclo de vida da VIEW do fragment: só observa entre STARTED e STOPPED.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.biblioteca.collect { b ->
                    if (b == null) return@collect
                    val lista = status?.let { s -> b.jogos.filter { it.game.status == s } } ?: b.jogos
                    adapter.submitList(lista)

                    binding.tvVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvVazio.text = when {
                        !b.temJogos -> "Nenhum jogo cadastrado ainda.\nToque no + para adicionar."
                        status == null -> "Nenhum jogo encontrado com esses filtros."
                        else -> "Nenhum jogo \"$status\" com os filtros atuais."
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); log("onStart") }
    override fun onResume() { super.onResume(); log("onResume") }
    override fun onPause() { log("onPause"); super.onPause() }
    override fun onStop() { log("onStop"); super.onStop() }

    override fun onDestroyView() {
        log("onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() { log("onDestroy"); super.onDestroy() }
}
