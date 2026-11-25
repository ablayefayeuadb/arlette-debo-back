-- =============================================================================
-- V30 — Responsable boutique : plus d'accès Planning & RDV.
-- Métier = ventes et stock ; les rendez-vous salon ne concernent pas ce poste.
-- =============================================================================

DELETE FROM tr_profil_module_action
 WHERE profil_code = 'VENDEUR_BOUTIQUE' AND module_code = 'planning';

DELETE FROM tr_profil_module
 WHERE profil_code = 'VENDEUR_BOUTIQUE' AND module_code = 'planning';

UPDATE td_utilisateur
   SET version_jeton = version_jeton + 1
 WHERE profil_code = 'VENDEUR_BOUTIQUE';
