package com.motoristapro.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pedido de "abra o app já nesta aba", vindo de fora da tela.
 *
 * O menu da bolha roda no serviço de acessibilidade, mas no MESMO processo do
 * app — então um valor em memória chega mais rápido e com menos peça móvel do
 * que um extra de Intent, que ainda precisaria de onNewIntent para funcionar
 * quando o app já está aberto atrás da bolha.
 *
 * Quem lê (o AppRaiz) zera depois de usar, para o pedido não repetir na próxima
 * vez que a tela for recomposta.
 */
object NavegacaoRapida {
    val abaPedida = MutableStateFlow<Int?>(null)

    fun pedir(indice: Int) {
        abaPedida.value = indice
    }
}
