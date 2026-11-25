-- Références d'articles générées par séquence (ART-0001…), reprise après les articles existants.
CREATE SEQUENCE sq_article_reference START 1;
SELECT setval('sq_article_reference',
              GREATEST(1, COALESCE((SELECT MAX(NULLIF(regexp_replace(reference, '\D', '', 'g'), '')::int) FROM td_article), 0)),
              EXISTS (SELECT 1 FROM td_article));

-- Un seul écart d'inventaire ouvert par article : un nouveau comptage attend le traitement du précédent.
CREATE UNIQUE INDEX ux_td_comptage_ecart_ouvert ON td_comptage (article_id) WHERE statut = 'ECART_SIGNALE';

