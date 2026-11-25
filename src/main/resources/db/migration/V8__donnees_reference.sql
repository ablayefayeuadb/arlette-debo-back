-- =============================================================================
-- V8 — Données de référence (matrice des profils & fonctionnalités, catalogues)
-- =============================================================================

INSERT INTO tp_perimetre (code, libelle, type, ordre) VALUES
    ('SKY',      'Sky Club',  'SALON',    1),
    ('KIDS',     'Kids Club', 'SALON',    2),
    ('BOUTIQUE', 'Boutique',  'BOUTIQUE', 3);

INSERT INTO tp_module (code, libelle, description, icone, ordre) VALUES
    ('dashboard',      'Tableau de bord',     'Indicateurs et alertes',                       'space_dashboard', 1),
    ('rh',             'Personnel',           'Profils, absences, congés et évaluations',      'badge',           2),
    ('planning',       'Planning & RDV',      'Rendez-vous, plannings et affectations',        'event',           3),
    ('qualite',        'Qualité',             'Contrôles, non-conformités et réclamations',    'verified',        4),
    ('caisse',         'Caisse & Ventes',     'Encaissements, clôtures et abonnements',        'point_of_sale',   5),
    ('stocks',         'Stocks & Boutique',   'Inventaires, mouvements et commandes',          'inventory_2',     6),
    ('communication',  'Communication',       'Calendrier éditorial et publications',          'campaign',        7),
    ('administration', 'Administration',      'Achats, dépenses, justificatifs et suivi RH',   'account_balance', 8);

INSERT INTO tp_action (code, libelle, ordre) VALUES
    ('C', 'Consultation', 1),
    ('S', 'Saisie / enregistrement', 2),
    ('G', 'Gestion / modification', 3),
    ('V', 'Validation / approbation', 4);

INSERT INTO tp_profil (code, libelle, libelle_court, finalite, icone, ordre) VALUES
    ('dg',             'Directrice Générale (DG)',                              'DG',             'Pilotage stratégique et supervision globale',              'workspace_premium', 1),
    ('do',             'Directeur des Opérations (DO)',                         'DO',             'Pilotage opérationnel de l''ensemble des activités',       'insights',          2),
    ('manager',        'Manager des Salons',                                    'Manager',        'Gestion quotidienne des salons SKY / KIDS',                'storefront',        3),
    ('coordinatrice',  'Coordinatrice Opérationnelle',                          'Coordinatrice',  'Coordination, qualité et expérience client',               'fact_check',        4),
    ('caisse_sky',     'Responsable Caisse & Commerciale',                      'Caisse SKY',     'Caisse, accueil et développement commercial SKY',          'point_of_sale',     5),
    ('clientele_kids', 'Chargée de Clientèle & Commerciale',                    'Clientèle KIDS', 'Accueil et développement commercial KIDS ; relais SKY',    'child_care',        6),
    ('coiffeuse',      'Coiffeuse',                                             'Coiffeuse',      'Réalisation des prestations et expérience client',         'content_cut',       7),
    ('boutique',       'Responsable Boutique',                                  'Boutique',       'Gestion et développement commercial de la Boutique',       'shopping_bag',      8),
    ('inventaires',    'Assistant Inventaires & Stocks',                        'Inventaires',    'Fiabilisation des inventaires et suivi des mouvements',    'inventory',         9),
    ('community',      'Community Manager & Content Creator',                   'Community',      'Visibilité et promotion SKY / KIDS / Boutique',            'campaign',          10),
    ('administration', 'Responsable Administration, Contrôle & Coordination',   'Administration', 'Suivi administratif, RH, financier et coordination DG',    'account_balance',   11);

