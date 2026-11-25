package sn.arlettedebo.platform.caisse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * API publique : émission de factures (boutique et prestations).
 * L'encaissement est fait ensuite par le caissier du salon, sur la caisse ouverte.
 * Le PDF Jasper est généré à la demande (données en base = source de vérité).
 */
public interface Facturation {

    /** Lecture d'une facture (réimpression boutique / caisse). */
    FactureEmise lire(UUID id);

    /** Factures boutique du périmètre, paginées (jour décroissant). */
    Page<FactureEmise> listerBoutique(String perimetre, Pageable page);

    /**
     * Établit une facture boutique (stock déjà sorti dans la même transaction) — sans encaissement.
     */
    FactureEmise etablirVenteBoutique(String perimetre, String libelle, long montant, UUID clientId, String clienteNom,
            String telephone, List<LigneVente> lignes);

    /**
     * Encaisse sur-le-champ une facture boutique qui vient d'être établie (vente en une étape).
     * Le paiement est rattaché à la caisse ouverte du jour du salon ; sans caisse ouverte (ou avec une caisse
     * d'un jour précédent encore ouverte), l'appel échoue (409) et la vente entière est annulée.
     */
    FactureEmise encaisserVenteBoutique(UUID factureId, String modePaiement);

    /**
     * Annule une facture boutique impayée ou payée, avec motif. Si elle est payée, l'encaissement lié est annulé
     * (caisse encore ouverte exigée). Déjà annulée ⇒ 409. Retourne les lignes pour la remise en stock.
     */
    FactureEmise annulerVenteBoutique(UUID factureId, String motif);

    /**
     * Établit la facture d'une prestation terminée (idempotent si une facture non annulée existe déjà pour le RDV).
     */
    @SuppressWarnings("java:S107") // contrat port : une prestation = tous les champs d'émission
    FactureEmise etablirPrestation(UUID rendezVousId, String perimetre, LocalDate jour, UUID clientId, String clienteNom,
            String telephone, String libelle, long montant);

    record LigneVente(UUID articleId, String libelle, int quantite, long prixUnitaire) {
    }

    /** Facture prête à imprimer / présenter au client. */
    record FactureEmise(UUID id, String numero, String perimetreOrigine, LocalDate jour, String origine, String statut,
            long montant, String libelle, String clienteNom, String telephone, UUID clientId, List<LigneEmise> lignes) {
    }

    record LigneEmise(UUID articleId, String libelle, int quantite, long prixUnitaire, long montant) {
    }
}
