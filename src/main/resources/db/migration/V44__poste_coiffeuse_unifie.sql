-- V44 — Unifier le poste « Coiffeur / Coiffeuse » → « Coiffeuse »
-- (filtre planning : startsWith("coiffeuse") ; libellé profil IAM utilisé à la création de dossier).

UPDATE td_employe
SET poste = 'Coiffeuse'
WHERE lower(poste) = lower('Coiffeur / Coiffeuse')
   OR lower(poste) = 'coiffeur';

UPDATE tp_profil
SET libelle = 'Coiffeuse',
    libelle_court = 'Coiffeuse'
WHERE code = 'COIFFEUR';
