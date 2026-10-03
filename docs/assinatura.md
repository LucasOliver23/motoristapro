# Teste grátis e assinatura

14 dias de teste a partir do primeiro login. Acabou e não assinou, só a tela de
assinatura abre. Planos: **R$ 14,90/mês** e **R$ 39,90/trimestre** (R$ 13,30 por mês).

Os preços ficam **na nuvem**, não no app: mudar de preço não exige publicar
versão nova.

---

## Como o dinheiro chega na conta certa

O caminho tem um detalhe que é o coração de tudo:

1. O motorista toca em **Assinar**.
2. O **app pede ao servidor** um link de pagamento.
3. A função `criarCheckout` cria o link no Mercado Pago **com o id da conta
   grudado no pagamento** (`external_reference`) e devolve o endereço.
4. O app abre esse endereço no navegador. Pix, cartão ou boleto.
5. O Mercado Pago avisa a função `webhookMercadoPago`, que **confere com a API
   deles** (nunca acredita no aviso recebido, que qualquer um pode forjar) e
   grava `assinaturaAte` no documento da pessoa.
6. O app está ouvindo esse documento e destrava sozinho. Por Pix, segundos.

**Por que o link não pode ser fixo.** A primeira versão guardava um link de
plano na nuvem e o app colava `?external_reference=UID` no fim. Dois problemas:
o link de plano do Mercado Pago já vem com `?`, e — pior — a documentação deles
não garante que um parâmetro posto na URL chegue no pagamento. Sem o id, o
dinheiro entra e o servidor não sabe de quem é: cai em `pagamentos` com
`situacao: SEM_DONO` para você liberar à mão. Por isso quem cria o link é o
servidor.

**Por que pagamento avulso e não assinatura recorrente.** Para cobrar sozinho
todo mês, o Mercado Pago exige `card_token_id` — ou seja, formulário de cartão
dentro do app — e não aceita Pix recorrente. Como o Pix é o que o motorista
brasileiro usa, cada pagamento vale 1 ou 3 meses e o app avisa na faixa do topo
quando faltam 5 dias para vencer.

### Por que não é a cobrança do Google

O Google Play Billing só funciona em app **instalado pela Play Store**. O
MotoristaPro é instalado por APK, direto do GitHub.

---

## O que você precisa criar (uma vez)

### 1. Firebase no plano Blaze

As funções do servidor só rodam no plano **Blaze** (paga por uso). Para o volume
de um app pequeno, o uso cai quase todo dentro da cota gratuita — mas é um cartão
cadastrado, então ponha um **alerta de orçamento**:

1. <https://console.firebase.google.com> → seu projeto → engrenagem → **Uso e faturamento**
2. **Detalhes e configurações** → **Modificar plano** → Blaze
3. Em **Orçamentos e alertas**, crie um alerta em R$ 20/mês. Não corta nada
   sozinho, só te avisa por e-mail.

### 2. Conta do Mercado Pago

1. <https://www.mercadopago.com.br> → crie a conta (ou use a sua)
2. <https://www.mercadopago.com.br/developers/panel> → **Suas integrações** →
   **Criar aplicação** → nome `MotoristaPro`, tipo **Pagamentos online**
3. Dentro dela, **Credenciais de produção** → copie o **Access Token**
   (começa com `APP_USR-`). **Esse token é a chave do seu dinheiro: não mande
   para ninguém, não cole em arquivo do projeto, não suba para o GitHub.**

Não precisa criar plano de assinatura nenhum no painel. Quem monta a cobrança é
a função, com o preço que estiver na nuvem.

---

## Instalar o servidor

No computador, dentro da pasta do projeto:

```bash
npm install -g firebase-tools
firebase login
cd servidor
firebase use --add            # escolha o projeto MotoristaPro
cd functions && npm install && cd ..

# guarda o token do Mercado Pago em cofre, fora do código
firebase functions:secrets:set MP_ACCESS_TOKEN
# cole o APP_USR-... quando pedir

firebase deploy --only functions,firestore:rules
```

Sobem três funções:

| Função | Para quê |
|---|---|
| `criarCheckout` | o app pede o link de pagamento |
| `webhookMercadoPago` | o Mercado Pago avisa que o dinheiro entrou |
| `conferirAssinatura` | o botão "Já paguei — conferir agora" |

