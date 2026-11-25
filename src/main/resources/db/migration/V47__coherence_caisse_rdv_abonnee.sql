-- V47 — Cohérence caisse / RDV sans coiffeuse / abonnées
-- 1) Clôturer les caisses OUVERTE des jours passés ; une seule OUVERTE par périmètre.
-- 2) Lever les doublons RDV sans coiffeuse puis exclure les chevauchements.
-- 3) Dossier manquant, abonnements manquants, rattachement client_id + séances.

-- ---------------------------------------------------------------------------
-- 1. Caisses
-- ---------------------------------------------------------------------------

-- Clôture technique des sessions encore ouvertes sur un jour passé (Africa/Dakar).
-- montant_compte = fond + espèces ⇒ écart 0 ; contrôle indépendant reste à faire.
UPDATE td_session_caisse s
SET statut = 'CLOTUREE',
    cloturee_par = s.ouverte_par,
    cloturee_le = (s.jour + TIME '19:30') AT TIME ZONE 'Africa/Dakar',
    montant_compte = s.fond_ouverture + COALESCE((
        SELECT SUM(e.montant)
        FROM td_encaissement e
        WHERE e.session_id = s.id
          AND e.annule = FALSE
          AND e.mode_paiement_code = 'ESPECES'
    ), 0),
    ecart = 0,
    modifie_le = now()
WHERE s.statut = 'OUVERTE'
  AND s.jour < (now() AT TIME ZONE 'Africa/Dakar')::date;

-- Si plusieurs OUVERTE restaient le même jour (ne devrait pas arriver), garder la plus récente.
WITH doublons AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY perimetre_code ORDER BY jour DESC, ouverte_le DESC) AS rang
    FROM td_session_caisse
    WHERE statut = 'OUVERTE'
)
UPDATE td_session_caisse s
SET statut = 'CLOTUREE',
    cloturee_par = s.ouverte_par,
    cloturee_le = now(),
    montant_compte = COALESCE(s.montant_compte, s.fond_ouverture),
    ecart = COALESCE(s.ecart, 0),
    modifie_le = now()
FROM doublons d
WHERE s.id = d.id AND d.rang > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_td_session_caisse_ouverte
    ON td_session_caisse (perimetre_code)
    WHERE statut = 'OUVERTE';

-- ---------------------------------------------------------------------------
-- 2. RDV sans coiffeuse : lever les doublons puis contrainte
-- ---------------------------------------------------------------------------

-- Annuler les doublons de créneau (même salon, plage qui se chevauche, sans coiffeuse) :
-- on conserve le plus ancien, on annule les suivants (souvent des tests).
WITH chevauche AS (
    SELECT a.id AS garder, b.id AS jeter
    FROM td_rendez_vous a
    JOIN td_rendez_vous b
      ON a.perimetre_code = b.perimetre_code
     AND a.coiffeuse_id IS NULL
     AND b.coiffeuse_id IS NULL
     AND a.statut NOT IN ('ANNULEE', 'ABSENTE')
     AND b.statut NOT IN ('ANNULEE', 'ABSENTE')
     AND a.plage && b.plage
     AND a.cree_le < b.cree_le
)
UPDATE td_rendez_vous r
SET statut = 'ANNULEE',
    modifie_le = now()
FROM chevauche c
WHERE r.id = c.jeter;

ALTER TABLE td_rendez_vous
    ADD CONSTRAINT ex_td_rendez_vous_sans_coiffeuse
    EXCLUDE USING gist (perimetre_code WITH =, plage WITH &&)
    WHERE (coiffeuse_id IS NULL AND statut NOT IN ('ANNULEE', 'ABSENTE'));

-- ---------------------------------------------------------------------------
-- 3. Abonnées : dossier, abonnement formule, client_id RDV, séances
-- ---------------------------------------------------------------------------

-- Dossier manquant pour les fiches déjà en statut ABONNEE.
INSERT INTO td_dossier_abonnee (
    id, client_id, perimetre_code, prenom, type_abonnement, date_inscription, version, cree_le
)
SELECT gen_random_uuid(),
       c.id,
       c.perimetre_code,
       NULLIF(trim(split_part(c.nom, ' ', 1)), ''),
       'MENSUEL',
       COALESCE(c.derniere_visite, (now() AT TIME ZONE 'Africa/Dakar')::date),
       0,
       now()
