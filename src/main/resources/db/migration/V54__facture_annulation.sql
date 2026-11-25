-- =============================================================================
-- V54 — Annulation motivée d'une facture boutique (impayée ou payée)
-- =============================================================================

ALTER TABLE td_facture
    ADD COLUMN motif_annulation VARCHAR(160),
    ADD COLUMN annulee_le       TIMESTAMPTZ,
    ADD COLUMN annulee_par      UUID;
