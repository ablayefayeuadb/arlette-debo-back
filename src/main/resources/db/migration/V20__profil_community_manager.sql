-- =============================================================================
-- V20 — Profil Community manager : COMMUNICATION → COMMUNITY_MANAGER
-- (le module fonctionnel COMMUNICATION reste inchangé)
-- =============================================================================

ALTER TABLE tp_profil_fonctionnalite DROP CONSTRAINT tp_profil_fonctionnalite_profil_code_fkey;
ALTER TABLE tr_profil_module_action  DROP CONSTRAINT tr_profil_module_action_profil_code_module_code_fkey;
ALTER TABLE tr_profil_module         DROP CONSTRAINT tr_profil_module_profil_code_fkey;
ALTER TABLE tr_profil_perimetre      DROP CONSTRAINT tr_profil_perimetre_profil_code_fkey;
ALTER TABLE td_utilisateur           DROP CONSTRAINT td_utilisateur_profil_code_fkey;
ALTER TABLE td_delegation            DROP CONSTRAINT td_delegation_beneficiaire_profil_code_fkey;

UPDATE tp_profil                SET code = 'COMMUNITY_MANAGER' WHERE code = 'COMMUNICATION';
UPDATE tp_profil_fonctionnalite SET profil_code = 'COMMUNITY_MANAGER' WHERE profil_code = 'COMMUNICATION';
UPDATE tr_profil_module         SET profil_code = 'COMMUNITY_MANAGER' WHERE profil_code = 'COMMUNICATION';
UPDATE tr_profil_module_action  SET profil_code = 'COMMUNITY_MANAGER' WHERE profil_code = 'COMMUNICATION';
UPDATE tr_profil_perimetre      SET profil_code = 'COMMUNITY_MANAGER' WHERE profil_code = 'COMMUNICATION';
UPDATE td_delegation            SET beneficiaire_profil_code = 'COMMUNITY_MANAGER' WHERE beneficiaire_profil_code = 'COMMUNICATION';
UPDATE td_journal_audit         SET profil_code = 'COMMUNITY_MANAGER' WHERE profil_code = 'COMMUNICATION';
UPDATE td_utilisateur
   SET profil_code = 'COMMUNITY_MANAGER', version_jeton = version_jeton + 1
 WHERE profil_code = 'COMMUNICATION';

ALTER TABLE tp_profil_fonctionnalite ADD CONSTRAINT tp_profil_fonctionnalite_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE tr_profil_module         ADD CONSTRAINT tr_profil_module_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE tr_profil_module_action  ADD CONSTRAINT tr_profil_module_action_profil_code_module_code_fkey
    FOREIGN KEY (profil_code, module_code) REFERENCES tr_profil_module (profil_code, module_code) ON DELETE CASCADE;
ALTER TABLE tr_profil_perimetre      ADD CONSTRAINT tr_profil_perimetre_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE td_utilisateur           ADD CONSTRAINT td_utilisateur_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE td_delegation            ADD CONSTRAINT td_delegation_beneficiaire_profil_code_fkey FOREIGN KEY (beneficiaire_profil_code) REFERENCES tp_profil (code);

UPDATE tp_profil SET libelle_court = 'Community manager'
 WHERE code = 'COMMUNITY_MANAGER';
