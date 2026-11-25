-- V43 — Index pour signalement des doublons RDV (CLI-01 : même téléphone / cliente le même jour).

CREATE INDEX IF NOT EXISTS ix_td_rendez_vous_telephone_jour
    ON td_rendez_vous (telephone, jour)
    WHERE telephone IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_td_rendez_vous_client_jour
    ON td_rendez_vous (client_id, jour)
    WHERE client_id IS NOT NULL;
