package com.pixelshelf.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Formatação de valores para exibição em pt-BR. */
object Formatos {
    private val ptBR = Locale("pt", "BR")

    fun nota(nota: Float?): String =
        if (nota == null) "Sem nota" else numero(nota)

    fun numero(valor: Float): String =
        NumberFormat.getNumberInstance(ptBR).apply {
            maximumFractionDigits = 1
            isGroupingUsed = false // "1500" e não "1.500", para o formulário conseguir ler de volta
        }.format(valor)

    /** "2017-02-24" -> "24/02/2017" */
    fun data(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", ptBR).parse(iso)!!
            SimpleDateFormat("dd/MM/yyyy", ptBR).format(d)
        }.getOrNull()
    }
}
