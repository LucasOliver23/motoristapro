# Atualização automática — sem abrir o Android Studio

Depois de configurar (uma vez só, ~20 minutos), o ciclo passa a ser:

```
você muda o código  →  envia pro GitHub  →  o GitHub compila e assina o APK sozinho
                                         →  o celular vê a versão nova e se atualiza
```

Nenhum APK gerado à mão. Nenhum campo editado no Firebase. Nenhum Android Studio.

---

## ⚠️ Leia antes de começar

O APK que está no seu celular hoje foi assinado com a **chave de depuração** do seu
Android Studio. O APK do GitHub vai usar uma **chave nova, sua**. O Android não deixa
trocar a assinatura de um app instalado — ele recusa com "app não instalado".

Então, uma única vez:

1. No app atual: **Mais > Backup e exportação > Fazer backup** (salve o arquivo).
2. **Desinstale** o MotoristaPro.
3. Instale o APK que o GitHub gerar.
4. Restaure o backup (ou simplesmente entre na sua conta — a nuvem traz tudo de volta).

Dessa vez em diante, nunca mais. A chave nova serve para sempre — **desde que você
guarde o arquivo `motoristapro.jks`**. Perdeu a chave, perdeu a capacidade de atualizar.
Copie para um pendrive e para o Google Drive.

---

## Configuração (uma vez só)

### 1. Criar a chave de assinatura

Na pasta do projeto, entre em `ferramentas\` e dê **dois cliques** em
`Criar_Chave_De_Assinatura.bat`.

Ele pede uma senha (escolha uma e **anote**) e alguns dados — no "nome e sobrenome"
pode escrever `MotoristaPro`, o resto pode deixar em branco e confirmar com `sim`.

No fim ficam dois arquivos nessa pasta:

| Arquivo | O que fazer |
|---|---|
| `motoristapro.jks` | **Guardar em lugar seguro.** Nunca vai pro GitHub. |
| `KEYSTORE_BASE64.txt` | O texto de dentro vira um segredo no GitHub (passo 4). |

### 2. Criar a conta e o repositório no GitHub

1. Crie a conta grátis em <https://github.com>.
2. **New repository** → nome: `motoristapro` → **Public** → **Create repository**.

> **Por que público?** O celular baixa o APK sem fazer login, e isso só funciona em
> repositório público. Seu `google-services.json` e sua chave de assinatura **não**
> vão para lá — ficam como segredos (o `.gitignore` já bloqueia os dois).

### 3. Enviar o projeto

O jeito mais fácil, sem comando nenhum:

1. Baixe o **GitHub Desktop**: <https://desktop.github.com>
2. **File > Add local repository** → escolha a pasta `MotoristaPro`.
3. Ele pergunta se quer criar um repositório — aceite (**create a repository**).
4. Escreva qualquer coisa em *Summary* → **Commit to main** → **Publish repository**
   (desmarque "Keep this code private").

### 4. Cadastrar os segredos

No GitHub, no seu repositório:
**Settings > Secrets and variables > Actions > New repository secret**

Crie cinco, um por um:

| Nome do segredo | Valor |
|---|---|
| `KEYSTORE_BASE64` | todo o texto de `KEYSTORE_BASE64.txt` |
| `KEYSTORE_SENHA` | a senha que você digitou no passo 1 |
| `KEY_SENHA` | a mesma senha |
| `KEY_ALIAS` | `motoristapro` |
| `GOOGLE_SERVICES_JSON` | todo o conteúdo do seu `app/google-services.json` |

> Para o último: abra o `google-services.json` no Bloco de Notas, `Ctrl+A`, `Ctrl+C`, cole.

### 5. Dizer ao app onde procurar

Já está feito neste projeto — o arquivo `app/src/main/res/values/atualizacao.xml` vem com:

```xml
<string name="repo_github">LucasOliver23/motoristapro</string>
```

Só mexa aqui se um dia mudar o nome do usuário ou do repositório. O valor é exatamente
o que aparece no endereço do repositório, depois de `github.com/`.

### 6. Pronto — veja compilando

No GitHub, aba **Actions**. Vai ter um item "Gerar APK" rodando. Leva uns 5 minutos.
Quando terminar com ✅, vá em **Releases** (coluna da direita): está lá o
`MotoristaPro-1.8.0.apk`.

Baixe esse APK no celular, desinstale o antigo e instale este. **É o último APK que
você instala à mão.**

---

## A partir de agora: lançar uma versão nova

1. Em `app/build.gradle.kts`, suba os dois números:

   ```kotlin
   versionCode = 15          // sempre +1 — é o que o app compara
   versionName = "1.8.1"     // o que o motorista vê
   ```

2. GitHub Desktop: **Commit to main** → **Push origin**.
3. Espere uns 5 minutos.
4. No celular: **Mais > Versão do app > Procurar atualização** → **Baixar e instalar**.

Só isso. Três cliques.

> Se esquecer de subir o `versionCode`, o GitHub compila mas o celular ignora
> (ele só atualiza quando o número publicado é **maior** que o instalado).

---

## Como o app descobre a versão nova

O app lê este endereço, que o GitHub mantém sempre apontando para a Release mais recente:

```
https://github.com/<usuario>/motoristapro/releases/latest/download/versao.json
```

O arquivo é montado pelo próprio robô do GitHub a cada build:

```json
{
  "versionCode": 14,
  "versionName": "1.8.0",
  "url": "https://github.com/.../releases/download/v1.8.0/MotoristaPro-1.8.0.apk",
  "notas": "mensagem do seu último commit",
  "obrigatoria": false
}
```

Se o GitHub estiver fora do ar ou não configurado, o app cai no documento
`app/versao` do Firestore (o jeito antigo, manual) — a regra de leitura dele
continua no `docs/firebase.md`.

---

## No celular, uma vez

Na primeira atualização o Android pede para liberar **"Instalar apps desconhecidos"**
para o MotoristaPro. O app já abre essa tela sozinho — é só ligar e tocar de novo
em atualizar.

O Android **sempre** mostra a tela de confirmação da instalação. Isso é proteção do
sistema: nenhum app instala outro escondido. O que a gente eliminou foi o trabalho de
procurar, baixar e passar o APK na mão.

---

## Quando der errado

| O que acontece | Causa provável |
|---|---|
| Actions fica ❌ vermelho no passo "Compilar" | Abra o log e procure a linha `error:`. Costuma ser erro de código Kotlin. |
| ❌ já no passo "Restaurar a chave" | Falta o segredo `KEYSTORE_BASE64`, ou ele foi colado pela metade. |
| "app não instalado" no celular | Assinatura diferente da instalada: desinstale o app e instale o APK novo. |
| "Você já está na versão mais recente" sem parar | O `versionCode` não foi aumentado, ou o `repo_github` está errado. |
| App não acha nada e nem dá erro | Repositório privado, ou ainda não existe nenhuma Release. |
