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

    /**
     * Sub-tela do Menu a abrir assim que a aba Menu aparecer.
     *
     * Guardado como texto (o nome do enum) de propósito: assim este objeto não
     * precisa conhecer a tela do Menu, e os atalhos do Início podem apontar para
     * lá sem que uma tela passe a depender da outra.
     */
    val subTelaPedida = MutableStateFlow<String?>(null)

    /**
     * "Inicie a jornada assim que o Início aparecer" — vem do botão do widget.
     * O Início é quem pede a permissão de localização, então o pedido espera lá.
     */
    val iniciarJornadaPedido = MutableStateFlow(false)

    /**
     * Imagem que chegou pelo "Compartilhar" (WhatsApp, Galeria): o Menu abre o
     * "Testar com print" já com ela. Quem lê zera.
     */
    val printCompartilhado = MutableStateFlow<android.net.Uri?>(null)

    fun pedir(indice: Int) {
        abaPedida.value = indice
    }

    /** "Abra o Menu já dentro desta tela" — usado pelos atalhos do Início. */
    fun pedirSubTela(nome: String, abaDoMenu: Int) {
        subTelaPedida.value = nome
        abaPedida.value = abaDoMenu
    }
}
