-- =============================================================================
-- V6 — Caisse & ventes, Stocks & boutique
-- =============================================================================

-- Une session de caisse par périmètre et par jour
CREATE TABLE td_session_caisse (
    id                   UUID          PRIMARY KEY,
    perimetre_code       VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    jour                 DATE          NOT NULL,
    statut               VARCHAR(20)   NOT NULL CHECK (statut IN ('OUVERTE', 'CLOTUREE')),
    fond_ouverture       NUMERIC(12,0) NOT NULL CHECK (fond_ouverture >= 0),
    ouverte_par          UUID          NOT NULL REFERENCES td_utilisateur (id),
    ouverte_le           TIMESTAMPTZ   NOT NULL,
    cloturee_par         UUID          REFERENCES td_utilisateur (id),
    cloturee_le          TIMESTAMPTZ,
    montant_compte       NUMERIC(12,0),
    ecart                NUMERIC(12,0),
    controle_par         UUID          REFERENCES td_utilisateur (id),
    controle_le          TIMESTAMPTZ,
    controle_conforme    BOOLEAN,
    controle_commentaire VARCHAR(500),
    version              BIGINT        NOT NULL DEFAULT 0,
    cree_le              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par             UUID,
    modifie_le           TIMESTAMPTZ,
    modifie_par          UUID,
    -- Règle « Séparation des tâches de caisse » : contrôle journalier indépendant
    CONSTRAINT ck_td_session_caisse_controle_independant
        CHECK (controle_par IS NULL OR (controle_par <> ouverte_par AND controle_par IS DISTINCT FROM cloturee_par)),
    CONSTRAINT ck_td_session_caisse_controle_apres_cloture
        CHECK (controle_par IS NULL OR statut = 'CLOTUREE')
);
CREATE UNIQUE INDEX ux_td_session_caisse_perimetre_jour ON td_session_caisse (perimetre_code, jour);
CREATE INDEX ix_td_session_caisse_a_controler ON td_session_caisse (jour DESC) WHERE statut = 'CLOTUREE' AND controle_par IS NULL;

CREATE TABLE td_encaissement (
    id                 UUID          PRIMARY KEY,
    session_id         UUID          NOT NULL REFERENCES td_session_caisse (id),
    horodatage         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    libelle            VARCHAR(160)  NOT NULL,
    rendez_vous_id     UUID          REFERENCES td_rendez_vous (id),
    client_id          UUID          REFERENCES td_client (id),
    coiffeuse_id       UUID          REFERENCES td_employe (id),
    demande_autorisation_id UUID     REFERENCES td_demande_autorisation (id),
    montant            NUMERIC(12,0) NOT NULL CHECK (montant > 0),
    mode_paiement_code VARCHAR(30)   NOT NULL REFERENCES tp_mode_paiement (code),
    encaisse_par       UUID          NOT NULL REFERENCES td_utilisateur (id),
    note               VARCHAR(255),
    annule             BOOLEAN       NOT NULL DEFAULT FALSE,
    cree_le            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par           UUID
);
CREATE INDEX ix_td_encaissement_session ON td_encaissement (session_id);
CREATE INDEX ix_td_encaissement_coiffeuse ON td_encaissement (coiffeuse_id, horodatage DESC) WHERE coiffeuse_id IS NOT NULL;
CREATE INDEX ix_td_encaissement_client ON td_encaissement (client_id) WHERE client_id IS NOT NULL;
CREATE UNIQUE INDEX ux_td_encaissement_rdv ON td_encaissement (rendez_vous_id) WHERE rendez_vous_id IS NOT NULL AND NOT annule;

CREATE TABLE td_article (
    id               UUID          PRIMARY KEY,
    reference        VARCHAR(40)   NOT NULL,
    nom              VARCHAR(160)  NOT NULL,
    categorie_code   VARCHAR(30)   NOT NULL REFERENCES tp_categorie_article (code),
    perimetre_code   VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    unite            VARCHAR(20)   NOT NULL,
    stock_actuel     INTEGER       NOT NULL DEFAULT 0 CHECK (stock_actuel >= 0),
    seuil            INTEGER       NOT NULL DEFAULT 0 CHECK (seuil >= 0),
    prix_achat       NUMERIC(12,0) CHECK (prix_achat >= 0),
    prix_vente       NUMERIC(12,0) CHECK (prix_vente >= 0),
    fournisseur      VARCHAR(120),
    dernier_appro_le DATE,
    actif            BOOLEAN       NOT NULL DEFAULT TRUE,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID
);
CREATE UNIQUE INDEX ux_td_article_reference ON td_article (reference);
CREATE INDEX ix_td_article_perimetre_categorie ON td_article (perimetre_code, categorie_code) WHERE actif;
CREATE INDEX ix_td_article_alertes ON td_article (perimetre_code) WHERE actif AND stock_actuel <= seuil;
CREATE INDEX ix_td_article_nom_trgm ON td_article USING gin (nom gin_trgm_ops);

