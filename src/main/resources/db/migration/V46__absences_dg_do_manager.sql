-- V46 — Absences & congés : visibles et gérées uniquement par DG, DO et Manager
-- (plus l'Administration).

DELETE FROM tp_profil_fonctionnalite
WHERE profil_code = 'ADMINISTRATION' AND libelle = 'Absences et congés';

INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre)
SELECT p.code, 'Absences et congés',
       COALESCE((SELECT MAX(ordre) FROM tp_profil_fonctionnalite f WHERE f.profil_code = p.code), 0) + 1
FROM (VALUES ('DG'), ('DO'), ('MANAGER')) AS p(code)
WHERE NOT EXISTS (
    SELECT 1 FROM tp_profil_fonctionnalite f
    WHERE f.profil_code = p.code AND f.libelle = 'Absences et congés'
);

UPDATE td_utilisateur
SET version_jeton = version_jeton + 1
WHERE profil_code IN ('DG', 'DO', 'MANAGER', 'ADMINISTRATION');
