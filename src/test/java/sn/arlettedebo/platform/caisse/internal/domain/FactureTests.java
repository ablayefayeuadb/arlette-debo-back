package sn.arlettedebo.platform.caisse.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Cycle de vie d'une facture : création, paiement, annulation, réouverture. */
@DisplayName("Facture : règles de statut")
class FactureTests {

    private static Facture impayee() {
        return new Facture("FAC-1", "SKY", LocalDate.of(2026, 11, 18), Facture.Origine.BOUTIQUE, "Vente", 5_000,
                null, "  Aïssatou  ", " 77 123 45 67 ", null);
    }

    @Test
    @DisplayName("Création : refuse un montant nul ou négatif ; normalise nom et téléphone")
    void creation() {
        assertThatThrownBy(() -> montantInvalide(0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("positif");
        assertThatThrownBy(() -> montantInvalide(-1))
                .isInstanceOf(DomainException.class);
        Facture f = impayee();
        assertThat(f.getStatut()).isEqualTo(Facture.Statut.IMPAYEE);
        assertThat(f.getClienteNom()).isEqualTo("Aïssatou");
        assertThat(f.getTelephone()).isEqualTo("77 123 45 67");
        Facture vide = new Facture("FAC-2", "SKY", LocalDate.now(), Facture.Origine.PRESTATION, "Brushing", 1,
                null, "  ", "", null);
        assertThat(vide.getClienteNom()).isNull();
        assertThat(vide.getTelephone()).isNull();
    }

    @Test
    @DisplayName("Paiement : une seule fois ; refuse si déjà payée ou annulée")
    void paiement() {
        Facture f = impayee();
        UUID enc = UUID.randomUUID();
        Instant le = Instant.parse("2026-11-18T12:00:00Z");
        f.payer(enc, le);
        assertThat(f.getStatut()).isEqualTo(Facture.Statut.PAYEE);
        assertThat(f.getEncaissementId()).isEqualTo(enc);
        assertThat(f.getPayeeLe()).isEqualTo(le);
        UUID autre = UUID.randomUUID();
        assertThatThrownBy(() -> f.payer(autre, le))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("n'est plus à encaisser");
        Facture annulee = impayee();
        annulee.annuler();
        UUID enc2 = UUID.randomUUID();
        assertThatThrownBy(() -> annulee.payer(enc2, le))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Annulation : possible tant qu'impayée ; interdite une fois payée")
    void annulation() {
        Facture f = impayee();
        f.annuler();
        assertThat(f.getStatut()).isEqualTo(Facture.Statut.ANNULEE);
        Facture payee = impayee();
        payee.payer(UUID.randomUUID(), Instant.now());
        assertThatThrownBy(payee::annuler)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("déjà payée");
    }

    @Test
    @DisplayName("Annulation d'une facture émise : impayée ou payée, motivée ; déjà annulée refusée")
    void annulationEmise() {
        Facture impayee = impayee();
        UUID par = UUID.randomUUID();
        Instant le = Instant.parse("2026-11-19T10:00:00Z");
        assertThatThrownBy(() -> impayee.annulerEmise(" ", par, le)).isInstanceOf(DomainException.class).hasMessageContaining("motif");
        impayee.annulerEmise("Erreur de saisie", par, le);
        assertThat(impayee.getStatut()).isEqualTo(Facture.Statut.ANNULEE);
        assertThat(impayee.getMotifAnnulation()).isEqualTo("Erreur de saisie");
        assertThat(impayee.getAnnuleePar()).isEqualTo(par);
        assertThatThrownBy(() -> impayee.annulerEmise("Encore", par, le)).isInstanceOf(DomainException.class).hasMessageContaining("déjà annulée");
        Facture payee = impayee();
        payee.payer(UUID.randomUUID(), le);
        payee.annulerEmise("Retour client", par, le);
        assertThat(payee.getStatut()).isEqualTo(Facture.Statut.ANNULEE);
    }

    @Test
    @DisplayName("Réouverture : seulement après paiement ; efface l'encaissement")
    void reouverture() {
        Facture f = impayee();
        assertThatThrownBy(f::reouvrirApresAnnulationEncaissement)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("payée");
        f.payer(UUID.randomUUID(), Instant.now());
        f.reouvrirApresAnnulationEncaissement();
        assertThat(f.getStatut()).isEqualTo(Facture.Statut.IMPAYEE);
        assertThat(f.getEncaissementId()).isNull();
        assertThat(f.getPayeeLe()).isNull();
    }

    @Test
    @DisplayName("Lignes : ajout sur une facture boutique ; quantité / prix invalides refusés")
    void lignes() {
        Facture f = impayee();
        UUID article = UUID.randomUUID();
        f.ajouterLigne(article, "Sérum", 2, 9_500);
        assertThat(f.getLignes()).hasSize(1);
        assertThat(f.getLignes().getFirst().getArticleId()).isEqualTo(article);
        assertThat(f.getLignes().getFirst().getQuantite()).isEqualTo((short) 2);
        assertThatThrownBy(() -> f.ajouterLigne(article, "X", 0, 100))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> f.ajouterLigne(article, "X", 1, -1))
                .isInstanceOf(DomainException.class);
    }

    private static void montantInvalide(long montant) {
        new Facture("FAC-0", "SKY", LocalDate.now(), Facture.Origine.BOUTIQUE, "X", montant, null, null, null, null);
    }
}
