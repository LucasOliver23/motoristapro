package com.motoristapro.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** true se o usuário ligou o leitor de ofertas em Configurações > Acessibilidade. */
fun Context.leitorOfertasAtivo(): Boolean {
    val alvo = ComponentName(this, OfertaAccessibilityService::class.java)
    val ativos = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return ativos.split(':').any { ComponentName.unflattenFromString(it) == alvo }
}
