-- =============================================================================
-- V16 — Salons gérés par la DG et profil « Responsable de salon »
-- =============================================================================

-- Le nom d'un salon l'identifie dans l'interface : unique, sans tenir compte de la casse
CREATE UNIQUE INDEX ux_tp_perimetre_libelle ON tp_perimetre (lower(libelle));

-- Responsable de salon : pilotage de son salon et comptes de son équipe.
-- Aucun périmètre par défaut : le salon est fixé sur le compte à sa création.
INSERT INTO tp_profil (code, libelle, libelle_court, finalite, icone, ordre) VALUES
    ('responsable_salon', 'Responsable de salon', 'Responsable', 'Gestion de son salon : équipe, comptes, planning et qualité', 'store', 12);

INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre) VALUES
    ('responsable_salon', 'Comptes de son équipe (création, désactivation, mot de passe)', 1),
    ('responsable_salon', 'Plannings et rendez-vous', 2),
    ('responsable_salon', 'Qualité, hygiène et réclamations', 3),
    ('responsable_salon', 'Suivi de la caisse et contrôle des clôtures', 4),
    ('responsable_salon', 'Suivi des stocks', 5),
    ('responsable_salon', 'Tableau de bord du salon', 6);

-- Mêmes droits que la Manager, limités à son salon par le périmètre du compte
INSERT INTO tr_profil_module (profil_code, module_code, valeur_brute, restriction, sous_habilitation) VALUES
    ('responsable_salon', 'dashboard', 'C', NULL, FALSE),
    ('responsable_salon', 'rh', 'C (limité)', 'limité', FALSE),
    ('responsable_salon', 'planning', 'G', NULL, FALSE),
    ('responsable_salon', 'qualite', 'G', NULL, FALSE),
    ('responsable_salon', 'caisse', 'C', NULL, FALSE),
    ('responsable_salon', 'stocks', 'C (limité)', 'limité', FALSE);

INSERT INTO tr_profil_module_action (profil_code, module_code, action_code) VALUES
    ('responsable_salon', 'dashboard', 'C'),
    ('responsable_salon', 'rh', 'C'),
    ('responsable_salon', 'planning', 'G'),
    ('responsable_salon', 'qualite', 'G'),
    ('responsable_salon', 'caisse', 'C'),
    ('responsable_salon', 'stocks', 'C');