INSERT INTO tr_profil_perimetre (profil_code, perimetre_code) VALUES
    ('dg', 'SKY'), ('dg', 'KIDS'), ('dg', 'BOUTIQUE'),
    ('do', 'SKY'), ('do', 'KIDS'), ('do', 'BOUTIQUE'),
    ('manager', 'SKY'), ('manager', 'KIDS'),
    ('coordinatrice', 'SKY'), ('coordinatrice', 'KIDS'),
    ('caisse_sky', 'SKY'),
    ('clientele_kids', 'KIDS'),
    ('coiffeuse', 'SKY'),
    ('boutique', 'BOUTIQUE'),
    ('inventaires', 'SKY'), ('inventaires', 'KIDS'), ('inventaires', 'BOUTIQUE'),
    ('community', 'SKY'), ('community', 'KIDS'), ('community', 'BOUTIQUE'),
    ('administration', 'SKY'), ('administration', 'KIDS'), ('administration', 'BOUTIQUE');

INSERT INTO tp_profil_fonctionnalite (profil_code, libelle, ordre) VALUES
    ('dg', 'Tableau de bord global', 1), ('dg', 'Consultation des performances', 2),
    ('dg', 'Validation des décisions stratégiques, dépenses et arbitrages', 3), ('dg', 'Suivi des reportings', 4),
    ('do', 'Tableau de bord', 1), ('do', 'Suivi CA, qualité, satisfaction et stocks', 2), ('do', 'Objectifs', 3),
    ('do', 'Coordination des équipes', 4), ('do', 'Arbitrages opérationnels selon délégation', 5), ('do', 'Reporting à la DG', 6),
    ('manager', 'Gestion des équipes et plannings', 1), ('manager', 'Suivi des rendez-vous', 2), ('manager', 'Contrôle qualité et hygiène', 3),
    ('manager', 'Réclamations', 4), ('manager', 'Évaluations clientes', 5), ('manager', 'Performance des salons', 6), ('manager', 'Incidents et plans d''action', 7),
    ('coordinatrice', 'Contrôle des prestations', 1), ('coordinatrice', 'Enregistrement des non-conformités', 2), ('coordinatrice', 'Suivi des actions correctives', 3),
    ('coordinatrice', 'Vérification des prestations réalisées', 4), ('coordinatrice', 'Reporting qualité et relais sur délégation', 5),
    ('caisse_sky', 'Encaissements', 1), ('caisse_sky', 'Justificatifs', 2), ('caisse_sky', 'Clôture quotidienne', 3), ('caisse_sky', 'Accueil', 4),
    ('caisse_sky', 'Rendez-vous', 5), ('caisse_sky', 'Suivi prospects et clientes', 6), ('caisse_sky', 'Promotion des prestations et abonnements', 7),
    ('clientele_kids', 'Rendez-vous KIDS', 1), ('clientele_kids', 'Accueil', 2), ('clientele_kids', 'Abonnements', 3), ('clientele_kids', 'Relation client', 4),
    ('clientele_kids', 'Opérations commerciales', 5), ('clientele_kids', 'Suivi des chiffres', 6), ('clientele_kids', 'Facturation et encaissements selon habilitation', 7),
    ('clientele_kids', 'Relais SKY après formation', 8),
    ('coiffeuse', 'Consultation du planning et des prestations attribuées', 1), ('coiffeuse', 'Mise à jour du statut des prestations', 2),
    ('coiffeuse', 'Signalement des incidents, insatisfactions et besoins matériels', 3),
    ('boutique', 'Ouverture et organisation', 1), ('boutique', 'Catalogue et présentation produits', 2), ('boutique', 'Ventes', 3), ('boutique', 'Suivi des stocks', 4),
    ('boutique', 'Commandes', 5), ('boutique', 'Besoins de réapprovisionnement', 6), ('boutique', 'Actions commerciales terrain', 7),
    ('inventaires', 'Saisie des comptages', 1), ('inventaires', 'Comparaison physique / théorique', 2), ('inventaires', 'Signalement des écarts', 3),
    ('inventaires', 'Suivi des mouvements autorisés', 4), ('inventaires', 'Alertes de rupture', 5), ('inventaires', 'Reporting inventaire', 6),
    ('community', 'Calendrier éditorial', 1), ('community', 'Création et dépôt de contenus', 2), ('community', 'Gestion des réseaux sociaux', 3),
    ('community', 'Campagnes', 4), ('community', 'Publications après validation', 5), ('community', 'Statistiques et performances digitales', 6),
    ('administration', 'Dossiers du personnel', 1), ('administration', 'Absences et congés', 2), ('administration', 'Éléments variables de paie', 3),
    ('administration', 'Suivi financier', 4), ('administration', 'Préparation des achats et paiements', 5), ('administration', 'Justificatifs', 6),
    ('administration', 'Reportings administratifs', 7);

