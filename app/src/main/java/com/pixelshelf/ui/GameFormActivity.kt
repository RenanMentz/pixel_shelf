package com.pixelshelf.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.withTransaction
import coil.load
import com.pixelshelf.R
import com.pixelshelf.data.AppDatabase
import com.pixelshelf.data.Conquista
import com.pixelshelf.data.Game
import com.pixelshelf.data.GameStatus
import com.pixelshelf.data.Plataformas
import com.pixelshelf.data.SessaoUsuario
import com.pixelshelf.data.steam.JogoSteam
import com.pixelshelf.data.steam.SituacaoConquistas
import com.pixelshelf.data.steam.SituacaoConta
import com.pixelshelf.databinding.ActivityGameFormBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Cadastro (Create) e edição (Update) de jogo.
 * Sem EXTRA_GAME_ID abre em modo "novo jogo"; com o id, carrega o jogo para editar.
 * No modo "novo jogo" dá para buscar o jogo na Steam (Retrofit) e preencher os campos.
 */
class GameFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_ID = "GAME_ID"
        private const val ESTADO_CAPA = "capa"
        private const val ESTADO_CAPA_NOVA = "capa_nova"
        private const val ESTADO_DATA = "data"
    }

    private lateinit var binding: ActivityGameFormBinding
    private val db by lazy { AppDatabase.get(this) }
    private val dao by lazy { db.gameDao() }
    private val viewModel: GameFormViewModel by viewModels()

    private var gameId = 0                   // 0 = novo jogo
    private var original: Game? = null       // jogo carregado no modo edição
    private var capaUrl: String? = null      // capa atualmente exibida no formulário
    private var capaNovaLocal: String? = null // arquivo copiado nesta tela e ainda não salvo
    private var dataIso: String? = null      // data de lançamento (yyyy-MM-dd)
    private var salvo = false

    private val formatoIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Volta da BuscaSteamActivity com o appid do jogo escolhido. */
    private val buscarNaSteam =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val appId = r.data?.getIntExtra(BuscaSteamActivity.EXTRA_APP_ID, 0) ?: 0
            if (r.resultCode == Activity.RESULT_OK && appId != 0) viewModel.importar(appId)
        }

    private val escolherCapa =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                runCatching { Capas.copiar(this@GameFormActivity, uri) }
                    .onSuccess { novo ->
                        descartarCapaNova()
                        capaNovaLocal = novo
                        capaUrl = novo
                        mostrarCapa()
                    }
                    .onFailure {
                        Toast.makeText(this@GameFormActivity, "Não foi possível abrir a imagem", Toast.LENGTH_SHORT).show()
                    }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameId = intent.getIntExtra(EXTRA_GAME_ID, 0)
        binding.toolbar.title = if (gameId == 0) "Novo jogo" else "Editar jogo"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.acPlataforma.setSimpleItems(Plataformas.OPCOES.toTypedArray())
        binding.acStatus.setSimpleItems(GameStatus.OPCOES.toTypedArray())
        binding.acStatus.doAfterTextChanged { aplicarRegraDaNota() }

        binding.btnEscolherCapa.setOnClickListener {
            escolherCapa.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnRemoverCapa.setOnClickListener {
            descartarCapaNova()
            capaUrl = null
            mostrarCapa()
        }
        binding.etLancamento.setOnClickListener { abrirCalendario() }
        binding.tilLancamento.setEndIconOnClickListener {
            dataIso = null
            binding.etLancamento.setText("")
        }
        binding.btnSalvar.setOnClickListener { salvar() }

        binding.secaoSteam.visibility = if (gameId == 0) View.VISIBLE else View.GONE
        binding.btnBuscarSteam.setOnClickListener {
            buscarNaSteam.launch(
                Intent(this, BuscaSteamActivity::class.java)
                    .putExtra(BuscaSteamActivity.EXTRA_TERMO, binding.etTitulo.text.toString().trim())
            )
        }
        viewModel.importado?.let { mostrarSituacaoSteam(it) } // após girar a tela
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.carregando.collect { carregando ->
                        binding.progressoSteam.visibility = if (carregando) View.VISIBLE else View.GONE
                        binding.btnBuscarSteam.isEnabled = !carregando
                        binding.btnSalvar.isEnabled = !carregando
                        if (carregando) {
                            binding.tvSteam.visibility = View.VISIBLE
                            binding.tvSteam.setTextColor(cor(R.color.texto_secundario))
                            binding.tvSteam.text = "Carregando dados da Steam…"
                        }
                    }
                }
                launch {
                    viewModel.eventos.collect { e ->
                        when (e) {
                            is GameFormViewModel.Evento.Importado -> preencher(e.jogo)
                            is GameFormViewModel.Evento.Erro -> {
                                binding.tvSteam.visibility = View.VISIBLE
                                binding.tvSteam.setTextColor(cor(R.color.erro))
                                binding.tvSteam.text = e.mensagem
                            }
                        }
                    }
                }
            }
        }

        if (savedInstanceState != null) {
            // Ciclo de vida: ao girar a tela os campos de texto são restaurados pelo Android;
            // a capa e a data ficam em variáveis, então são salvas manualmente.
            capaUrl = savedInstanceState.getString(ESTADO_CAPA)
            capaNovaLocal = savedInstanceState.getString(ESTADO_CAPA_NOVA)
            dataIso = savedInstanceState.getString(ESTADO_DATA)
            mostrarCapa()
            if (gameId != 0) lifecycleScope.launch { original = dao.buscarPorId(gameId) }
        } else if (gameId != 0) {
            lifecycleScope.launch { carregarParaEdicao() }
        } else {
            binding.acStatus.setText(GameStatus.NA_FILA, false)
            mostrarCapa()
        }
        aplicarRegraDaNota()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ESTADO_CAPA, capaUrl)
        outState.putString(ESTADO_CAPA_NOVA, capaNovaLocal)
        outState.putString(ESTADO_DATA, dataIso)
    }

    override fun onDestroy() {
        super.onDestroy()
        // saiu sem salvar: apaga a imagem que tinha sido copiada
        if (isFinishing && !salvo) descartarCapaNova()
    }

    private suspend fun carregarParaEdicao() {
        val g = dao.buscarPorId(gameId)
        if (g == null) { finish(); return }
        original = g
        with(binding) {
            etTitulo.setText(g.titulo)
            acPlataforma.setText(g.plataforma, false)
            acStatus.setText(g.status, false)
            etNota.setText(g.nota?.let { Formatos.numero(it) }.orEmpty())
            etHoras.setText(Formatos.numero(g.horasJogadas))
            etDesenvolvedora.setText(g.desenvolvedora.orEmpty())
            etGenero.setText(g.genero.orEmpty())
            etDescricao.setText(g.descricao.orEmpty())
        }
        // plataforma digitada na versão antiga do app que não está na lista
        if (g.plataforma !in Plataformas.OPCOES) {
            binding.acPlataforma.setSimpleItems((Plataformas.OPCOES + g.plataforma).toTypedArray())
        }
        dataIso = g.dataLancamento
        binding.etLancamento.setText(Formatos.data(g.dataLancamento).orEmpty())
        capaUrl = g.capaUrl
        mostrarCapa()
        aplicarRegraDaNota()
    }

    private fun cor(id: Int) = ContextCompat.getColor(this, id)

    /** Preenche o formulário com o jogo da Steam. Status, nota e horas ficam com o usuário. */
    private fun preencher(j: JogoSteam) {
        with(binding) {
            etTitulo.setText(j.titulo)
            tilTitulo.error = null
            acPlataforma.setText("PC", false) // a Steam é a loja de PC
            tilPlataforma.error = null
            etDesenvolvedora.setText(j.desenvolvedora.orEmpty())
            etGenero.setText(j.genero.orEmpty())
            etDescricao.setText(j.descricao.orEmpty())
        }
        dataIso = j.dataLancamento
        binding.etLancamento.setText(Formatos.data(j.dataLancamento).orEmpty())
        binding.tilLancamento.error = null
        if (j.capaUrl != null) {
            descartarCapaNova()
            capaUrl = j.capaUrl
            mostrarCapa()
        }
        mostrarSituacaoSteam(j)
    }

    private fun mostrarSituacaoSteam(j: JogoSteam) {
        val texto = when (j.situacaoConquistas) {
            SituacaoConquistas.IMPORTADAS ->
                "✓ ${j.titulo}: ${j.conquistas.size} conquistas serão adicionadas ao salvar." +
                    when (j.situacaoConta) {
                        SituacaoConta.SINCRONIZADA ->
                            "\n${j.conquistasObtidas} já desbloqueadas na sua conta Steam."
                        SituacaoConta.PERFIL_PRIVADO ->
                            "\nSeu perfil Steam está privado: o progresso não foi trazido."
                        else -> ""
                    }
            SituacaoConquistas.NENHUMA ->
                "✓ ${j.titulo}: esse jogo não tem conquistas na Steam."
            SituacaoConquistas.SEM_CHAVE ->
                "${j.titulo} tem ${j.totalConquistasSteam} conquistas na Steam, mas a chave da Steam " +
                    "não está configurada (veja o README). Você pode adicioná-las manualmente depois."
            SituacaoConquistas.CHAVE_INVALIDA ->
                "A Steam recusou a chave configurada: as conquistas não foram importadas."
            SituacaoConquistas.FALHA ->
                "Não foi possível carregar as conquistas agora. O resto dos dados foi preenchido."
        }
        val ok = j.situacaoConquistas == SituacaoConquistas.IMPORTADAS ||
            j.situacaoConquistas == SituacaoConquistas.NENHUMA
        binding.tvSteam.visibility = View.VISIBLE
        binding.tvSteam.text = texto
        binding.tvSteam.setTextColor(cor(if (ok) R.color.dourado else R.color.texto_secundario))
    }

    private fun mostrarCapa() {
        binding.ivCapa.load(capaUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_capa_placeholder)
            error(R.drawable.ic_capa_placeholder)
        }
        binding.btnRemoverCapa.isEnabled = capaUrl != null
    }

    private fun descartarCapaNova() {
        capaNovaLocal?.let { Capas.apagarSeLocal(this, it) }
        capaNovaLocal = null
    }

    /**
     * Regras de negócio da nota: jogo "Na fila" ainda não foi jogado, então não tem nota;
     * "Jogando" pode ficar sem nota até o usuário avaliar; "Zerado" precisa de nota.
     */
    private fun aplicarRegraDaNota() {
        val status = binding.acStatus.text.toString()
        val naFila = status == GameStatus.NA_FILA
        binding.tilNota.isEnabled = !naFila
        binding.tilNota.hint = when (status) {
            GameStatus.NA_FILA -> "Nota (após jogar)"
            GameStatus.ZERADO -> "Nota (0 a 10) *"
            else -> "Nota (opcional)"
        }
        if (naFila) {
            binding.etNota.setText("")
            binding.tilNota.error = null
        }
    }

    private fun abrirCalendario() {
        val cal = Calendar.getInstance()
        dataIso?.let { iso -> runCatching { cal.time = formatoIso.parse(iso)!! } }
        DatePickerDialog(this, { _, ano, mes, dia ->
            val escolhida = Calendar.getInstance().apply { set(ano, mes, dia) }
            dataIso = formatoIso.format(escolhida.time)
            binding.etLancamento.setText(Formatos.data(dataIso))
            binding.tilLancamento.error = null
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun salvar() {
        val titulo = binding.etTitulo.text.toString().trim()
        val plataforma = binding.acPlataforma.text.toString()
        val status = binding.acStatus.text.toString()
        val naFila = status == GameStatus.NA_FILA
        val nota = binding.etNota.text.toString().replace(',', '.').toFloatOrNull()
        val horas = binding.etHoras.text.toString().replace(',', '.').toFloatOrNull()
        val hoje = formatoIso.format(Calendar.getInstance().time)

        // --- validações e regras de negócio ---
        val erroTitulo = if (titulo.isEmpty()) "Informe o título" else null
        val erroPlataforma = if (plataforma.isEmpty()) "Escolha a plataforma" else null
        val erroStatus = if (status !in GameStatus.OPCOES) "Escolha o status" else null
        val textoNota = binding.etNota.text.toString().trim()
        val erroNota = when {
            naFila -> null
            textoNota.isNotEmpty() && nota == null -> "Nota inválida"
            nota == null && status == GameStatus.ZERADO -> "Jogo zerado precisa de nota"
            nota != null && nota !in 0f..10f -> "Nota entre 0 e 10"
            else -> null
        }
        val erroHoras = when {
            horas == null || horas < 0f -> "Informe horas válidas (≥ 0)"
            status == GameStatus.ZERADO && horas == 0f -> "Jogo zerado precisa ter horas jogadas"
            else -> null
        }
        // datas ISO podem ser comparadas como texto
        val erroData = if (!naFila && dataIso != null && dataIso!! > hoje)
            "Um jogo que ainda não lançou só pode estar \"Na fila\"" else null

        binding.tilTitulo.error = erroTitulo
        binding.tilPlataforma.error = erroPlataforma
        binding.tilStatus.error = erroStatus
        binding.tilNota.error = erroNota
        binding.tilHoras.error = erroHoras
        binding.tilLancamento.error = erroData
        if (listOf(erroTitulo, erroPlataforma, erroStatus, erroNota, erroHoras, erroData).any { it != null }) return

        val usuarioId = SessaoUsuario.idLogado(this)
        val dados = Game(
            id = gameId,
            usuarioId = usuarioId,
            titulo = titulo,
            plataforma = plataforma,
            status = status,
            nota = if (naFila) null else nota,
            horasJogadas = horas!!,
            capaUrl = capaUrl,
            dataLancamento = dataIso,
            desenvolvedora = binding.etDesenvolvedora.text.toString().trim().ifEmpty { null },
            genero = binding.etGenero.text.toString().trim().ifEmpty { null },
            descricao = binding.etDescricao.text.toString().trim().ifEmpty { null },
            favorito = original?.favorito ?: false, // a edição não mexe no favorito
            // id da Steam: vem da importação no cadastro; na edição é mantido
            steamAppId = if (gameId == 0) viewModel.importado?.appId?.takeIf { plataforma == "PC" }
            else original?.steamAppId
        )

        binding.btnSalvar.isEnabled = false
        lifecycleScope.launch {
            // Regra de negócio: não pode haver o mesmo jogo duas vezes na mesma plataforma
            if (dao.contarDuplicados(usuarioId, titulo, plataforma, gameId) > 0) {
                binding.tilTitulo.error = "Esse jogo já está na biblioteca para $plataforma"
                binding.btnSalvar.isEnabled = true
                return@launch
            }
            if (gameId == 0) {
                val conquistas = viewModel.importado?.conquistas.orEmpty()
                // jogo e conquistas gravados juntos: ou salva tudo, ou nada
                db.withTransaction {
                    val novoId = dao.inserir(dados).toInt()
                    if (conquistas.isNotEmpty()) {
                        db.conquistaDao().inserirTodas(conquistas.map {
                            Conquista(
                                jogoId = novoId, nome = it.nome, descricao = it.descricao,
                                iconeUrl = it.iconeUrl, raridade = it.raridade, obtida = it.obtida
                            )
                        })
                    }
                }
            } else {
                dao.atualizar(dados)
            }

            // capa antiga substituída/removida: apaga o arquivo local antigo
            val capaAntiga = original?.capaUrl
            if (capaAntiga != null && capaAntiga != capaUrl) Capas.apagarSeLocal(this@GameFormActivity, capaAntiga)

            salvo = true
            val msg = if (gameId == 0) "Jogo adicionado!" else "Alterações salvas!"
            Toast.makeText(this@GameFormActivity, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
