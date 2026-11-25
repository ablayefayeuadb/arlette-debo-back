-- =============================================================================
-- V18 — Profils : codes en majuscules, alignés sur leurs noms (plus de SKY / KIDS dans les profils),
-- et libellés de métier valables pour tous les salons.
--   dg → DG · do → DO · manager → MANAGER · coordinatrice → COORDINATION · caisse_sky → CAISSIER
--   clientele_kids → CHARGE_CLIENTELE · coiffeuse → COIFFEUR · boutique → VENDEUR_BOUTIQUE
--   inventaires → INVENTAIRE · community → COMMUNICATION · administration → ADMINISTRATION
-- Les comptes, droits, salons par défaut et relais sont conservés ; les sessions ouvertes sont révoquées
-- (leur jeton porte l'ancien code).
-- =============================================================================

CREATE TEMPORARY TABLE correspondance_profil (ancien VARCHAR(30) PRIMARY KEY, nouveau VARCHAR(30) NOT NULL) ON COMMIT DROP;
INSERT INTO correspondance_profil VALUES
    ('dg', 'DG'), ('do', 'DO'), ('manager', 'MANAGER'), ('coordinatrice', 'COORDINATION'),
    ('caisse_sky', 'CAISSIER'), ('clientele_kids', 'CHARGE_CLIENTELE'), ('coiffeuse', 'COIFFEUR'),
    ('boutique', 'VENDEUR_BOUTIQUE'), ('inventaires', 'INVENTAIRE'), ('community', 'COMMUNICATION'),
    ('administration', 'ADMINISTRATION');

-- Clés étrangères levées le temps du renommage, puis rétablies à l'identique
ALTER TABLE tp_profil_fonctionnalite DROP CONSTRAINT tp_profil_fonctionnalite_profil_code_fkey;
ALTER TABLE tr_profil_module_action  DROP CONSTRAINT tr_profil_module_action_profil_code_module_code_fkey;
ALTER TABLE tr_profil_module         DROP CONSTRAINT tr_profil_module_profil_code_fkey;
ALTER TABLE tr_profil_perimetre      DROP CONSTRAINT tr_profil_perimetre_profil_code_fkey;
ALTER TABLE td_utilisateur           DROP CONSTRAINT td_utilisateur_profil_code_fkey;
ALTER TABLE td_delegation            DROP CONSTRAINT td_delegation_beneficiaire_profil_code_fkey;

UPDATE tp_profil p                SET code = c.nouveau FROM correspondance_profil c WHERE p.code = c.ancien;
UPDATE tp_profil_fonctionnalite t SET profil_code = c.nouveau FROM correspondance_profil c WHERE t.profil_code = c.ancien;
UPDATE tr_profil_module t         SET profil_code = c.nouveau FROM correspondance_profil c WHERE t.profil_code = c.ancien;
UPDATE tr_profil_module_action t  SET profil_code = c.nouveau FROM correspondance_profil c WHERE t.profil_code = c.ancien;
UPDATE tr_profil_perimetre t      SET profil_code = c.nouveau FROM correspondance_profil c WHERE t.profil_code = c.ancien;
UPDATE td_delegation t            SET beneficiaire_profil_code = c.nouveau FROM correspondance_profil c WHERE t.beneficiaire_profil_code = c.ancien;
UPDATE td_journal_audit t         SET profil_code = c.nouveau FROM correspondance_profil c WHERE t.profil_code = c.ancien;
UPDATE td_utilisateur t           SET profil_code = c.nouveau, version_jeton = t.version_jeton + 1
                                  FROM correspondance_profil c WHERE t.profil_code = c.ancien;

ALTER TABLE tp_profil_fonctionnalite ADD CONSTRAINT tp_profil_fonctionnalite_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE tr_profil_module         ADD CONSTRAINT tr_profil_module_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE tr_profil_module_action  ADD CONSTRAINT tr_profil_module_action_profil_code_module_code_fkey
    FOREIGN KEY (profil_code, module_code) REFERENCES tr_profil_module (profil_code, module_code) ON DELETE CASCADE;
ALTER TABLE tr_profil_perimetre      ADD CONSTRAINT tr_profil_perimetre_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE td_utilisateur           ADD CONSTRAINT td_utilisateur_profil_code_fkey FOREIGN KEY (profil_code) REFERENCES tp_profil (code);
ALTER TABLE td_delegation            ADD CONSTRAINT td_delegation_beneficiaire_profil_code_fkey FOREIGN KEY (beneficiaire_profil_code) REFERENCES tp_profil (code);

-- Codes en majuscules garantis pour la suite
ALTER TABLE tp_profil ADD CONSTRAINT ck_tp_profil_code_majuscules CHECK (code = upper(code));

-- Libellés de métier, valables pour tous les salons
UPDATE tp_profil SET libelle_court = 'DG',                  libelle = 'Direction Générale',
       finalite = 'Pilotage stratégique et supervision globale' WHERE code = 'DG';
UPDATE tp_profil SET libelle_court = 'DO',                  libelle = 'Direction des Opérations',
       finalite = 'Pilotage opérationnel de l''ensemble des activités' WHERE code = 'DO';
UPDATE tp_profil SET libelle_court = 'Manager',             libelle = 'Manager de salon' WHERE code = 'MANAGER';
UPDATE tp_profil SET libelle_court = 'Coordination',        libelle = 'Coordination opérationnelle',
       finalite = 'Contrôle des prestations, qualité et expérience client' WHERE code = 'COORDINATION';
UPDATE tp_profil SET libelle_court = 'Caissier',            libelle = 'Caissier – accueil et ventes',
       finalite = 'Caisse, accueil et développement commercial du salon' WHERE code = 'CAISSIER';
UPDATE tp_profil SET libelle_court = 'Chargé de clientèle', libelle = 'Chargé de clientèle – accueil, abonnements et ventes',
       finalite = 'Accueil, abonnements et développement commercial du salon ; relais caisse' WHERE code = 'CHARGE_CLIENTELE';
UPDATE tp_profil SET libelle_court = 'Coiffeur',            libelle = 'Coiffeur / Coiffeuse' WHERE code = 'COIFFEUR';
UPDATE tp_profil SET libelle_court = 'Vendeur boutique',    libelle = 'Responsable boutique – ventes et stock',
       finalite = 'Ventes et développement commercial de la boutique' WHERE code = 'VENDEUR_BOUTIQUE';
UPDATE tp_profil SET libelle_court = 'Inventaire',          libelle = 'Assistant inventaire et stocks' WHERE code = 'INVENTAIRE';
UPDATE tp_profil SET libelle_court = 'Communication',       libelle = 'Community manager',
       finalite = 'Visibilité et promotion des salons et de la boutique' WHERE code = 'COMMUNICATION';
UPDATE tp_profil SET libelle_court = 'Administration',      libelle = 'Administration et finances' WHERE code = 'ADMINISTRATION';
