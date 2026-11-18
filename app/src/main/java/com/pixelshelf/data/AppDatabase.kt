package com.pixelshelf.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Game::class, Conquista::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun conquistaDao(): ConquistaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "pixel_shelf.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /**
         * Versão 1 -> 2: novos campos do jogo (capa, lançamento, desenvolvedora, gênero,
         * descrição), nota passa a aceitar nulo e é criada a tabela de conquistas.
         * O SQLite não altera a nulidade de uma coluna, então a tabela é recriada
         * mantendo os jogos já cadastrados.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE games_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        titulo TEXT NOT NULL,
                        plataforma TEXT NOT NULL,
                        status TEXT NOT NULL,
                        nota REAL,
                        horasJogadas REAL NOT NULL,
                        capaUrl TEXT,
                        dataLancamento TEXT,
                        desenvolvedora TEXT,
                        genero TEXT,
                        descricao TEXT
                    )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO games_new (id, titulo, plataforma, status, nota, horasJogadas)
                    SELECT id, titulo, plataforma, status, nota, horasJogadas FROM games
                    """
                )
                db.execSQL("DROP TABLE games")
                db.execSQL("ALTER TABLE games_new RENAME TO games")
                db.execSQL(
                    """
                    CREATE TABLE conquistas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        jogoId INTEGER NOT NULL,
                        nome TEXT NOT NULL,
                        descricao TEXT NOT NULL,
                        iconeUrl TEXT,
                        raridade REAL,
                        obtida INTEGER NOT NULL,
                        FOREIGN KEY(jogoId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX index_conquistas_jogoId ON conquistas(jogoId)")
            }
        }
    }
}
