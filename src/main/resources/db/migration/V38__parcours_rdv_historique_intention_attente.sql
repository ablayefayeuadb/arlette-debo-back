-- =============================================================================
-- V38 — Parcours RDV : historique, intention, attente, réalisé + suppléments.
-- CLI-03 / CLI-04 / CLI-06 / CLI-07 (backlog Must + Should).
-- =============================================================================

ALTER TABLE td_rendez_vous
    ADD COLUMN IF NOT EXISTS prestation_intention_code VARCHAR(40)
        REFERENCES td_prestation (code),
    ADD COLUMN IF NOT EXISTS note_arrivee VARCHAR(500),
    ADD COLUMN IF NOT EXISTS prestation_realisee_code VARCHAR(40)
        REFERENCES td_prestation (code),
    ADD COLUMN IF NOT EXISTS observations_cloture VARCHAR(500),
    ADD COLUMN IF NOT EXISTS motif_ecart VARCHAR(500);

CREATE TABLE td_rendez_vous_historique (
    id               UUID          PRIMARY KEY,
    rendez_vous_id   UUID          NOT NULL REFERENCES td_rendez_vous (id) ON DELETE CASCADE,
    champ            VARCHAR(40)   NOT NULL,
    avant            VARCHAR(255),
    apres            VARCHAR(255),
    motif            VARCHAR(500),
    acteur_id        UUID          REFERENCES td_utilisateur (id),
    le               TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID
);

CREATE INDEX ix_td_rdv_historique_rdv
    ON td_rendez_vous_historique (rendez_vous_id, le ASC);

CREATE TABLE td_attente_rdv (
    id               UUID          PRIMARY KEY,
    rendez_vous_id   UUID          NOT NULL REFERENCES td_rendez_vous (id) ON DELETE CASCADE,
    attente_minutes  INT           NOT NULL,
    motif            VARCHAR(500)  NOT NULL,
    info_cliente     BOOLEAN       NOT NULL DEFAULT FALSE,
    decision         VARCHAR(20)   NOT NULL
        CHECK (decision IN ('REALLOUER', 'REPORTER', 'MAINTENIR')),
    acteur_id        UUID          REFERENCES td_utilisateur (id),
    le               TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID
);

CREATE INDEX ix_td_attente_rdv_rdv ON td_attente_rdv (rendez_vous_id, le DESC);

CREATE TABLE td_rendez_vous_supplement (
    id               UUID          PRIMARY KEY,
    rendez_vous_id   UUID          NOT NULL REFERENCES td_rendez_vous (id) ON DELETE CASCADE,
    libelle          VARCHAR(120)  NOT NULL,
    montant          NUMERIC(12,0) NOT NULL CHECK (montant >= 0),
    accepte_le       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID
);

CREATE INDEX ix_td_rdv_supplement_rdv ON td_rendez_vous_supplement (rendez_vous_id);
