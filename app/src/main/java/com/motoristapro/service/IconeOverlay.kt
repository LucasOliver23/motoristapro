package com.motoristapro.service

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/**
 * Um ícone do Material Symbols (Rounded, 24 px) desenhado numa janela de overlay.
 *
 * O painel da bolha é feito de Views, não de Compose, então não alcança o
 * [com.motoristapro.ui.componentes.Icones]. Este Drawable lê o MESMO formato de
 * caminho (caixa 960 x 960, com o y de -960 a 0) e pinta numa cor sólida: os
 * mesmos traços do app, sem PNG e sem emoji.
 */
class IconeOverlay(caminho: String, cor: Int) : Drawable() {

    private val original: Path = PathParser.createPathFromPathData(caminho)
    private val desenhado = Path()
    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cor
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val escala = minOf(b.width(), b.height()) / 960f
        val m = Matrix().apply {
            setScale(escala, escala)
            postTranslate(b.left.toFloat(), b.top + 960f * escala)
        }
        original.transform(m, desenhado)
        canvas.drawPath(desenhado, tinta)
    }

    override fun setAlpha(alpha: Int) {
        tinta.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        tinta.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    /** Os caminhos usados pelo painel da bolha (Material Symbols Rounded). */
    object Caminho {
        const val PAUSAR = "M600-200q-33 0-56.5-23.5T520-280v-400q0-33 23.5-56.5T600-760h80q33 0 56.5 23.5T760-680v400q0 33-23.5 56.5T680-200h-80Zm-320 0q-33 0-56.5-23.5T200-280v-400q0-33 23.5-56.5T280-760h80q33 0 56.5 23.5T440-680v400q0 33-23.5 56.5T360-200h-80Zm320-80h80v-400h-80v400Zm-320 0h80v-400h-80v400Zm0-400v400-400Zm320 0v400-400Z"
        const val RETOMAR = "M320-273v-414q0-17 12-28.5t28-11.5q5 0 10.5 1.5T381-721l326 207q9 6 13.5 15t4.5 19q0 10-4.5 19T707-446L381-239q-5 3-10.5 4.5T360-233q-16 0-28-11.5T320-273Zm80-207Zm0 134 210-134-210-134v268Z"
        const val ENCERRAR = "M360-320h240q17 0 28.5-11.5T640-360v-240q0-17-11.5-28.5T600-640H360q-17 0-28.5 11.5T320-600v240q0 17 11.5 28.5T360-320ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z"
        const val REGISTRAR = "M440-440v120q0 17 11.5 28.5T480-280q17 0 28.5-11.5T520-320v-120h120q17 0 28.5-11.5T680-480q0-17-11.5-28.5T640-520H520v-120q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640v120H320q-17 0-28.5 11.5T280-480q0 17 11.5 28.5T320-440h120Zm40 360q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z"
        const val LER_TELA = "M280-280h80q17 0 28.5 11.5T400-240q0 17-11.5 28.5T360-200H240q-17 0-28.5-11.5T200-240v-120q0-17 11.5-28.5T240-400q17 0 28.5 11.5T280-360v80Zm0-400v80q0 17-11.5 28.5T240-560q-17 0-28.5-11.5T200-600v-120q0-17 11.5-28.5T240-760h120q17 0 28.5 11.5T400-720q0 17-11.5 28.5T360-680h-80Zm400 0h-80q-17 0-28.5-11.5T560-720q0-17 11.5-28.5T600-760h120q17 0 28.5 11.5T760-720v120q0 17-11.5 28.5T720-560q-17 0-28.5-11.5T680-600v-80Zm0 480h-80q-17 0-28.5-11.5T560-240q0-17 11.5-28.5T600-280h80v-80q0-17 11.5-28.5T720-400q17 0 28.5 11.5T760-360v80h80q17 0 28.5 11.5T880-240q0 17-11.5 28.5T840-200h-80v80q0 17-11.5 28.5T720-80q-17 0-28.5-11.5T680-120v-80Z"
        const val VOZ_LIGADA = "M760-481q0-83-44-151.5T598-735q-15-7-22-21.5t-2-29.5q6-16 21.5-23t31.5 0q97 43 155 131.5T840-481q0 108-58 196.5T627-153q-16 7-31.5 0T574-176q-5-15 2-29.5t22-21.5q74-34 118-102.5T760-481ZM280-360H160q-17 0-28.5-11.5T120-400v-160q0-17 11.5-28.5T160-600h120l132-132q19-19 43.5-8.5T480-703v446q0 27-24.5 37.5T412-228L280-360Zm380-120q0 42-19 79.5T591-339q-10 6-20.5.5T560-356v-250q0-12 10.5-17.5t20.5.5q31 25 50 63t19 80ZM400-606l-86 86H200v80h114l86 86v-252ZM300-480Z"
        const val VOZ_MUDA = "M671-177q-11 7-22 13t-23 11q-15 7-30.5 0T574-176q-6-15 1.5-29.5T598-227q7-3 13-6.5t12-7.5L480-368v111q0 27-24.5 37.5T412-228L280-360H160q-17 0-28.5-11.5T120-400v-160q0-17 11.5-28.5T160-600h88L84-764q-11-11-11-28t11-28q11-11 28-11t28 11l680 680q11 11 11 28t-11 28q-11 11-28 11t-28-11l-93-93Zm89-304q0-83-44-151.5T598-735q-15-7-22-21.5t-2-29.5q6-16 21.5-23t31.5 0q97 43 155 131t58 197q0 33-6 65.5T817-353q-8 22-24.5 27.5t-30.5.5q-14-5-22.5-18t-.5-30q11-26 16-52.5t5-55.5ZM591-623q33 21 51 63t18 80v10q0 5-1 10-2 13-14 17t-22-6l-51-51q-6-6-9-13.5t-3-15.5v-77q0-12 10.5-17.5t20.5.5Zm-201-59q-6-6-6-14t6-14l22-22q19-19 43.5-8.5T480-703v63q0 14-12 19t-22-5l-56-56Zm10 328v-94l-72-72H200v80h114l86 86Zm-36-130Z"
        const val ABRIR_APP = "M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h240q17 0 28.5 11.5T480-800q0 17-11.5 28.5T440-760H200v560h560v-240q0-17 11.5-28.5T800-480q17 0 28.5 11.5T840-440v240q0 33-23.5 56.5T760-120H200Zm560-584L416-360q-11 11-28 11t-28-11q-11-11-11-28t11-28l344-344H600q-17 0-28.5-11.5T560-800q0-17 11.5-28.5T600-840h200q17 0 28.5 11.5T840-800v200q0 17-11.5 28.5T800-560q-17 0-28.5-11.5T760-600v-104Z"
    }
}
