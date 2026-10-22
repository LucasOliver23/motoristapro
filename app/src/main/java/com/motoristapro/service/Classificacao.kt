package com.motoristapro.service

/**
 * O veredito da oferta, em palavra de ação.
 *
 * "BOA" e "RUIM" descreviam a corrida; "ACEITAR" e "RECUSAR" dizem o que fazer,
 * que é o que o motorista precisa nos segundos que ele tem. A voz já falava
 * assim desde o começo — agora o cartão fala igual.
 */
enum class Classificacao(val corFundo: Int, val rotulo: String) {
    BOA(0xE60288D1.toInt(), "ACEITAR"),   // azul do visual novo (era verde)
    MEDIA(0xE6F6B93B.toInt(), "ATENÇÃO"),
    RUIM(0xE6C62828.toInt(), "RECUSAR")
}