INSERT INTO tp_regle_gestion (code, module_code, libelle, parametrage, ordre) VALUES
    ('SEP_CAISSE',    'caisse',         'Séparation des tâches de caisse',     'La personne qui effectue les encaissements ne valide pas seule sa propre clôture ; contrôle journalier indépendant.', 1),
    ('SEP_FINANCE',   'administration', 'Séparation des tâches financières',   'Ne pas cumuler seul demande, validation, paiement et contrôle ; validation/paiement selon les seuils DG.', 2),
    ('STOCKS',        'stocks',         'Gestion des stocks',                  'L''assistant saisit les comptages et signale les écarts ; il ne corrige pas seul les données de stock.', 3),
    ('COMMERCIAL',    'caisse',         'Décisions commerciales',              'Remises, gratuités, remboursements et compensations exceptionnelles nécessitent une autorisation.', 4),
    ('MANAGEMENT',    'qualite',        'Management opérationnel',             'La Coordinatrice intervient par délégation de la Manager et ne constitue pas un niveau hiérarchique parallèle.', 5),
    ('COMMUNICATION', 'communication',  'Communication',                       'Circuit de publication : création → vérification → validation → publication → analyse.', 6),
    ('COIFFEUSES',    'planning',       'Coiffeuses',                          'Accès limité à leurs prestations et informations nécessaires ; signalement des anomalies et besoins.', 7),
    ('REMPLACEMENT',  'rh',             'Remplacement',                        'Les fonctions de relais sont activées après formation et validation des compétences.', 8);

INSERT INTO tp_prestation (code, libelle, perimetre_code, duree_min, prix, ordre) VALUES
    ('BRUSHING',       'Brushing',                 'SKY',  45, 10000, 1),
    ('NAPPY',          'Coiffure nappy',           'SKY',  90, 20000, 2),
    ('DEFRISAGE',      'Défrisage',                'SKY',  75, 18000, 3),
    ('RITUEL_SOIN',    'Rituel soin profond',      'SKY',  60, 25000, 4),
    ('TRESSES',        'Tresses',                  'SKY', 180, 30000, 5),
    ('KIDS_TRESSES',   'Tresses enfant',           'KIDS', 90, 12000, 1),
    ('KIDS_SHAMPOING', 'Shampoing & coiffage enfant', 'KIDS', 45, 7000, 2),
    ('KIDS_SOIN',      'Soin démêlant enfant',     'KIDS', 40, 8000, 3);

INSERT INTO tp_formule (code, libelle, perimetre_code, prix, nb_seances, duree_jours) VALUES
    ('SKY_BRUSHING_4', 'Pass brushing — 4 séances',  'SKY',  36000, 4,    60),
    ('SKY_PREMIUM',    'Abonnement Premium mensuel', 'SKY',  50000, NULL, 30),
    ('KIDS_MENSUEL',   'Abonnement Kids mensuel',    'KIDS', 25000, 4,    30);

INSERT INTO tp_mode_paiement (code, libelle, ordre) VALUES
    ('ESPECES', 'Espèces', 1), ('WAVE', 'Wave', 2), ('ORANGE_MONEY', 'Orange Money', 3),
    ('CARTE', 'Carte bancaire', 4), ('VIREMENT', 'Virement', 5), ('CHEQUE', 'Chèque', 6);

