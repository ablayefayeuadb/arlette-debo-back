-- =============================================================================
-- V24 — Manager : mêmes pouvoirs que la DG (union avec les droits opérationnels
-- planning/qualité en G). Accès Utilisateurs & accès, salons, journal, finance.
-- =============================================================================

-- Matrice : union DG ∩ opérationnel Manager (planning G, qualité G conservés)
DELETE FROM tr_profil_module_action WHERE profil_code = 'MANAGER';
DELETE FROM tr_profil_module WHERE profil_code = 'MANAGER';

INSERT INTO tr_profil_module (profil_code, module_code, valeur_brute, restriction, sous_habilitation) VALUES
    ('MANAGER', 'dashboard',      'C/G/V', NULL, FALSE),
    ('MANAGER', 'rh',             'C',     NULL, FALSE),
    ('MANAGER', 'planning',       'G',     NULL, FALSE),
    ('MANAGER', 'qualite',        'G',     NULL, FALSE),
    ('MANAGER', 'caisse',         'C/V',   NULL, FALSE),
    ('MANAGER', 'stocks',         'C/V',   NULL, FALSE),
    ('MANAGER', 'administration', 'C/V',   NULL, FALSE);

INSERT INTO tr_profil_module_action (profil_code, module_code, action_code) VALUES
    ('MANAGER', 'dashboard', 'C'),
    ('MANAGER', 'dashboard', 'G'),
    ('MANAGER', 'dashboard', 'V'),
    ('MANAGER', 'rh', 'C'),
    ('MANAGER', 'planning', 'G'),
    ('MANAGER', 'qualite', 'G'),
    ('MANAGER', 'caisse', 'C'),
    ('MANAGER', 'caisse', 'V'),
    ('MANAGER', 'stocks', 'C'),
    ('MANAGER', 'stocks', 'V'),
    ('MANAGER', 'administration', 'C'),
    ('MANAGER', 'administration', 'V');

-- Tous les périmètres (comme DG / DO)
INSERT INTO tr_profil_perimetre (profil_code, perimetre_code)
SELECT 'MANAGER', code FROM tp_perimetre
WHERE code NOT IN (SELECT perimetre_code FROM tr_profil_perimetre WHERE profil_code = 'MANAGER');

UPDATE tp_profil
SET finalite = 'Direction opérationnelle des salons : mêmes pouvoirs que la DG (comptes, droits, validations, reporting)'
WHERE code = 'MANAGER';

DELETE FROM tp_profil_fonctionnalite WHERE profil_code = 'MANAGER';
INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre) VALUES
    ('MANAGER', 'Tableau de bord et reporting', 1),
    ('MANAGER', 'Utilisateurs, profils et habilitations', 2),
    ('MANAGER', 'Gestion des équipes et plannings', 3),
    ('MANAGER', 'Contrôle qualité et hygiène', 4),
    ('MANAGER', 'Validations caisse, stocks et dépenses', 5),
    ('MANAGER', 'Salons et journal d''audit', 6);

UPDATE td_utilisateur SET version_jeton = version_jeton + 1 WHERE profil_code = 'MANAGER';