Todas em **southamerica-east1**. Se você mudar a região no `index.js`, mude
também a constante `REGIAO` em `AssinaturaManager.kt`, senão o app chama um
endereço que não existe.

### Avisar o Mercado Pago (rede de segurança)

A função já manda o endereço do webhook dentro de cada cobrança que cria, então
isso funciona sem você configurar nada. Ainda assim vale cadastrar no painel,
que é o que cobre renovação e estorno:

Painel do Mercado Pago → sua aplicação → **Webhooks** → **Configurar notificações**:

- URL: `https://southamerica-east1-SEU-PROJETO.cloudfunctions.net/webhookMercadoPago`
- Eventos: marque **Pagamentos**

O endereço exato aparece no terminal no fim do `firebase deploy`.

---

## Ligar os preços

No **Firestore** (console do Firebase → Firestore Database), crie a coleção
`config` com o documento `precos`:

| Campo | Tipo | Valor |
|---|---|---|
| `mensal_centavos` | number | `1490` |
| `trimestral_centavos` | number | `3990` |

Valores em **centavos**: R$ 14,90 é `1490`. Mudou de ideia no preço? Edite aqui e
todo mundo vê na hora, sem atualizar o app. Se o documento não existir, a função
usa 1490 e 3990 como rede de segurança — mas aí a TELA do app mostra os preços
padrão dela, que podem ficar diferentes do que você cobra. Crie o documento.

---

## Testar antes de divulgar

1. Crie uma conta de teste no app com outro e-mail.
2. Em `assinaturas/{uid}` no Firestore, mude `trialInicio` para 20 dias atrás.
   O app deve travar na tela de assinatura em segundos.
3. Baixe o preço para `100` (R$ 1,00) em `config/precos` e pague **por Pix**, de
   verdade, com outra conta.
4. Confira, em ordem:
   - nos **Logs** do Firebase, `checkout criado` com o seu uid;
   - em `assinaturas/{uid}`, o campo `assinaturaAte` apareceu;
   - em `pagamentos`, o recibo entrou com `situacao: OK`;
   - o app destravou sozinho, sem você tocar em nada.
5. Volte o preço para `1490` e `3990`.

Se o passo 4 travar, aperte **"Já paguei — conferir agora"** na tela de
assinatura: ele chama `conferirAssinatura`, que procura o pagamento na API do
Mercado Pago pelo seu uid e libera na hora. Esse botão é a saída de emergência
quando o aviso do Mercado Pago falha.

---

## Quando alguém paga e não destrava

O servidor não joga dinheiro fora: todo pagamento que ele não conseguiu ligar a
uma conta vira um documento na coleção `pagamentos` com `situacao: SEM_DONO`,
guardando o e-mail do pagador.

Para liberar à mão: abra `assinaturas/{uid}` da pessoa e crie o campo
`assinaturaAte` do tipo **timestamp**, com a data de vencimento. O app destrava
em segundos.

---

## O que o motorista não consegue burlar

- **Mudar a data do celular.** O início do teste é carimbado pelo servidor, e o
  app guarda o último carimbo que viu — atrasar o relógio não devolve dias.
- **Editar a própria assinatura.** As regras do Firestore deixam o app criar o
  documento e carimbar que abriu hoje. `assinaturaAte` e `plano` só a função do
  servidor escreve.
- **Reinstalar o app.** O teste é da conta, não do aparelho.
- **Forjar o aviso de pagamento.** O webhook não acredita no corpo do aviso:
  ele pega o id e vai perguntar à API do Mercado Pago se aquilo foi aprovado.
- **Entrar com outra conta no mesmo celular.** Ganha outro teste, sim — isso
  nenhum app resolve sem pedir documento. Se virar problema, dá para passar a
  contar o teste também pelo identificador do aparelho.

Renovar antes de vencer não perde dias: a função soma os meses a partir do fim
da assinatura atual, não de hoje.

---

## Lembre

Não sou advogado nem contador. Antes de cobrar de verdade, vale conferir com um
contador como declarar essa receita e o que o Mercado Pago retém.
