package com.pixelshelf.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Conta de usuário. A senha nunca é guardada: só o hash (PBKDF2) e o salt. */
@Entity(tableName = "usuarios", indices = [Index(value = ["email"], unique = true)])
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val email: String,      // sempre em minúsculas
    val senhaHash: String,
    val salt: String
)
