-- Anti double réservation d'une coiffeuse : seuls les rendez-vous actifs occupent un créneau.
-- Avant : un rendez-vous ANNULEE ou ABSENTE bloquait encore la plage horaire de la coiffeuse.
ALTER TABLE td_rendez_vous DROP CONSTRAINT ex_td_rendez_vous_coiffeuse;
ALTER TABLE td_rendez_vous
    ADD CONSTRAINT ex_td_rendez_vous_coiffeuse
    EXCLUDE USING gist (coiffeuse_id WITH =, plage WITH &&) WHERE (statut NOT IN ('ANNULEE', 'ABSENTE'));
