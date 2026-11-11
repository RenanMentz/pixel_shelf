package com.pixelshelf.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.Game
import com.pixelshelf.data.GameStatus
import com.pixelshelf.databinding.ActivityGameFormBinding
import kotlinx.coroutines.launch

/** Primeira funcionalidade: cadastro de jogo (Create do CRUD). */
class GameFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameFormBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spStatus.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, GameStatus.OPCOES
        )
        binding.btnSalvar.setOnClickListener { salvar() }
    }

    private fun salvar() {
        val titulo = binding.etTitulo.text.toString().trim()
        val plataforma = binding.etPlataforma.text.toString().trim()
        val nota = binding.etNota.text.toString().replace(',', '.').toFloatOrNull()
        val horas = binding.etHoras.text.toString().replace(',', '.').toFloatOrNull()

        val notaOk = nota != null && nota in 0f..10f
        val horasOk = horas != null && horas >= 0f

        binding.tilTitulo.error = if (titulo.isEmpty()) "Informe o título" else null
        binding.tilPlataforma.error = if (plataforma.isEmpty()) "Informe a plataforma" else null
        binding.tilNota.error = if (!notaOk) "Nota entre 0 e 10" else null
        binding.tilHoras.error = if (!horasOk) "Informe horas válidas (≥ 0)" else null

        if (titulo.isEmpty() || plataforma.isEmpty() || !notaOk || !horasOk) return

        val game = Game(
            titulo = titulo,
            plataforma = plataforma,
            status = binding.spStatus.selectedItem as String,
            nota = nota!!,
            horasJogadas = horas!!
        )
        lifecycleScope.launch {
            AppDatabase.get(this@GameFormActivity).gameDao().inserir(game)
            Toast.makeText(this@GameFormActivity, "Jogo salvo!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
