-- =============================================================================
-- V4 — CRM / fidélisation & Planning / rendez-vous
-- =============================================================================

CREATE TABLE td_client (
    id                      UUID         PRIMARY KEY,
    perimetre_code          VARCHAR(30)  NOT NULL REFERENCES tp_perimetre (code),
    nom                     VARCHAR(120) NOT NULL,
    telephone               VARCHAR(20),
    email                   CITEXT,
    date_naissance          DATE,
    statut                  VARCHAR(20)  NOT NULL CHECK (statut IN ('PROSPECT', 'CLIENTE', 'ABONNEE')),
    source                  VARCHAR(30),
    coiffeuse_habituelle_id UUID REFERENCES td_employe (id) ON DELETE SET NULL,
    preferences             VARCHAR(1000),
    notes                   VARCHAR(1000),
    consentement_marketing  BOOLEAN      NOT NULL DEFAULT FALSE,
    derniere_visite         DATE,
    version                 BIGINT       NOT NULL DEFAULT 0,
    cree_le                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par                UUID,
    modifie_le              TIMESTAMPTZ,
    modifie_par             UUID
);
CREATE UNIQUE INDEX ux_td_client_telephone ON td_client (telephone) WHERE telephone IS NOT NULL;
CREATE INDEX ix_td_client_perimetre_statut ON td_client (perimetre_code, statut);
CREATE INDEX ix_td_client_nom_trgm ON td_client USING gin (nom gin_trgm_ops);
-- « Clientes inactives depuis 90 jours »
CREATE INDEX ix_td_client_derniere_visite ON td_client (derniere_visite);
-- « Anniversaires de la semaine » : index sur (mois, jour) de naissance
CREATE INDEX ix_td_client_anniversaire ON td_client ((EXTRACT(MONTH FROM date_naissance)), (EXTRACT(DAY FROM date_naissance)))
    WHERE date_naissance IS NOT NULL;

CREATE TABLE td_abonnement (
    id                UUID         PRIMARY KEY,
    client_id         UUID         NOT NULL REFERENCES td_client (id),
    formule_code      VARCHAR(40)  NOT NULL REFERENCES tp_formule (code),
    debut             DATE         NOT NULL,
    fin               DATE         NOT NULL,
    seances_restantes SMALLINT,
    statut            VARCHAR(20)  NOT NULL CHECK (statut IN ('ACTIF', 'SUSPENDU', 'EXPIRE', 'RESILIE')),
    vendu_par         UUID         NOT NULL REFERENCES td_utilisateur (id),
    version           BIGINT       NOT NULL DEFAULT 0,
    cree_le           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par          UUID,
    modifie_le        TIMESTAMPTZ,
    modifie_par       UUID,
    CONSTRAINT ck_td_abonnement_periode CHECK (fin >= debut)
);
CREATE INDEX ix_td_abonnement_client ON td_abonnement (client_id);
CREATE INDEX ix_td_abonnement_echeance ON td_abonnement (fin) WHERE statut = 'ACTIF';

-- Remises, gratuités, remboursements, compensations : autorisation DG / DO obligatoire
CREATE TABLE td_demande_autorisation (
    id             UUID          PRIMARY KEY,
    perimetre_code VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    type_code      VARCHAR(30)   NOT NULL REFERENCES tp_type_decision (code),
    client_id      UUID          REFERENCES td_client (id),
    cliente_nom    VARCHAR(120)  NOT NULL,
    montant        NUMERIC(12,0) NOT NULL CHECK (montant >= 0),
    motif          VARCHAR(500)  NOT NULL,
    statut         VARCHAR(20)   NOT NULL CHECK (statut IN ('EN_ATTENTE', 'AUTORISEE', 'REFUSEE', 'APPLIQUEE')),
    demande_par    UUID          NOT NULL REFERENCES td_utilisateur (id),
    demande_le     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decide_par     UUID          REFERENCES td_utilisateur (id),
    decide_le      TIMESTAMPTZ,
    commentaire    VARCHAR(500),
    appliquee_le   TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID,
    -- Règle « Décisions commerciales » : le demandeur ne s'autorise pas lui-même
    CONSTRAINT ck_td_demande_autorisation_separation CHECK (decide_par IS NULL OR decide_par <> demande_par)
);
CREATE INDEX ix_td_demande_autorisation_attente ON td_demande_autorisation (perimetre_code, demande_le DESC) WHERE statut = 'EN_ATTENTE';
CREATE INDEX ix_td_demande_autorisation_demandeur ON td_demande_autorisation (demande_par);

