-- =============================================================================
-- V54 — Lot F : bilan trimestriel de soins + échéances de paiement des abonnements
-- =============================================================================

-- Bilan trimestriel (suivi qualitatif de l'abonnée, rédigé par le staff)
CREATE TABLE td_bilan_trimestriel_abonnee (
    id                   UUID         PRIMARY KEY,
    dossier_id           UUID         NOT NULL REFERENCES td_dossier_abonnee (id) ON DELETE CASCADE,
    trimestre_debut      DATE         NOT NULL,
    analyse_objectifs    TEXT,
    progres              TEXT,
    difficultes          TEXT,
    habitudes_a_corriger TEXT,
    recommandations      TEXT,
    conclusion           VARCHAR(30)  NOT NULL
        CHECK (conclusion IN ('OBJECTIFS_ATTEINTS', 'PARTIELLEMENT_ATTEINTS', 'PROGRAMME_A_POURSUIVRE', 'NOUVEAU_DIAGNOSTIC')),
    version              BIGINT       NOT NULL DEFAULT 0,
    cree_le              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par             UUID,
    modifie_le           TIMESTAMPTZ,
    modifie_par          UUID
);

CREATE INDEX ix_td_bilan_trim_dossier ON td_bilan_trimestriel_abonnee (dossier_id, trimestre_debut DESC);

COMMENT ON TABLE td_bilan_trimestriel_abonnee IS 'Bilan de soins trimestriel d''une abonnée (objectifs, progrès, difficultés, recommandations, conclusion)';
COMMENT ON COLUMN td_bilan_trimestriel_abonnee.trimestre_debut IS 'Date du bilan / début de la période de trois mois évaluée';

-- Échéances de paiement : une par période (mois) et par dossier, reconduite à chaque séance
CREATE TABLE td_echeance_abonnement (
    id               UUID          PRIMARY KEY,
    dossier_id       UUID          NOT NULL REFERENCES td_dossier_abonnee (id) ON DELETE CASCADE,
    abonnement_id    UUID          REFERENCES td_abonnement (id) ON DELETE SET NULL,
    periode          VARCHAR(7)    NOT NULL CHECK (periode ~ '^[0-9]{4}-[0-9]{2}$'),
    echeance_le      DATE          NOT NULL,
    montant          NUMERIC(12,0) NOT NULL CHECK (montant >= 0),
    statut           VARCHAR(10)   NOT NULL DEFAULT 'ATTENDUE'
        CHECK (statut IN ('ATTENDUE', 'PAYEE', 'ANNULEE')),
    facture_id       UUID          REFERENCES td_facture (id) ON DELETE SET NULL,
    seance_suivi_id  UUID          REFERENCES td_suivi_seance_abonnee (id) ON DELETE SET NULL,
    payee_le         TIMESTAMPTZ,
    payee_par        UUID,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID
);

CREATE INDEX ix_td_echeance_dossier ON td_echeance_abonnement (dossier_id, echeance_le DESC);
CREATE INDEX ix_td_echeance_attendues ON td_echeance_abonnement (echeance_le) WHERE statut = 'ATTENDUE';
-- Une seule échéance non annulée par dossier et par mois
CREATE UNIQUE INDEX ux_td_echeance_dossier_periode
    ON td_echeance_abonnement (dossier_id, periode) WHERE statut <> 'ANNULEE';

COMMENT ON TABLE td_echeance_abonnement IS 'Paiements attendus d''un abonnement (mensuel, bimensuel, locks), un par mois de séance';
COMMENT ON COLUMN td_echeance_abonnement.periode IS 'Mois concerné (AAAA-MM)';
