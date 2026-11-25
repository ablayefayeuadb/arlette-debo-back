-- Jour OFF (repos planifié) : DG / DO le week-end, 1 jour par semaine pour les autres profils (retours v1).
INSERT INTO tp_type_absence (code, libelle, soumis_validation) VALUES
    ('OFF', 'Jour OFF', FALSE)
ON CONFLICT (code) DO NOTHING;
