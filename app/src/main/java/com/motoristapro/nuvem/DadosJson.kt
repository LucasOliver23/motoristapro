package com.motoristapro.nuvem

import com.motoristapro.data.local.VERSAO_BANCO
import com.motoristapro.data.local.dao.Pacote
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.ItemManutencao
import com.motoristapro.data.local.entity.Jornada
import com.motoristapro.data.local.entity.PerfilCusto
import com.motoristapro.data.local.entity.Veiculo
import com.motoristapro.data.local.entity.Plataforma
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converte os dados do app para JSON e de volta, usando org.json (já vem no Android,
 * sem biblioteca extra). É o formato que sobe para a nuvem.
 */
object DadosJson {

    private fun JSONObject.textoOuNulo(chave: String): String? =
        if (isNull(chave)) null else optString(chave, null)

    private fun JSONObject.longOuNulo(chave: String): Long? =
        if (isNull(chave)) null else optLong(chave)

    fun paraJson(p: Pacote): String {
        val raiz = JSONObject()
        raiz.put("versaoBanco", VERSAO_BANCO)
        raiz.put("geradoEm", System.currentTimeMillis())

        raiz.put("plataformas", JSONArray().apply {
            p.plataformas.forEach {
                put(JSONObject()
                    .put("id", it.id).put("nome", it.nome)
                    .put("corHex", it.corHex ?: JSONObject.NULL).put("ativa", it.ativa))
            }
        })
        raiz.put("corridas", JSONArray().apply {
            p.corridas.forEach {
                put(JSONObject()
                    .put("id", it.id).put("plataformaId", it.plataformaId)
                    .put("origem", it.origem).put("destino", it.destino)
                    .put("valorCentavos", it.valorCentavos).put("gorjetaCentavos", it.gorjetaCentavos)
                    .put("distanciaMetros", it.distanciaMetros).put("deslocamentoMetros", it.deslocamentoMetros)
                    .put("duracaoSegundos", it.duracaoSegundos).put("inicioEm", it.inicioEm)
                    .put("observacao", it.observacao ?: JSONObject.NULL).put("criadoEm", it.criadoEm)
                    .put("veiculoId", it.veiculoId))
            }
        })
        raiz.put("despesas", JSONArray().apply {
            p.despesas.forEach {
                put(JSONObject()
                    .put("id", it.id).put("categoria", it.categoria.name)
                    .put("valorCentavos", it.valorCentavos).put("dataEm", it.dataEm)
                    .put("descricao", it.descricao ?: JSONObject.NULL)
                    .put("litrosMl", it.litrosMl ?: JSONObject.NULL)
                    .put("odometroKm", it.odometroKm ?: JSONObject.NULL)
                    .put("criadoEm", it.criadoEm).put("veiculoId", it.veiculoId))
            }
        })
        raiz.put("jornadas", JSONArray().apply {
            p.jornadas.forEach {
                put(JSONObject()
                    .put("id", it.id).put("inicioEm", it.inicioEm)
                    .put("fimEm", it.fimEm ?: JSONObject.NULL).put("metrosGps", it.metrosGps)
                    // Tempo por estado e por app: sem eles a Atividade perdia
                    // "parado" e "R$/h conectado" depois de restaurar.
                    .put("segOffline", it.segOffline).put("segAguardando", it.segAguardando)
                    .put("segBuscando", it.segBuscando).put("segEsperando", it.segEsperando)
                    .put("segEmViagem", it.segEmViagem)
                    .put("segUber", it.segUber).put("seg99", it.seg99)
                    .put("segIfood", it.segIfood).put("segInDrive", it.segInDrive)
                    .put("pausadaEm", it.pausadaEm).put("segPausados", it.segPausados)
                    .put("veiculoId", it.veiculoId))
            }
        })
        raiz.put("custosFixos", JSONArray().apply {
            p.custosFixos.forEach {
                put(JSONObject()
                    .put("id", it.id).put("nome", it.nome)
                    .put("valorMensalCentavos", it.valorMensalCentavos).put("ativo", it.ativo)
                    .put("origem", it.origem))
            }
        })
        p.configuracao?.let { c ->
            raiz.put("configuracao", JSONObject()
                .put("metaLucroDiarioCentavos", c.metaLucroDiarioCentavos)
                .put("metaLucroSemanalCentavos", c.metaLucroSemanalCentavos)
                .put("metaLucroMensalCentavos", c.metaLucroMensalCentavos)
                .put("custoKmCentavos", c.custoKmCentavos)
                .put("tarifaMinimaCentavos", c.tarifaMinimaCentavos)
                .put("veiculoNome", c.veiculoNome ?: JSONObject.NULL)
                .put("consumoKmLx100", c.consumoKmLx100)
                .put("precoLitroCentavos", c.precoLitroCentavos)
                .put("diasTrabalhoMes", c.diasTrabalhoMes)
                .put("nomeMotorista", c.nomeMotorista ?: JSONObject.NULL)
                .put("telefone", c.telefone ?: JSONObject.NULL)
                .put("cidade", c.cidade ?: JSONObject.NULL))
        }
        p.manutencoes?.let { lista ->
            raiz.put("manutencoes", JSONArray().apply {
                lista.forEach {
                    put(JSONObject()
                        .put("id", it.id).put("nome", it.nome)
                        .put("intervaloKm", it.intervaloKm).put("odometroUltimaKm", it.odometroUltimaKm)
                        .put("atualizadoEm", it.atualizadoEm).put("veiculoId", it.veiculoId))
                }
            })
        }
        p.perfilCusto?.let { c ->
            raiz.put("perfilCusto", JSONObject()
                .put("forma", c.forma).put("tipoVeiculo", c.tipoVeiculo)
                .put("veiculoNome", c.veiculoNome ?: JSONObject.NULL).put("tipoTrabalho", c.tipoTrabalho)
                .put("valorVeiculoCentavos", c.valorVeiculoCentavos)
                .put("parcelaMensalCentavos", c.parcelaMensalCentavos)
                .put("aluguelMensalCentavos", c.aluguelMensalCentavos)
                .put("seguroMensalCentavos", c.seguroMensalCentavos)
                .put("ipvaPercentX100", c.ipvaPercentX100).put("ipvaEmReais", c.ipvaEmReais)
                .put("ipvaAnualCentavos", c.ipvaAnualCentavos)
                .put("desvalorizacaoAnualX100", c.desvalorizacaoAnualX100)
                .put("outrosMensaisCentavos", c.outrosMensaisCentavos)
                .put("revisaoCentavos", c.revisaoCentavos).put("intervaloRevisaoKm", c.intervaloRevisaoKm)
                .put("trocaOleoCentavos", c.trocaOleoCentavos).put("intervaloOleoKm", c.intervaloOleoKm)
                .put("jogoPneusCentavos", c.jogoPneusCentavos).put("duracaoPneusKm", c.duracaoPneusKm)
                .put("outrosDesgasteCentavos", c.outrosDesgasteCentavos).put("outrosDesgasteKm", c.outrosDesgasteKm)
                .put("combustivel", c.combustivel).put("precoLitroCentavos", c.precoLitroCentavos)
                .put("consumoX100", c.consumoX100).put("kmPorDia", c.kmPorDia)
                .put("diasPorSemana", c.diasPorSemana).put("horasPorDia", c.horasPorDia)
                .put("metaLucroMensalCentavos", c.metaLucroMensalCentavos)
                .put("calculadoEm", c.calculadoEm))
        }
        p.veiculos?.let { lista ->
            raiz.put("veiculos", JSONArray().apply {
                lista.forEach {
                    put(JSONObject()
                        .put("id", it.id).put("nome", it.nome).put("tipo", it.tipo).put("combustivel", it.combustivel)
                        .put("consumoKmLx100", it.consumoKmLx100).put("precoLitroCentavos", it.precoLitroCentavos)
                        .put("custoKmCentavos", it.custoKmCentavos).put("tarifaMinimaCentavos", it.tarifaMinimaCentavos)
                        .put("criadoEm", it.criadoEm))
                }
            })
        }
        return raiz.toString()
    }