INSERT INTO tp_categorie_depense (code, libelle, ordre) VALUES
    ('ACHAT_STOCK', 'Achats stock', 1), ('MAINTENANCE', 'Maintenance', 2), ('LOYER_CHARGES', 'Loyer & charges', 3),
    ('MARKETING', 'Marketing', 4), ('SALAIRES', 'Salaires & primes', 5), ('DIVERS', 'Divers', 6);

INSERT INTO tp_type_absence (code, libelle, soumis_validation) VALUES
    ('CONGE', 'Congé', TRUE), ('ABSENCE', 'Absence', FALSE), ('MALADIE', 'Maladie', FALSE),
    ('RETARD', 'Retard', FALSE), ('FORMATION', 'Formation', FALSE);

INSERT INTO tp_type_variable_paie (code, libelle, sens) VALUES
    ('PRIME', 'Prime', 1), ('HEURES_SUP', 'Heures supplémentaires', 1), ('COMMISSION', 'Commission', 1),
    ('RETENUE', 'Retenue', -1), ('AVANCE', 'Avance sur salaire', -1);

INSERT INTO tp_categorie_article (code, libelle) VALUES
    ('CONSOMMABLE', 'Consommable'), ('PRODUIT_BOUTIQUE', 'Produit boutique'), ('MATERIEL', 'Matériel');

INSERT INTO tp_type_decision (code, libelle) VALUES
    ('REMISE', 'Remise'), ('GRATUITE', 'Gratuité'), ('REMBOURSEMENT', 'Remboursement'), ('COMPENSATION', 'Compensation exceptionnelle');

INSERT INTO tp_type_signalement (code, libelle) VALUES
    ('INCIDENT', 'Incident'), ('INSATISFACTION', 'Insatisfaction cliente'),
    ('BESOIN_MATERIEL', 'Besoin matériel'), ('ANOMALIE', 'Anomalie');

INSERT INTO tp_canal_publication (code, libelle) VALUES
    ('INSTAGRAM', 'Instagram'), ('FACEBOOK', 'Facebook'), ('TIKTOK', 'TikTok'), ('WHATSAPP', 'WhatsApp'), ('SITE', 'Site web');

INSERT INTO tp_formation (code, libelle, obligatoire) VALUES
    ('HYGIENE', 'Standards d''hygiène', TRUE), ('ACCUEIL', 'Accueil cliente', TRUE),
    ('CAISSE', 'Tenue de caisse', FALSE), ('RECLAMATION', 'Gestion d''une réclamation', FALSE);

INSERT INTO tp_checklist_item (code, moment, libelle, ordre) VALUES
    ('OUV_POSTES', 'OUVERTURE', 'Postes propres', 1), ('OUV_SOLS', 'OUVERTURE', 'Sols propres', 2),
    ('OUV_SANITAIRES', 'OUVERTURE', 'Sanitaires propres', 3), ('OUV_SERVIETTES', 'OUVERTURE', 'Serviettes disponibles', 4),
    ('OUV_CAPES', 'OUVERTURE', 'Capes disponibles', 5), ('OUV_PRODUITS', 'OUVERTURE', 'Produits disponibles', 6),
    ('OUV_DESODORISANT', 'OUVERTURE', 'Désodorisant', 7), ('OUV_CUISINE', 'OUVERTURE', 'Cuisine fermée', 8),
    ('OUV_CLIM', 'OUVERTURE', 'Climatisation fonctionnelle', 9),
    ('APR_POSTE', 'APRES_PRESTATION', 'Poste nettoyé', 1), ('APR_OUTILS', 'APRES_PRESTATION', 'Outils désinfectés', 2),
    ('APR_SERVIETTES', 'APRES_PRESTATION', 'Serviettes déposées', 3), ('APR_DECHETS', 'APRES_PRESTATION', 'Déchets évacués', 4),
    ('APR_MATERIEL', 'APRES_PRESTATION', 'Matériel rangé', 5),
    ('FER_ZONE', 'FERMETURE', 'Zone validée par la responsable', 1);

