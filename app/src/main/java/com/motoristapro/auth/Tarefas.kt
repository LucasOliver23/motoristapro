package com.motoristapro.auth

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Espera uma Task do Firebase dentro de uma coroutine.
 * Feito à mão para não precisar da biblioteca kotlinx-coroutines-play-services.
 */
suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { tarefa ->
        val erro = tarefa.exception
        when {
            erro != null -> cont.resumeWithException(erro)
            tarefa.isCanceled -> cont.cancel()
            else -> @Suppress("UNCHECKED_CAST") cont.resume(tarefa.result as T)
        }
    }
}
