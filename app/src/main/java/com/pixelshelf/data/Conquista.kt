package com.pixelshelf.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conquistas",
    foreignKeys = [ForeignKey(
        entity = Game::class,
        parentColumns = ["id"],
        childColumns = ["jogoId"],
        onDelete = ForeignKey.CASCADE // excluir o jogo apaga as conquistas dele
    )],
    indices = [Index("jogoId")]
)
data class Conquista(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val jogoId: Int,
    val nome: String,
    val descricao: String,
    val iconeUrl: String?,
    val raridade: Float?,   // % de jogadores que desbloquearam (dado global da Steam)
    val obtida: Boolean
)