-- Créneaux ouverts à la réservation en ligne
CREATE TABLE td_creneau (
    id             UUID         PRIMARY KEY,
    perimetre_code VARCHAR(30)  NOT NULL REFERENCES tp_perimetre (code),
    jour           DATE         NOT NULL,
    heure_debut    TIME         NOT NULL,
    heure_fin      TIME         NOT NULL,
    capacite       SMALLINT     NOT NULL DEFAULT 1 CHECK (capacite > 0),
    ouvert         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0,
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par       UUID,
    modifie_le     TIMESTAMPTZ,
    modifie_par    UUID,
    CONSTRAINT ck_td_creneau_heures CHECK (heure_fin > heure_debut)
);
CREATE UNIQUE INDEX ux_td_creneau ON td_creneau (perimetre_code, jour, heure_debut);

CREATE TABLE td_rendez_vous (
    id               UUID          PRIMARY KEY,
    perimetre_code   VARCHAR(30)   NOT NULL REFERENCES tp_perimetre (code),
    jour             DATE          NOT NULL,
    heure            TIME          NOT NULL,
    duree_min        SMALLINT      NOT NULL CHECK (duree_min > 0),
    client_id        UUID          REFERENCES td_client (id),
    cliente_nom      VARCHAR(120)  NOT NULL,
    telephone        VARCHAR(20),
    prestation_code  VARCHAR(40)   NOT NULL REFERENCES tp_prestation (code),
    coiffeuse_id     UUID          REFERENCES td_employe (id),
    prix             NUMERIC(12,0) NOT NULL CHECK (prix >= 0),
    statut           VARCHAR(20)   NOT NULL CHECK (statut IN ('PREVU', 'CONFIRME', 'ARRIVEE', 'EN_COURS', 'TERMINEE', 'ANNULEE', 'ABSENTE')),
    source           VARCHAR(20)   NOT NULL CHECK (source IN ('ACCUEIL', 'EN_LIGNE', 'TELEPHONE')),
    note             VARCHAR(500),
    arrivee_le       TIMESTAMPTZ,  -- temps d'attente = debut_le - arrivee_le
    debut_le         TIMESTAMPTZ,
    fin_le           TIMESTAMPTZ,
    verifie_par      UUID          REFERENCES td_utilisateur (id),
    verifie_le       TIMESTAMPTZ,
    verifie_conforme BOOLEAN,
    -- Créneau occupé, calculé et stocké pour la contrainte d'exclusion
    plage            TSRANGE GENERATED ALWAYS AS (tsrange(jour + heure, jour + heure + make_interval(mins => duree_min))) STORED,
    version          BIGINT        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cree_par         UUID,
    modifie_le       TIMESTAMPTZ,
    modifie_par      UUID,
    -- Une coiffeuse ne peut pas avoir deux rendez-vous qui se chevauchent
    CONSTRAINT ex_td_rendez_vous_coiffeuse EXCLUDE USING gist (coiffeuse_id WITH =, plage WITH &&)
        WHERE (coiffeuse_id IS NOT NULL AND statut NOT IN ('ANNULEE', 'ABSENTE'))
);
CREATE INDEX ix_td_rendez_vous_perimetre_jour ON td_rendez_vous (perimetre_code, jour, heure);
CREATE INDEX ix_td_rendez_vous_coiffeuse_jour ON td_rendez_vous (coiffeuse_id, jour);
CREATE INDEX ix_td_rendez_vous_client ON td_rendez_vous (client_id) WHERE client_id IS NOT NULL;
CREATE INDEX ix_td_rendez_vous_actifs ON td_rendez_vous (jour) WHERE statut IN ('PREVU', 'CONFIRME', 'ARRIVEE', 'EN_COURS');
