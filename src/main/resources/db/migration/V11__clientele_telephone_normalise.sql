-- Téléphones clientes normalisés (chiffres seuls, sans indicatif +221) : l'index unique ux_td_client_telephone
-- détecte alors réellement les doublons (« 77 111 22 33 » et « 771112233 » sont la même cliente).
UPDATE td_client
SET telephone = CASE
        WHEN regexp_replace(telephone, '\D', '', 'g') ~ '^221[0-9]{9}$' THEN substr(regexp_replace(telephone, '\D', '', 'g'), 4)
        ELSE regexp_replace(telephone, '\D', '', 'g')
    END
WHERE telephone IS NOT NULL;

ALTER TABLE td_client
    ADD CONSTRAINT ck_td_client_telephone_normalise CHECK (telephone IS NULL OR telephone ~ '^[0-9]{7,15}$');
