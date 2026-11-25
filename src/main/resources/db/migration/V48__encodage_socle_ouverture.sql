-- V48 — Encodage socle d'ouverture : catalogue prestations, compétences coiffeuses,
-- nettoyage prestations/comptes de test, paramètres manquants.

-- ---------------------------------------------------------------------------
-- Prestations : retirer le bruit, compléter le catalogue métier
-- ---------------------------------------------------------------------------

UPDATE td_prestation
SET actif = FALSE
WHERE code = 'THFGHFGH' OR lower(libelle) IN ('thfghfgh', 'test');

INSERT INTO td_prestation (code, libelle, perimetre_code, duree_min, prix, ordre, actif) VALUES
    ('LOCKS',            'Locks / entretien locks',     'SKY', 120, 35000, 6, TRUE),
    ('COUPE',            'Coupe femme',                 'SKY',  45, 12000, 7, TRUE),
    ('COLORATION',       'Coloration',                  'SKY', 120, 40000, 8, TRUE),
    ('SHAMPOOING_SOIN',  'Shampoing & soin',            'SKY',  40,  8000, 9, TRUE),
    ('POSE_EXTENSIONS',  'Pose d''extensions',          'SKY', 150, 45000, 10, TRUE),
    ('KIDS_BRUSHING',    'Brushing enfant',             'KIDS', 40,  6000, 4, TRUE),
    ('KIDS_NAPPY',       'Coiffure nappy enfant',       'KIDS', 60, 10000, 5, TRUE)
ON CONFLICT (code) DO UPDATE
SET libelle = EXCLUDED.libelle,
    duree_min = EXCLUDED.duree_min,
    prix = EXCLUDED.prix,
    ordre = EXCLUDED.ordre,
    actif = TRUE;

-- Formule locks (pass)
INSERT INTO tp_formule (code, libelle, perimetre_code, prix, nb_seances, duree_jours, actif) VALUES
    ('SKY_LOCKS_2', 'Pass locks — 2 entretiens', 'SKY', 60000, 2, 60, TRUE)
ON CONFLICT (code) DO UPDATE
SET libelle = EXCLUDED.libelle,
    prix = EXCLUDED.prix,
    nb_seances = EXCLUDED.nb_seances,
    duree_jours = EXCLUDED.duree_jours,
    actif = TRUE;

-- ---------------------------------------------------------------------------
-- Paramètres d'exploitation manquants
-- ---------------------------------------------------------------------------

INSERT INTO tp_parametre (cle, valeur, type, description) VALUES
    ('caisse.fond-defaut', '20000', 'MONTANT', 'Fond de caisse proposé à l''ouverture (modifiable à la saisie).'),
    ('planning.duree-creneau-min', '30', 'ENTIER', 'Pas de grille des créneaux en ligne (minutes).')
ON CONFLICT (cle) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Compétences : toute coiffeuse active reçoit les prestations actives de son salon
-- ---------------------------------------------------------------------------

INSERT INTO tr_employe_competence (employe_id, prestation_code, niveau, validee_le)
SELECT e.id, p.code, 2, (now() AT TIME ZONE 'Africa/Dakar')::date
FROM td_employe e
JOIN td_prestation p ON p.perimetre_code = e.perimetre_code AND p.actif
WHERE e.actif
  AND e.date_sortie IS NULL
  AND lower(e.poste) LIKE 'coiffeuse%'
  AND e.perimetre_code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tr_employe_competence c
      WHERE c.employe_id = e.id AND c.prestation_code = p.code
  );

-- Horaires de poste par défaut si absents / incohérents (09:30 → 19:00 Dakar)
UPDATE td_employe
SET heure_prise_poste = TIME '09:30',
    heure_fin_poste = TIME '19:00'
WHERE actif
  AND (heure_prise_poste IS NULL OR heure_fin_poste IS NULL
       OR heure_fin_poste <= heure_prise_poste);

-- ---------------------------------------------------------------------------
-- Comptes / dossiers de test : désactiver (pas de suppression)
-- ---------------------------------------------------------------------------

-- Comptes clairement artificiels (SMS de test, domaines jetables hors Maty déjà en poste).
UPDATE td_utilisateur u
SET actif = FALSE,
    version_jeton = version_jeton + 1,
    modifie_le = now()
WHERE u.actif
  AND (
      u.email ILIKE 'sms.%@%'
      OR u.email ILIKE '%@ujbvc.dh'
      OR u.email ~* '^[^@]*\.test\.[0-9]+@'
  );

UPDATE td_employe e
SET actif = FALSE,
    date_sortie = COALESCE(date_sortie, (now() AT TIME ZONE 'Africa/Dakar')::date),
    modifie_le = now()
FROM td_utilisateur u
WHERE e.utilisateur_id = u.id
  AND e.actif
  AND (
      u.email ILIKE 'sms.%@%'
      OR u.email ILIKE '%@ujbvc.dh'
      OR u.email ~* '^[^@]*\.test\.[0-9]+@'
  );

-- ---------------------------------------------------------------------------
-- Catalogue stock de base (sans mouvements) si références absentes
-- ---------------------------------------------------------------------------

INSERT INTO td_article (
    id, reference, nom, categorie_code, perimetre_code, unite,
    stock_actuel, seuil, prix_achat, prix_vente, fournisseur, actif, version, cree_le
)
SELECT gen_random_uuid(), v.reference, v.nom, v.categorie, v.perimetre, v.unite,
       0, v.seuil, v.prix_achat, v.prix_vente, 'Fournisseur Dakar', TRUE, 0, now()
FROM (VALUES
    ('ART-SKY-SRV',  'Serviettes (pack)',           'CONSOMMABLE',      'SKY',  'pack',   5,  8000::numeric, NULL::numeric),
    ('ART-SKY-CAP',  'Capes de coupe',              'CONSOMMABLE',      'SKY',  'pièce',  4,  4000, NULL),
    ('ART-SKY-SHP',  'Shampoing professionnel 1 L', 'CONSOMMABLE',      'SKY',  'flacon', 3,  9500, NULL),
    ('ART-SKY-MSK',  'Masque capillaire',           'CONSOMMABLE',      'SKY',  'pot',    3,  5500, 12000),
    ('ART-SKY-HUI',  'Huile de karité 250 ml',      'PRODUIT_BOUTIQUE', 'SKY',  'pièce',  5,  3000,  6500),
    ('ART-SKY-BON',  'Bonnet satin',                'PRODUIT_BOUTIQUE', 'SKY',  'pièce',  8,  2500,  5000),
    ('ART-KIDS-DEM', 'Démêlant enfant',             'CONSOMMABLE',      'KIDS', 'flacon', 4,  4500, NULL),
    ('ART-KIDS-GNT', 'Gants nitrile',               'CONSOMMABLE',      'KIDS', 'boîte',  3,  3500, NULL)
) AS v(reference, nom, categorie, perimetre, unite, seuil, prix_achat, prix_vente)
WHERE NOT EXISTS (SELECT 1 FROM td_article a WHERE a.reference = v.reference);
