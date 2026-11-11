package com.pixelshelf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pixelshelf.data.Game
import com.pixelshelf.databinding.ItemGameBinding

class GameAdapter(private val onClick: (Game) -> Unit) :
    ListAdapter<Game, GameAdapter.VH>(Diff) {

    class VH(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g = getItem(position)
        holder.binding.tvTitulo.text = g.titulo
        holder.binding.tvPlataformaStatus.text = "${g.plataforma} • ${g.status}"
        holder.binding.tvNotaHoras.text = "Nota ${g.nota} • ${g.horasJogadas} h"
        holder.binding.root.setOnClickListener { onClick(g) }
    }

    private object Diff : DiffUtil.ItemCallback<Game>() {
        override fun areItemsTheSame(a: Game, b: Game) = a.id == b.id
        override fun areContentsTheSame(a: Game, b: Game) = a == b
    }
}
