package com.motoristapro.data.repository

import com.motoristapro.data.local.AppDatabase
import com.motoristapro.data.local.dao.FaixaHoraria
import com.motoristapro.data.local.dao.ResumoOfertas
import com.motoristapro.data.local.dao.ResumoPeriodo
import com.motoristapro.data.local.dao.TempoConectado
import com.motoristapro.data.local.dao.TotaisJornada
import com.motoristapro.data.local.dao.TotalPorCategoria
import com.motoristapro.data.local.entity.PerfilCusto
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.OfertaRecebida
import com.motoristapro.data.local.entity.Plataforma
import com.motoristapro.data.local.model.CorridaComPlataforma
import com.motoristapro.data.local.model.ConsumoCombustivel
import com.motoristapro.data.local.model.CustoKmPorCategoria
import com.motoristapro.data.local.model.CustoPorKm
import com.motoristapro.data.local.model.GanhoPorHora
import com.motoristapro.data.local.model.LucroDiario
import com.motoristapro.data.local.model.ResumoPlataforma
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Converte datas do calendário em intervalos [inicio, fim) de epoch millis no fuso do aparelho. */
object Periodo {
    private val zona: ZoneId get() = ZoneId.systemDefault()

    fun dia(data: LocalDate = LocalDate.now()): Pair<Long, Long> =
        data.atStartOfDay(zona).toInstant().toEpochMilli() to
            data.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()

    /** Semana de segunda a domingo que contém [data]. */
    fun semana(data: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val segunda = data.minusDays((data.dayOfWeek.value - 1).toLong())
        return segunda.atStartOfDay(zona).toInstant().toEpochMilli() to
            segunda.plusDays(7).atStartOfDay(zona).toInstant().toEpochMilli()
    }

    /** Últimos [dias] dias, incluindo hoje. */
    fun ultimosDias(dias: Int, hoje: LocalDate = LocalDate.now()): Pair<Long, Long> =
        hoje.minusDays((dias - 1).toLong()).atStartOfDay(zona).toInstant().toEpochMilli() to
            hoje.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()

    fun mes(mes: YearMonth = YearMonth.now()): Pair<Long, Long> =
        mes.atDay(1).atStartOfDay(zona).toInstant().toEpochMilli() to
            mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant().toEpochMilli()

    /**
     * Emite a data de hoje e reemite à meia-noite.
     * Tudo que é "de hoje" deriva deste Flow, então vira o dia sozinho com o app aberto.
     */
    fun hoje(): Flow<LocalDate> = flow {
        while (true) {
            val hoje = LocalDate.now()
            emit(hoje)
            val ateMeiaNoite = Duration.between(LocalDateTime.now(), hoje.plusDays(1).atStartOfDay()).toMillis()
            delay(ateMeiaNoite.coerceAtLeast(1_000))
        }
    }.distinctUntilChanged()
}

