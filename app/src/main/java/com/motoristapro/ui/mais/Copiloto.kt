package com.motoristapro.ui.mais

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.motoristapro.MotoristaApp
import com.motoristapro.jornada.Notificacoes
import com.motoristapro.ui.componentes.bateriaLiberada

/**
 * O Copiloto (5.1, era o Checkup): as autorizações que o app precisa para ler
 * as ofertas e não parar no meio do turno, lidas do sistema.
 *
 * Fica fora da tela porque duas telas perguntam a mesma coisa: o Início (o selo
 * "COPILOTO OK" do cabeçalho) e o próprio Copiloto. Só entra na conta o que o
 * Android deixa o app conferir: o "Início automático" do fabricante não tem
 * como ser lido, então aparece na tela mas não conta como pronto nem pendente.
 */
data class EstadoCopiloto(
    val leitor: Boolean,
    val sobreposicao: Boolean,
    val bateria: Boolean,
    val notificacoes: Boolean,
    /** null = Android antigo (antes do 11), que não tem leitura por imagem. */
    val leituraImagem: Boolean?,
    val localizacao: Boolean
) {
    private val conferidos: List<Boolean>
        get() = listOfNotNull(leitor, sobreposicao, bateria, notificacoes, leituraImagem, localizacao)

    val total: Int get() = conferidos.size
    val prontos: Int get() = conferidos.count { it }
    val tudoPronto: Boolean get() = prontos == total
}

/** Lê o estado de agora. Barato: dá para chamar a cada volta à tela. */
fun Context.lerCopiloto(leitorOk: Boolean): EstadoCopiloto = EstadoCopiloto(
    leitor = leitorOk,
    sobreposicao = Settings.canDrawOverlays(this),
    bateria = bateriaLiberada(),
    notificacoes = Notificacoes.podeNotificar(this),
    leituraImagem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        (applicationContext as MotoristaApp).preferencias.ocrAtivo
    } else {
        null
    },
    localizacao = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
)
