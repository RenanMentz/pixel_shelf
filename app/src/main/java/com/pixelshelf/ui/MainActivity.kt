package com.pixelshelf.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = GameAdapter { game ->
            startActivity(
                Intent(this, GameDetailActivity::class.java)
                    .putExtra(GameDetailActivity.EXTRA_GAME_ID, game.id)
            )
        }
        binding.rvGames.layoutManager = LinearLayoutManager(this)
        binding.rvGames.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, GameFormActivity::class.java))
        }

        val dao = AppDatabase.get(this).gameDao()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.listar().collect { jogos ->
                    adapter.submitList(jogos)
                    binding.tvVazio.visibility = if (jogos.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
