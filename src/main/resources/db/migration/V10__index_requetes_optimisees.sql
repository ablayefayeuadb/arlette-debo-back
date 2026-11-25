-- Index alignés sur les requêtes optimisées (audit SonarQube / analyse des plans d'exécution).

-- Dossiers et noms du personnel d'un périmètre (accès RH « limité » de la Manager), actifs ET sortis,
-- triés par nom : l'index partiel ix_td_employe_perimetre_actif (WHERE actif) ne couvre pas ce cas.
-- INCLUDE (id) : la projection « id + nom » est servie par un parcours d'index seul.
CREATE INDEX ix_td_employe_perimetre_nom ON td_employe (perimetre_code, nom) INCLUDE (id);
