-- =============================================================================
-- V19 — Pointage : heure de fin de poste, départ anticipé, départ non pointé et régularisation.
-- =============================================================================

-- Paramètres de la DG (nouveau type HEURE, au format HH:MM)
ALTER TABLE tp_parametre DROP CONSTRAINT tp_parametre_type_check;
ALTER TABLE tp_parametre ADD CONSTRAINT tp_parametre_type_check CHECK (type IN ('ENTIER', 'MONTANT', 'TEXTE', 'BOOLEEN', 'HEURE'));

INSERT INTO tp_parametre (cle, valeur, type, description) VALUES
    ('pointage.heure-fin-defaut',      '19:00', 'HEURE',  'Heure de fin de poste proposée pour un nouveau dossier du personnel.'),
    ('pointage.tolerance-depart-min',  '5',     'ENTIER', 'Tolérance avant qu''un départ soit compté comme anticipé (minutes).'),
    ('pointage.controle-depart',       '23:00', 'HEURE',  'Heure à laquelle les départs non pointés de la journée sont signalés.');

-- Heure de fin de poste de chaque employé(e)
ALTER TABLE td_employe ADD COLUMN heure_fin_poste TIME NOT NULL DEFAULT '19:00';
ALTER TABLE td_employe ADD CONSTRAINT ck_td_employe_horaires CHECK (heure_fin_poste > heure_prise_poste);

-- Pointage : fin prévue recopiée à l'arrivée, départ anticipé, départ non pointé, régularisation tracée
ALTER TABLE td_pointage
    ADD COLUMN heure_fin_prevue      TIME,
    ADD COLUMN depart_anticipe_min   INTEGER     NOT NULL DEFAULT 0 CHECK (depart_anticipe_min >= 0),
    ADD COLUMN depart_non_pointe     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN regularise_par        UUID        REFERENCES td_utilisateur (id),
    ADD COLUMN regularise_le         TIMESTAMPTZ,
    ADD COLUMN motif_regularisation  VARCHAR(255),
    ADD CONSTRAINT ck_td_pointage_regularisation CHECK (regularise_par IS NULL OR motif_regularisation IS NOT NULL);

-- Départs non pointés à régulariser (tâches « À traiter » de la Manager)
CREATE INDEX ix_td_pointage_depart_non_pointe ON td_pointage (jour) WHERE depart_non_pointe AND regularise_par IS NULL;
-- Recherche par le contrôle planifié : arrivées sans départ
CREATE INDEX ix_td_pointage_sans_depart ON td_pointage (jour) WHERE arrivee_le IS NOT NULL AND depart_le IS NULL AND NOT depart_non_pointe;
