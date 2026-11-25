-- =============================================================================
-- V7 — Communication (externe & interne), événements, Administration / Finance
-- =============================================================================

-- Circuit : brouillon → à vérifier (DO) → à valider (DG) → validée → publiée → analysée
CREATE TABLE td_publication (
    id               UUID          PRIMARY KEY,
    titre            VARCHAR(160)  NOT NULL,
    contenu          TEXT          NOT NULL,
    canal_code       VARCHAR(30)   NOT NULL REFERENCES tp_canal_publication (code),
    perimetre_code   VARCHAR(30)   REFERENCES tp_perimetre (code),
    date_prevue      DATE,
    statut           VARCHAR(20)   NOT NULL CHECK (statut IN ('BROUILLON', 'A_VERIFIER', 'A_REVOIR', 'A_VALIDER', 'VALIDEE', 'PUBLIEE', 'ANALYSEE')),
    auteur_id        UUID          NOT NULL REFERENCES td_utilisateur (id),
    verifie_par      UUID          REFERENCES td_utilisateur (id),
    verifie_le       TIMESTAMPTZ,
    valide_par       UUID          REFERENCES td_utilisateur (id),
    valide_le        TIMESTAMPTZ,
    publiee_le       TIMESTAMPTZ,
    motif_revoir     VARCHAR(500),
    portee           INTEGER       CHECK (portee >= 0),
    interactions     INTEGER       CHECK (interactions >= 0),
    nouveaux_abonnes INTEGER,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID,
    CONSTRAINT ck_td_publication_separation CHECK (valide_par IS NULL OR valide_par <> auteur_id)
);
CREATE INDEX ix_td_publication_statut_date ON td_publication (statut, date_prevue);
CREATE INDEX ix_td_publication_auteur ON td_publication (auteur_id);

-- Communication interne : actualités, notes de service, annonces, documents
CREATE TABLE td_note_interne (
    id          UUID         PRIMARY KEY,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('ACTUALITE', 'NOTE_SERVICE', 'ANNONCE', 'DOCUMENT')),
    titre       VARCHAR(160) NOT NULL,
    contenu     TEXT         NOT NULL,
    auteur_id   UUID         NOT NULL REFERENCES td_utilisateur (id),
    publiee_le  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    epinglee    BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    cree_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par    UUID,
    modifie_le  TIMESTAMPTZ,
    modifie_par UUID
);
CREATE INDEX ix_td_note_interne_publication ON td_note_interne (epinglee DESC, publiee_le DESC);

-- Événements & calendrier : date → responsable → budget → statut
CREATE TABLE td_evenement (
    id             UUID          PRIMARY KEY,
    titre          VARCHAR(160)  NOT NULL,
    type           VARCHAR(30)   NOT NULL CHECK (type IN ('CAMPAGNE', 'FORMATION', 'REUNION', 'EVENEMENT', 'AUTRE')),
    perimetre_code VARCHAR(30)   REFERENCES tp_perimetre (code),
    date_debut     DATE          NOT NULL,
    date_fin       DATE          NOT NULL,
    responsable_id UUID          REFERENCES td_utilisateur (id),
    budget         NUMERIC(14,0) CHECK (budget >= 0),
    statut         VARCHAR(20)   NOT NULL CHECK (statut IN ('PREVU', 'EN_PREPARATION', 'EN_COURS', 'TERMINE', 'ANNULE')),
    description    VARCHAR(1000),
    version        BIGINT        NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID,
    CONSTRAINT ck_td_evenement_dates CHECK (date_fin >= date_debut)
);
CREATE INDEX ix_td_evenement_dates ON td_evenement (date_debut, date_fin);

-- Achats & dépenses : demande → validation → paiement → contrôle
CREATE TABLE td_depense (
    id                 UUID          PRIMARY KEY,
    reference          VARCHAR(30)   NOT NULL,
    objet              VARCHAR(255)  NOT NULL,
    categorie_code     VARCHAR(30)   NOT NULL REFERENCES tp_categorie_depense (code),
    -- NULL = dépense transverse
    perimetre_code     VARCHAR(30)   REFERENCES tp_perimetre (code),
    montant            NUMERIC(14,0) NOT NULL CHECK (montant > 0),
    fournisseur        VARCHAR(160)  NOT NULL,
    statut             VARCHAR(20)   NOT NULL CHECK (statut IN ('DEMANDEE', 'VALIDEE', 'REFUSEE', 'PAYEE', 'CONTROLEE')),
    demande_par        UUID          NOT NULL REFERENCES td_utilisateur (id),
    demande_le         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    valide_par         UUID          REFERENCES td_utilisateur (id),
    valide_le          TIMESTAMPTZ,
    motif_refus        VARCHAR(500),
    paye_par           UUID          REFERENCES td_utilisateur (id),
    paye_le            TIMESTAMPTZ,
    mode_paiement_code VARCHAR(30)   REFERENCES tp_mode_paiement (code),
    controle_par       UUID          REFERENCES td_utilisateur (id),
    controle_le        TIMESTAMPTZ,
    commentaire        VARCHAR(500),
    version            BIGINT        NOT NULL DEFAULT 0,
    cree_le            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par           UUID,
    modifie_le         TIMESTAMPTZ,
    modifie_par        UUID,
    -- Règle « Séparation des tâches financières », garantie aussi par la base
    CONSTRAINT ck_td_depense_valideur   CHECK (valide_par IS NULL OR valide_par <> demande_par),
    CONSTRAINT ck_td_depense_payeur     CHECK (paye_par IS NULL OR paye_par IS DISTINCT FROM valide_par),
    CONSTRAINT ck_td_depense_controleur CHECK (controle_par IS NULL OR (controle_par <> demande_par
                                               AND controle_par IS DISTINCT FROM valide_par
                                               AND controle_par IS DISTINCT FROM paye_par)),
    CONSTRAINT ck_td_depense_paiement   CHECK (paye_par IS NULL OR mode_paiement_code IS NOT NULL)
);
CREATE UNIQUE INDEX ux_td_depense_reference ON td_depense (reference);
CREATE INDEX ix_td_depense_statut_date ON td_depense (statut, demande_le DESC);
CREATE INDEX ix_td_depense_en_circuit ON td_depense (demande_le DESC) WHERE statut IN ('DEMANDEE', 'VALIDEE', 'PAYEE');
CREATE INDEX ix_td_depense_perimetre ON td_depense (perimetre_code, demande_le DESC);
CREATE INDEX ix_td_depense_demandeur ON td_depense (demande_par);

CREATE TABLE td_justificatif (
    id              UUID         PRIMARY KEY,
    depense_id      UUID         NOT NULL REFERENCES td_depense (id) ON DELETE CASCADE,
    nom_fichier     VARCHAR(255) NOT NULL,
    type_mime       VARCHAR(100) NOT NULL,
    taille_octets   BIGINT       NOT NULL CHECK (taille_octets > 0),
    empreinte_sha256 CHAR(64)    NOT NULL,
    chemin_stockage VARCHAR(500) NOT NULL,
    depose_par      UUID         NOT NULL REFERENCES td_utilisateur (id),
    depose_le       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_td_justificatif_depense ON td_justificatif (depense_id);

-- Séquences de numérotation métier (DEP-2026-000123, CMD-…)
CREATE SEQUENCE sq_depense_reference START 1;
CREATE SEQUENCE sq_commande_reference START 1;
CREATE SEQUENCE sq_employe_matricule START 1;