FROM td_client c
WHERE c.statut = 'ABONNEE'
  AND NOT EXISTS (SELECT 1 FROM td_dossier_abonnee d WHERE d.client_id = c.id);

-- Abonnement formule manquant (dossier abonnée sans ligne td_abonnement).
INSERT INTO td_abonnement (
    id, client_id, formule_code, debut, fin, seances_restantes, statut, vendu_par, version, cree_le
)
SELECT gen_random_uuid(),
       c.id,
       f.code,
       COALESCE(d.date_inscription, (now() AT TIME ZONE 'Africa/Dakar')::date),
       COALESCE(d.date_inscription, (now() AT TIME ZONE 'Africa/Dakar')::date) + (f.duree_jours - 1),
       f.nb_seances,
       'ACTIF',
       (SELECT u.id FROM td_utilisateur u WHERE u.profil_code = 'DG' AND u.actif ORDER BY u.cree_le LIMIT 1),
       0,
       now()
FROM td_client c
JOIN td_dossier_abonnee d ON d.client_id = c.id
JOIN LATERAL (
    SELECT f.code, f.duree_jours, f.nb_seances
    FROM tp_formule f
    WHERE f.perimetre_code = c.perimetre_code
      AND f.actif
    ORDER BY
        CASE
            WHEN d.type_abonnement = 'MENSUEL' AND f.code LIKE '%MENSUEL%' THEN 0
            WHEN d.type_abonnement = 'MENSUEL' AND f.code LIKE '%PREMIUM%' THEN 1
            WHEN d.type_abonnement = 'BIMENSUEL' THEN 2
            WHEN d.type_abonnement = 'LOCKS' AND f.code LIKE '%BRUSH%' THEN 3
            ELSE 9
        END,
        f.code
    LIMIT 1
) f ON TRUE
WHERE c.statut = 'ABONNEE'
  AND NOT EXISTS (SELECT 1 FROM td_abonnement a WHERE a.client_id = c.id)
  AND EXISTS (SELECT 1 FROM td_utilisateur u WHERE u.profil_code = 'DG' AND u.actif);

-- Rattacher client_id sur les RDV quand le téléphone correspond (même salon).
UPDATE td_rendez_vous r
SET client_id = c.id,
    modifie_le = now()
FROM td_client c
WHERE r.client_id IS NULL
  AND r.telephone IS NOT NULL
  AND c.telephone IS NOT NULL
  AND c.perimetre_code = r.perimetre_code
  AND regexp_replace(c.telephone, '\D', '', 'g') = regexp_replace(r.telephone, '\D', '', 'g');

-- Séances manquantes pour RDV TERMINEE liés à un dossier (par client_id ou téléphone).
INSERT INTO td_suivi_seance_abonnee (
    id, dossier_id, jour, observation, version, cree_le
)
SELECT gen_random_uuid(),
       d.id,
       r.jour,
       left(
           'RDV terminé'
           || coalesce(' · ' || to_char(r.heure, 'HH24:MI'), '')
           || coalesce(' · ' || p.libelle, coalesce(' · ' || r.prestation_code, '')),
           1000
       ),
       0,
       now()
FROM td_rendez_vous r
JOIN td_dossier_abonnee d
  ON d.client_id = r.client_id
  OR (
        r.client_id IS NULL
        AND r.telephone IS NOT NULL
        AND d.client_id = (
            SELECT c.id FROM td_client c
            WHERE c.perimetre_code = r.perimetre_code
              AND regexp_replace(c.telephone, '\D', '', 'g') = regexp_replace(r.telephone, '\D', '', 'g')
            LIMIT 1
        )
     )
LEFT JOIN td_prestation p ON p.code = coalesce(r.prestation_realisee_code, r.prestation_intention_code, r.prestation_code)
WHERE r.statut = 'TERMINEE'
  AND NOT EXISTS (
      SELECT 1 FROM td_suivi_seance_abonnee s
      WHERE s.dossier_id = d.id AND s.jour = r.jour
  );