    /** Lança exceção se o JSON não for do MotoristaPro. */
    fun deJson(texto: String): Pacote {
        val raiz = JSONObject(texto)
        require(raiz.has("corridas") || raiz.has("despesas")) { "Arquivo não é um backup do MotoristaPro" }

        val plataformas = raiz.optJSONArray("plataformas").mapear { o ->
            Plataforma(
                id = o.optLong("id"),
                nome = o.optString("nome"),
                corHex = o.textoOuNulo("corHex"),
                ativa = o.optBoolean("ativa", true)
            )
        }
        val corridas = raiz.optJSONArray("corridas").mapear { o ->
            Corrida(
                id = o.optLong("id"),
                plataformaId = o.optLong("plataformaId"),
                origem = o.textoOuNulo("origem").orEmpty(),
                destino = o.textoOuNulo("destino").orEmpty(),
                valorCentavos = o.optLong("valorCentavos"),
                gorjetaCentavos = o.optLong("gorjetaCentavos"),
                distanciaMetros = o.optLong("distanciaMetros"),
                deslocamentoMetros = o.optLong("deslocamentoMetros"),
                duracaoSegundos = o.optLong("duracaoSegundos"),
                inicioEm = o.optLong("inicioEm"),
                observacao = o.textoOuNulo("observacao"),
                criadoEm = o.optLong("criadoEm"),
                veiculoId = o.optLong("veiculoId", 1L)
            )
        }
        val despesas = raiz.optJSONArray("despesas").mapear { o ->
            Despesa(
                id = o.optLong("id"),
                categoria = runCatching { CategoriaDespesa.valueOf(o.optString("categoria")) }
                    .getOrDefault(CategoriaDespesa.OUTROS),
                valorCentavos = o.optLong("valorCentavos"),
                dataEm = o.optLong("dataEm"),
                descricao = o.textoOuNulo("descricao"),
                litrosMl = o.longOuNulo("litrosMl"),
                odometroKm = o.longOuNulo("odometroKm"),
                criadoEm = o.optLong("criadoEm"),
                veiculoId = o.optLong("veiculoId", 1L)
            )
        }
        val jornadas = raiz.optJSONArray("jornadas").mapear { o ->
            Jornada(
                id = o.optLong("id"),
                inicioEm = o.optLong("inicioEm"),
                fimEm = o.longOuNulo("fimEm"),
                metrosGps = o.optLong("metrosGps"),
                segOffline = o.optLong("segOffline"),
                segAguardando = o.optLong("segAguardando"),
                segBuscando = o.optLong("segBuscando"),
                segEsperando = o.optLong("segEsperando"),
                segEmViagem = o.optLong("segEmViagem"),
                segUber = o.optLong("segUber"),
                seg99 = o.optLong("seg99"),
                segIfood = o.optLong("segIfood"),
                segInDrive = o.optLong("segInDrive"),
                pausadaEm = o.optLong("pausadaEm"),
                segPausados = o.optLong("segPausados"),
                veiculoId = o.optLong("veiculoId", 1L)
            )
        }
        val custos = raiz.optJSONArray("custosFixos").mapear { o ->
            CustoFixo(
                id = o.optLong("id"),
                nome = o.optString("nome"),
                valorMensalCentavos = o.optLong("valorMensalCentavos"),
                ativo = o.optBoolean("ativo", true),
                origem = o.optString("origem", CustoFixo.MANUAL)
            )
        }
        val config = raiz.optJSONObject("configuracao")?.let { o ->
            Configuracao(
                metaLucroDiarioCentavos = o.optLong("metaLucroDiarioCentavos"),
                metaLucroSemanalCentavos = o.optLong("metaLucroSemanalCentavos"),
                metaLucroMensalCentavos = o.optLong("metaLucroMensalCentavos"),
                custoKmCentavos = o.optLong("custoKmCentavos"),
                tarifaMinimaCentavos = o.optLong("tarifaMinimaCentavos"),
                veiculoNome = o.textoOuNulo("veiculoNome"),
                consumoKmLx100 = o.optLong("consumoKmLx100"),
                precoLitroCentavos = o.optLong("precoLitroCentavos"),
                diasTrabalhoMes = o.optInt("diasTrabalhoMes", 26),
                nomeMotorista = o.textoOuNulo("nomeMotorista"),
                telefone = o.textoOuNulo("telefone"),
                cidade = o.textoOuNulo("cidade")
            )
        }
        val manutencoes = if (raiz.has("manutencoes")) raiz.optJSONArray("manutencoes").mapear { o ->
            ItemManutencao(
                id = o.optLong("id"),
                nome = o.optString("nome"),
                intervaloKm = o.optLong("intervaloKm"),
                odometroUltimaKm = o.optLong("odometroUltimaKm"),
                atualizadoEm = o.optLong("atualizadoEm"),
                veiculoId = o.optLong("veiculoId", 1L)
            )
        } else null
        val perfil = raiz.optJSONObject("perfilCusto")?.let { o ->
            val padrao = PerfilCusto()
            PerfilCusto(
                forma = o.optString("forma", padrao.forma),
                tipoVeiculo = o.optString("tipoVeiculo", padrao.tipoVeiculo),
                veiculoNome = o.textoOuNulo("veiculoNome"),
                tipoTrabalho = o.optString("tipoTrabalho", padrao.tipoTrabalho),
                valorVeiculoCentavos = o.optLong("valorVeiculoCentavos"),
                parcelaMensalCentavos = o.optLong("parcelaMensalCentavos"),
                aluguelMensalCentavos = o.optLong("aluguelMensalCentavos"),
                seguroMensalCentavos = o.optLong("seguroMensalCentavos"),
                ipvaPercentX100 = o.optLong("ipvaPercentX100", padrao.ipvaPercentX100),
                ipvaEmReais = o.optBoolean("ipvaEmReais", false),
                ipvaAnualCentavos = o.optLong("ipvaAnualCentavos"),
                desvalorizacaoAnualX100 = o.optLong("desvalorizacaoAnualX100", padrao.desvalorizacaoAnualX100),
                outrosMensaisCentavos = o.optLong("outrosMensaisCentavos"),
                revisaoCentavos = o.optLong("revisaoCentavos"),
                intervaloRevisaoKm = o.optLong("intervaloRevisaoKm"),
                trocaOleoCentavos = o.optLong("trocaOleoCentavos"),
                intervaloOleoKm = o.optLong("intervaloOleoKm"),
                jogoPneusCentavos = o.optLong("jogoPneusCentavos"),
                duracaoPneusKm = o.optLong("duracaoPneusKm"),
                outrosDesgasteCentavos = o.optLong("outrosDesgasteCentavos"),
                outrosDesgasteKm = o.optLong("outrosDesgasteKm"),
                combustivel = o.optString("combustivel", padrao.combustivel),
                precoLitroCentavos = o.optLong("precoLitroCentavos"),
                consumoX100 = o.optLong("consumoX100"),
                kmPorDia = o.optLong("kmPorDia"),
                diasPorSemana = o.optInt("diasPorSemana", padrao.diasPorSemana),
                horasPorDia = o.optInt("horasPorDia", padrao.horasPorDia),
                metaLucroMensalCentavos = o.optLong("metaLucroMensalCentavos"),
                calculadoEm = o.optLong("calculadoEm")
            )
        }
        val veiculos = if (raiz.has("veiculos")) raiz.optJSONArray("veiculos").mapear { o ->
            Veiculo(
                id = o.optLong("id"),
                nome = o.optString("nome", "Meu veículo"),
                tipo = o.optString("tipo", "MOTO"),
                combustivel = o.optString("combustivel", "GASOLINA"),
                consumoKmLx100 = o.optLong("consumoKmLx100"),
                precoLitroCentavos = o.optLong("precoLitroCentavos"),
                custoKmCentavos = o.optLong("custoKmCentavos"),
                tarifaMinimaCentavos = o.optLong("tarifaMinimaCentavos"),
                criadoEm = o.optLong("criadoEm")
            )
        } else null
        return Pacote(plataformas, corridas, despesas, jornadas, custos, config, manutencoes, perfil, veiculos)
    }

    private fun <T> JSONArray?.mapear(bloco: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val saida = ArrayList<T>(length())
        for (i in 0 until length()) {
            optJSONObject(i)?.let { saida += bloco(it) }
        }
        return saida
    }
}
