-- =============================================================================
-- V28 — Téléphone client sur la facture (impression carnet boutique)
-- =============================================================================

ALTER TABLE td_facture
    ADD COLUMN telephone VARCHAR(20);
