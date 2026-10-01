package com.motoristapro.nuvem

import com.motoristapro.data.local.VERSAO_BANCO
import com.motoristapro.data.local.dao.Pacote
import com.motoristapro.data.local.entity.CategoriaDespesa
import com.motoristapro.data.local.entity.Configuracao
import com.motoristapro.data.local.entity.Corrida
import com.motoristapro.data.local.entity.CustoFixo
import com.motoristapro.data.local.entity.Despesa
import com.motoristapro.data.local.entity.Jornada
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
                    .put("observacao", it.observacao ?: JSONObject.NULL).put("criadoEm", it.criadoEm))
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
                    .put("criadoEm", it.criadoEm))
            }
        })
        raiz.put("jornadas", JSONArray().apply {
            p.jornadas.forEach {
                put(JSONObject()
                    .put("id", it.id).put("inicioEm", it.inicioEm)
                    .put("fimEm", it.fimEm ?: JSONObject.NULL).put("metrosGps", it.metrosGps))
            }
        })
        raiz.put("custosFixos", JSONArray().apply {
            p.custosFixos.forEach {
                put(JSONObject()
                    .put("id", it.id).put("nome", it.nome)
                    .put("valorMensalCentavos", it.valorMensalCentavos).put("ativo", it.ativo))
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
                origem = o.optString("origem"),
                destino = o.optString("destino"),
                valorCentavos = o.optLong("valorCentavos"),
                gorjetaCentavos = o.optLong("gorjetaCentavos"),
                distanciaMetros = o.optLong("distanciaMetros"),
                deslocamentoMetros = o.optLong("deslocamentoMetros"),
                duracaoSegundos = o.optLong("duracaoSegundos"),
                inicioEm = o.optLong("inicioEm"),
                observacao = o.textoOuNulo("observacao"),
                criadoEm = o.optLong("criadoEm")
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
                criadoEm = o.optLong("criadoEm")
            )
        }
        val jornadas = raiz.optJSONArray("jornadas").mapear { o ->
            Jornada(
                id = o.optLong("id"),
                inicioEm = o.optLong("inicioEm"),
                fimEm = o.longOuNulo("fimEm"),
                metrosGps = o.optLong("metrosGps")
            )
        }
        val custos = raiz.optJSONArray("custosFixos").mapear { o ->
            CustoFixo(
                id = o.optLong("id"),
                nome = o.optString("nome"),
                valorMensalCentavos = o.optLong("valorMensalCentavos"),
                ativo = o.optBoolean("ativo", true)
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
        return Pacote(plataformas, corridas, despesas, jornadas, custos, config)
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
