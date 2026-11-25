package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import sn.arlettedebo.platform.support.ApiTest;

/**
 * Défense en profondeur : même un accès SQL direct (bug, script, outil d'administration)
 * ne peut pas violer les règles de gestion portées par le schéma.
 */
@DisplayName("Base de données : conventions, index et règles garanties par contraintes")
class ContraintesBaseTests extends ApiTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID utilisateur(String email) {
        return jdbc.queryForObject("select id from td_utilisateur where email = ?", UUID.class, email);
    }

    @Test
    @DisplayName("Toutes les tables respectent les préfixes tp_ / td_ / tr_")
    void prefixes() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE' "
                        + "and table_name <> 'flyway_schema_history'", String.class);
        assertThat(tables).hasSizeGreaterThanOrEqualTo(60).allMatch(t -> t.matches("^(tp|td|tr)_.+"));
    }

    @Test
    @DisplayName("Politique d'index : toute clé étrangère vers une donnée dynamique est indexée")
    void clesEtrangeresIndexees() {
        List<Map<String, Object>> nonIndexees = jdbc.queryForList("""
                select c.conrelid::regclass::text as table_name, a.attname as colonne, c.confrelid::regclass::text as cible
                from pg_constraint c
                join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
                where c.contype = 'f' and c.connamespace = 'public'::regnamespace
                  and not exists (
                      select 1 from pg_index i
                      where i.indrelid = c.conrelid and i.indkey[0] = c.conkey[1])
                """);
        // Seules exceptions admises (voir V9) : catalogues tp_ et colonnes « acteur » vers td_utilisateur.
        List<String> horsPolitique = nonIndexees.stream()
                .filter(r -> !String.valueOf(r.get("cible")).matches("^(tp_.+|td_utilisateur)$"))
                .map(r -> r.get("table_name") + "." + r.get("colonne") + " → " + r.get("cible"))
                .toList();
        assertThat(horsPolitique).as("FK vers des tables td_ sans index").isEmpty();
    }

    @Test
    @DisplayName("Dépense : un demandeur ne peut pas valider sa propre demande, même en SQL")
    void separationFinanciere() {
        UUID khadija = utilisateur(ADMINISTRATION);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into td_depense (id, reference, objet, categorie_code, montant, fournisseur, statut, demande_par)
                values (?, ?, 'Test SQL', 'DIVERS', 1000, 'X', 'DEMANDEE', ?)""", id, "DEP-SQL-" + id.toString().substring(0, 8), khadija);
        assertThatThrownBy(() -> jdbc.update("update td_depense set statut = 'VALIDEE', valide_par = demande_par where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_td_depense_valideur");
    }

    @Test
    @DisplayName("Caisse : la personne qui a ouvert la caisse ne peut pas contrôler la clôture")
    void separationCaisse() {
        UUID khady = utilisateur(CAISSE_SKY);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into td_session_caisse (id, perimetre_code, jour, statut, fond_ouverture, ouverte_par, ouverte_le, cloturee_par, cloturee_le)
                values (?, 'SKY', date '2020-01-01', 'CLOTUREE', 20000, ?, now(), ?, now())""", id, khady, khady);
        assertThatThrownBy(() -> jdbc.update("update td_session_caisse set controle_par = ouverte_par where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_td_session_caisse_controle_independant");
    }

    @Test
    @DisplayName("Planning : deux rendez-vous qui se chevauchent pour une même coiffeuse sont impossibles")
    void antiDoubleReservation() {
        UUID coiffeuse = jdbc.queryForObject("select e.id from td_employe e join td_utilisateur u on u.id = e.utilisateur_id where u.email = ?",
                UUID.class, COIFFEUSE);
        String sql = """
                insert into td_rendez_vous (id, perimetre_code, jour, heure, duree_min, cliente_nom, prestation_code, coiffeuse_id, prix, statut, source)
                values (?, 'SKY', date '2030-03-10', ?::time, ?, 'Cliente', 'BRUSHING', ?, 10000, 'PREVU', 'ACCUEIL')""";
        jdbc.update(sql, UUID.randomUUID(), "10:00", 60, coiffeuse);
        jdbc.update(sql, UUID.randomUUID(), "11:00", 45, coiffeuse); // contigu : autorisé
        UUID chevauchant = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(sql, chevauchant, "10:30", 45, coiffeuse))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ex_td_rendez_vous_coiffeuse");
    }

    @Test
    @DisplayName("Inventaire : la personne qui compte ne peut pas traiter son propre écart")
    void separationInventaire() {
        UUID inv = utilisateur(INVENTAIRES);
        UUID article = UUID.randomUUID();
        jdbc.update("insert into td_article (id, reference, nom, categorie_code, perimetre_code, unite, stock_actuel, seuil) "
                + "values (?, ?, 'Serviettes', 'CONSOMMABLE', 'SKY', 'pièce', 20, 15)", article, "ART-" + article.toString().substring(0, 8));
        UUID comptage = UUID.randomUUID();
        jdbc.update("insert into td_comptage (id, article_id, theorique, compte, statut, saisi_par) values (?, ?, 20, 8, 'ECART_SIGNALE', ?)",
                comptage, article, inv);
        assertThat(jdbc.queryForObject("select ecart from td_comptage where id = ?", Integer.class, comptage)).isEqualTo(-12);
        assertThatThrownBy(() -> jdbc.update("update td_comptage set statut = 'AJUSTE', traite_par = saisi_par where id = ?", comptage))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_td_comptage_separation");
        assertThatThrownBy(() -> jdbc.update("update td_article set stock_actuel = -1 where id = ?", article))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Relais : impossible d'activer sans formation, même en SQL")
    void relaisFormation() {
        assertThatThrownBy(() -> jdbc.update("update td_delegation set active = true where code = 'RELAIS_SKY'"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_td_delegation_formation");
    }

    @Test
    @DisplayName("E-mails uniques sans tenir compte de la casse (CITEXT)")
    void emailUnique() {
        String sql = """
                insert into td_utilisateur (id, nom, email, mot_de_passe_hash, profil_code) values (?, 'Doublon', 'DG@ArletteDebo.SN', 'x', 'dg')""";
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(sql, id)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
