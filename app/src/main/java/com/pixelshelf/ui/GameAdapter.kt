package com.pixelshelf.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

            // selo da nota só aparece quando o jogo tem nota
            tvNota.visibility = if (g.nota == null) View.GONE else View.VISIBLE
            tvNota.text = "★ ${Formatos.nota(g.nota)}"

            ivFavorito.visibility = if (g.favorito) View.VISIBLE else View.GONE

            // jogo platinado ganha selo e borda dourada no card
            tvPlatina.visibility = if (r.platinado) View.VISIBLE else View.GONE
            val card = root
            card.strokeColor = ContextCompat.getColor(card.context, R.color.dourado)
            card.strokeWidth = if (r.platinado) card.resources.getDimensionPixelSize(R.dimen.borda_platina) else 0

            if (r.totalConquistas > 0 && !r.platinado) {
                grupoConquistas.visibility = View.VISIBLE
                val pct = r.conquistasObtidas * 100 / r.totalConquistas
                progressoConquistas.setProgressCompat(pct, false)
                tvConquistas.text = "🏆 ${r.conquistasObtidas}/${r.totalConquistas}"
            } else {
                grupoConquistas.visibility = View.GONE
            }
            root.setOnClickListener { onClick(r) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<GameResumo>() {
        override fun areItemsTheSame(a: GameResumo, b: GameResumo) = a.game.id == b.game.id
        override fun areContentsTheSame(a: GameResumo, b: GameResumo) = a == b
    }
}
