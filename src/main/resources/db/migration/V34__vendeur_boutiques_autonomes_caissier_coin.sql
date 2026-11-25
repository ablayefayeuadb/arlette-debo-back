-- =============================================================================
-- V34 — Vendeur boutique = boutiques autonomes seulement.
-- Coin boutique d'un salon = caissier (stocks C/S, restriction produits).
-- =============================================================================

-- 1) Profil vendeur : plus aucun salon (SKY, etc.)
DELETE FROM tr_profil_perimetre
 WHERE profil_code = 'VENDEUR_BOUTIQUE'
   AND perimetre_code IN (SELECT code FROM tp_perimetre WHERE type = 'SALON');

-- 2) Comptes vendeur : retirer les salons ; conserver uniquement les boutiques autonomes
DELETE FROM tr_utilisateur_perimetre up
 USING td_utilisateur u, tp_perimetre p
 WHERE up.utilisateur_id = u.id
   AND u.profil_code = 'VENDEUR_BOUTIQUE'
   AND up.perimetre_code = p.code
   AND p.type = 'SALON';

-- 3) Caissier : coin boutique (vente / catalogue produits du salon)
INSERT INTO tr_profil_module (profil_code, module_code, valeur_brute, restriction, sous_habilitation)
VALUES ('CAISSIER', 'stocks', 'C/S (produits)', 'produits', FALSE)
ON CONFLICT (profil_code, module_code) DO UPDATE
    SET valeur_brute = EXCLUDED.valeur_brute,
        restriction = EXCLUDED.restriction,
        sous_habilitation = EXCLUDED.sous_habilitation;

INSERT INTO tr_profil_module_action (profil_code, module_code, action_code)
VALUES ('CAISSIER', 'stocks', 'C'), ('CAISSIER', 'stocks', 'S')
ON CONFLICT DO NOTHING;

UPDATE tp_profil
   SET finalite = 'Magasins autonomes : ventes, stocks et commandes des boutiques qui lui sont rattachées'
 WHERE code = 'VENDEUR_BOUTIQUE';

DELETE FROM tp_profil_fonctionnalite WHERE profil_code = 'VENDEUR_BOUTIQUE';
INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre) VALUES
    ('VENDEUR_BOUTIQUE', 'Boutiques autonomes rattachées', 1),
    ('VENDEUR_BOUTIQUE', 'Catalogue et présentation produits', 2),
    ('VENDEUR_BOUTIQUE', 'Ventes (facture)', 3),
    ('VENDEUR_BOUTIQUE', 'Suivi des stocks et inventaires', 4),
    ('VENDEUR_BOUTIQUE', 'Commandes et réapprovisionnement', 5);

-- Caissier : coin boutique du salon
INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre)
SELECT 'CAISSIER', 'Vente coin boutique du salon', COALESCE((SELECT MAX(ordre) FROM tp_profil_fonctionnalite WHERE profil_code = 'CAISSIER'), 0) + 1
 WHERE NOT EXISTS (
       SELECT 1 FROM tp_profil_fonctionnalite
        WHERE profil_code = 'CAISSIER' AND libelle = 'Vente coin boutique du salon'
   );

UPDATE td_utilisateur
   SET version_jeton = version_jeton + 1
 WHERE profil_code IN ('VENDEUR_BOUTIQUE', 'CAISSIER');
