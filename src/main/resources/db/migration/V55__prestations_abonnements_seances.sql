-- =============================================================================
-- V54 — Lot D : prestations métier Sky (abonnements & séances), idempotent.
--
-- Prix issus du backlog : bimensuel 60 000 · mensuel 50 000 · locksés 40 000 ·
--   séance Sky 30 000 · séance locks 25 000.
-- HYPOTHÈSE : « Séance challenge (sur tresses) » n'a pas de prix au backlog →
--   20 000 FCFA proposés ; durées (min) également proposées. Le Manager peut les
--   ajuster depuis le catalogue des prestations.
-- KIDS : aucun tarif Kids défini au backlog pour ces catégories → non seedé
--   (le Manager crée les prestations Kids depuis l'écran Prestations si besoin).
-- Catégories alignées sur Prestation.CATEGORIES (V41, colonne categorie_code).
-- =============================================================================

INSERT INTO td_prestation (code, libelle, perimetre_code, duree_min, prix, ordre, actif, categorie_code)
SELECT v.code, v.libelle, v.perimetre, v.duree, v.prix, v.ordre, TRUE, v.categorie
FROM (VALUES
    ('ABO_BIMENSUEL',    'Abonnement bimensuel',                'SKY', 120, 60000, 20, 'ABO_BIMENSUEL'),
    ('ABO_MENSUEL',      'Abonnement mensuel',                  'SKY', 120, 50000, 21, 'ABO_MENSUEL'),
    ('ABO_LOCKS',        'Abonnement locksés',                  'SKY', 150, 40000, 22, 'ABO_LOCKS'),
    ('SEANCE_SKY',       'Séance Sky',                          'SKY', 120, 30000, 23, 'SEANCE_SKY'),
    ('SEANCE_LOCKS',     'Séance locks',                        'SKY', 150, 25000, 24, 'SEANCE_LOCKS'),
    ('SEANCE_CHALLENGE', 'Séance challenge (sur tresses)',      'SKY', 120, 20000, 25, 'SEANCE_CHALLENGE')
) AS v(code, libelle, perimetre, duree, prix, ordre, categorie)
WHERE NOT EXISTS (SELECT 1 FROM td_prestation p WHERE p.code = v.code)
  AND NOT EXISTS (
      SELECT 1 FROM td_prestation p
      WHERE p.perimetre_code = v.perimetre AND p.categorie_code = v.categorie
  );
