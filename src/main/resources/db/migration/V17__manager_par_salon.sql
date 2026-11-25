-- =============================================================================
-- V17 — La Manager joue le rôle de responsable de salon : le profil « Responsable de salon » est supprimé.
-- Le salon d'une Manager est porté par son compte (tr_utilisateur_perimetre) ; sans salon précisé,
-- elle couvre tous les salons de coiffure (salons par défaut du profil).
-- =============================================================================

-- Les comptes « Responsable de salon » deviennent Manager du même salon (salons propres conservés, sessions révoquées)
UPDATE td_utilisateur SET profil_code = 'manager', version_jeton = version_jeton + 1 WHERE profil_code = 'responsable_salon';

DELETE FROM tr_profil_module_action WHERE profil_code = 'responsable_salon';
DELETE FROM tr_profil_module WHERE profil_code = 'responsable_salon';
DELETE FROM tp_profil_fonctionnalite WHERE profil_code = 'responsable_salon';
DELETE FROM tr_profil_perimetre WHERE profil_code = 'responsable_salon';
DELETE FROM tp_profil WHERE code = 'responsable_salon';

UPDATE tp_profil
   SET finalite = 'Gestion quotidienne de ses salons : équipe, comptes, planning, qualité et performance'
 WHERE code = 'manager';

INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre) VALUES
    ('manager', 'Comptes de son équipe (création, désactivation, mot de passe)', 8);
