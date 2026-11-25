package sn.arlettedebo.platform.shared.crypto;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passe les valeurs encore en clair (legacy) en {@code v1:} + remplit les index aveugles.
 * Idempotent : les lignes déjà chiffrées sont ignorées.
 */
@Component
@Order(1)
public class ChiffrementBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChiffrementBackfill.class);

    private final JdbcTemplate jdbc;
    private final FieldCrypto crypto;

    public ChiffrementBackfill(JdbcTemplate jdbc, FieldCrypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int n = 0;
        n += backfillTexte("td_client", "telephone", true);
        n += backfillTexte("td_client", "email", false);
        n += backfillClientRecherche();
        n += backfillTexte("td_utilisateur", "email", false);
        n += backfillTexte("td_utilisateur", "telephone", false);
        n += backfillUtilisateurEmailRecherche();
        n += backfillTexte("td_employe", "telephone", false);
        n += backfillSalaire();
        n += backfillTexte("td_absence", "motif", false);
        n += backfillTexte("td_rendez_vous", "telephone", true);
        n += backfillTexte("td_rendez_vous", "motif_ecart", false);
        n += backfillRdvRecherche();
        n += backfillTexte("td_facture", "telephone", false);
        n += backfillTexte("td_demande_autorisation", "motif", false);
        if (n > 0) {
            log.info("Chiffrement : {} valeur(s) migrée(s) vers AES-GCM.", n);
        }
    }

    private int backfillTexte(String table, String colonne, boolean avecRechercheTel) {
        List<Map<String, Object>> lignes = jdbc.queryForList(
                "select id, " + colonne + " as v from " + table + " where " + colonne + " is not null"
                        + " and " + colonne + " not like 'v1:%'");
        int n = 0;
        for (Map<String, Object> row : lignes) {
            Object id = row.get("id");
            String clair = String.valueOf(row.get("v"));
            if (avecRechercheTel) {
                jdbc.update("update " + table + " set " + colonne + " = ?, telephone_recherche = ? where id = ?",
                        crypto.chiffrer(clair), crypto.rechercheTelephone(clair), id);
            } else {
                jdbc.update("update " + table + " set " + colonne + " = ? where id = ?", crypto.chiffrer(clair), id);
            }
            n++;
        }
        return n;
    }

    private int backfillClientRecherche() {
        List<Map<String, Object>> lignes = jdbc.queryForList("""
                select id, telephone from td_client
                where telephone is not null and telephone like 'v1:%'
                  and (telephone_recherche is null or telephone_recherche = '')
                """);
        // Ne peut pas déchiffrer pour remplir si déjà chiffré sans index — cas rare (crash mid-backfill).
        // Les lignes encore en clair sont traitées dans backfillTexte(..., true).
        int n = 0;
        for (Map<String, Object> row : lignes) {
            String clair = crypto.dechiffrer(String.valueOf(row.get("telephone")));
            jdbc.update("update td_client set telephone_recherche = ? where id = ?",
                    crypto.rechercheTelephone(clair), row.get("id"));
            n++;
        }
        return n;
    }

    private int backfillUtilisateurEmailRecherche() {
        List<Map<String, Object>> lignes = jdbc.queryForList("""
                select id, email from td_utilisateur
                where email is not null
                  and (email_recherche is null or email_recherche = '')
                """);
        int n = 0;
        for (Map<String, Object> row : lignes) {
            String clair = crypto.dechiffrer(String.valueOf(row.get("email")));
            jdbc.update("update td_utilisateur set email_recherche = ? where id = ?",
                    crypto.rechercheEmail(clair), row.get("id"));
            n++;
        }
        // E-mails encore en clair sans recherche (si backfillTexte a déjà chiffré dans la même passe :
        // email_recherche rempli ici après déchiffrement).
        return n;
    }

    private int backfillRdvRecherche() {
        List<Map<String, Object>> lignes = jdbc.queryForList("""
                select id, telephone from td_rendez_vous
                where telephone is not null
                  and (telephone_recherche is null or telephone_recherche = '')
                """);
        int n = 0;
        for (Map<String, Object> row : lignes) {
            String clair = crypto.dechiffrer(String.valueOf(row.get("telephone")));
            jdbc.update("update td_rendez_vous set telephone_recherche = ? where id = ?",
                    crypto.rechercheTelephone(clair), row.get("id"));
            n++;
        }
        return n;
    }

    private int backfillSalaire() {
        List<Map<String, Object>> lignes = jdbc.queryForList("""
                select id, salaire_base as v from td_employe
                where salaire_base is not null and salaire_base not like 'v1:%'
                """);
        int n = 0;
        for (Map<String, Object> row : lignes) {
            String brut = String.valueOf(row.get("v"));
            long valeur;
            try {
                valeur = Math.round(Double.parseDouble(brut.trim()));
            } catch (NumberFormatException e) {
                valeur = 0L;
            }
            jdbc.update("update td_employe set salaire_base = ? where id = ?", crypto.chiffrerLong(valeur), row.get("id"));
            n++;
        }
        return n;
    }
}