INSERT INTO tp_parametre (cle, valeur, type, description) VALUES
    ('finance.seuil-dg',        '100000', 'MONTANT', 'Au-delà de ce montant, toute dépense est validée par la DG ; en deçà, la DO valide par délégation.'),
    ('pointage.rotation-qr-s',  '60',     'ENTIER',  'Durée de validité du QR de pointage (secondes).'),
    ('pointage.tolerance-min',  '5',      'ENTIER',  'Tolérance avant qu''une arrivée soit comptée en retard (minutes).');

-- Matrice des droits (source : Matrice_profils_fonctionnalites_plateforme AD.xlsx)

INSERT INTO tr_profil_module (profil_code, module_code, valeur_brute, restriction, sous_habilitation) VALUES
    ('dg', 'dashboard', 'C/G/V', NULL, FALSE),
    ('dg', 'rh', 'C', NULL, FALSE),
    ('dg', 'planning', 'C', NULL, FALSE),
    ('dg', 'qualite', 'C', NULL, FALSE),
    ('dg', 'caisse', 'C/V', NULL, FALSE),
    ('dg', 'stocks', 'C/V', NULL, FALSE),
    ('dg', 'communication', 'C/V', NULL, FALSE),
    ('dg', 'administration', 'C/V', NULL, FALSE),
    ('do', 'dashboard', 'C/G', NULL, FALSE),
    ('do', 'planning', 'C', NULL, FALSE),
    ('do', 'qualite', 'C/G', NULL, FALSE),
    ('do', 'caisse', 'C/G*', NULL, TRUE),
    ('do', 'stocks', 'C/G', NULL, FALSE),
    ('do', 'communication', 'C/G*', NULL, TRUE),
    ('do', 'administration', 'C', NULL, FALSE),
    ('manager', 'dashboard', 'C', NULL, FALSE),
    ('manager', 'rh', 'C (limité)', 'limité', FALSE),
    ('manager', 'planning', 'G', NULL, FALSE),
    ('manager', 'qualite', 'G', NULL, FALSE),
    ('manager', 'caisse', 'C', NULL, FALSE),
    ('manager', 'stocks', 'C (limité)', 'limité', FALSE),
    ('coordinatrice', 'dashboard', 'C (limité)', 'limité', FALSE),
    ('coordinatrice', 'planning', 'C', NULL, FALSE),
    ('coordinatrice', 'qualite', 'S/G', NULL, FALSE),
    ('caisse_sky', 'dashboard', 'C (limité)', 'limité', FALSE),
    ('caisse_sky', 'planning', 'G', NULL, FALSE),
    ('caisse_sky', 'qualite', 'C', NULL, FALSE),
    ('caisse_sky', 'caisse', 'S/G', NULL, FALSE),
    ('clientele_kids', 'dashboard', 'C (limité)', 'limité', FALSE),
    ('clientele_kids', 'planning', 'G', NULL, FALSE),
    ('clientele_kids', 'qualite', 'C', NULL, FALSE),
    ('clientele_kids', 'caisse', 'S/G*', NULL, TRUE),
    ('clientele_kids', 'stocks', 'C*', NULL, TRUE),
    ('coiffeuse', 'dashboard', 'C (personnel)', 'personnel', FALSE),
    ('coiffeuse', 'planning', 'C (personnel)', 'personnel', FALSE),
    ('coiffeuse', 'qualite', 'S (limité)', 'limité', FALSE),
    ('boutique', 'dashboard', 'C (limité)', 'limité', FALSE),
    ('boutique', 'planning', 'C', NULL, FALSE),
    ('boutique', 'caisse', 'S/G', NULL, FALSE),
    ('boutique', 'stocks', 'S/G', NULL, FALSE),
    ('inventaires', 'stocks', 'S (limité)', 'limité', FALSE),
    ('community', 'dashboard', 'C (statistiques)', 'statistiques', FALSE),
    ('community', 'stocks', 'C (produits)', 'produits', FALSE),
    ('community', 'communication', 'S/G*', NULL, TRUE),
    ('administration', 'dashboard', 'C (administratif)', 'administratif', FALSE),
    ('administration', 'rh', 'G', NULL, FALSE),
    ('administration', 'caisse', 'C*', NULL, TRUE),
    ('administration', 'stocks', 'C*', NULL, TRUE),
    ('administration', 'administration', 'S/G*', NULL, TRUE);

