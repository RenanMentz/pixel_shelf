package com.pixelshelf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pixelshelf.R
import com.pixelshelf.data.Conquista
import com.pixelshelf.databinding.ItemConquistaBinding

class ConquistaAdapter(private val onAlternar: (Conquista) -> Unit) :
    ListAdapter<Conquista, ConquistaAdapter.VH>(Diff) {

    class VH(val binding: ItemConquistaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemConquistaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        with(holder.binding) {
            ivIcone.load(c.iconeUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_trofeu)
                error(R.drawable.ic_trofeu)
            }
            ivIcone.alpha = if (c.obtida) 1f else 0.35f // bloqueada fica "apagada"
            tvNome.text = c.nome
            tvDescricao.text = c.descricao
            tvRaridade.text = c.raridade?.let {
                val rara = if (it < 10f) " • Rara" else ""
                "${Formatos.numero(it)}% dos jogadores$rara"
            }.orEmpty()

            cbObtida.setOnCheckedChangeListener(null)
            cbObtida.isChecked = c.obtida
            cbObtida.setOnCheckedChangeListener { _, _ -> onAlternar(c) }
            root.setOnClickListener { cbObtida.toggle() }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Conquista>() {
        override fun areItemsTheSame(a: Conquista, b: Conquista) = a.id == b.id
        override fun areContentsTheSame(a: Conquista, b: Conquista) = a == b
    }
}
