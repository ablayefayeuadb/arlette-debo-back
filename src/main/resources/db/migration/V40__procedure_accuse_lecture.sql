-- =============================================================================
-- V40 — DOC-02 : accusé de lecture des procédures publiées.
-- =============================================================================

CREATE TABLE td_procedure_lecture (
    id               UUID          PRIMARY KEY,
    procedure_id     UUID          NOT NULL REFERENCES td_procedure (id) ON DELETE CASCADE,
    utilisateur_id   UUID          NOT NULL REFERENCES td_utilisateur (id) ON DELETE CASCADE,
    lu_le            TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID,
    CONSTRAINT uq_td_procedure_lecture UNIQUE (procedure_id, utilisateur_id)
);

CREATE INDEX ix_td_procedure_lecture_proc ON td_procedure_lecture (procedure_id, lu_le DESC);
CREATE INDEX ix_td_procedure_lecture_user ON td_procedure_lecture (utilisateur_id);
