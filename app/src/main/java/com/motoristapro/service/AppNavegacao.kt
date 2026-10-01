package com.motoristapro.service

import android.content.Context

/**
 * Para onde vai o botão "Ver embarque" do cartão da oferta.
 *
 * "Perguntar sempre" deixa o Android mostrar a listinha de apps — bom para quem
 * alterna. Escolhendo um, o app abre direto nele e economiza dois toques no
 * meio do trânsito, que é onde esses dois toques custam caro.
 */
enum class AppNavegacao(
    val rotulo: String,
    val descricao: String,
    /** null = deixa o Android escolher. */
    val pacote: String?
) {
    PERGUNTAR("Perguntar sempre", "O Android mostra a lista de apps a cada endereço", null),
    MAPS("Google Maps", "Abre direto no Maps, com Street View quando dá", "com.google.android.apps.maps"),
    WAZE("Waze", "Abre direto no Waze", "com.waze");

    companion object {
        private const val ARQ = "navegacao"
        private const val KEY = "app"

        fun lida(c: Context): AppNavegacao {
            val salvo = c.applicationContext.getSharedPreferences(ARQ, Context.MODE_PRIVATE)
                .getString(KEY, null)
            return runCatching { valueOf(salvo ?: "") }.getOrDefault(PERGUNTAR)
        }

        fun definir(c: Context, escolha: AppNavegacao) {
            c.applicationContext.getSharedPreferences(ARQ, Context.MODE_PRIVATE)
                .edit().putString(KEY, escolha.name).apply()
        }

        /** O app está instalado neste celular? A tela usa para não oferecer o que não existe. */
        fun instalado(c: Context, escolha: AppNavegacao): Boolean {
            val pacote = escolha.pacote ?: return true
            return runCatching {
                c.packageManager.getLaunchIntentForPackage(pacote) != null
            }.getOrDefault(false)
        }
    }
}
