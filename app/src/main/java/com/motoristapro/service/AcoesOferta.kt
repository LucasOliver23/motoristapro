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
        val coordenada = withContext(Dispatchers.IO) { geocodificar(context, endereco) }
        val streetView = coordenada?.let { (lat, lng) ->
            Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=$lat,$lng"))
                .setPackage(PACOTE_MAPS)
        }
        val busca = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(endereco)))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(endereco))
        )
        for (intent in listOfNotNull(streetView, busca, web)) {
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
