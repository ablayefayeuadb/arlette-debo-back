-- =============================================================================
-- V2 — Identité, habilitations, délégations, jetons
-- =============================================================================

-- Matrice : une cellule profil × module (valeur brute, restriction, « * »)
CREATE TABLE tr_profil_module (
    profil_code       VARCHAR(30) NOT NULL REFERENCES tp_profil (code),
    module_code       VARCHAR(30) NOT NULL REFERENCES tp_module (code),
    valeur_brute      VARCHAR(40) NOT NULL,
    restriction       VARCHAR(30),
    sous_habilitation BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Accès « * » accordé ou suspendu par la DG (sans effet si sous_habilitation = false)
    accordee          BOOLEAN     NOT NULL DEFAULT TRUE,
    modifie_par       UUID,
    modifie_le        TIMESTAMPTZ,
    PRIMARY KEY (profil_code, module_code)
);
CREATE INDEX ix_tr_profil_module_module ON tr_profil_module (module_code);

-- Actions (C/S/G/V) de chaque cellule
CREATE TABLE tr_profil_module_action (
    profil_code VARCHAR(30) NOT NULL,
    module_code VARCHAR(30) NOT NULL,
    action_code CHAR(1)     NOT NULL REFERENCES tp_action (code),
    PRIMARY KEY (profil_code, module_code, action_code),
    FOREIGN KEY (profil_code, module_code) REFERENCES tr_profil_module (profil_code, module_code) ON DELETE CASCADE
);

-- Périmètres par défaut d'un profil
CREATE TABLE tr_profil_perimetre (
    profil_code    VARCHAR(30) NOT NULL REFERENCES tp_profil (code),
    perimetre_code VARCHAR(30) NOT NULL REFERENCES tp_perimetre (code),
    PRIMARY KEY (profil_code, perimetre_code)
);
CREATE INDEX ix_tr_profil_perimetre_perimetre ON tr_profil_perimetre (perimetre_code);

-- Comptes utilisateurs
CREATE TABLE td_utilisateur (
    id                  UUID         PRIMARY KEY,
    nom                 VARCHAR(120) NOT NULL,
    email               CITEXT       NOT NULL,
    mot_de_passe_hash   VARCHAR(100) NOT NULL,
    profil_code         VARCHAR(30)  NOT NULL REFERENCES tp_profil (code),
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    doit_changer_mdp    BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Incrémenté à chaque révocation globale : invalide immédiatement tous les JWT émis
    version_jeton       INTEGER      NOT NULL DEFAULT 0,
    tentatives_echouees SMALLINT     NOT NULL DEFAULT 0,
    verrouille_jusqua   TIMESTAMPTZ,
    derniere_connexion  TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    cree_le             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par            UUID,
    modifie_le          TIMESTAMPTZ,
    modifie_par         UUID,
    CONSTRAINT ck_td_utilisateur_email CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);
CREATE UNIQUE INDEX ux_td_utilisateur_email ON td_utilisateur (email);
CREATE INDEX ix_td_utilisateur_profil ON td_utilisateur (profil_code) WHERE actif;
CREATE INDEX ix_td_utilisateur_nom_trgm ON td_utilisateur USING gin (nom gin_trgm_ops);

-- Périmètres propres à un compte (sinon ceux du profil)
CREATE TABLE tr_utilisateur_perimetre (
    utilisateur_id UUID        NOT NULL REFERENCES td_utilisateur (id) ON DELETE CASCADE,
    perimetre_code VARCHAR(30) NOT NULL REFERENCES tp_perimetre (code),
    PRIMARY KEY (utilisateur_id, perimetre_code)
);
CREATE INDEX ix_tr_utilisateur_perimetre_perimetre ON tr_utilisateur_perimetre (perimetre_code);

