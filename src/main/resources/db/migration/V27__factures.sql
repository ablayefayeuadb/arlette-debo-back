-- =============================================================================
-- V27 — Factures : boutique et prestations émettent ; la caisse (salon) encaisse
-- =============================================================================

CREATE TABLE td_facture (
    id                  UUID          PRIMARY KEY,
    numero              VARCHAR(24)   NOT NULL,
    perimetre_origine   VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    jour                DATE          NOT NULL,
    origine             VARCHAR(20)   NOT NULL CHECK (origine IN ('BOUTIQUE', 'PRESTATION')),
    statut              VARCHAR(20)   NOT NULL CHECK (statut IN ('IMPAYEE', 'PAYEE', 'ANNULEE')),
    client_id           UUID          REFERENCES td_client (id),
    cliente_nom         VARCHAR(120),
    montant             NUMERIC(12,0) NOT NULL CHECK (montant > 0),
    libelle             VARCHAR(160)  NOT NULL,
    rendez_vous_id      UUID          REFERENCES td_rendez_vous (id),
    encaissement_id     UUID          REFERENCES td_encaissement (id),
    payee_le            TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    cree_le             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par            UUID,
    modifie_le          TIMESTAMPTZ,
    modifie_par         UUID,
    CONSTRAINT ux_td_facture_numero UNIQUE (numero)
);

CREATE UNIQUE INDEX ux_td_facture_rdv
    ON td_facture (rendez_vous_id)
    WHERE rendez_vous_id IS NOT NULL AND statut <> 'ANNULEE';

CREATE INDEX ix_td_facture_impayees
    ON td_facture (jour DESC, perimetre_origine)
    WHERE statut = 'IMPAYEE';

CREATE TABLE td_ligne_facture (
    id            UUID          PRIMARY KEY,
    facture_id    UUID          NOT NULL REFERENCES td_facture (id) ON DELETE CASCADE,
    article_id    UUID          REFERENCES td_article (id),
    libelle       VARCHAR(160)  NOT NULL,
    quantite      SMALLINT      NOT NULL CHECK (quantite > 0),
    prix_unitaire NUMERIC(12,0) NOT NULL CHECK (prix_unitaire >= 0),
    montant       NUMERIC(12,0) NOT NULL CHECK (montant >= 0)
);
CREATE INDEX ix_td_ligne_facture_facture ON td_ligne_facture (facture_id);

ALTER TABLE td_encaissement
    ADD COLUMN facture_id UUID REFERENCES td_facture (id);

CREATE UNIQUE INDEX ux_td_encaissement_facture
    ON td_encaissement (facture_id)
    WHERE facture_id IS NOT NULL AND NOT annule;

CREATE SEQUENCE sq_facture_numero START 1;

-- Le vendeur boutique établit la facture ; il n'encaisse plus (le client paie au caissier).
DELETE FROM tr_profil_module_action
 WHERE profil_code = 'VENDEUR_BOUTIQUE' AND module_code = 'caisse';

DELETE FROM tr_profil_module
 WHERE profil_code = 'VENDEUR_BOUTIQUE' AND module_code = 'caisse';
