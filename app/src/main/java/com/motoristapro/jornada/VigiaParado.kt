package com.motoristapro.jornada

/**
 * "Parado há 30 min com a jornada rodando": avisa uma vez por parada.
 *
 * Lógica pura (sem Android) para dar para testar. O serviço da jornada passa
 * cada posição do GPS em [posicao] e pergunta de minuto em minuto em [deveAvisar].
 * Andar mais de [RAIO_M] do ponto onde parou conta como movimento e libera o
 * próximo aviso.
 */
class VigiaParado(agora: Long) {

    /** Desde quando está no mesmo lugar. */
    var paradoDesde: Long = agora
        private set

    private var pontoX: Double? = null
    private var pontoY: Double? = null
    private var avisou = false

    /**
     * [distanciaDoPonto] mede (em metros) da posição nova até o ponto da parada;
     * recebe a latitude/longitude do ponto. Devolve true se andou (o aviso, se
     * houver, deve sumir).
     */
    fun posicao(lat: Double, lon: Double, agora: Long, distanciaDoPonto: (Double, Double) -> Double): Boolean {
        val x = pontoX
        val y = pontoY
        if (x == null || y == null || distanciaDoPonto(x, y) > RAIO_M) {
            pontoX = lat
            pontoY = lon
            paradoDesde = agora
            val tinhaAvisado = avisou
            avisou = false
            return tinhaAvisado || x == null
        }
        return false
    }

    /** true uma vez só por parada, depois de [LIMITE_MS] sem sair do lugar e com o turno correndo. */
    fun deveAvisar(agora: Long, pausada: Boolean): Boolean {
        if (avisou || pausada || agora - paradoDesde < LIMITE_MS) return false
        avisou = true
        return true
    }

    /** Pausou ou retomou: começa a contar de novo. */
    fun reiniciar(agora: Long) {
        paradoDesde = agora
        avisou = false
    }

    companion object {
        const val LIMITE_MS = 30 * 60_000L
        const val RAIO_M = 100.0
    }
}
