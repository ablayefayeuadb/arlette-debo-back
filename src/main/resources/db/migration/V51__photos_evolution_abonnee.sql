-- =============================================================================
-- V51 — Photos d'évolution capillaire (dossier abonnée)
-- =============================================================================

CREATE TABLE td_photo_evolution_abonnee (
    id               UUID          PRIMARY KEY,
    dossier_id       UUID          NOT NULL REFERENCES td_dossier_abonnee (id) ON DELETE CASCADE,
    suivi_id         UUID          REFERENCES td_suivi_seance_abonnee (id) ON DELETE SET NULL,
    jour             DATE          NOT NULL,
    type_photo       VARCHAR(20)   NOT NULL
        CHECK (type_photo IN ('EVOLUTION', 'AVANT', 'APRES')),
    legende          VARCHAR(255),
    nom_fichier      VARCHAR(255)  NOT NULL,
    type_mime        VARCHAR(80)   NOT NULL,
    taille_octets    BIGINT        NOT NULL CHECK (taille_octets > 0),
    empreinte_sha256 CHAR(64)      NOT NULL,
    chemin_stockage  VARCHAR(500)  NOT NULL,
    depose_par       UUID          NOT NULL REFERENCES td_utilisateur (id),
    depose_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID
);

CREATE INDEX ix_td_photo_evo_dossier_jour ON td_photo_evolution_abonnee (dossier_id, jour DESC, depose_le DESC);
CREATE INDEX ix_td_photo_evo_suivi ON td_photo_evolution_abonnee (suivi_id) WHERE suivi_id IS NOT NULL;

COMMENT ON TABLE td_photo_evolution_abonnee IS 'Photos avant / après / évolution pour suivre la pousse et l''état des cheveux';
COMMENT ON COLUMN td_photo_evolution_abonnee.type_photo IS 'EVOLUTION (timeline), AVANT / APRES (repères bilan)';
