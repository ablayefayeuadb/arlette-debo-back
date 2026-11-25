-- =============================================================================
-- V9 — Index complémentaires
--
-- Politique d'indexation des clés étrangères :
--   * vers une table dynamique (td_) : TOUJOURS indexée (jointures, vérifications d'intégrité) ;
--   * vers un catalogue (tp_) : non indexée (quelques lignes, jamais supprimées, faible sélectivité),
--     sauf usage analytique (prestation sur les rendez-vous) ;
--   * colonne « acteur » vers td_utilisateur (…_par) : non indexée (comptes jamais supprimés, seulement
--     désactivés), sauf quand l'écran filtre par cette personne.
-- Les index partiels (WHERE … IS NOT NULL) évitent d'indexer les valeurs absentes.
-- =============================================================================

-- Références vers des données dynamiques
CREATE INDEX ix_td_avis_client_client      ON td_avis_client (client_id) WHERE client_id IS NOT NULL;
CREATE INDEX ix_td_avis_client_rdv         ON td_avis_client (rendez_vous_id) WHERE rendez_vous_id IS NOT NULL;
CREATE INDEX ix_td_client_coiffeuse        ON td_client (coiffeuse_habituelle_id) WHERE coiffeuse_habituelle_id IS NOT NULL;
CREATE INDEX ix_td_demande_autorisation_client ON td_demande_autorisation (client_id) WHERE client_id IS NOT NULL;
CREATE INDEX ix_td_encaissement_autorisation   ON td_encaissement (demande_autorisation_id) WHERE demande_autorisation_id IS NOT NULL;
CREATE INDEX ix_td_mouvement_commande      ON td_mouvement_stock (commande_id) WHERE commande_id IS NOT NULL;
CREATE INDEX ix_td_mouvement_comptage      ON td_mouvement_stock (comptage_id) WHERE comptage_id IS NOT NULL;
CREATE INDEX ix_td_mouvement_encaissement  ON td_mouvement_stock (encaissement_id) WHERE encaissement_id IS NOT NULL;
CREATE INDEX ix_td_reclamation_rdv         ON td_reclamation (rendez_vous_id) WHERE rendez_vous_id IS NOT NULL;

-- Filtres par périmètre
CREATE INDEX ix_td_publication_perimetre   ON td_publication (perimetre_code, date_prevue) WHERE perimetre_code IS NOT NULL;
CREATE INDEX ix_td_evenement_perimetre     ON td_evenement (perimetre_code, date_debut) WHERE perimetre_code IS NOT NULL;

-- Usages « par personne » (écrans personnels, contrôles d'indépendance)
CREATE INDEX ix_td_encaissement_caissier   ON td_encaissement (encaisse_par, horodatage DESC);
CREATE INDEX ix_td_commande_demandeur      ON td_commande (cree_par_id);
CREATE INDEX ix_td_comptage_saisi_par      ON td_comptage (saisi_par, horodatage DESC);
CREATE INDEX ix_td_note_interne_auteur     ON td_note_interne (auteur_id);
CREATE INDEX ix_td_evenement_responsable   ON td_evenement (responsable_id) WHERE responsable_id IS NOT NULL;
CREATE INDEX ix_td_checklist_responsable   ON td_checklist_hygiene (responsable_id, jour DESC);
CREATE INDEX ix_td_controle_controleur     ON td_controle_prestation (controleur_id, date_controle DESC);

-- Analytique : fréquentation et CA par prestation
CREATE INDEX ix_td_rendez_vous_prestation  ON td_rendez_vous (prestation_code, jour);