INSERT INTO tr_profil_module_action (profil_code, module_code, action_code) VALUES
    ('dg', 'dashboard', 'C'),
    ('dg', 'dashboard', 'G'),
    ('dg', 'dashboard', 'V'),
    ('dg', 'rh', 'C'),
    ('dg', 'planning', 'C'),
    ('dg', 'qualite', 'C'),
    ('dg', 'caisse', 'C'),
    ('dg', 'caisse', 'V'),
    ('dg', 'stocks', 'C'),
    ('dg', 'stocks', 'V'),
    ('dg', 'communication', 'C'),
    ('dg', 'communication', 'V'),
    ('dg', 'administration', 'C'),
    ('dg', 'administration', 'V'),
    ('do', 'dashboard', 'C'),
    ('do', 'dashboard', 'G'),
    ('do', 'planning', 'C'),
    ('do', 'qualite', 'C'),
    ('do', 'qualite', 'G'),
    ('do', 'caisse', 'C'),
    ('do', 'caisse', 'G'),
    ('do', 'stocks', 'C'),
    ('do', 'stocks', 'G'),
    ('do', 'communication', 'C'),
    ('do', 'communication', 'G'),
    ('do', 'administration', 'C'),
    ('manager', 'dashboard', 'C'),
    ('manager', 'rh', 'C'),
    ('manager', 'planning', 'G'),
    ('manager', 'qualite', 'G'),
    ('manager', 'caisse', 'C'),
    ('manager', 'stocks', 'C'),
    ('coordinatrice', 'dashboard', 'C'),
    ('coordinatrice', 'planning', 'C'),
    ('coordinatrice', 'qualite', 'S'),
    ('coordinatrice', 'qualite', 'G'),
    ('caisse_sky', 'dashboard', 'C'),
    ('caisse_sky', 'planning', 'G'),
    ('caisse_sky', 'qualite', 'C'),
    ('caisse_sky', 'caisse', 'S'),
    ('caisse_sky', 'caisse', 'G'),
    ('clientele_kids', 'dashboard', 'C'),
    ('clientele_kids', 'planning', 'G'),
    ('clientele_kids', 'qualite', 'C'),
    ('clientele_kids', 'caisse', 'S'),
    ('clientele_kids', 'caisse', 'G'),
    ('clientele_kids', 'stocks', 'C'),
    ('coiffeuse', 'dashboard', 'C'),
    ('coiffeuse', 'planning', 'C'),
    ('coiffeuse', 'qualite', 'S'),
    ('boutique', 'dashboard', 'C'),
    ('boutique', 'planning', 'C'),
    ('boutique', 'caisse', 'S'),
    ('boutique', 'caisse', 'G'),
    ('boutique', 'stocks', 'S'),
    ('boutique', 'stocks', 'G'),
    ('inventaires', 'stocks', 'S'),
    ('community', 'dashboard', 'C'),
    ('community', 'stocks', 'C'),
    ('community', 'communication', 'S'),
    ('community', 'communication', 'G'),
    ('administration', 'dashboard', 'C'),
    ('administration', 'rh', 'G'),
    ('administration', 'caisse', 'C'),
    ('administration', 'stocks', 'C'),
    ('administration', 'administration', 'S'),
    ('administration', 'administration', 'G');