/**
 * Ponte entre os DAOs e a UI/serviços.
 *
 * - Leituras retornam Flow: a tela se atualiza sozinha quando o banco muda.
 * - Escritas são suspend: o Room já executa em thread própria, não precisa de withContext.
 * - Nenhuma soma é feita em Kotlin: todos os totais vêm prontos do SQLite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinanceiroRepository(private val db: AppDatabase) {

    private val corridaDao = db.corridaDao()
    private val despesaDao = db.despesaDao()
    private val configuracaoDao = db.configuracaoDao()
    private val relatorioDao = db.relatorioDao()
    private val jornadaDao = db.jornadaDao()
    private val plataformaDao = db.plataformaDao()
    private val ofertaDao = db.ofertaDao()
    private val perfilCustoDao = db.perfilCustoDao()
    private val custoFixoDao = db.custoFixoDao()

    // ================================================================== corridas

    suspend fun registrarCorrida(corrida: Corrida): Long {
        require(corrida.valorCentavos >= 0) { "Valor da corrida não pode ser negativo" }
        require(corrida.distanciaMetros >= 0 && corrida.deslocamentoMetros >= 0) { "Distância inválida" }
        return corridaDao.inserir(corrida)
    }

    suspend fun atualizarCorrida(corrida: Corrida) = corridaDao.atualizar(corrida)

    suspend fun excluirCorrida(id: Long) = corridaDao.excluirPorId(id)

    fun corridasDoPeriodo(inicio: Long, fim: Long): Flow<List<CorridaComPlataforma>> =
        corridaDao.observarPorPeriodo(inicio, fim)

    fun corridasDeHoje(): Flow<List<CorridaComPlataforma>> =
        Periodo.hoje().flatMapLatest { dia ->
            val (ini, fim) = Periodo.dia(dia)
            corridaDao.observarPorPeriodo(ini, fim)
        }

    fun plataformasAtivas(): Flow<List<Plataforma>> = plataformaDao.observarAtivas()

    /**
     * Registra a corrida a partir da janela da oferta (botão "Registrar").
     * A plataforma vem do pacote do app lido (Uber / 99); km e tempo já são totais.
     */
    suspend fun registrarCorridaDaOferta(valorReais: Double, km: Double, minutos: Int, pacote: String?): Long {
        val nome = nomePlataforma(pacote)
        val plataforma = plataformaDao.buscarPorNome(nome)
            ?: plataformaDao.listarTodas().firstOrNull()
            ?: error("Nenhuma plataforma cadastrada")
        return registrarCorrida(
            Corrida(
                plataformaId = plataforma.id,
                origem = "Leitor de ofertas",
                destino = "",
                valorCentavos = Math.round(valorReais * 100),
                distanciaMetros = Math.round(km * 1000),
                duracaoSegundos = minutos * 60L,
                inicioEm = System.currentTimeMillis(),
                observacao = "Registrada pela janela da oferta"
            )
        )
    }

    // ================================================================== despesas

    suspend fun registrarDespesa(despesa: Despesa): Long {
        require(despesa.valorCentavos > 0) { "Valor da despesa deve ser maior que zero" }
        return despesaDao.inserir(despesa)
    }

    /** Atalho do botão "Abastecer". */
    suspend fun registrarAbastecimento(
        valorCentavos: Long,
        litrosMl: Long? = null,
        odometroKm: Long? = null,
        dataEm: Long = System.currentTimeMillis()
    ): Long = registrarDespesa(
        Despesa(
            categoria = CategoriaDespesa.COMBUSTIVEL,
            valorCentavos = valorCentavos,
            dataEm = dataEm,
            descricao = "Abastecimento",
            litrosMl = litrosMl,
            odometroKm = odometroKm
        )
    )

    suspend fun atualizarDespesa(despesa: Despesa) = despesaDao.atualizar(despesa)

    suspend fun excluirDespesa(id: Long) = despesaDao.excluirPorId(id)

    fun despesasDoPeriodo(inicio: Long, fim: Long): Flow<List<Despesa>> =
        despesaDao.observarPorPeriodo(inicio, fim)

    fun despesasPorCategoria(inicio: Long, fim: Long): Flow<List<TotalPorCategoria>> =
        despesaDao.observarTotalPorCategoria(inicio, fim)

    suspend fun ultimoOdometro(): Long? = despesaDao.ultimoAbastecimentoComOdometro()?.odometroKm

    // ================================================================== totais de HOJE

    /** Faturamento de hoje (centavos). */
    fun faturamentoHoje(): Flow<Long> = porDia { ini, fim -> corridaDao.observarFaturamento(ini, fim) }

    /** Despesas de hoje (centavos). */
    fun despesasHoje(): Flow<Long> = porDia { ini, fim -> despesaDao.observarTotal(ini, fim) }

    /** Lucro líquido de hoje = faturamento − despesas (centavos). */
    fun lucroLiquidoHoje(): Flow<Long> = porDia { ini, fim -> corridaDao.observarLucroLiquido(ini, fim) }

    /** Faturamento, despesas, lucro, corridas, km e tempo de hoje numa única query. */
    fun resumoHoje(): Flow<ResumoPeriodo> = porDia { ini, fim -> corridaDao.observarResumo(ini, fim) }

    // ================================================================== totais por período

    fun resumoDoPeriodo(inicio: Long, fim: Long): Flow<ResumoPeriodo> =
        corridaDao.observarResumo(inicio, fim)

    fun resumoDoMes(mes: YearMonth = YearMonth.now()): Flow<ResumoPeriodo> {
        val (ini, fim) = Periodo.mes(mes)
        return corridaDao.observarResumo(ini, fim)
    }

    /** Uma linha por dia (inclui lucro estimado pelo custo/km). */
    fun lucroDiario(inicio: Long, fim: Long): Flow<List<LucroDiario>> =
        relatorioDao.observarLucroDiario(inicio, fim)

    /** Custo real por km no mês: despesas ÷ km rodado. */
    fun custoPorKmDoMes(mes: YearMonth = YearMonth.now()): Flow<CustoPorKm> {
        val (ini, fim) = Periodo.mes(mes)
        return relatorioDao.observarCustoPorKm(ini, fim)
    }

    fun resumoDaSemana(data: LocalDate = LocalDate.now()): Flow<ResumoPeriodo> {
        val (ini, fim) = Periodo.semana(data)
        return corridaDao.observarResumo(ini, fim)
    }

    fun resumoPorPlataforma(inicio: Long, fim: Long): Flow<List<ResumoPlataforma>> =
        relatorioDao.observarResumoPorPlataforma(inicio, fim)

    fun ganhoPorHoraDoDia(inicio: Long, fim: Long): Flow<List<GanhoPorHora>> =
        relatorioDao.observarGanhoPorHoraDoDia(inicio, fim)

    fun custoKmPorCategoria(inicio: Long, fim: Long): Flow<List<CustoKmPorCategoria>> =
        relatorioDao.observarCustoKmPorCategoria(inicio, fim)

    fun custoPorKm(inicio: Long, fim: Long): Flow<CustoPorKm> =
        relatorioDao.observarCustoPorKm(inicio, fim)

    suspend fun consumoCombustivel(inicio: Long, fim: Long): ConsumoCombustivel =
        relatorioDao.consumoCombustivel(inicio, fim)

    // ================================================================== jornada (turno)

    fun jornadaAtiva(): Flow<Jornada?> = jornadaDao.observarAtiva()

    suspend fun obterJornadaAtiva(): Jornada? = jornadaDao.ativa()

    /** Abre um turno (ou devolve o que já está aberto). */
    suspend fun iniciarJornada(): Jornada {
        jornadaDao.ativa()?.let { return it }
        val nova = Jornada(inicioEm = System.currentTimeMillis())
        return nova.copy(id = jornadaDao.inserir(nova))
    }

    suspend fun encerrarJornada(id: Long, fimEm: Long = System.currentTimeMillis()) =
        jornadaDao.encerrar(id, fimEm)

    suspend fun atualizarMetrosJornada(id: Long, metros: Long) = jornadaDao.atualizarMetros(id, metros)

    suspend fun pausarJornada(id: Long, em: Long) = jornadaDao.pausar(id, em)

    fun jornadasDoPeriodo(inicio: Long, fim: Long): Flow<List<Jornada>> =
        jornadaDao.observarPorPeriodo(inicio, fim)


    /** Tempo conectado no período, já contando a jornada aberta (ver JornadaDao). */
    fun tempoConectado(inicio: Long, fim: Long, agora: Long = System.currentTimeMillis()): Flow<TempoConectado> =
        jornadaDao.observarTempoConectado(inicio, fim, agora)

    fun totaisJornadasEncerradas(inicio: Long, fim: Long): Flow<TotaisJornada> =
        jornadaDao.observarTotaisEncerradas(inicio, fim)

    // ================================================================== histórico de ofertas

    suspend fun salvarOferta(oferta: OfertaRecebida): Long = ofertaDao.inserir(oferta)

    suspend fun marcarOfertaRegistrada(id: Long) = ofertaDao.marcarRegistrada(id)

    suspend fun excluirOferta(id: Long) = ofertaDao.excluirPorId(id)

    /** Apaga as leituras com R$/km impossível no período. Devolve quantas saíram. */
    suspend fun limparOfertasSuspeitas(inicio: Long, fim: Long): Int =
        ofertaDao.apagarSuspeitas(inicio, fim, OfertaRecebida.TETO_REAIS_KM_CENTAVOS)

    fun ofertasDoPeriodo(inicio: Long, fim: Long): Flow<List<OfertaRecebida>> =
        ofertaDao.observarPorPeriodo(inicio, fim)

    fun resumoOfertas(inicio: Long, fim: Long): Flow<ResumoOfertas> = ofertaDao.observarResumo(inicio, fim)

    // ---------------------------------------------------------- perfil de custo

    /** Respostas do assistente de custo. null = o motorista ainda não preencheu. */
    fun perfilCusto(): Flow<PerfilCusto?> = perfilCustoDao.observar()

    suspend fun obterPerfilCusto(): PerfilCusto? = perfilCustoDao.obter()

    suspend fun salvarPerfilCusto(perfil: PerfilCusto) = perfilCustoDao.salvar(perfil)

    /** Faixas de 2 h do dia com o R$/km médio das ofertas — os "melhores horários". */
    fun faixasHorarias(inicio: Long, fim: Long, minimoOfertas: Int = 3): Flow<List<FaixaHoraria>> =
        ofertaDao.observarFaixasHorarias(inicio, fim, minimoOfertas)

    /** Faixas de 2 h de um dia da semana só (-1 = todos). Tela "Melhores horários". */
    fun faixasHorariasDoDia(
        inicio: Long,
        fim: Long,
        diaSemana: Int,
        minimoOfertas: Int = 1
    ): Flow<List<FaixaHoraria>> =
        ofertaDao.observarFaixasPorDiaDaSemana(inicio, fim, diaSemana, minimoOfertas)

    /** Mantém só os últimos [dias] dias de ofertas. */
    suspend fun limparOfertasAntigas(dias: Int = 180): Int =
        ofertaDao.apagarAntesDe(System.currentTimeMillis() - dias * 24L * 60 * 60 * 1000)

    // ================================================================== custos fixos

    fun custosFixos(): Flow<List<CustoFixo>> = custoFixoDao.observarTodos()

    /** Soma mensal dos custos fixos ativos (centavos). */
    fun custoFixoMensal(): Flow<Long> = custoFixoDao.observarTotalMensal()

    suspend fun salvarCustoFixo(c: CustoFixo) {
        require(c.nome.isNotBlank()) { "Informe o nome" }
        require(c.valorMensalCentavos > 0) { "Valor deve ser maior que zero" }
        if (c.id == 0L) custoFixoDao.inserir(c) else custoFixoDao.atualizar(c)
    }

    suspend fun excluirCustoFixo(id: Long) = custoFixoDao.excluir(id)

    /**
     * Reescreve os custos fixos que vieram do assistente de custo.
     *
     * Apaga e recria em vez de atualizar: assim, um custo que o motorista zerou
     * no assistente (trocou o seguro por um mais barato, quitou o financiamento)
     * some da lista, em vez de ficar cobrando pelo resto da vida. O que ele
     * digitou à mão (origem MANUAL) não é tocado.
     */
    suspend fun substituirCustosFixosDoAssistente(itens: List<Pair<String, Long>>) {
        custoFixoDao.apagarPorOrigem(CustoFixo.ASSISTENTE)
        itens.filter { it.second > 0 }.forEach { (nome, valor) ->
            custoFixoDao.inserir(
                CustoFixo(nome = nome, valorMensalCentavos = valor, origem = CustoFixo.ASSISTENTE)
            )
        }
    }

    // ================================================================== configuração

    /** Nunca nulo: se a linha ainda não existir, devolve os valores padrão. */
    fun configuracao(): Flow<Configuracao> =
        configuracaoDao.observar().map { it ?: Configuracao() }

    suspend fun obterConfiguracao(): Configuracao = configuracaoDao.obter() ?: Configuracao()

    suspend fun salvarConfiguracao(config: Configuracao) {
        require(config.custoKmCentavos >= 0 && config.tarifaMinimaCentavos >= 0) { "Valores negativos" }
        configuracaoDao.salvar(config.copy(id = Configuracao.ID_UNICO))
    }

    suspend fun definirTarifaMinima(centavos: Long) =
        salvarConfiguracao(obterConfiguracao().copy(tarifaMinimaCentavos = centavos))

    suspend fun definirCustoKm(centavos: Long) =
        salvarConfiguracao(obterConfiguracao().copy(custoKmCentavos = centavos))

    // ================================================================== interno

    /** Recalcula o intervalo de "hoje" a cada virada de dia e troca a query ativa. */
    private fun <T> porDia(consulta: (inicio: Long, fim: Long) -> Flow<T>): Flow<T> =
        Periodo.hoje().flatMapLatest { dia ->
            val (ini, fim) = Periodo.dia(dia)
            consulta(ini, fim)
        }
}

/** Nome da plataforma a partir do pacote do app lido pelo leitor. */
fun nomePlataforma(pacote: String?): String = when (pacote) {
    "com.ubercab.driver" -> "Uber"
    "com.app99.driver" -> "99"
    "br.com.ifood.driver.app" -> "iFood"
    "sinet.startup.inDriver" -> "inDrive"
    else -> "Particular"
}

/** Custo fixo por dia trabalhado (centavos): total mensal ÷ dias trabalhados no mês. */
fun custoFixoDiario(mensalCentavos: Long, diasTrabalhoMes: Int): Long =
    if (mensalCentavos > 0 && diasTrabalhoMes > 0) mensalCentavos / diasTrabalhoMes else 0

/** Formata centavos -> "R$ 1.234,56" (independe do idioma do aparelho). */
fun Long.emReais(): String {
    val negativo = this < 0
    val abs = kotlin.math.abs(this)
    val inteiro = (abs / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    val centavos = (abs % 100).toString().padStart(2, '0')
    return (if (negativo) "-R$ " else "R$ ") + inteiro + "," + centavos
}
