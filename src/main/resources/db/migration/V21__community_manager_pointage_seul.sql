-- =============================================================================
-- V21 — Community manager : aucun module métier pour l'instant (pointage seul).
-- Les droits communication / dashboard / stocks seront réouverts plus tard.
-- =============================================================================

DELETE FROM tr_profil_module_action WHERE profil_code = 'COMMUNITY_MANAGER';
DELETE FROM tr_profil_module WHERE profil_code = 'COMMUNITY_MANAGER';

DELETE FROM tp_profil_fonctionnalite WHERE profil_code = 'COMMUNITY_MANAGER';
INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre) VALUES
    ('COMMUNITY_MANAGER', 'Pointage arrivée / départ', 1);

UPDATE tp_profil
SET finalite = 'Pointage du personnel (accès métier à réouvrir)'
WHERE code = 'COMMUNITY_MANAGER';

-- Sessions ouvertes : recharger les droits (jeton porte encore l'ancienne matrice)
UPDATE td_utilisateur
SET version_jeton = version_jeton + 1
WHERE profil_code = 'COMMUNITY_MANAGER';
