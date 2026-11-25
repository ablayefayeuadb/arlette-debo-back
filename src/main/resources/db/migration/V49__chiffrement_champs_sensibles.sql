-- =============================================================================
-- V49 — Chiffrement applicatif (AES-GCM) : téléphones, e-mails, salaires, motifs
-- Index aveugles HMAC pour égalité / unicité (téléphone cliente, e-mail compte, RDV).
-- La clé reste hors base (CRYPTO_KEY). Le backfill clair → v1: est fait au démarrage API.
-- =============================================================================

-- ----- Clientes -----
ALTER TABLE td_client DROP CONSTRAINT IF EXISTS ck_td_client_telephone_normalise;
DROP INDEX IF EXISTS ux_td_client_telephone;

ALTER TABLE td_client
    ALTER COLUMN telephone TYPE TEXT USING telephone::text,
    ALTER COLUMN email TYPE TEXT USING email::text;

ALTER TABLE td_client
    ADD COLUMN IF NOT EXISTS telephone_recherche VARCHAR(64);

CREATE UNIQUE INDEX ux_td_client_telephone_recherche
    ON td_client (telephone_recherche) WHERE telephone_recherche IS NOT NULL;

COMMENT ON COLUMN td_client.telephone IS 'AES-GCM (v1:) — clair uniquement en mémoire applicative';
COMMENT ON COLUMN td_client.email IS 'AES-GCM (v1:)';
COMMENT ON COLUMN td_client.telephone_recherche IS 'HMAC-SHA256 du numéro national (égalité / unicité)';

-- ----- Comptes -----
ALTER TABLE td_utilisateur DROP CONSTRAINT IF EXISTS ck_td_utilisateur_email;
DROP INDEX IF EXISTS ux_td_utilisateur_email;

ALTER TABLE td_utilisateur
    ALTER COLUMN email TYPE TEXT USING email::text,
    ALTER COLUMN telephone TYPE TEXT USING telephone::text;

ALTER TABLE td_utilisateur
    ADD COLUMN IF NOT EXISTS email_recherche VARCHAR(64);

CREATE UNIQUE INDEX ux_td_utilisateur_email_recherche
    ON td_utilisateur (email_recherche) WHERE email_recherche IS NOT NULL;

COMMENT ON COLUMN td_utilisateur.email IS 'AES-GCM (v1:) — login via email_recherche';
COMMENT ON COLUMN td_utilisateur.telephone IS 'AES-GCM (v1:) — SMS';
COMMENT ON COLUMN td_utilisateur.email_recherche IS 'HMAC-SHA256 de l''e-mail normalisé (login / unicité)';

-- ----- RH -----
ALTER TABLE td_employe DROP CONSTRAINT IF EXISTS td_employe_salaire_base_check;

ALTER TABLE td_employe
    ALTER COLUMN telephone TYPE TEXT USING telephone::text,
    ALTER COLUMN salaire_base TYPE TEXT USING salaire_base::text;

ALTER TABLE td_absence
    ALTER COLUMN motif TYPE TEXT USING motif::text;

COMMENT ON COLUMN td_employe.salaire_base IS 'AES-GCM (v1:) — long en clair côté API';
COMMENT ON COLUMN td_employe.telephone IS 'AES-GCM (v1:)';
COMMENT ON COLUMN td_absence.motif IS 'AES-GCM (v1:)';

-- ----- Planning / caisse / commercial -----
ALTER TABLE td_rendez_vous
    ALTER COLUMN telephone TYPE TEXT USING telephone::text,
    ALTER COLUMN motif_ecart TYPE TEXT USING motif_ecart::text;

ALTER TABLE td_rendez_vous
    ADD COLUMN IF NOT EXISTS telephone_recherche VARCHAR(64);

CREATE INDEX ix_td_rendez_vous_telephone_recherche
    ON td_rendez_vous (telephone_recherche)
    WHERE telephone_recherche IS NOT NULL;

ALTER TABLE td_facture
    ALTER COLUMN telephone TYPE TEXT USING telephone::text;

ALTER TABLE td_demande_autorisation
    ALTER COLUMN motif TYPE TEXT USING motif::text;

COMMENT ON COLUMN td_rendez_vous.telephone IS 'AES-GCM (v1:)';
COMMENT ON COLUMN td_rendez_vous.telephone_recherche IS 'HMAC pour anti-doublon / plafond réservation';
COMMENT ON COLUMN td_rendez_vous.motif_ecart IS 'AES-GCM (v1:)';
COMMENT ON COLUMN td_facture.telephone IS 'AES-GCM (v1:)';
COMMENT ON COLUMN td_demande_autorisation.motif IS 'AES-GCM (v1:)';