CREATE TABLE td_comptage (
    id          UUID         PRIMARY KEY,
    article_id  UUID         NOT NULL REFERENCES td_article (id),
    horodatage  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    theorique   INTEGER      NOT NULL,
    compte      INTEGER      NOT NULL CHECK (compte >= 0),
    ecart       INTEGER      GENERATED ALWAYS AS (compte - theorique) STORED,
    statut      VARCHAR(20)  NOT NULL CHECK (statut IN ('CONFORME', 'ECART_SIGNALE', 'AJUSTE', 'REJETE')),
    saisi_par   UUID         NOT NULL REFERENCES td_utilisateur (id),
    traite_par  UUID         REFERENCES td_utilisateur (id),
    traite_le   TIMESTAMPTZ,
    commentaire VARCHAR(500),
    version     BIGINT       NOT NULL DEFAULT 0,
    -- Règle « Gestion des stocks » : la personne qui compte ne corrige pas seule le stock
    CONSTRAINT ck_td_comptage_separation CHECK (traite_par IS NULL OR traite_par <> saisi_par)
);
CREATE INDEX ix_td_comptage_article ON td_comptage (article_id, horodatage DESC);
CREATE INDEX ix_td_comptage_ecarts ON td_comptage (horodatage DESC) WHERE statut = 'ECART_SIGNALE';

CREATE TABLE td_commande (
    id             UUID          PRIMARY KEY,
    reference      VARCHAR(30)   NOT NULL,
    perimetre_code VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    fournisseur    VARCHAR(120)  NOT NULL,
    montant_estime NUMERIC(14,0) NOT NULL DEFAULT 0 CHECK (montant_estime >= 0),
    statut         VARCHAR(20)   NOT NULL CHECK (statut IN ('BESOIN', 'VALIDEE', 'REFUSEE', 'COMMANDEE', 'RECUE')),
    cree_par_id    UUID          NOT NULL REFERENCES td_utilisateur (id),
    valide_par     UUID          REFERENCES td_utilisateur (id),
    valide_le      TIMESTAMPTZ,
    passee_le      TIMESTAMPTZ,
    recue_par      UUID          REFERENCES td_utilisateur (id),
    recue_le       TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID,
    CONSTRAINT ck_td_commande_separation CHECK (valide_par IS NULL OR valide_par <> cree_par_id)
);
CREATE UNIQUE INDEX ux_td_commande_reference ON td_commande (reference);
CREATE INDEX ix_td_commande_en_cours ON td_commande (perimetre_code, statut) WHERE statut IN ('BESOIN', 'VALIDEE', 'COMMANDEE');

CREATE TABLE td_ligne_commande (
    id            UUID          PRIMARY KEY,
    commande_id   UUID          NOT NULL REFERENCES td_commande (id) ON DELETE CASCADE,
    article_id    UUID          NOT NULL REFERENCES td_article (id),
    quantite      INTEGER       NOT NULL CHECK (quantite > 0),
    prix_unitaire NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (prix_unitaire >= 0),
    CONSTRAINT uq_td_ligne_commande UNIQUE (commande_id, article_id)
);
CREATE INDEX ix_td_ligne_commande_article ON td_ligne_commande (article_id);

CREATE TABLE td_mouvement_stock (
    id             UUID         PRIMARY KEY,
    article_id     UUID         NOT NULL REFERENCES td_article (id),
    horodatage     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    type           VARCHAR(20)  NOT NULL CHECK (type IN ('ENTREE', 'SORTIE', 'VENTE', 'AJUSTEMENT', 'TRANSFERT', 'PERTE', 'CASSE')),
    quantite       INTEGER      NOT NULL CHECK (quantite <> 0),
    motif          VARCHAR(255),
    effectue_par   UUID         NOT NULL REFERENCES td_utilisateur (id),
    autorise_par   UUID         REFERENCES td_utilisateur (id),
    encaissement_id UUID        REFERENCES td_encaissement (id),
    comptage_id    UUID         REFERENCES td_comptage (id),
    commande_id    UUID         REFERENCES td_commande (id)
);
CREATE INDEX ix_td_mouvement_article_date ON td_mouvement_stock (article_id, horodatage DESC);
CREATE INDEX ix_td_mouvement_horodatage_brin ON td_mouvement_stock USING brin (horodatage);
