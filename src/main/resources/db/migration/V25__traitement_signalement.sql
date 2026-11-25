-- =============================================================================
-- V25 — Trace d'audit des traitements de signalement (qui, quand, de → vers).
-- =============================================================================

ALTER TABLE td_signalement ADD COLUMN IF NOT EXISTS traite_le TIMESTAMPTZ;

CREATE TABLE td_traitement_signalement (
    id              UUID          PRIMARY KEY,
    signalement_id  UUID          NOT NULL REFERENCES td_signalement (id) ON DELETE CASCADE,
    statut_avant    VARCHAR(20)   NOT NULL CHECK (statut_avant IN ('NOUVEAU', 'PRIS_EN_COMPTE', 'TRAITE')),
    statut_apres    VARCHAR(20)   NOT NULL CHECK (statut_apres IN ('NOUVEAU', 'PRIS_EN_COMPTE', 'TRAITE')),
    traite_par      UUID          NOT NULL REFERENCES td_utilisateur (id),
    traite_le       TIMESTAMPTZ   NOT NULL,
    commentaire     VARCHAR(500),
    version         BIGINT        NOT NULL DEFAULT 0,
    cree_le         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par        UUID,
    modifie_le      TIMESTAMPTZ,
    modifie_par     UUID
);

CREATE INDEX ix_td_traitement_signalement_signalement
    ON td_traitement_signalement (signalement_id, traite_le ASC);

-- Reprise des traitements déjà enregistrés (dernier statut connu seulement)
INSERT INTO td_traitement_signalement (id, signalement_id, statut_avant, statut_apres, traite_par, traite_le, version, cree_le)
SELECT gen_random_uuid(),
       s.id,
       'NOUVEAU',
       s.statut,
       s.traite_par,
       COALESCE(s.traite_le, s.modifie_le, s.cree_le),
       0,
       now()
FROM td_signalement s
WHERE s.traite_par IS NOT NULL
  AND s.statut <> 'NOUVEAU';

UPDATE td_signalement s
SET traite_le = COALESCE(s.traite_le, s.modifie_le, s.cree_le)
WHERE s.traite_par IS NOT NULL AND s.traite_le IS NULL;
