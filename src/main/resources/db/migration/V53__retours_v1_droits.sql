-- =============================================================================
-- V53 — Retours v1 : droits (Manager dépenses lecture seule)
-- =============================================================================

-- Manager : consultation des dépenses / admin, plus de validation (réservée DG, DO par délégation)
UPDATE tr_profil_module
   SET valeur_brute = 'C', restriction = NULL
 WHERE profil_code = 'MANAGER' AND module_code = 'administration';

DELETE FROM tr_profil_module_action
 WHERE profil_code = 'MANAGER' AND module_code = 'administration' AND action_code = 'V';

INSERT INTO tr_profil_module_action (profil_code, module_code, action_code)
SELECT 'MANAGER', 'administration', 'C'
WHERE NOT EXISTS (
    SELECT 1 FROM tr_profil_module_action
    WHERE profil_code = 'MANAGER' AND module_code = 'administration' AND action_code = 'C'
);
