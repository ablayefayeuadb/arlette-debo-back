-- =============================================================================
-- V5 — Prestations & qualité : contrôles, non-conformités, réclamations, avis,
--      signalements, hygiène, maintenance des locaux
-- =============================================================================

CREATE TABLE td_controle_prestation (
    id              UUID         PRIMARY KEY,
    perimetre_code  VARCHAR(30)  NOT NULL REFERENCES tp_perimetre (code),
    date_controle   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rendez_vous_id  UUID         REFERENCES td_rendez_vous (id),
    prestation_code VARCHAR(40)  REFERENCES tp_prestation (code),
    coiffeuse_id    UUID         REFERENCES td_employe (id),
    controleur_id   UUID         NOT NULL REFERENCES td_utilisateur (id),
    note_accueil    SMALLINT     NOT NULL CHECK (note_accueil BETWEEN 1 AND 5),
    note_technique  SMALLINT     NOT NULL CHECK (note_technique BETWEEN 1 AND 5),
    note_hygiene    SMALLINT     NOT NULL CHECK (note_hygiene BETWEEN 1 AND 5),
    note_delai      SMALLINT     NOT NULL CHECK (note_delai BETWEEN 1 AND 5),
    conforme        BOOLEAN      NOT NULL,
    commentaire     VARCHAR(1000),
    par_delegation  BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par        UUID,
    modifie_le      TIMESTAMPTZ,
    modifie_par     UUID
);
CREATE INDEX ix_td_controle_perimetre_date ON td_controle_prestation (perimetre_code, date_controle DESC);
CREATE INDEX ix_td_controle_coiffeuse ON td_controle_prestation (coiffeuse_id, date_controle DESC);
CREATE INDEX ix_td_controle_rdv ON td_controle_prestation (rendez_vous_id) WHERE rendez_vous_id IS NOT NULL;

CREATE TABLE td_non_conformite (
    id             UUID         PRIMARY KEY,
    perimetre_code VARCHAR(30)  NOT NULL REFERENCES tp_perimetre (code),
    date_constat   DATE         NOT NULL,
    origine        VARCHAR(20)  NOT NULL CHECK (origine IN ('CONTROLE', 'RECLAMATION', 'HYGIENE', 'SIGNALEMENT', 'AUDIT')),
    description    VARCHAR(1000) NOT NULL,
    gravite        VARCHAR(20)  NOT NULL CHECK (gravite IN ('MINEURE', 'MAJEURE', 'CRITIQUE')),
    statut         VARCHAR(20)  NOT NULL CHECK (statut IN ('OUVERTE', 'ACTION_EN_COURS', 'CLOTUREE')),
    controle_id    UUID         REFERENCES td_controle_prestation (id),
    cree_par_id    UUID         NOT NULL REFERENCES td_utilisateur (id),
    cloturee_par   UUID         REFERENCES td_utilisateur (id),
    cloturee_le    TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID
);
CREATE INDEX ix_td_nc_ouvertes ON td_non_conformite (perimetre_code, date_constat DESC) WHERE statut <> 'CLOTUREE';
CREATE INDEX ix_td_nc_controle ON td_non_conformite (controle_id) WHERE controle_id IS NOT NULL;

CREATE TABLE td_action_corrective (
    id                 UUID         PRIMARY KEY,
    non_conformite_id  UUID         NOT NULL REFERENCES td_non_conformite (id) ON DELETE CASCADE,
    libelle            VARCHAR(500) NOT NULL,
    responsable_id     UUID         NOT NULL REFERENCES td_utilisateur (id),
    echeance           DATE         NOT NULL,
    statut             VARCHAR(20)  NOT NULL CHECK (statut IN ('A_FAIRE', 'FAITE', 'VERIFIEE')),
    verifiee_par       UUID         REFERENCES td_utilisateur (id),
    verifiee_le        TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,
    cree_le            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par           UUID,
    modifie_le         TIMESTAMPTZ,
    modifie_par        UUID
);
CREATE INDEX ix_td_action_corrective_nc ON td_action_corrective (non_conformite_id);
CREATE INDEX ix_td_action_corrective_echeances ON td_action_corrective (responsable_id, echeance) WHERE statut <> 'VERIFIEE';

CREATE TABLE td_reclamation (
    id               UUID         PRIMARY KEY,
    perimetre_code   VARCHAR(30)  NOT NULL REFERENCES tp_perimetre (code),
    date_reclamation TIMESTAMPTZ  NOT NULL DEFAULT now(),
    client_id        UUID         REFERENCES td_client (id),
    cliente_nom      VARCHAR(120) NOT NULL,
    rendez_vous_id   UUID         REFERENCES td_rendez_vous (id),
    canal            VARCHAR(20)  NOT NULL CHECK (canal IN ('ACCUEIL', 'TELEPHONE', 'WHATSAPP', 'RESEAUX')),
    motif            VARCHAR(1000) NOT NULL,
    gravite          VARCHAR(20)  NOT NULL DEFAULT 'MINEURE' CHECK (gravite IN ('MINEURE', 'MAJEURE', 'CRITIQUE')),
    action_immediate VARCHAR(500),
    statut           VARCHAR(20)  NOT NULL CHECK (statut IN ('NOUVELLE', 'EN_TRAITEMENT', 'RESOLUE')),
    reponse          VARCHAR(1000),
    traite_par       UUID         REFERENCES td_utilisateur (id),
    cloturee_le      TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID
);
CREATE INDEX ix_td_reclamation_ouvertes ON td_reclamation (perimetre_code, date_reclamation DESC) WHERE statut <> 'RESOLUE';
CREATE INDEX ix_td_reclamation_client ON td_reclamation (client_id) WHERE client_id IS NOT NULL;