-- Relais / délégations encadrées (activées après formation)
CREATE TABLE td_delegation (
    id                       UUID         PRIMARY KEY,
    code                     VARCHAR(40)  NOT NULL,
    beneficiaire_profil_code VARCHAR(30)  NOT NULL REFERENCES tp_profil (code),
    titre                    VARCHAR(120) NOT NULL,
    description              VARCHAR(500) NOT NULL,
    formation_validee        BOOLEAN      NOT NULL DEFAULT FALSE,
    active                   BOOLEAN      NOT NULL DEFAULT FALSE,
    activee_par              UUID REFERENCES td_utilisateur (id),
    activee_le               TIMESTAMPTZ,
    version                  BIGINT       NOT NULL DEFAULT 0,
    cree_le                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par                 UUID,
    modifie_le               TIMESTAMPTZ,
    modifie_par              UUID,
    -- Règle : un relais n'est actif qu'après formation et validation des compétences
    CONSTRAINT ck_td_delegation_formation CHECK (NOT active OR formation_validee)
);
CREATE UNIQUE INDEX ux_td_delegation_code ON td_delegation (code);
CREATE INDEX ix_td_delegation_beneficiaire_active ON td_delegation (beneficiaire_profil_code) WHERE active;

CREATE TABLE tr_delegation_perimetre (
    delegation_id  UUID        NOT NULL REFERENCES td_delegation (id) ON DELETE CASCADE,
    perimetre_code VARCHAR(30) NOT NULL REFERENCES tp_perimetre (code),
    PRIMARY KEY (delegation_id, perimetre_code)
);

CREATE TABLE tr_delegation_action (
    delegation_id UUID        NOT NULL REFERENCES td_delegation (id) ON DELETE CASCADE,
    module_code   VARCHAR(30) NOT NULL REFERENCES tp_module (code),
    action_code   CHAR(1)     NOT NULL REFERENCES tp_action (code),
    PRIMARY KEY (delegation_id, module_code, action_code)
);

-- Jetons de rafraîchissement : seule l'empreinte SHA-256 est stockée ; rotation + détection de rejeu par famille
CREATE TABLE td_jeton_rafraichissement (
    id             UUID         PRIMARY KEY,
    utilisateur_id UUID         NOT NULL REFERENCES td_utilisateur (id) ON DELETE CASCADE,
    empreinte      CHAR(64)     NOT NULL,
    famille        UUID         NOT NULL,
    expire_le      TIMESTAMPTZ  NOT NULL,
    revoque_le     TIMESTAMPTZ,
    remplace_par   UUID,
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    adresse_ip     VARCHAR(45),
    agent          VARCHAR(255)
);
CREATE UNIQUE INDEX ux_td_jeton_empreinte ON td_jeton_rafraichissement (empreinte);
CREATE INDEX ix_td_jeton_utilisateur_actif ON td_jeton_rafraichissement (utilisateur_id) WHERE revoque_le IS NULL;
CREATE INDEX ix_td_jeton_famille ON td_jeton_rafraichissement (famille);
CREATE INDEX ix_td_jeton_expiration ON td_jeton_rafraichissement (expire_le);

-- =============================================================================
-- Journal d'audit (append-only)
-- =============================================================================
CREATE TABLE td_journal_audit (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    horodatage     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    utilisateur_id UUID REFERENCES td_utilisateur (id) ON DELETE SET NULL,
    utilisateur_nom VARCHAR(120),
    profil_code    VARCHAR(30),
    module_code    VARCHAR(30)  NOT NULL REFERENCES tp_module (code),
    action         VARCHAR(120) NOT NULL,
    detail         VARCHAR(1000),
    adresse_ip     VARCHAR(45)
);
-- BRIN : index minuscule et très efficace sur une colonne croissante en insertion seule
CREATE INDEX ix_td_journal_horodatage_brin ON td_journal_audit USING brin (horodatage);
CREATE INDEX ix_td_journal_module_horodatage ON td_journal_audit (module_code, horodatage DESC);
CREATE INDEX ix_td_journal_utilisateur_horodatage ON td_journal_audit (utilisateur_id, horodatage DESC);
