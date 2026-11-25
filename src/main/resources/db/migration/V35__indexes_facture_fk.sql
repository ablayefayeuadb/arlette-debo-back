-- Index FK facture (politique ContraintesBaseTests : toute FK vers td_ est indexée).
CREATE INDEX IF NOT EXISTS ix_td_facture_client ON td_facture (client_id) WHERE client_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_td_facture_encaissement ON td_facture (encaissement_id) WHERE encaissement_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_td_ligne_facture_article ON td_ligne_facture (article_id) WHERE article_id IS NOT NULL;
