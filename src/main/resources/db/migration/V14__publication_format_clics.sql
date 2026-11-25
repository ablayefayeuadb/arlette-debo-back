-- Calendrier éditorial : format du contenu et clics mesurés à l'analyse.
ALTER TABLE td_publication
    ADD COLUMN format VARCHAR(20) NOT NULL DEFAULT 'POST' CHECK (format IN ('POST', 'STORY', 'REEL', 'CAMPAGNE')),
    ADD COLUMN clics  INTEGER CHECK (clics >= 0);
