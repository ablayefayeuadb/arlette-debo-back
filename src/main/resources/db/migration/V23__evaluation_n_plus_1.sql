-- =============================================================================
-- V23 — Évaluations par N+1 : la DO consulte le RH pour noter ses N-1
-- (Manager, transverse, Boutique). Les Manager (rh C limité) et la DG (rh C)
-- avaient déjà l'accès lecture.
-- =============================================================================

INSERT INTO tr_profil_module (profil_code, module_code, valeur_brute, restriction, sous_habilitation)
VALUES ('DO', 'rh', 'C', NULL, FALSE)
ON CONFLICT (profil_code, module_code) DO UPDATE
SET valeur_brute = EXCLUDED.valeur_brute, restriction = EXCLUDED.restriction, sous_habilitation = EXCLUDED.sous_habilitation;

INSERT INTO tr_profil_module_action (profil_code, module_code, action_code)
VALUES ('DO', 'rh', 'C')
ON CONFLICT DO NOTHING;

UPDATE td_utilisateur SET version_jeton = version_jeton + 1 WHERE profil_code = 'DO';
