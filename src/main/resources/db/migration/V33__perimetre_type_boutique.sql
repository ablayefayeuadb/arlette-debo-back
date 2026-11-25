-- =============================================================================
-- V33 — Boutiques autonomes (périmètres de type BOUTIQUE), distinctes du coin
-- boutique d'un salon (articles PRODUIT_BOUTIQUE rattachés au salon).
-- =============================================================================

ALTER TABLE tp_perimetre DROP CONSTRAINT IF EXISTS tp_perimetre_type_check;
ALTER TABLE tp_perimetre ADD CONSTRAINT tp_perimetre_type_check CHECK (type IN ('SALON', 'BOUTIQUE'));
