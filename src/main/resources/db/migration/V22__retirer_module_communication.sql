-- =============================================================================
-- V22 — Suppression des accès au module Communication (ex-actions Community).
-- Aucun report sur un autre profil : le calendrier éditorial n'est plus exposé.
-- =============================================================================

DELETE FROM tr_profil_module_action WHERE module_code = 'communication';
DELETE FROM tr_profil_module WHERE module_code = 'communication';

UPDATE td_utilisateur SET version_jeton = version_jeton + 1;
