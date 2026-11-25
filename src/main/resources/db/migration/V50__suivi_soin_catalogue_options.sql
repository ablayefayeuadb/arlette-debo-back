-- =============================================================================
-- V50 — Calendrier de soins abonnée : catalogue d'options + multi-coche + semaine
-- =============================================================================

CREATE TABLE tp_option_suivi_soin (
    code      VARCHAR(40)  PRIMARY KEY,
    libelle   VARCHAR(120) NOT NULL,
    categorie VARCHAR(30)  NOT NULL
        CHECK (categorie IN ('PREPOO', 'SHAMPOO', 'MASQUE', 'SOIN', 'LEAVE_IN', 'SECHAGE', 'COIFFURE')),
    ordre     SMALLINT     NOT NULL DEFAULT 0,
    actif     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX ix_tp_option_suivi_categorie ON tp_option_suivi_soin (categorie, ordre) WHERE actif;

COMMENT ON TABLE tp_option_suivi_soin IS 'Produits / modes cochables sur une séance de suivi abonnée';

INSERT INTO tp_option_suivi_soin (code, libelle, categorie, ordre) VALUES
-- Pré-poo
('PREPOO_CHANTILLY_AICHA',  'Chantilly Aïcha',           'PREPOO', 10),
('PREPOO_CHANTILLY_FATIMA', 'Chantilly Fatima',          'PREPOO', 20),
('PREPOO_CHANTILLY_IMAN',   'Chantilly Iman',            'PREPOO', 30),
-- Shampoo
('SHAMPOO_SADIYA',          'Sadiya',                    'SHAMPOO', 10),
('SHAMPOO_ZAHRA',           'Zahra',                     'SHAMPOO', 20),
('SHAMPOO_MARIAM',          'Mariam',                    'SHAMPOO', 30),
-- Masque
('MASQUE_DIARRA',           'Diarra',                    'MASQUE', 10),
('MASQUE_ZEYNA',            'Zeyna',                     'MASQUE', 20),
('MASQUE_ABY',              'Aby',                       'MASQUE', 30),
('MASQUE_MALICK',           'Malick',                    'MASQUE', 40),
-- Soin personnalisé
('SOIN_ANTI_PELLICULAIRE',  'Soin anti pelliculaire',    'SOIN', 10),
('SOIN_HYDRATANT',          'Soin hydratant',            'SOIN', 20),
('SOIN_HYDRATANT_PROFOND',  'Soin hydratant profond',    'SOIN', 30),
('SOIN_PROTEINE',           'Soin protéiné',             'SOIN', 40),
-- Leave-in
('LEAVE_LAIT_FABY',         'Lait Faby',                 'LEAVE_IN', 10),
('LEAVE_CHANTILLY_AICHA',   'Chantilly Aïcha',           'LEAVE_IN', 20),
('LEAVE_SPRAY_SOPHIE',      'Spray Sophie',              'LEAVE_IN', 30),
('LEAVE_SPRAY_SALY',        'Spray Saly',                'LEAVE_IN', 40),
('LEAVE_CHANTILLY_FATIMA',  'Chantilly Fatima',          'LEAVE_IN', 50),
('LEAVE_CHANTILLY_IMAN',    'Chantilly Iman',            'LEAVE_IN', 60),
('LEAVE_PACK_POUSSE',       'Pack pousse',               'LEAVE_IN', 70),
-- Séchage
('SECHAGE_AIR_FROID',       'Air froid',                 'SECHAGE', 10),
('SECHAGE_AIR_CHAUD',       'Air chaud',                 'SECHAGE', 20),
('SECHAGE_AIR_LIBRE',       'À l''air libre',            'SECHAGE', 30),
-- Coiffure protectrice
('COIFFURE_NATTES',         'Nattes',                    'COIFFURE', 10),
('COIFFURE_LIFES',          'Lifes',                     'COIFFURE', 20);

ALTER TABLE td_suivi_seance_abonnee
    ADD COLUMN IF NOT EXISTS numero_semaine SMALLINT,
    ADD COLUMN IF NOT EXISTS coiffeuse_id UUID REFERENCES td_employe (id) ON DELETE SET NULL;

CREATE INDEX ix_td_suivi_seance_coiffeuse ON td_suivi_seance_abonnee (coiffeuse_id)
    WHERE coiffeuse_id IS NOT NULL;

COMMENT ON COLUMN td_suivi_seance_abonnee.numero_semaine IS 'N° de semaine du calendrier de soins (Week N)';
COMMENT ON COLUMN td_suivi_seance_abonnee.coiffeuse_id IS 'Coiffeuse ayant réalisé la séance';

-- Choix multi (cases cochées) par séance
CREATE TABLE tr_suivi_seance_option (
    suivi_id    UUID        NOT NULL REFERENCES td_suivi_seance_abonnee (id) ON DELETE CASCADE,
    option_code VARCHAR(40) NOT NULL REFERENCES tp_option_suivi_soin (code),
    PRIMARY KEY (suivi_id, option_code)
);

CREATE INDEX ix_tr_suivi_seance_option_code ON tr_suivi_seance_option (option_code);

-- Numéro de semaine pour les suivis déjà présents (ordre chronologique par dossier)
WITH ordres AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY dossier_id ORDER BY jour ASC, cree_le ASC) AS n
    FROM td_suivi_seance_abonnee
)
UPDATE td_suivi_seance_abonnee s
SET numero_semaine = ordres.n
FROM ordres
WHERE s.id = ordres.id
  AND s.numero_semaine IS NULL;
