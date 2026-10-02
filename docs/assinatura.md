# Teste grátis e assinatura

14 dias de teste a partir do primeiro login. Acabou e não assinou, só a tela de
assinatura abre. Planos: **R$ 14,90/mês** e **R$ 39,90/trimestre** (R$ 13,30 por mês).

Os preços e os links de pagamento ficam **na nuvem**, não no app: mudar de preço
não exige publicar versão nova.

---

## Por que não é a cobrança do Google

O Google Play Billing só funciona em app **instalado pela Play Store**. O
MotoristaPro é instalado por APK, direto do GitHub. Então a cobrança sai pelo
Mercado Pago, no navegador, e o servidor libera a conta quando o dinheiro entra.

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

### 3. Os dois planos de assinatura

No painel do Mercado Pago → **Assinaturas** → **Criar plano**:

| Campo | Mensal | Trimestral |
|---|---|---|
| Nome (motivo) | `MotoristaPro Mensal` | `MotoristaPro Trimestral` |
| Valor | 14,90 | 39,90 |
| Frequência | 1 mês | 3 meses |

A palavra **mensal** ou **trimestral** tem que aparecer no nome — é por ela que o
servidor sabe quantos meses liberar.

Copie o **link de pagamento** de cada um.

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

No fim, o terminal mostra o endereço da função, algo como:

```
https://southamerica-east1-motoristapro-xxxx.cloudfunctions.net/webhookMercadoPago
```

Copie esse endereço.

### Avisar o Mercado Pago

Painel do Mercado Pago → sua aplicação → **Webhooks** → **Configurar notificações**:

- URL: o endereço que você copiou
- Eventos: marque **Pagamentos** e **Assinaturas**

---

## Ligar os preços no app

No **Firestore** (console do Firebase → Firestore Database), crie a coleção
`config` com o documento `precos`:

| Campo | Tipo | Valor |
|---|---|---|
| `mensal_centavos` | number | `1490` |
| `mensal_link` | string | link do plano mensal |
| `trimestral_centavos` | number | `3990` |
| `trimestral_link` | string | link do plano trimestral |

Valores em **centavos**: R$ 14,90 é `1490`. Mudou de ideia no preço? Edite aqui e
todo mundo vê na hora, sem atualizar o app.

---

## Testar antes de divulgar

1. Crie uma conta de teste no app com outro e-mail.
2. Em `assinaturas/{uid}` no Firestore, mude `trialInicio` para 20 dias atrás.
   O app deve travar na tela de assinatura em segundos.
3. Pague de verdade, com o valor mais baixo que der (dá para criar um plano de
   R$ 1,00 só para o teste e apagar depois).
4. Confira que `assinaturaAte` apareceu no documento e que o app destravou.
5. Em `pagamentos`, confira que o recibo entrou com `situacao: OK`.

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
- **Entrar com outra conta no mesmo celular.** Ganha outro teste, sim — isso
  nenhum app resolve sem pedir documento. Se virar problema, dá para passar a
  contar o teste também pelo identificador do aparelho.

---

## Lembre

Não sou advogado nem contador. Antes de cobrar de verdade, vale conferir com um
contador como declarar essa receita e o que o Mercado Pago retém.
