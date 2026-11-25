-- Téléphone du compte (SMS d'accès) — format masqué « 77 123 45 67 »
ALTER TABLE td_utilisateur
    ADD COLUMN telephone VARCHAR(13);

COMMENT ON COLUMN td_utilisateur.telephone IS 'Numéro sénégalais masqué (9 chiffres, ex. 77 123 45 67) pour SMS';
