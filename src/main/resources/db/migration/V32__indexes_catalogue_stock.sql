-- Catalogue boutique / inventaire : accélère pagination, vendables et recherche texte.

-- Liste paginée (ORDER BY id DESC) filtrée par périmètre + actif
CREATE INDEX IF NOT EXISTS ix_td_article_perimetre_actif_id
    ON td_article (perimetre_code, id DESC)
    WHERE actif;

-- Vente boutique : produits avec prix de vente
CREATE INDEX IF NOT EXISTS ix_td_article_vendables
    ON td_article (perimetre_code, id DESC)
    WHERE actif AND prix_vente IS NOT NULL AND prix_vente > 0;

-- Recherche catalogue (référence / fournisseur) — nom déjà couvert par ix_td_article_nom_trgm
CREATE INDEX IF NOT EXISTS ix_td_article_reference_trgm
    ON td_article USING gin (reference gin_trgm_ops);

CREATE INDEX IF NOT EXISTS ix_td_article_fournisseur_trgm
    ON td_article USING gin (fournisseur gin_trgm_ops)
    WHERE fournisseur IS NOT NULL;
