-- Esquema equivalente ao que o Room gera (versão 6). Referência / uso em ferramentas SQL.
-- Dinheiro em CENTAVOS, distância em METROS, duração em SEGUNDOS, datas em epoch MILLIS.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS plataformas (
    id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    nome      TEXT    NOT NULL,
    cor_hex   TEXT,
    ativa     INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS index_plataformas_nome ON plataformas (nome);

CREATE TABLE IF NOT EXISTS corridas (
    id               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    plataforma_id    INTEGER NOT NULL,
    origem           TEXT    NOT NULL,
    destino          TEXT    NOT NULL,
    valor_centavos   INTEGER NOT NULL,
    gorjeta_centavos INTEGER NOT NULL DEFAULT 0,
    distancia_m      INTEGER NOT NULL,
    deslocamento_m   INTEGER NOT NULL DEFAULT 0,
    duracao_seg      INTEGER NOT NULL,
    inicio_em        INTEGER NOT NULL,
    observacao       TEXT,
    criado_em        INTEGER NOT NULL,
    FOREIGN KEY (plataforma_id) REFERENCES plataformas (id) ON UPDATE NO ACTION ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS index_corridas_plataforma_id ON corridas (plataforma_id);
CREATE INDEX IF NOT EXISTS index_corridas_inicio_em     ON corridas (inicio_em);

CREATE TABLE IF NOT EXISTS despesas (
    id             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    categoria      TEXT    NOT NULL,   -- COMBUSTIVEL | MANUTENCAO | SEGURO | OUTROS
    valor_centavos INTEGER NOT NULL,
    data_em        INTEGER NOT NULL,
    descricao      TEXT,
    litros_ml      INTEGER,
    odometro_km    INTEGER,
    criado_em      INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_despesas_data_em           ON despesas (data_em);
CREATE INDEX IF NOT EXISTS index_despesas_categoria_data_em ON despesas (categoria, data_em);

CREATE TABLE IF NOT EXISTS configuracoes (
    id                         INTEGER PRIMARY KEY NOT NULL,  -- sempre 1
    meta_lucro_diario_centavos INTEGER NOT NULL DEFAULT 0,
    meta_lucro_mensal_centavos INTEGER NOT NULL DEFAULT 0,
    custo_km_centavos          INTEGER NOT NULL DEFAULT 0,
    tarifa_minima_centavos     INTEGER NOT NULL DEFAULT 0,   -- v2
    dias_trabalho_mes          INTEGER NOT NULL DEFAULT 26,
    meta_lucro_semanal_centavos INTEGER NOT NULL DEFAULT 0,  -- v3
    veiculo_nome               TEXT,                         -- v3
    consumo_km_l_x100          INTEGER NOT NULL DEFAULT 0,   -- v3
    preco_litro_centavos       INTEGER NOT NULL DEFAULT 0,   -- v3
    nome_motorista             TEXT,                         -- v6
    telefone                   TEXT,                         -- v6
    cidade                     TEXT                          -- v6
);

-- v3
CREATE TABLE IF NOT EXISTS jornadas (
    id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    inicio_em   INTEGER NOT NULL,
    fim_em      INTEGER,
    metros_gps  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS index_jornadas_inicio_em ON jornadas (inicio_em);

-- v5
CREATE TABLE IF NOT EXISTS ofertas (
    id             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    recebida_em    INTEGER NOT NULL,
    plataforma     TEXT    NOT NULL,
    valor_centavos INTEGER NOT NULL,
    metros         INTEGER NOT NULL,
    minutos        INTEGER NOT NULL,
    paradas        INTEGER NOT NULL DEFAULT 0,
    classificacao  TEXT    NOT NULL,   -- BOA | MEDIA | RUIM
    registrada     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS index_ofertas_recebida_em ON ofertas (recebida_em);

CREATE TABLE IF NOT EXISTS custos_fixos (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    nome                  TEXT    NOT NULL,
    valor_mensal_centavos INTEGER NOT NULL,
    ativo                 INTEGER NOT NULL DEFAULT 1
);
