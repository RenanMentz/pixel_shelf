package com.pixelshelf.data

import kotlin.math.roundToInt

/**
 * Estatísticas pessoais da biblioteca (objeto de domínio, calculado a partir dos jogos).
 *
 * Regras:
 * - Média das notas considera só jogos com nota (jogos "Na fila" não têm nota).
 * - Um jogo conta como concluído quando está "Zerado" ou platinado.
 * - Empates na plataforma com mais jogos são mostrados juntos.
 */
data class Estatisticas(
    val total: Int,
    val zerados: Int,
    val platinados: Int,
    val concluidos: Int,
    val mediaNotas: Float?,     // null quando nenhum jogo tem nota
    val jogosAvaliados: Int,
    val porPlataforma: List<Pair<String, Int>> // da plataforma com mais jogos para a com menos
) {
    /** Plataforma(s) com mais jogos; vazia se a biblioteca estiver vazia. */
    val plataformasLideres: List<String>
        get() {
            val maior = porPlataforma.firstOrNull()?.second ?: return emptyList()
            return porPlataforma.filter { it.second == maior }.map { it.first }
        }

    /** Percentual da biblioteca concluída (0 a 100). */
    val percentualConcluido: Int
        get() = if (total == 0) 0 else (concluidos * 100f / total).roundToInt()

    companion object {
        fun calcular(jogos: List<GameResumo>): Estatisticas {
            val notas = jogos.mapNotNull { it.game.nota }
            val porPlataforma = jogos
                .groupingBy { it.game.plataforma }
                .eachCount()
                .toList()
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })

            return Estatisticas(
                total = jogos.size,
                zerados = jogos.count { it.game.status == GameStatus.ZERADO },
                platinados = jogos.count { it.platinado },
                concluidos = jogos.count { it.game.status == GameStatus.ZERADO || it.platinado },
                mediaNotas = if (notas.isEmpty()) null else notas.average().toFloat(),
                jogosAvaliados = notas.size,
                porPlataforma = porPlataforma
            )
        }
    }
}
