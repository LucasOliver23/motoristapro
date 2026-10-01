package com.motoristapro.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Ações disparadas pelos botões da janela da oferta. */
object AcoesOferta {

    private const val TAG = "MotoristaPro"
    private const val PACOTE_MAPS = "com.google.android.apps.maps"

    /**
     * Abre o Street View no endereço (converte endereço -> coordenada com o Geocoder).
     * Sem coordenada ou sem Street View: abre a busca do endereço no Google Maps.
     */
    suspend fun abrirLocal(context: Context, endereco: String) {
        val escolhido = AppNavegacao.lida(context)
        val coordenada = withContext(Dispatchers.IO) { geocodificar(context, endereco) }

        // Waze: so entende coordenada ou busca por texto; nao tem Street View.
        val waze = if (escolhido == AppNavegacao.WAZE) {
            val destino = coordenada?.let { (lat, lng) -> "ll=$lat,$lng&navigate=yes" }
                ?: ("q=" + Uri.encode(endereco))
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?$destino"))
        } else {
            null
        }

        // Street View so quando o motorista nao pediu outro app: a ideia dele e
        // VER a esquina do embarque antes de aceitar, nao tracar rota.
        val streetView = if (escolhido != AppNavegacao.WAZE) {
            coordenada?.let { (lat, lng) ->
                Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=$lat,$lng"))
                    .setPackage(PACOTE_MAPS)
            }
        } else {
            null
        }

        val busca = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(endereco)))
            .apply { escolhido.pacote?.let { setPackage(it) } }
        // Ultimo recurso sem pacote: cai na listinha do Android ou no navegador.
        val qualquer = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(endereco)))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(endereco))
        )
        for (intent in listOfNotNull(waze, streetView, busca, qualquer, web)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // tenta a próxima opção
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao abrir mapa", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun geocodificar(context: Context, endereco: String): Pair<Double, Double>? {
        if (!Geocoder.isPresent()) return null
        return try {
            Geocoder(context, Locale("pt", "BR"))
                .getFromLocationName(endereco, 1)
                ?.firstOrNull()
                ?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder falhou para '$endereco'", e)
            null
        }
    }
}
