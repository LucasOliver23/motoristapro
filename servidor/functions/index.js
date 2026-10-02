/**
 * Assinatura do MotoristaPro.
 *
 * Duas funcoes:
 *   webhookMercadoPago  — o Mercado Pago avisa aqui quando um pagamento entra;
 *                         a funcao confere com a API deles e grava a validade.
 *   conferirAssinatura  — o app chama para forcar uma reconferencia ("Ja paguei").
 *
 * Por que o app nao grava a validade sozinho: qualquer um conseguiria editar o
 * proprio documento e ganhar o app de graca. As regras do Firestore proibem o
 * app de escrever `assinaturaAte`; aqui usamos a chave de administrador, que
 * passa por cima das regras — e esta funcao so acredita no que a API do Mercado
 * Pago responde, nunca no corpo do aviso recebido (que qualquer um pode forjar).
 */

const { onRequest, onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/** Token de producao do Mercado Pago. Guardado como segredo, nunca no codigo. */
const TOKEN_MP = defineSecret("MP_ACCESS_TOKEN");

const API = "https://api.mercadopago.com";

/** Quantos meses cada plano vale. A chave e o `reason`/`external_reference` do plano. */
const MESES_POR_PLANO = { mensal: 1, trimestral: 3 };

// ---------------------------------------------------------------- utilidades

async function mp(caminho, token) {
  const resposta = await fetch(`${API}${caminho}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!resposta.ok) {
    throw new Error(`Mercado Pago ${resposta.status} em ${caminho}`);
  }
  return resposta.json();
}

/**
 * Descobre de quem e o pagamento.
 *
 * Primeiro pelo external_reference (o uid que o app poe no link). Se vier vazio
 * — acontece quando o motorista abre o link do plano por fora do app — cai no
 * e-mail do pagador, que o app guarda no documento da assinatura.
 */
async function acharUid(externalReference, email) {
  if (externalReference) {
    const doc = await db.collection("assinaturas").doc(externalReference).get();
    if (doc.exists) return externalReference;
    logger.warn("external_reference sem documento", { externalReference });
  }
  if (email) {
    const achados = await db
      .collection("assinaturas")
      .where("email", "==", String(email).toLowerCase().trim())
      .limit(2)
      .get();
    if (achados.size === 1) return achados.docs[0].id;
    if (achados.size > 1) logger.warn("e-mail em mais de uma conta", { email });
  }
  return null;
}

/**
 * Soma meses a partir de HOJE ou do fim da assinatura atual, o que for maior.
 * Renovar antes de vencer nao pode fazer o motorista perder os dias que faltavam.
 */
function novaValidade(validadeAtualMs, meses) {
  const agora = Date.now();
  const base = validadeAtualMs && validadeAtualMs > agora ? validadeAtualMs : agora;
  const data = new Date(base);
  data.setMonth(data.getMonth() + meses);
  return data;
}

async function liberar(uid, plano, meses, origem) {
  const ref = db.collection("assinaturas").doc(uid);
  const atual = await ref.get();
  const atualMs = atual.exists && atual.get("assinaturaAte")
    ? atual.get("assinaturaAte").toDate().getTime()
    : 0;

  const ate = novaValidade(atualMs, meses);
  await ref.set(
    {
      assinaturaAte: admin.firestore.Timestamp.fromDate(ate),
      plano,
      atualizadaEm: admin.firestore.FieldValue.serverTimestamp(),
      origem,
    },
    { merge: true }
  );
  logger.info("assinatura liberada", { uid, plano, ate: ate.toISOString() });
  return ate;
}

// ---------------------------------------------------------------- webhook

exports.webhookMercadoPago = onRequest(
  { secrets: [TOKEN_MP], region: "southamerica-east1", cors: false },
  async (req, res) => {
    // O Mercado Pago reenvia o aviso ate receber 200. Responder 200 mesmo em
    // erro nosso faria o pagamento sumir calado, entao: 200 so quando tratamos.
    try {
      const token = TOKEN_MP.value();
      const tipo = req.body?.type || req.query?.type || req.body?.topic;
      const id = req.body?.data?.id || req.query?.["data.id"] || req.query?.id;

      if (!id) {
        logger.info("aviso sem id, ignorado", { body: req.body, query: req.query });
        res.status(200).send("sem id");
        return;
      }

      let externalReference = null;
      let email = null;
      let plano = null;
      let pago = false;

      if (tipo === "payment") {
        // Pagamento avulso (inclui a primeira cobranca de uma assinatura).
        const pagamento = await mp(`/v1/payments/${id}`, token);
        pago = pagamento.status === "approved";
        externalReference = pagamento.external_reference;
        email = pagamento.payer?.email;
        plano = (pagamento.metadata?.plano || pagamento.description || "").toLowerCase();
      } else if (tipo === "subscription_preapproval" || tipo === "preapproval") {
        // Assinatura recorrente: vale enquanto o status for "authorized".
        const assinatura = await mp(`/preapproval/${id}`, token);
        pago = assinatura.status === "authorized";
        externalReference = assinatura.external_reference;
        email = assinatura.payer_email;
        plano = (assinatura.reason || "").toLowerCase();
      } else {
        logger.info("tipo de aviso ignorado", { tipo });
        res.status(200).send("tipo ignorado");
        return;
      }

      if (!pago) {
        logger.info("pagamento ainda nao aprovado", { id, tipo });
        res.status(200).send("nao aprovado");
        return;
      }

      const chavePlano = Object.keys(MESES_POR_PLANO).find((p) => plano.includes(p)) || "mensal";
      const uid = await acharUid(externalReference, email);

      if (!uid) {
        // Guardado para voce ligar a mao, em vez de o dinheiro entrar e sumir.
        await db.collection("pagamentos").doc(String(id)).set({
          recebidoEm: admin.firestore.FieldValue.serverTimestamp(),
          tipo,
          externalReference: externalReference || null,
          email: email || null,
          plano: chavePlano,
          situacao: "SEM_DONO",
        });
        logger.error("pagamento sem dono", { id, externalReference, email });
        res.status(200).send("sem dono, guardado");
        return;
      }

      const ate = await liberar(uid, chavePlano, MESES_POR_PLANO[chavePlano], `mp:${tipo}:${id}`);

      await db.collection("pagamentos").doc(String(id)).set({
        recebidoEm: admin.firestore.FieldValue.serverTimestamp(),
        tipo,
        uid,
        email: email || null,
        plano: chavePlano,
        validoAte: admin.firestore.Timestamp.fromDate(ate),
        situacao: "OK",
      });

      res.status(200).send("ok");
    } catch (erro) {
      logger.error("falha no webhook", erro);
      // 500 faz o Mercado Pago tentar de novo — e o que queremos num erro nosso.
      res.status(500).send("erro");
    }
  }
);

// ---------------------------------------------------------------- "Ja paguei"

/**
 * Chamado pelo app quando o motorista volta do navegador e nada destravou.
 * Procura na API do Mercado Pago um pagamento aprovado com o uid dele.
 */
exports.conferirAssinatura = onCall(
  { secrets: [TOKEN_MP], region: "southamerica-east1" },
  async (req) => {
    const uid = req.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Entre na sua conta");

    const token = TOKEN_MP.value();
    const busca = await mp(
      `/v1/payments/search?external_reference=${encodeURIComponent(uid)}&status=approved&sort=date_created&criteria=desc&limit=1`,
      token
    );
    const pagamento = busca.results?.[0];
    if (!pagamento) return { liberado: false };

    const plano = (pagamento.metadata?.plano || pagamento.description || "").toLowerCase();
    const chave = Object.keys(MESES_POR_PLANO).find((p) => plano.includes(p)) || "mensal";
    const ate = await liberar(uid, chave, MESES_POR_PLANO[chave], `conferencia:${pagamento.id}`);
    return { liberado: true, ate: ate.toISOString(), plano: chave };
  }
);
