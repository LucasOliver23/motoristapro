# MotoristaPro

App Android para **motorista e entregador de aplicativo** (Uber, 99, iFood e inDrive) que lê a oferta de corrida na tela, mostra na hora se ela compensa — com o custo real do veículo — e controla o dia de trabalho: lucro, jornada, metas, km, manutenção e relatórios.

Versão atual: **5.0.0** (banco de dados v14) · Android 8.0+ (minSdk 26, targetSdk 35) · Kotlin + Jetpack Compose.

---

## Sumário

- [O que o app faz](#o-que-o-app-faz)
- [Como o leitor de ofertas funciona](#como-o-leitor-de-ofertas-funciona)
- [Telas](#telas)
- [Tecnologia](#tecnologia)
- [Estrutura do código](#estrutura-do-código)
- [Banco de dados](#banco-de-dados)
- [Compilar e testar](#compilar-e-testar)
- [Publicar uma versão (CI/CD)](#publicar-uma-versão-cicd)
- [Firebase: login, nuvem e assinatura](#firebase-login-nuvem-e-assinatura)
- [Instalar e configurar no celular](#instalar-e-configurar-no-celular)
- [Problemas comuns](#problemas-comuns)
- [Privacidade e segurança](#privacidade-e-segurança)
- [Próximos passos](#próximos-passos)

---

## O que o app faz

| Área | Recursos |
|---|---|
| **Leitor de ofertas** | Lê a oferta na tela da Uber, 99, iFood e inDrive (texto e, quando o app esconde o texto, **imagem/OCR**). Cartão flutuante com R$/km, R$/h, lucro e a etiqueta **ACEITAR / ATENÇÃO / RECUSAR**. Também lê **foto de oferta** aberta no WhatsApp, Facebook, Instagram, Telegram e Galeria. |
| **Testar com print** | Escolha (ou compartilhe do WhatsApp/Galeria) o print de uma oferta da Uber, 99, iFood ou inDrive: passa pela mesma regra do leitor e mostra a etiqueta, os números e, se não leu, o porquê. "Copiar textos" troca os endereços para o print virar teste automático. |
| **Semáforo** | Desde a 4.16 a cor vem do **lucro por hora** da corrida contra a meta limpa por hora (já contando a espera), com "estimado" enquanto o app não mediu o motorista. As faixas de R$/km, R$/hora, nota, valor e lucro mínimo viraram **Faixas avançadas** (trava a mais). |
| **Registro de corridas** | Botão "Registrar" no cartão e na notificação; leitura da tela **Detalhes da corrida** da 99 (valor final, já com o pacote 0%); registro manual. |
| **Jornada** | Cronômetro do turno, km pelo GPS, pausas, tempo por estado e por app. Pergunta o **km do painel** ao iniciar e ao encerrar (pega o uso fora do app). |
| **Lucro e metas** | Lucro líquido do dia (corridas − despesas − custos fixos), meta diária/semanal/mensal, **meta por hora** com a conta aberta, previsão de horário em que a meta bate. |
| **Custo real do veículo** | **Assistente rápido obrigatório** (4.17: veículo, combustível, o que paga pelo veículo, rotina e meta limpa por hora, em 1 minuto) → custo **básico**. O assistente completo (IPVA, seguro, depreciação, óleo, pneus, revisão) deixa o custo **completo**. Carro, moto e **bike**. |
| **Pacote 0% de taxa da 99** | Lança o preço do pacote como despesa e mostra se valeu a pena no dia. |
| **Vários veículos** | Moto e carro, cada um com km, foto, custo por km, manutenção e lucro do mês. O veículo **ativo** recebe o que for lançado. |
| **Manutenção** | Itens por km (óleo, relação, pneu, freio…), anel de progresso, **EM DIA / ATENÇÃO / ATRASADA**, lembrete na barra do celular com "Fiz agora" / "Lembrar amanhã". |
| **Análises** | Melhores horários (dia × faixa de 2 h), **Comparar apps** (R$/h, R$/km, lucro, % de aceite), mapa de calor, gráficos por período. |
| **Relatório do mês em PDF** | Ganhos por app, despesas, custos fixos, lucro, km e horas, com a lista do mês — para imposto, banco ou financiamento. |
| **Conta e nuvem** | Login (e-mail/senha e Google), backup automático no Firestore, backup em arquivo e exportação CSV. |
| **Assinatura** | Teste grátis + planos mensal/trimestral pagos no Mercado Pago, liberados por função no servidor. |
| **Outros** | Bolha flutuante com o lucro do dia, widget na tela inicial, resumo às 22h, aviso por voz, endereços de risco, atualização automática do APK, checkup do app com o último erro registrado. |

---

## Como o leitor de ofertas funciona

```
App de corrida mostra a oferta
        │  (evento de acessibilidade)
        ▼
OfertaAccessibilityService ── debounce (120 ms, no máximo 400 ms) ──► lê os textos da tela
        │                                                         │
        │  app esconde o texto (ex.: card da 99)                  ▼
        ├──► LeitorOcr: print da tela (takeScreenshot) ──► ML Kit (no aparelho) ──► textos
        ▼
OfertaParser / OfertaInDrive / ResumoCorrida99  ──►  Oferta(valor, km, minutos, nota, endereços)
        ▼
LimitesOferta.classificar(custo/km, faixas)  ──►  BOA / MÉDIA / RUIM
        ▼
OverlayOferta (cartão)  +  NotificacaoOferta  +  AvisoVoz  +  histórico (tabela ofertas)
```

- **Janela própria de acessibilidade** (`TYPE_ACCESSIBILITY_OVERLAY`): não precisa da permissão "sobrepor a outros apps".
- **OCR econômico**: só quando o app não expõe texto; o "precisa de imagem" de cada app vale 10 min; sem oferta, a espera entre prints cresce de 1,2 s até 3 s e volta ao mínimo quando abre uma janela nova; falha de captura tenta no máximo 4 vezes. A cópia da imagem roda fora da thread principal.
- **Vários apps abertos**: vale o app que mexeu por último; duas ofertas iguais seguidas não viram uma só (janela de 60 s pela última vez vista).
- **Apps de foto** (WhatsApp, Instagram, Galeria…): só leitura por imagem e só com a foto em tela cheia — texto de conversa nunca vira oferta. Bancos e outros apps **não** são lidos.
- **Robustez**: toda leitura é protegida; os escopos de coroutine do leitor, da jornada e da assinatura têm `CoroutineExceptionHandler`; o último erro fica em **Menu ▸ Checkup do app** (`RegistroErros`). Se o leitor parar, o app avisa na barra de notificações.

Pacotes lidos (`OfertaAccessibilityService.PACOTES_ALVO`): `com.ubercab.driver`, `com.app99.driver`, `br.com.ifood.driver.app`, `sinet.startup.inDriver`. Ao incluir um app, atualize também `res/xml/accessibility_service_config.xml`.

---

## Telas

| Aba | Conteúdo |
|---|---|
| **Início** | Lucro líquido de hoje com a meta e o ritmo (R$/h vs meta por hora), cartão do pacote da 99, alerta de manutenção chegando, números do dia, atalhos (Semáforo, Veículo, Checkup) e o cartão da jornada. |
| **Corridas** | Histórico de ofertas lidas e corridas registradas por dia/semana/mês, filtro por app, aceitas × recusadas, endereços com "ver no mapa". |
| **Finanças** | Mês com entradas e saídas, despesas por categoria, extrato, uso do pacote de ganhos da 99 e **Relatório do mês (PDF)**. |
| **Atividade** | Resumo do período, R$ por hora conectada, mapa de calor, gráficos e e as análises **Melhores horários**, **Comparar apps** e **Relatório do mês (PDF)**, abertas ali mesmo. |
| **Menu** | 8 entradas (4.18): **Perfil**, **Leitor de ofertas** (semáforo, aparência do cartão, notificação, voz, bolha, endereços de risco, app de navegação, testar com print e — só para quem roda 99 — pacote 0%), **Meus veículos** (custos e manutenção), **Suas metas**, **Checkup**, **Conta e nuvem** (backup e planilhas), **Assinatura** e **Ajustes** (tema, resumo das 22h, versão). Voltar de uma tela de dentro leva para a de cima. |

Os desenhos aprovados de cada recurso ficam em `mockups/` (HTML + PNG).

---

## Tecnologia

| Item | Versão |
|---|---|
| Kotlin / KSP / plugin Compose | 2.0.21 / 2.0.21-1.0.28 / 2.0.21 |
| Android Gradle Plugin / Gradle | 8.7.3 / 8.9 |
| JDK | 17 |
| Jetpack Compose (BOM) + Material 3 | 2024.12.01 |
| Room (KSP) | 2.6.1 |
| Lifecycle / Coroutines | 2.8.7 / 1.8.1 |
| WorkManager | 2.9.1 |
| ML Kit Text Recognition (no aparelho) | 16.0.1 |
| Firebase (Auth, Firestore, Functions) | BoM 33.7.0 |

Sem Hilt, sem Navigation e sem version catalog: injeção manual em `MotoristaApp` e navegação por estado (`Aba`, `SubTela`).

---

## Estrutura do código

```
app/src/main/java/com/motoristapro/
  MotoristaApp.kt        injeção manual (repositório, preferências, odômetro, régua, veículos…)
  service/               leitor de ofertas: serviço de acessibilidade, parsers, OCR, cartão,
                         bolha, notificação, voz, limites do semáforo, aviso de leitor caído
  jornada/               serviço do turno (GPS em primeiro plano) e canais de notificação
  data/
    local/               Room: AppDatabase (migrações 1→14), entidades e DAOs
    repository/          FinanceiroRepository (regras de negócio) e Periodo
    prefs/               PreferenciasApp (liga/desliga, metas por hora, veículo ativo…)
    backup/              backup em arquivo e exportação CSV
  custo/                 calculadora de custo por km e meta por hora (Kotlin puro)
  veiculo/               odômetro, foto, lembrete de manutenção, troca de veículo
  horarios/ comparar/    melhores horários e comparação entre apps (Kotlin puro)
  relatorio/             relatório do mês em PDF
  pacote/                pacote 0% de taxa da 99
  nuvem/ auth/           backup no Firestore (JSON gzip) e login
  assinatura/ atualizacao/   assinatura (Mercado Pago) e atualização do APK
  diagnostico/           registro do último erro do app
  resumo/ widget/        resumo das 22h e widget da tela inicial
  ui/                    telas Compose (dashboard, corridas, financas, relatorios, mais, manutencao…)
servidor/                Cloud Functions + regras do Firestore
docs/                    passo a passo do Firebase, assinatura, atualização e schema
mockups/                 desenhos aprovados das telas
APAGAR.txt               o que mudou em cada versão e o que (não) apagar
```

**Regra da casa:** tudo que é conta fica em Kotlin puro (sem Android) e tem teste em `app/src/test` — parsers das ofertas, custo, metas, odômetro, lembrete, horários, comparação, régua do lucro por hora, backup JSON.

---

## Banco de dados

Room/SQLite, **versão 14**. Dinheiro em **centavos** (Long), distância em **metros**, duração em **segundos**, datas em **epoch millis**.

| Tabela | Para quê |
|---|---|
| `plataformas` | Uber, 99, iFood, inDrive, Particular |
| `corridas` | valor, gorjeta, km, deslocamento, duração, endereços, `veiculo_id` |
| `despesas` | categoria, valor, litros, hodômetro, `veiculo_id` |
| `jornadas` | turno, GPS, tempo por estado/app, pausas, `veiculo_id` |
| `ofertas` | histórico do leitor (180 dias) |
| `custos_fixos` | custos mensais (manuais e do assistente) |
| `configuracoes` | linha única: metas, custo/km, veículo ativo em uso, perfil |
| `perfil_custo` | respostas do assistente de custo |
| `manutencoes` | itens por km, `veiculo_id` |
| `veiculos` | veículos do motorista (v14) |

**Migrações** em `AppDatabase` (nunca usar `fallbackToDestructiveMigration`). Ao mudar uma entidade: suba `VERSAO_BANCO`, escreva a `Migration(n, n+1)`, registre em `addMigrations`, atualize `SincronizacaoDao`/`DadosJson` (backup) e o `DadosJsonTest`. Coluna nova com padrão: `@ColumnInfo(defaultValue = "…")` igual ao `DEFAULT` do `ALTER TABLE`.

Preferências simples (faixas do semáforo, km do painel, horas por dia, veículo ativo, eventos anotados) ficam em SharedPreferences para não exigir migração.

---

## Compilar e testar

```bash
./gradlew assembleDebug          # APK de depuração em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # testes de unidade (Kotlin puro)
```

Android Studio: **File ▸ Open** na pasta do projeto, JDK 17.

Sem `app/google-services.json` o app compila e roda **sem login e sem nuvem** (dados só no celular). Veja `docs/firebase.md`.

---

## Publicar uma versão (CI/CD)

```
branch de trabalho ──push──► "Verificar build" (compila + testes)
        │
        └── PR para main ──merge──► "Gerar APK": APK assinado + Release v<versão> + versao.json
                                             │
                                             └── o app vê a versão nova e se atualiza sozinho
```

Passos de cada versão:

1. Suba `versionCode` e `versionName` em `app/build.gradle.kts`.
2. Atualize `APAGAR.txt` (o que mudou, arquivos novos/alterados, se o banco mudou).
3. Envie para a branch de trabalho e espere o **Verificar build** ficar verde.
4. Abra o PR e faça o merge na `main`: o **Gerar APK** publica a Release.
5. No celular: **Menu ▸ Versão do app ▸ Procurar atualização**.

Segredos do GitHub usados pelo `apk.yml`: `GOOGLE_SERVICES_JSON`, `KEYSTORE_BASE64` e as senhas da chave. Guarde o `motoristapro.jks`: sem ele não dá para atualizar o app instalado. Detalhes em `docs/atualizacao.md`.

---

## Firebase: login, nuvem e assinatura

- **Login e backup na nuvem**: `docs/firebase.md`. Backup em `usuarios/{uid}` (JSON gzip). Celular vazio nunca sobe por cima de um backup com dados; trocar de conta guarda uma cópia local antes de zerar.
- **Assinatura (Mercado Pago)**: `docs/assinatura.md` e `servidor/functions`.
- **Regras do Firestore**: `servidor/firestore.rules` — publique sempre que mudar.
- **Radar de eventos**: saiu do app na 4.18 (dependia de alguém cadastrar os eventos de cada cidade à mão). O código está no histórico do Git (até a 4.17.0) e a regra da coleção `eventos` continua no `firestore.rules`, sem uso.

---

## Instalar e configurar no celular

1. Instale o APK da última Release e abra o app.
2. **Leitor de ofertas**: Configurações ▸ Acessibilidade ▸ Apps baixados ▸ **MotoristaPro – Leitor de ofertas** ▸ ligar. No Android 13+, se aparecer "configuração restrita": Informações do app ▸ ⋮ ▸ **Permitir configurações restritas**.
3. **Bateria sem restrições** e **inicialização automática** (Xiaomi, Samsung, Motorola) — o **Checkup do app** tem atalho para cada uma.
4. **Notificações** liberadas (aviso de leitor caído, lembretes, resumo).
5. Rode o **assistente de custo** (Menu ▸ Meu veículo e custos) e defina a **meta** e as **horas por dia** (Menu ▸ Suas metas).
6. Em **Manutenção**, informe o km do painel e cadastre os itens.

---

## Problemas comuns

| Sintoma | O que fazer |
|---|---|
| "MotoristaPro – Leitor de ofertas: Não está funcionando" | Desligar e ligar o leitor em Acessibilidade; se repetir, reiniciar o celular. Depois de **toda atualização** o Android costuma derrubar o leitor uma vez. Conferir bateria sem restrições e inicialização automática. Se cair sozinho, copiar o **Último erro do app** no Checkup. |
| Cartão não aparece na 99 | Ligar a **leitura por imagem** (Checkup) — a 99 não expõe o texto da oferta. |
| Oferta lida errada | Menu ▸ Leitor de ofertas mostra o diagnóstico da última leitura (textos e motivo). |
| Km da manutenção errado | Manutenção ▸ **Corrigir** o odômetro; o app também pergunta o km ao iniciar/encerrar a jornada. |
| Atualização não chega | Menu ▸ Versão do app ▸ Procurar atualização; conferir se o **Gerar APK** da `main` terminou. |

---

## Privacidade e segurança

- A leitura de texto e o OCR acontecem **só no aparelho** (ML Kit); nenhuma imagem da tela é enviada ou guardada.
- O leitor só olha os apps de corrida e os apps de foto listados — **não lê bancos nem outros apps**.
- O texto da tela só vai para o log na versão de depuração (`LOG_TEXTOS = BuildConfig.DEBUG`).
- Backup na nuvem só da própria conta (regras do Firestore); o histórico de ofertas não sobe.
- Assinatura confirmada pelo servidor consultando o Mercado Pago (o app não decide se pagou).
- **Play Store**: o uso de Acessibilidade exige a declaração no Play Console, divulgação em destaque no app antes de ativar e política de privacidade.

---

## Próximos passos

- Radar de eventos de volta só com fonte automática (ex.: API-Football e voos do aeroporto numa função agendada), nunca cadastro à mão.
- Tirar a varredura da árvore de acessibilidade da thread principal.
- Testes de migração do Room (`MigrationTestHelper`) e exportação do schema em `app/schemas/`.
- Publicação na Play Store.
