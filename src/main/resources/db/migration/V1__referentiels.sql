-- =============================================================================
-- V1 — Extensions & tables de référence (tp_)
-- Convention : tp_ = référence/paramétrage · td_ = données dynamiques · tr_ = relations
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- recherche floue (noms clientes, articles…)
CREATE EXTENSION IF NOT EXISTS citext;      -- e-mails insensibles à la casse
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- contraintes d'exclusion (anti double réservation)

-- Périmètres métier : Sky Club, Kids Club, Boutique
CREATE TABLE tp_perimetre (
    code        VARCHAR(30)  PRIMARY KEY,
    libelle     VARCHAR(80)  NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('SALON', 'BOUTIQUE')),
    ordre       SMALLINT     NOT NULL DEFAULT 0,
    actif       BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Modules fonctionnels de la matrice (colonnes)
CREATE TABLE tp_module (
    code        VARCHAR(30)  PRIMARY KEY,
    libelle     VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    icone       VARCHAR(40),
    ordre       SMALLINT     NOT NULL DEFAULT 0,
    actif       BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Niveaux de droit : C, S, G, V
CREATE TABLE tp_action (
    code        CHAR(1)      PRIMARY KEY CHECK (code IN ('C', 'S', 'G', 'V')),
    libelle     VARCHAR(60)  NOT NULL,
    ordre       SMALLINT     NOT NULL
);

-- Profils (lignes de la matrice)
CREATE TABLE tp_profil (
    code          VARCHAR(30)  PRIMARY KEY,
    libelle       VARCHAR(120) NOT NULL,
    libelle_court VARCHAR(40)  NOT NULL,
    finalite      VARCHAR(255) NOT NULL,
    icone         VARCHAR(40),
    ordre         SMALLINT     NOT NULL DEFAULT 0,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Fonctionnalités principales décrites pour chaque profil (fiche de poste)
CREATE TABLE tp_profil_fonctionnalite (
    id          SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profil_code VARCHAR(30)  NOT NULL REFERENCES tp_profil (code),
    libelle     VARCHAR(160) NOT NULL,
    ordre       SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_tp_profil_fonctionnalite UNIQUE (profil_code, libelle)
);

-- Règles de gestion (onglet « Règles » de la matrice)
CREATE TABLE tp_regle_gestion (
    code        VARCHAR(40)  PRIMARY KEY,
    module_code VARCHAR(30)  NOT NULL REFERENCES tp_module (code),
    libelle     VARCHAR(120) NOT NULL,
    parametrage TEXT         NOT NULL,
    ordre       SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX ix_tp_regle_gestion_module ON tp_regle_gestion (module_code);

-- Catalogue des prestations
CREATE TABLE tp_prestation (
    code           VARCHAR(40)   PRIMARY KEY,
    libelle        VARCHAR(120)  NOT NULL,
    perimetre_code VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    duree_min      SMALLINT      NOT NULL CHECK (duree_min > 0),
    prix           NUMERIC(12,0) NOT NULL CHECK (prix >= 0),
    ordre          SMALLINT      NOT NULL DEFAULT 0,
    actif          BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_tp_prestation_perimetre ON tp_prestation (perimetre_code) WHERE actif;

-- Formules d'abonnement
CREATE TABLE tp_formule (
    code           VARCHAR(40)   PRIMARY KEY,
    libelle        VARCHAR(120)  NOT NULL,
    perimetre_code VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    prix           NUMERIC(12,0) NOT NULL CHECK (prix >= 0),
    nb_seances     SMALLINT,
    duree_jours    SMALLINT      NOT NULL CHECK (duree_jours > 0),
    actif          BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_tp_formule_perimetre ON tp_formule (perimetre_code) WHERE actif;

CREATE TABLE tp_mode_paiement (
    code    VARCHAR(30) PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL,
    ordre   SMALLINT    NOT NULL DEFAULT 0,
    actif   BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE tp_categorie_depense (
    code    VARCHAR(30) PRIMARY KEY,
    libelle VARCHAR(80) NOT NULL,
    ordre   SMALLINT    NOT NULL DEFAULT 0
);

CREATE TABLE tp_type_absence (
    code              VARCHAR(30) PRIMARY KEY,
    libelle           VARCHAR(60) NOT NULL,
    soumis_validation BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE tp_type_variable_paie (
    code    VARCHAR(30) PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL,
    sens    SMALLINT    NOT NULL CHECK (sens IN (-1, 1))
);

CREATE TABLE tp_categorie_article (
    code    VARCHAR(30) PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL
);

CREATE TABLE tp_type_decision (
    code    VARCHAR(30) PRIMARY KEY,
    libelle VARCHAR(80) NOT NULL
);

CREATE TABLE tp_type_signalement (
    code    VARCHAR(30) PRIMARY KEY,
    libelle VARCHAR(80) NOT NULL
);

CREATE TABLE tp_canal_publication (
    code    VARCHAR(30) PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL
);

CREATE TABLE tp_formation (
    code        VARCHAR(40)  PRIMARY KEY,
    libelle     VARCHAR(120) NOT NULL,
    obligatoire BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Check-lists hygiène : ouverture / après prestation / fermeture
CREATE TABLE tp_checklist_item (
    code    VARCHAR(40)  PRIMARY KEY,
    moment  VARCHAR(20)  NOT NULL CHECK (moment IN ('OUVERTURE', 'APRES_PRESTATION', 'FERMETURE')),
    libelle VARCHAR(120) NOT NULL,
    ordre   SMALLINT     NOT NULL DEFAULT 0,
    actif   BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_tp_checklist_item_moment ON tp_checklist_item (moment, ordre) WHERE actif;

-- Paramètres applicatifs (seuils DG, rotation QR…)
CREATE TABLE tp_parametre (
    cle         VARCHAR(60)  PRIMARY KEY,
    valeur      VARCHAR(255) NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('ENTIER', 'MONTANT', 'TEXTE', 'BOOLEEN')),
    description VARCHAR(255),
    modifie_par UUID,
    modifie_le  TIMESTAMPTZ
);
