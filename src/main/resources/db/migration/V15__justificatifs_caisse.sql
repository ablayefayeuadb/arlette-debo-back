-- =============================================================================
-- V15 — Justificatifs de caisse : ticket TPE, reçu Wave / Orange Money, bordereau de remise,
-- fiche de comptage. Rattachés à la journée de caisse et, si besoin, à un encaissement.
-- =============================================================================

CREATE TABLE td_justificatif_caisse (
    id               UUID         PRIMARY KEY,
    session_id       UUID         NOT NULL REFERENCES td_session_caisse (id),
    encaissement_id  UUID         REFERENCES td_encaissement (id),
    nature           VARCHAR(30)  NOT NULL CHECK (nature IN ('TICKET_CARTE', 'RECU_MOBILE', 'BORDEREAU_REMISE', 'FICHE_COMPTAGE', 'AUTRE')),
    commentaire      VARCHAR(160),
    nom_fichier      VARCHAR(255) NOT NULL,
    type_mime        VARCHAR(100) NOT NULL,
    taille_octets    BIGINT       NOT NULL CHECK (taille_octets > 0),
    empreinte_sha256 CHAR(64)     NOT NULL,
    chemin_stockage  VARCHAR(500) NOT NULL,
    depose_par       UUID         NOT NULL REFERENCES td_utilisateur (id),
    depose_le        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_td_justificatif_caisse_session ON td_justificatif_caisse (session_id);
CREATE INDEX ix_td_justificatif_caisse_encaissement ON td_justificatif_caisse (encaissement_id) WHERE encaissement_id IS NOT NULL;
