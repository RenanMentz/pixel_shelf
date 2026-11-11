package com.pixelshelf.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.Game
import com.pixelshelf.databinding.ActivityGameDetailBinding
import kotlinx.coroutines.launch

class GameDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_GAME_ID = "GAME_ID" }

    private lateinit var binding: ActivityGameDetailBinding
    private var game: Game? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.get(this).gameDao()
        val id = intent.getIntExtra(EXTRA_GAME_ID, -1)

        lifecycleScope.launch {
            val g = dao.buscarPorId(id)
            if (g == null) { finish(); return@launch }
            game = g
            binding.tvTitulo.text = g.titulo
            binding.tvPlataforma.text = "Plataforma: ${g.plataforma}"
            binding.tvStatus.text = "Status: ${g.status}"
            binding.tvNota.text = "Nota: ${g.nota}"
            binding.tvHoras.text = "Horas jogadas: ${g.horasJogadas}"
        }

        binding.btnExcluir.setOnClickListener {
            val g = game ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Excluir jogo")
                .setMessage("Remover \"${g.titulo}\" da estante?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir") { _, _ ->
                    lifecycleScope.launch { dao.excluir(g); finish() }
                }
                .show()
        }
    }
}
