-- Prestations = données métier (catalogue géré par le Manager), plus un référentiel figé.
-- Les FK existantes (rdv, compétences, contrôles) suivent le rename PostgreSQL.

ALTER TABLE tp_prestation RENAME TO td_prestation;
ALTER INDEX ix_tp_prestation_perimetre RENAME TO ix_td_prestation_perimetre;

COMMENT ON TABLE td_prestation IS 'Catalogue des prestations du salon — créé et tenu à jour par le Manager';
