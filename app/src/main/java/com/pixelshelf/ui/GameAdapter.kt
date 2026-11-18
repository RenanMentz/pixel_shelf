package com.pixelshelf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pixelshelf.R
import com.pixelshelf.data.GameResumo
import com.pixelshelf.databinding.ItemGameBinding

class GameAdapter(private val onClick: (GameResumo) -> Unit) :
    ListAdapter<GameResumo, GameAdapter.VH>(Diff) {

    class VH(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = getItem(position)
        val g = r.game
        with(holder.binding) {
            ivCapa.load(g.capaUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_capa_placeholder)
                error(R.drawable.ic_capa_placeholder)
            }
            tvTitulo.text = g.titulo
            tvPlataformaStatus.text = "${g.plataforma} • ${g.status}"
            val nota = "★ ${Formatos.nota(g.nota)}"
            tvNotaConquistas.text = if (r.totalConquistas > 0)
                "$nota   🏆 ${r.conquistasObtidas}/${r.totalConquistas}" else nota
            root.setOnClickListener { onClick(r) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<GameResumo>() {
        override fun areItemsTheSame(a: GameResumo, b: GameResumo) = a.game.id == b.game.id
        override fun areContentsTheSame(a: GameResumo, b: GameResumo) = a == b
    }
}
