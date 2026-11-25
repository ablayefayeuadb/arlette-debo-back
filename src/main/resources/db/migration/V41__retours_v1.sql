-- =============================================================================
-- V41 — Retours tests v1 : type cliente, types produits, catégories prestations,
--       RDV hors créneau, dossiers abonnées + suivi de séance.
-- =============================================================================

-- Réservation / RDV
ALTER TABLE td_rendez_vous
    ADD COLUMN IF NOT EXISTS type_cliente VARCHAR(20)
        CHECK (type_cliente IS NULL OR type_cliente IN ('ABONNEE', 'CLIENTE')),
    ADD COLUMN IF NOT EXISTS hors_creneau BOOLEAN NOT NULL DEFAULT FALSE;

-- Catalogue boutique : gamme / type de produit (Masques, Chantilly…)
ALTER TABLE td_article
    ADD COLUMN IF NOT EXISTS type_produit VARCHAR(40);

CREATE INDEX IF NOT EXISTS ix_td_article_type_produit ON td_article (type_produit)
    WHERE type_produit IS NOT NULL;

-- Prestations : catégorie métier (abonnement / séance…)
ALTER TABLE td_prestation
    ADD COLUMN IF NOT EXISTS categorie_code VARCHAR(40);

-- Dossier abonnée (fiche riche)
CREATE TABLE td_dossier_abonnee (
    id                    UUID          PRIMARY KEY,
    client_id             UUID          NOT NULL UNIQUE REFERENCES td_client (id) ON DELETE CASCADE,
    perimetre_code        VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    prenom                VARCHAR(80),
    adresse               VARCHAR(255),
    profession            VARCHAR(120),
    date_inscription      DATE,
    prise_en_charge       VARCHAR(255),
    type_abonnement       VARCHAR(30)   CHECK (type_abonnement IS NULL OR type_abonnement IN ('MENSUEL', 'BIMENSUEL', 'LOCKS')),
    -- Profil capillaire & routine (JSON flexible selon le formulaire métier)
    profil_json           JSONB         NOT NULL DEFAULT '{}'::jsonb,
    recommandations       VARCHAR(2000),
    version               BIGINT        NOT NULL DEFAULT 0,
    cree_le               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par              UUID,
    modifie_le            TIMESTAMPTZ,
    modifie_par           UUID
);

CREATE INDEX ix_td_dossier_abonnee_perimetre ON td_dossier_abonnee (perimetre_code);

-- Suivi après chaque séance
CREATE TABLE td_suivi_seance_abonnee (
    id                    UUID          PRIMARY KEY,
    dossier_id            UUID          NOT NULL REFERENCES td_dossier_abonnee (id) ON DELETE CASCADE,
    jour                  DATE          NOT NULL,
    prepoo                VARCHAR(80),
    shampoo               VARCHAR(80),
    masque                VARCHAR(80),
    soin_personnalise     VARCHAR(80),
    leave_in              VARCHAR(80),
    mode_sechage          VARCHAR(40),
    coiffure_protectrice  VARCHAR(255),
    observation           VARCHAR(1000),
    version               BIGINT        NOT NULL DEFAULT 0,
    cree_le               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par              UUID,
    modifie_le            TIMESTAMPTZ,
    modifie_par           UUID,
    CONSTRAINT uq_td_suivi_seance_jour UNIQUE (dossier_id, jour)
);

CREATE INDEX ix_td_suivi_seance_dossier_jour ON td_suivi_seance_abonnee (dossier_id, jour DESC);
