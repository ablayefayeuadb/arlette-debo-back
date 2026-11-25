-- =============================================================================
-- V39 — ORG-03 incidents de responsabilité + DOC-01 base documentaire.
-- =============================================================================

CREATE TABLE td_incident_responsabilite (
    id                  UUID          PRIMARY KEY,
    sujet               VARCHAR(200)  NOT NULL,
    roles_concernes     VARCHAR(500)  NOT NULL,
    personnes           VARCHAR(500),
    arbitrage           VARCHAR(1000),
    action_corrective   VARCHAR(1000),
    statut              VARCHAR(20)   NOT NULL
        CHECK (statut IN ('OUVERT', 'EN_COURS', 'CLOTURE')),
    echeance            DATE,
    responsable_id      UUID          REFERENCES td_utilisateur (id),
    responsable_nom     VARCHAR(120),
    rendez_vous_id      UUID          REFERENCES td_rendez_vous (id) ON DELETE SET NULL,
    cloture_le          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    cree_le             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par            UUID,
    modifie_le          TIMESTAMPTZ,
    modifie_par         UUID
);

CREATE INDEX ix_td_incident_resp_statut ON td_incident_responsabilite (statut, echeance);

CREATE TABLE td_procedure (
    id                  UUID          PRIMARY KEY,
    titre               VARCHAR(200)  NOT NULL,
    activite            VARCHAR(80)   NOT NULL,
    proprietaire        VARCHAR(120)  NOT NULL,
    version_num         INT           NOT NULL DEFAULT 1,
    statut              VARCHAR(20)   NOT NULL
        CHECK (statut IN ('BROUILLON', 'PUBLIE', 'OBSOLETE')),
    resume              VARCHAR(2000),
    fichier_chemin      VARCHAR(500),
    fichier_nom         VARCHAR(255),
    fichier_type        VARCHAR(80),
    fichier_taille      BIGINT,
    publie_le           TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    cree_le             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par            UUID,
    modifie_le          TIMESTAMPTZ,
    modifie_par         UUID
);

CREATE INDEX ix_td_procedure_statut ON td_procedure (statut, activite);
