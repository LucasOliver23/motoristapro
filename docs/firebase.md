# Ligar o login e a nuvem (Firebase)

Enquanto o arquivo `app/google-services.json` **não existir**, o app compila e funciona
normalmente, só com os dados no celular e **sem tela de login**. Assim que o arquivo
entrar, o login e o backup na nuvem ligam sozinhos.

---

## ⚠️ Antes de tudo: faça um backup

Nesta versão o nome do pacote mudou de `com.motoristapro.debug` para `com.motoristapro`
(um pacote só, para simplificar o cadastro no Firebase). Para o Android, isso é **outro app**:
a versão nova instala do zero, com o banco vazio.

1. No app que está instalado: **Mais > Fazer backup completo** e salve o arquivo (Drive é o ideal).
2. Depois de instalar a versão nova: **Mais > Restaurar backup** e escolha esse arquivo.
3. Aí sim pode desinstalar o app antigo ("MotoristaPro" duplicado na lista).

---

## 1. Criar o projeto

1. Abra <https://console.firebase.google.com> e entre com sua conta Google.
2. **Adicionar projeto** → nome `MotoristaPro` → pode **desativar** o Google Analytics
   (não é usado) → Criar.

## 2. Registrar o app Android

No painel do projeto, clique no ícone do **Android**:

- **Nome do pacote:** `com.motoristapro` (exatamente assim)
- **Apelido:** MotoristaPro
- **SHA-1:** obrigatório para o login com Google. Como pegar no Windows:

  Abra o **Prompt de Comando** e cole (tudo numa linha só):

  ```
  "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
  ```

  Copie a linha que começa com `SHA1:` e cole no Firebase.
  Se o caminho do Android Studio for outro, ajuste; o arquivo `keytool.exe` fica dentro de `jbr\bin`.

Clique em **Registrar app** → **Baixar google-services.json**.

## 3. Colocar o arquivo no projeto

Copie o `google-services.json` para dentro da pasta **`app`** do projeto:

```
MotoristaPro\app\google-services.json
```

Não é na raiz: é dentro de `app`, ao lado do `build.gradle.kts`.

## 4. Ligar as formas de entrar

No Firebase: **Criação (Build) > Authentication > Vamos começar > Sign-in method**

- Ative **Email/senha**.
- Ative **Google** (escolha um email de suporte) → Salvar.

## 5. Criar o banco da nuvem

**Criação (Build) > Firestore Database > Criar banco de dados**

- Modo: **produção**
- Local: `southamerica-east1` (São Paulo)

Depois abra a aba **Regras**, apague tudo e cole isto:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Cada motorista só enxerga e grava o próprio documento.
    match /usuarios/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    // Versão publicada do app (atualização automática): qualquer um lê, ninguém grava.
    match /app/{doc} {
      allow read: if true;
      allow write: if false;
    }
  }
}
```

Clique em **Publicar**. Sem essa regra, ninguém consegue salvar nada.

## 6. Compilar

No Android Studio: **File > Sync Project with Gradle Files**, depois **Build > Build APK(s)**.
Ao abrir, o app vai pedir login.

---

## Como funciona a nuvem

- Ao entrar na conta, o app **envia** uma cópia dos dados deste celular para `usuarios/{uid}`.
- Se o celular estiver **vazio** e houver backup na conta (celular novo), ele **baixa** sozinho.
- Em **Mais > Minha conta** existem os botões **Enviar agora** e **Baixar da nuvem**.
  "Baixar da nuvem" **substitui** os dados do celular: use ao trocar de aparelho.
- Sobem: corridas, despesas, plataformas, jornadas, custos fixos e configurações.
  O histórico de ofertas fica só no celular (é grande e serve para o dia a dia).
- Os dados vão compactados num único documento, bem abaixo do limite de 1 MB do Firestore.

## Se der erro

| Mensagem | O que fazer |
|---|---|
| `No matching client found for package name` no build | O `google-services.json` é de outro pacote. Registre `com.motoristapro` no Firebase e baixe de novo. |
| "Nenhuma conta Google disponível" | O SHA-1 não está cadastrado, ou o login com Google não foi ativado no Firebase. |
| "Email ou senha incorretos" ao criar conta | Ative **Email/senha** em Authentication > Sign-in method. |
| `PERMISSION_DENIED` ao sincronizar | As regras do Firestore não foram publicadas (passo 5). |
