package com.motoristapro.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.asStateFlow

/** Quem está logado. null = ninguém. */
data class Usuario(val uid: String, val email: String?, val nome: String?)

/**
 * Login com email/senha e com Google (Firebase Authentication).
 *
 * O app funciona SEM Firebase: enquanto o arquivo google-services.json não estiver em app/,
 * [disponivel] é false, a tela de login não aparece e tudo continua local, como antes.
 */
class AutenticacaoManager(private val context: Context) {

    private val auth: FirebaseAuth? = try {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "Firebase não configurado; app segue só com dados locais", e)
        null
    }

    /** true quando o Firebase está configurado neste app. */
    val disponivel: Boolean get() = auth != null

    private val _usuario = MutableStateFlow(auth?.currentUser?.let(::converter))
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    init {
        auth?.addAuthStateListener { a -> _usuario.value = a.currentUser?.let(::converter) }
    }

    // ================================================================== email e senha

    suspend fun entrarComEmail(email: String, senha: String) {
        val a = auth ?: error(SEM_FIREBASE)
        a.signInWithEmailAndPassword(email.trim(), senha).esperar()
    }

    suspend fun cadastrarComEmail(email: String, senha: String) {
        val a = auth ?: error(SEM_FIREBASE)
        require(senha.length >= 6) { "A senha precisa ter pelo menos 6 caracteres" }
        a.createUserWithEmailAndPassword(email.trim(), senha).esperar()
    }

    suspend fun recuperarSenha(email: String) {
        val a = auth ?: error(SEM_FIREBASE)
        require(email.isNotBlank()) { "Informe o email" }
        a.sendPasswordResetEmail(email.trim()).esperar()
    }

    // ================================================================== Google

    /**
     * Abre o seletor de contas do Google e entra no Firebase com a conta escolhida.
     * Exige que o login com Google esteja ligado no Firebase e que o SHA-1 do app
     * esteja cadastrado lá (senão o Google recusa e cai em "nenhuma conta disponível").
     */
    suspend fun entrarComGoogle(contextoDaTela: Context) {
        val a = auth ?: error(SEM_FIREBASE)
        val clienteWeb = idClienteWeb() ?: error(
            "Login com Google não configurado: falta o google-services.json com o cliente web."
        )
        // O seletor de contas precisa da Activity: com o contexto da aplicação ele não abre.
        val tela = contextoDaTela.comoActivity()
            ?: error("Não foi possível abrir o seletor de contas do Google")
        val gerenciador = CredentialManager.create(tela)

        // 1ª tentativa: só contas já usadas neste app. 2ª: todas as contas do aparelho.
        val credencial = try {
            pedirCredencial(gerenciador, tela, clienteWeb, apenasAutorizadas = true)
        } catch (e: NoCredentialException) {
            pedirCredencial(gerenciador, tela, clienteWeb, apenasAutorizadas = false)
        }

        if (credencial !is CustomCredential ||
            credencial.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("O Google devolveu uma credencial inesperada")
        }
        val google = GoogleIdTokenCredential.createFrom(credencial.data)
        a.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).esperar()
    }

    private suspend fun pedirCredencial(
        gerenciador: CredentialManager,
        contextoDaTela: Context,
        clienteWeb: String,
        apenasAutorizadas: Boolean
    ) = gerenciador.getCredential(
        contextoDaTela,
        GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(apenasAutorizadas)
                    .setServerClientId(clienteWeb)
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()
    ).credential

    /** Sobe a cadeia de contextos até achar a Activity (o Compose pode entregar um wrapper). */
    private fun Context.comoActivity(): Activity? {
        var atual: Context? = this
        while (atual is ContextWrapper) {
            if (atual is Activity) return atual
            atual = atual.baseContext
        }
        return null
    }

    /**
     * Lê default_web_client_id, que o plugin do google-services cria a partir do
     * google-services.json. Buscado por nome para o app compilar mesmo sem o arquivo.
     */
    private fun idClienteWeb(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id).takeIf { it.isNotBlank() } else null
    }

    val googleDisponivel: Boolean get() = disponivel && idClienteWeb() != null

    // ================================================================== Google (clássico)

    /**
     * Caminho reserva: o seletor de contas antigo (GoogleSignIn), que abre como uma tela
     * normal do Android. Alguns aparelhos (Xiaomi/HyperOS, por exemplo) não exibem a folha
     * do Credential Manager e o login fica girando para sempre — aí usamos este.
     *
     * Devolve o Intent que a tela deve abrir com um ActivityResultLauncher.
     */
    fun intentGoogleClassico(contextoDaTela: Context): Intent {
        val clienteWeb = idClienteWeb() ?: error(
            "Login com Google não configurado: falta o google-services.json com o cliente web."
        )
        val tela = contextoDaTela.comoActivity()
            ?: error("Não foi possível abrir o seletor de contas do Google")
        val opcoes = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clienteWeb)
            .requestEmail()
            .build()
        val cliente = GoogleSignIn.getClient(tela, opcoes)
        // signOut antes de abrir: garante que o seletor de contas apareça toda vez,
        // em vez de reentrar calado numa conta que o motorista talvez não queira usar.
        runCatching { cliente.signOut() }
        return cliente.signInIntent
    }

    /** Conclui o caminho clássico com o resultado devolvido pela tela do Google. */
    suspend fun concluirGoogleClassico(dados: Intent?) {
        val a = auth ?: error(SEM_FIREBASE)
        if (dados == null) error("Login com Google cancelado")
        val conta = GoogleSignIn.getSignedInAccountFromIntent(dados).esperar()
        val token = conta.idToken ?: error(
            "O Google não devolveu o token. Confira se o SHA-1 deste APK está cadastrado no Firebase."
        )
        a.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).esperar()
    }

    // ================================================================== sair

    fun sair() {
        auth?.signOut()
        _usuario.value = null
    }

    private fun converter(u: com.google.firebase.auth.FirebaseUser) =
        Usuario(uid = u.uid, email = u.email, nome = u.displayName)

    /** Mensagem amigável para os erros mais comuns do Firebase. */
    fun mensagemDeErro(e: Throwable): String {
        Log.w(TAG, "Falha no login", e)
        if (e is TimeoutCancellationException) {
            return "Demorou demais e foi cancelado. Confira a internet e tente de novo."
        }
        if (e is GetCredentialCancellationException) return "Login com Google cancelado"
        if (e is ApiException) return when (e.statusCode) {
            12501 -> "Login com Google cancelado"
            7 -> "Sem internet no momento"
            10 -> "O Google recusou: o SHA-1 deste APK não está cadastrado no Firebase " +
                "(Firebase > Configurações do projeto > seu app Android > Adicionar impressão digital)."
            12500 -> "O Google Play Services deste celular recusou o login. Atualize o Google Play Services."
            else -> "O Google recusou o login (código ${e.statusCode})"
        }
        if (e is NoCredentialException) {
            return "Nenhuma conta Google disponível. Confira se há conta Google no celular e se o " +
                "SHA-1 está cadastrado no Firebase."
        }
        if (e is GetCredentialException) return "Google recusou o login: ${e.type}"
        val texto = e.message ?: return "Não foi possível entrar"
        return when {
            texto.contains("password is invalid", true) ||
                texto.contains("INVALID_LOGIN_CREDENTIALS", true) ||
                texto.contains("credential is incorrect", true) -> "Email ou senha incorretos"
            texto.contains("no user record", true) -> "Não existe conta com esse email"
            texto.contains("email address is already in use", true) -> "Já existe uma conta com esse email"
            texto.contains("badly formatted", true) -> "Email inválido"
            texto.contains("WEAK_PASSWORD", true) -> "Senha fraca: use pelo menos 6 caracteres"
            texto.contains("network error", true) ||
                texto.contains("UnknownHost", true) -> "Sem internet no momento"
            texto.contains("blocked all requests", true) -> "Muitas tentativas. Tente de novo mais tarde"
            else -> texto
        }
    }

    private companion object {
        const val TAG = "MotoristaPro"
        const val SEM_FIREBASE = "Firebase não configurado neste app"
    }
}
