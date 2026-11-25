-- =============================================================================
-- V26 — Paramètres de créneaux par salon (capacité et plage horaire type)
-- =============================================================================

ALTER TABLE tp_perimetre
    ADD COLUMN capacite_creneau  SMALLINT NOT NULL DEFAULT 1
        CHECK (capacite_creneau BETWEEN 1 AND 20),
    ADD COLUMN heure_ouverture  SMALLINT NOT NULL DEFAULT 9
        CHECK (heure_ouverture BETWEEN 0 AND 23),
    ADD COLUMN heure_fermeture  SMALLINT NOT NULL DEFAULT 18
        CHECK (heure_fermeture BETWEEN 1 AND 24);

ALTER TABLE tp_perimetre
    ADD CONSTRAINT ck_tp_perimetre_plage_creneaux
        CHECK (heure_fermeture > heure_ouverture);

-- Démo : Sky 2 places 9 h–18 h · Kids 1 place 10 h–17 h
UPDATE tp_perimetre SET capacite_creneau = 2, heure_ouverture = 9,  heure_fermeture = 18 WHERE code = 'SKY';
UPDATE tp_perimetre SET capacite_creneau = 1, heure_ouverture = 10, heure_fermeture = 17 WHERE code = 'KIDS';
