-- =============================================================================
-- V3 — Équipe & RH : dossiers, compétences, formations, absences, pointage, paie, évaluations
-- =============================================================================

CREATE TABLE td_employe (
    id             UUID          PRIMARY KEY,
    utilisateur_id UUID          REFERENCES td_utilisateur (id) ON DELETE SET NULL,
    matricule      VARCHAR(20)   NOT NULL,
    nom            VARCHAR(120)  NOT NULL,
    poste          VARCHAR(120)  NOT NULL,
    grade          VARCHAR(40),
    -- NULL = transverse (rattaché à la Direction / plusieurs périmètres)
    perimetre_code VARCHAR(30)   REFERENCES tp_perimetre (code),
    telephone      VARCHAR(20),
    date_entree    DATE          NOT NULL,
    date_sortie    DATE,
    contrat        VARCHAR(20)   NOT NULL CHECK (contrat IN ('CDI', 'CDD', 'STAGE', 'PRESTATAIRE')),
    salaire_base   NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (salaire_base >= 0),
    heure_prise_poste TIME       NOT NULL DEFAULT '09:30',
    actif          BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID,
    CONSTRAINT ck_td_employe_dates CHECK (date_sortie IS NULL OR date_sortie >= date_entree)
);
CREATE UNIQUE INDEX ux_td_employe_matricule ON td_employe (matricule);
CREATE UNIQUE INDEX ux_td_employe_utilisateur ON td_employe (utilisateur_id) WHERE utilisateur_id IS NOT NULL;
CREATE INDEX ix_td_employe_perimetre_actif ON td_employe (perimetre_code) WHERE actif;
CREATE INDEX ix_td_employe_nom_trgm ON td_employe USING gin (nom gin_trgm_ops);

-- Matrice des compétences (reliée au planning : qui est habilitée pour quelle prestation)
CREATE TABLE tr_employe_competence (
    employe_id      UUID        NOT NULL REFERENCES td_employe (id) ON DELETE CASCADE,
    prestation_code VARCHAR(40) NOT NULL REFERENCES tp_prestation (code),
    niveau          SMALLINT    NOT NULL DEFAULT 1 CHECK (niveau BETWEEN 1 AND 3),
    validee_le      DATE,
    validee_par     UUID REFERENCES td_utilisateur (id),
    PRIMARY KEY (employe_id, prestation_code)
);
CREATE INDEX ix_tr_employe_competence_prestation ON tr_employe_competence (prestation_code);

-- Formations suivies
CREATE TABLE tr_employe_formation (
    employe_id     UUID        NOT NULL REFERENCES td_employe (id) ON DELETE CASCADE,
    formation_code VARCHAR(40) NOT NULL REFERENCES tp_formation (code),
    statut         VARCHAR(20) NOT NULL CHECK (statut IN ('PLANIFIEE', 'SUIVIE', 'VALIDEE')),
    validee_le     DATE,
    PRIMARY KEY (employe_id, formation_code)
);
CREATE INDEX ix_tr_employe_formation_formation ON tr_employe_formation (formation_code, statut);

CREATE TABLE td_absence (
    id          UUID         PRIMARY KEY,
    employe_id  UUID         NOT NULL REFERENCES td_employe (id),
    type_code   VARCHAR(30)  NOT NULL REFERENCES tp_type_absence (code),
    du          DATE         NOT NULL,
    au          DATE         NOT NULL,
    motif       VARCHAR(255),
    statut      VARCHAR(20)  NOT NULL CHECK (statut IN ('DEMANDE', 'APPROUVEE', 'REFUSEE')),
    saisi_par   UUID         NOT NULL REFERENCES td_utilisateur (id),
    decide_par  UUID         REFERENCES td_utilisateur (id),
    decide_le   TIMESTAMPTZ,
    version     BIGINT       NOT NULL DEFAULT 0,
    cree_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par    UUID,
    modifie_le  TIMESTAMPTZ,
    modifie_par UUID,
    CONSTRAINT ck_td_absence_periode CHECK (au >= du)
);
CREATE INDEX ix_td_absence_employe_du ON td_absence (employe_id, du DESC);
CREATE INDEX ix_td_absence_a_traiter ON td_absence (du) WHERE statut = 'DEMANDE';
-- « Absents aujourd'hui » : recherche par intervalle de dates
CREATE INDEX ix_td_absence_periode_gist ON td_absence USING gist (daterange(du, au, '[]')) WHERE statut = 'APPROUVEE';

-- Pointage QR : arrivée → pause → retour → départ
CREATE TABLE td_pointage (
    id            UUID         PRIMARY KEY,
    employe_id    UUID         NOT NULL REFERENCES td_employe (id),
    jour          DATE         NOT NULL,
    heure_prevue  TIME         NOT NULL,
    arrivee_le    TIMESTAMPTZ,
    pause_debut   TIMESTAMPTZ,
    pause_fin     TIMESTAMPTZ,
    depart_le     TIMESTAMPTZ,
    retard_min    INTEGER      NOT NULL DEFAULT 0,
    justification VARCHAR(255),
    version       BIGINT       NOT NULL DEFAULT 0,
    cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par      UUID,
    modifie_le    TIMESTAMPTZ,
    modifie_par   UUID,
    CONSTRAINT ck_td_pointage_chrono CHECK (depart_le IS NULL OR arrivee_le IS NULL OR depart_le >= arrivee_le)
);
CREATE UNIQUE INDEX ux_td_pointage_employe_jour ON td_pointage (employe_id, jour);
CREATE INDEX ix_td_pointage_jour ON td_pointage (jour);
CREATE INDEX ix_td_pointage_retards ON td_pointage (jour) WHERE retard_min > 0;

CREATE TABLE td_variable_paie (
    id          UUID          PRIMARY KEY,
    employe_id  UUID          NOT NULL REFERENCES td_employe (id),
    mois        CHAR(7)       NOT NULL CHECK (mois ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    type_code   VARCHAR(30)   NOT NULL REFERENCES tp_type_variable_paie (code),
    montant     NUMERIC(12,0) NOT NULL CHECK (montant > 0),
    commentaire VARCHAR(255),
    statut      VARCHAR(20)   NOT NULL CHECK (statut IN ('SAISIE', 'TRANSMISE')),
    saisi_par   UUID          NOT NULL REFERENCES td_utilisateur (id),
    transmise_le TIMESTAMPTZ,
    version     BIGINT        NOT NULL DEFAULT 0,
    cree_le     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par    UUID,
    modifie_le  TIMESTAMPTZ,
    modifie_par UUID
);
CREATE INDEX ix_td_variable_paie_mois_statut ON td_variable_paie (mois, statut);
CREATE INDEX ix_td_variable_paie_employe_mois ON td_variable_paie (employe_id, mois);

CREATE TABLE td_evaluation (
    id              UUID         PRIMARY KEY,
    employe_id      UUID         NOT NULL REFERENCES td_employe (id),
    date_evaluation DATE         NOT NULL,
    note            SMALLINT     NOT NULL CHECK (note BETWEEN 1 AND 5),
    points_forts    VARCHAR(1000),
    axes_progres    VARCHAR(1000),
    evaluateur_id   UUID         NOT NULL REFERENCES td_utilisateur (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par        UUID,
    modifie_le      TIMESTAMPTZ,
    modifie_par     UUID
);
CREATE INDEX ix_td_evaluation_employe_date ON td_evaluation (employe_id, date_evaluation DESC);
