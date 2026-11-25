-- =============================================================================
-- V52 — Profil ABONNEE (portail cliente, hors matrice staff) + lien compte↔dossier
-- =============================================================================

INSERT INTO tp_profil (code, libelle, libelle_court, finalite, icone, ordre)
VALUES (
    'ABONNEE',
    'Abonnée — espace personnelle',
    'Abonnée',
    'Consultation de son dossier, séances, photos et rendez-vous',
    'person',
    20
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre)
SELECT v.profil_code, v.libelle, v.ordre
FROM (VALUES
    ('ABONNEE', 'Consulter ma fiche', 1),
    ('ABONNEE', 'Consulter mes séances', 2),
    ('ABONNEE', 'Consulter mes photos', 3),
    ('ABONNEE', 'Consulter mes rendez-vous', 4)
) AS v(profil_code, libelle, ordre)
WHERE NOT EXISTS (
    SELECT 1 FROM tp_profil_fonctionnalite f
    WHERE f.profil_code = v.profil_code AND f.libelle = v.libelle
);

-- Pas de tr_profil_module : aucun droit staff (C/S/G/V). Accès via API portail + PROFIL_ABONNEE.

ALTER TABLE td_dossier_abonnee
    ADD COLUMN IF NOT EXISTS utilisateur_id UUID UNIQUE REFERENCES td_utilisateur (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_td_dossier_abonnee_utilisateur
    ON td_dossier_abonnee (utilisateur_id)
    WHERE utilisateur_id IS NOT NULL;
