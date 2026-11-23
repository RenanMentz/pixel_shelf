package com.pixelshelf.ui

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Capas escolhidas da galeria são copiadas para o armazenamento interno do app,
 * assim continuam funcionando mesmo se a foto original for apagada.
 */
object Capas {

    private fun pasta(context: Context) = File(context.filesDir, "capas").apply { mkdirs() }

    /** Copia a imagem escolhida e devolve a URI do arquivo local (file://...). */
    suspend fun copiar(context: Context, origem: Uri): String = withContext(Dispatchers.IO) {
        val destino = File(pasta(context), "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(origem)!!.use { entrada ->
            destino.outputStream().use { saida -> entrada.copyTo(saida) }
        }
        Uri.fromFile(destino).toString()
    }

    /** Apaga o arquivo se a capa for local (capas da internet são ignoradas). */
    fun apagarSeLocal(context: Context, capaUrl: String?) {
        val uri = capaUrl?.let(Uri::parse) ?: return
        if (uri.scheme != "file") return
        val arquivo = File(uri.path ?: return)
        if (arquivo.parentFile == pasta(context)) arquivo.delete()
    }
}
