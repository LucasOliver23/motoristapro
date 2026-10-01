package com.motoristapro.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motoristapro.ui.theme.Lima
import com.motoristapro.ui.theme.TextoSecundario
import com.motoristapro.ui.theme.VermelhoPrejuizo
import androidx.credentials.exceptions.GetCredentialCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Tela de entrada: criar conta ou entrar com email/senha, ou entrar com o Google.
 * Só aparece quando o Firebase está configurado (app/google-services.json).
 */
@Composable
fun LoginScreen(
    autenticacao: AutenticacaoManager,
    onEntrou: () -> Unit
) {
    val escopo = rememberCoroutineScope()
    val context = LocalContext.current

    var modoCadastro by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var senha by rememberSaveable { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var tarefa by remember { mutableStateOf<Job?>(null) }
    var mensagem by rememberSaveable { mutableStateOf<String?>(null) }
    var aviso by rememberSaveable { mutableStateOf<String?>(null) }

    fun executar(bloco: suspend () -> Unit, aoTerminar: (() -> Unit)? = null) {
        if (ocupado) return
        ocupado = true
        mensagem = null
        aviso = null
        // Tempo limite: nenhuma tentativa pode travar a tela para sempre.
        tarefa = escopo.launch {
            runCatching { withTimeout(LIMITE_MS) { bloco() } }
                .onSuccess { aoTerminar?.invoke() ?: onEntrou() }
                .onFailure { mensagem = autenticacao.mensagemDeErro(it) }
            ocupado = false
            tarefa = null
        }
    }

    // Resultado da tela clássica do Google (caminho reserva).
    val seletorGoogle = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        aviso = null
        escopo.launch {
            runCatching { autenticacao.concluirGoogleClassico(resultado.data) }
                .onSuccess { ocupado = false; onEntrou() }
                .onFailure { ocupado = false; mensagem = autenticacao.mensagemDeErro(it) }
        }
    }

    /**
     * Entrar com o Google em dois tempos:
     *  1. tenta a folha nova (Credential Manager), com espera curta;
     *  2. se ela não aparecer, abre a tela clássica do Google, que funciona em todo aparelho.
     */
    fun entrarComGoogle() {
        if (ocupado) return
        ocupado = true
        mensagem = null
        aviso = null
        tarefa = escopo.launch {
            val tentativa = runCatching {
                withTimeout(LIMITE_GOOGLE_MS) { autenticacao.entrarComGoogle(context) }
            }
            tarefa = null
            if (tentativa.isSuccess) {
                ocupado = false
                onEntrou()
                return@launch
            }
            val erro = tentativa.exceptionOrNull()
            if (erro is GetCredentialCancellationException) {   // o motorista fechou a folha
                ocupado = false
                mensagem = "Login com Google cancelado"
                return@launch
            }
            val intent = runCatching { autenticacao.intentGoogleClassico(context) }.getOrNull()
            if (intent == null) {
                ocupado = false
                mensagem = erro?.let { autenticacao.mensagemDeErro(it) } ?: "Não foi possível entrar"
                return@launch
            }
            aviso = "Abrindo o seletor de contas do Google…"
            seletorGoogle.launch(intent)     // continua ocupado até voltar o resultado
        }
    }

    fun cancelar() {
        tarefa?.cancel()
        tarefa = null
        ocupado = false
        mensagem = "Cancelado"
    }

    val emailValido = email.contains("@") && email.contains(".")
    val podeEnviar = emailValido && senha.length >= 6 && !ocupado

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("MotoristaPro", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Lima)
        Text(
            if (modoCadastro) "Crie sua conta para salvar seus dados na nuvem"
            else "Entre na sua conta para recuperar seus dados",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            singleLine = true,
            isError = email.isNotBlank() && !emailValido,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = { if (modoCadastro) Text("Mínimo de 6 caracteres") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        mensagem?.let {
            Text(it, color = VermelhoPrejuizo, style = MaterialTheme.typography.bodyMedium)
        }
        aviso?.let {
            Text(it, color = Lima, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                executar({
                    if (modoCadastro) autenticacao.cadastrarComEmail(email, senha)
                    else autenticacao.entrarComEmail(email, senha)
                })
            },
            enabled = podeEnviar,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Lima, contentColor = Color(0xFF0A0D0B))
        ) {
            if (ocupado) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0A0D0B), strokeWidth = 2.dp)
            } else {
                Text(if (modoCadastro) "Criar conta" else "Entrar", fontWeight = FontWeight.Bold)
            }
        }

        if (ocupado) {
            TextButton(onClick = { cancelar() }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = TextoSecundario)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { modoCadastro = !modoCadastro; mensagem = null }) {
                Text(if (modoCadastro) "Já tenho conta" else "Criar conta", color = Lima)
            }
            if (!modoCadastro) {
                TextButton(
                    enabled = emailValido && !ocupado,
                    onClick = {
                        executar({ autenticacao.recuperarSenha(email) }) {
                            aviso = "Enviamos um link de recuperação para $email"
                        }
                    }
                ) { Text("Esqueci a senha", color = TextoSecundario) }
            }
        }

        if (autenticacao.googleDisponivel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  ou  ", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { entrarComGoogle() },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Entrar com o Google") }
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            Text(
                "Seus dados ficam na sua conta. Corridas, despesas e configurações são salvas " +
                    "na nuvem para você não perder nada ao trocar de celular.",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Tempo máximo de espera de qualquer operação de login (60 s). */
private const val LIMITE_MS = 60_000L

/**
 * Espera curta pela folha nova do Google. Se ela não abrir nesse tempo (acontece em
 * alguns aparelhos), o app já parte para a tela clássica em vez de ficar girando.
 */
private const val LIMITE_GOOGLE_MS = 12_000L
