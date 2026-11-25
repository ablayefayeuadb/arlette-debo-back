-- V45 — Rattrapage : chaque RDV TERMINEE lié à un dossier abonnée devient une séance
-- (un suivi par jour et par dossier ; les jours déjà saisis manuellement sont conservés).

INSERT INTO td_suivi_seance_abonnee (
    id, dossier_id, jour, observation, version, cree_le
)
SELECT gen_random_uuid(),
       d.id,
       r.jour,
       left(
           'RDV terminé'
           || coalesce(' · ' || to_char(r.heure, 'HH24:MI'), '')
           || coalesce(' · ' || p.libelle, coalesce(' · ' || r.prestation_code, ''))
           || coalesce(' — ' || nullif(trim(r.observations_cloture), ''), ''),
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
            WHERE c.telephone = r.telephone
              AND c.perimetre_code = r.perimetre_code
            LIMIT 1
        )
     )
LEFT JOIN td_prestation p ON p.code = coalesce(r.prestation_realisee_code, r.prestation_intention_code, r.prestation_code)
WHERE r.statut = 'TERMINEE'
  AND NOT EXISTS (
      SELECT 1 FROM td_suivi_seance_abonnee s
      WHERE s.dossier_id = d.id AND s.jour = r.jour
  );
