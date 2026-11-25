-- =============================================================================
-- V31 — La boutique n'est plus un périmètre.
-- Les produits se rattachent aux salons (coin boutique) ; le type BOUTIQUE disparaît.
-- =============================================================================

-- 1) Sessions caisse du périmètre BOUTIQUE : historique non reporté sur un salon
UPDATE td_facture f
   SET encaissement_id = NULL
 WHERE f.encaissement_id IN (
     SELECT e.id
       FROM td_encaissement e
       JOIN td_session_caisse s ON s.id = e.session_id
      WHERE s.perimetre_code = 'BOUTIQUE'
 );

UPDATE td_mouvement_stock m
   SET encaissement_id = NULL
 WHERE m.encaissement_id IN (
     SELECT e.id
       FROM td_encaissement e
       JOIN td_session_caisse s ON s.id = e.session_id
      WHERE s.perimetre_code = 'BOUTIQUE'
 );

DELETE FROM td_justificatif_caisse j
 WHERE j.encaissement_id IN (
     SELECT e.id
       FROM td_encaissement e
       JOIN td_session_caisse s ON s.id = e.session_id
      WHERE s.perimetre_code = 'BOUTIQUE'
 )
    OR j.session_id IN (SELECT id FROM td_session_caisse WHERE perimetre_code = 'BOUTIQUE');

DELETE FROM td_encaissement e
 WHERE e.session_id IN (SELECT id FROM td_session_caisse WHERE perimetre_code = 'BOUTIQUE');

DELETE FROM td_session_caisse WHERE perimetre_code = 'BOUTIQUE';

-- 2) Données métier : rattacher au salon principal SKY
UPDATE td_article SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_commande SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_facture SET perimetre_origine = 'SKY' WHERE perimetre_origine = 'BOUTIQUE';
UPDATE td_employe SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_client SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_demande_autorisation SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_depense SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_publication SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_evenement SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_ticket_maintenance SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_non_conformite SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_reclamation SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_avis_client SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_signalement SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_checklist_hygiene SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_controle_prestation SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_rendez_vous SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE td_creneau SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE tp_prestation SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';
UPDATE tp_formule SET perimetre_code = 'SKY' WHERE perimetre_code = 'BOUTIQUE';

-- 3) Accès : vendeur boutique couvre tous les salons actifs ; les autres perdent BOUTIQUE
DELETE FROM tr_delegation_perimetre WHERE perimetre_code = 'BOUTIQUE';

UPDATE tr_utilisateur_perimetre up
   SET perimetre_code = 'SKY'
 WHERE perimetre_code = 'BOUTIQUE'
   AND NOT EXISTS (
       SELECT 1 FROM tr_utilisateur_perimetre x
        WHERE x.utilisateur_id = up.utilisateur_id AND x.perimetre_code = 'SKY'
   );

DELETE FROM tr_utilisateur_perimetre WHERE perimetre_code = 'BOUTIQUE';

DELETE FROM tr_profil_perimetre WHERE perimetre_code = 'BOUTIQUE';

-- Vendeur boutique : coin boutique du salon principal (les caissiers gèrent le coin de leur salon)
INSERT INTO tr_profil_perimetre (profil_code, perimetre_code)
SELECT 'VENDEUR_BOUTIQUE', 'SKY'
 WHERE NOT EXISTS (
       SELECT 1 FROM tr_profil_perimetre x
        WHERE x.profil_code = 'VENDEUR_BOUTIQUE' AND x.perimetre_code = 'SKY'
   );

-- Comptes vendeur sans override : héritent déjà du profil. S'ils avaient un override BOUTIQUE seul → SKY ci-dessus.

UPDATE td_utilisateur
   SET version_jeton = version_jeton + 1
 WHERE profil_code = 'VENDEUR_BOUTIQUE'
    OR id IN (SELECT utilisateur_id FROM tr_utilisateur_perimetre WHERE perimetre_code = 'SKY');

-- 4) Supprimer le périmètre et n'autoriser plus que SALON
DELETE FROM tp_perimetre WHERE code = 'BOUTIQUE';

ALTER TABLE tp_perimetre DROP CONSTRAINT IF EXISTS tp_perimetre_type_check;
ALTER TABLE tp_perimetre ADD CONSTRAINT tp_perimetre_type_check CHECK (type IN ('SALON'));