CREATE TABLE td_avis_client (
    id             UUID          PRIMARY KEY,
    perimetre_code VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    date_avis      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    client_id      UUID          REFERENCES td_client (id),
    cliente_nom    VARCHAR(120)  NOT NULL,
    rendez_vous_id UUID          REFERENCES td_rendez_vous (id),
    note           SMALLINT      NOT NULL CHECK (note BETWEEN 1 AND 5),
    commentaire    VARCHAR(1000),
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par       UUID
);
CREATE INDEX ix_td_avis_perimetre_date ON td_avis_client (perimetre_code, date_avis DESC);

CREATE TABLE td_signalement (
    id             UUID          PRIMARY KEY,
    perimetre_code VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    date_signalement TIMESTAMPTZ NOT NULL DEFAULT now(),
    auteur_id      UUID          NOT NULL REFERENCES td_utilisateur (id),
    type_code      VARCHAR(30)   NOT NULL REFERENCES tp_type_signalement (code),
    description    VARCHAR(1000) NOT NULL,
    statut         VARCHAR(20)   NOT NULL CHECK (statut IN ('NOUVEAU', 'PRIS_EN_COMPTE', 'TRAITE')),
    traite_par     UUID          REFERENCES td_utilisateur (id),
    version        BIGINT        NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID
);
CREATE INDEX ix_td_signalement_nouveaux ON td_signalement (perimetre_code, date_signalement DESC) WHERE statut = 'NOUVEAU';
CREATE INDEX ix_td_signalement_auteur ON td_signalement (auteur_id, date_signalement DESC);

-- Hygiène : une check-list par périmètre, jour, moment et zone ; lignes détaillées
CREATE TABLE td_checklist_hygiene (
    id             UUID         PRIMARY KEY,
    perimetre_code VARCHAR(30)  NOT NULL REFERENCES tp_perimetre (code),
    jour           DATE         NOT NULL,
    moment         VARCHAR(20)  NOT NULL CHECK (moment IN ('OUVERTURE', 'APRES_PRESTATION', 'FERMETURE')),
    zone           VARCHAR(60)  NOT NULL,
    responsable_id UUID         NOT NULL REFERENCES td_utilisateur (id),
    validee_le     TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID
);
CREATE UNIQUE INDEX ux_td_checklist_hygiene ON td_checklist_hygiene (perimetre_code, jour, moment, zone);

CREATE TABLE td_checklist_hygiene_ligne (
    id           UUID         PRIMARY KEY,
    checklist_id UUID         NOT NULL REFERENCES td_checklist_hygiene (id) ON DELETE CASCADE,
    item_code    VARCHAR(40)  NOT NULL REFERENCES tp_checklist_item (code),
    conforme     BOOLEAN      NOT NULL,
    commentaire  VARCHAR(255),
    CONSTRAINT uq_td_checklist_ligne UNIQUE (checklist_id, item_code)
);

-- Maintenance & locaux : déclaré → assigné → en cours → résolu → validé
CREATE TABLE td_ticket_maintenance (
    id             UUID          PRIMARY KEY,
    perimetre_code VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    categorie      VARCHAR(40)   NOT NULL,
    local          VARCHAR(60)   NOT NULL,
    probleme       VARCHAR(1000) NOT NULL,
    urgence        VARCHAR(20)   NOT NULL CHECK (urgence IN ('BASSE', 'NORMALE', 'HAUTE')),
    statut         VARCHAR(20)   NOT NULL CHECK (statut IN ('DECLARE', 'ASSIGNE', 'EN_COURS', 'RESOLU', 'VALIDE')),
    declare_par    UUID          NOT NULL REFERENCES td_utilisateur (id),
    assigne_a      UUID          REFERENCES td_utilisateur (id),
    resolu_le      TIMESTAMPTZ,
    valide_par     UUID          REFERENCES td_utilisateur (id),
    valide_le      TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID
);
CREATE INDEX ix_td_ticket_ouverts ON td_ticket_maintenance (perimetre_code, urgence) WHERE statut <> 'VALIDE';
CREATE INDEX ix_td_ticket_assigne ON td_ticket_maintenance (assigne_a) WHERE statut IN ('ASSIGNE', 'EN_COURS');
