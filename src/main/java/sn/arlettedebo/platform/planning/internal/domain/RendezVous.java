package sn.arlettedebo.platform.planning.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.crypto.EncryptedStringConverter;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Rendez-vous. Le chevauchement de deux rendez-vous d'une même coiffeuse est interdit par la
 * contrainte d'exclusion {@code ex_td_rendez_vous_coiffeuse} (colonne générée {@code plage}).
 * Le temps d'attente se déduit de {@code arriveeLe → debutLe}.
 */
@Getter
@Entity
@Table(name = "td_rendez_vous")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RendezVous extends AuditableEntity {

    /** Transitions autorisées du cycle de vie. */
    private static final Map<Statut, Set<Statut>> TRANSITIONS = Map.of(
            Statut.PREVU, EnumSet.of(Statut.CONFIRME, Statut.ARRIVEE, Statut.ANNULEE, Statut.ABSENTE),
            Statut.CONFIRME, EnumSet.of(Statut.ARRIVEE, Statut.ANNULEE, Statut.ABSENTE),
            Statut.ARRIVEE, EnumSet.of(Statut.EN_COURS, Statut.ANNULEE),
            Statut.EN_COURS, EnumSet.of(Statut.TERMINEE),
            Statut.TERMINEE, EnumSet.noneOf(Statut.class),
            Statut.ANNULEE, EnumSet.noneOf(Statut.class),
            Statut.ABSENTE, EnumSet.noneOf(Statut.class));

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "jour", nullable = false)
    private LocalDate jour;

    @Column(name = "heure", nullable = false)
    private LocalTime heure;

    @Column(name = "duree_min", nullable = false)
    private short dureeMin;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "cliente_nom", nullable = false)
    private String clienteNom;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "telephone", columnDefinition = "text")
    private String telephone;

    @Column(name = "telephone_recherche", length = 64)
    private String telephoneRecherche;

    @Column(name = "prestation_code", nullable = false)
    private String prestationCode;

    /** Prestation confirmée / ajustée à l'arrivée (CLI-04) ; null = encore égale à la demande initiale. */
    @Column(name = "prestation_intention_code")
    private String prestationIntentionCode;

    @Column(name = "note_arrivee")
    private String noteArrivee;

    /** Prestation réellement réalisée à la clôture (CLI-07). */
    @Column(name = "prestation_realisee_code")
    private String prestationRealiseeCode;

    @Column(name = "observations_cloture")
    private String observationsCloture;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "motif_ecart", columnDefinition = "text")
    private String motifEcart;

    @Column(name = "coiffeuse_id")
    private UUID coiffeuseId;

    @Column(name = "prix", nullable = false, columnDefinition = "numeric(12,0)")
    private long prix;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private Source source;

    @Column(name = "note")
    private String note;

    @Column(name = "arrivee_le")
    private Instant arriveeLe;

    @Column(name = "debut_le")
    private Instant debutLe;

    @Column(name = "fin_le")
    private Instant finLe;

    @Column(name = "verifie_par")
    private UUID verifiePar;

    @Column(name = "verifie_le")
    private Instant verifieLe;

    @Column(name = "verifie_conforme")
    private Boolean verifieConforme;

    /** Abonnée ou cliente ponctuelle (retours v1 — réservation). */
    @Column(name = "type_cliente")
    private String typeCliente;

    /** RDV exceptionnel hors grille de créneaux (Manager / Accueil). */
    @Column(name = "hors_creneau", nullable = false)
    private boolean horsCreneau = false;

    /** Prise de rendez-vous (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String perimetre, LocalDate jour, LocalTime heure, int dureeMin, UUID clientId,
            String clienteNom, String telephone, String prestation, UUID coiffeuseId, long prix, Source source) {
    }

    public RendezVous(Donnees d) {
        this.perimetreCode = d.perimetre();
        this.jour = d.jour();
        this.heure = d.heure();
        this.dureeMin = (short) d.dureeMin();
        this.clientId = d.clientId();
        this.clienteNom = d.clienteNom();
        this.telephone = d.telephone();
        this.prestationCode = d.prestation();
        this.coiffeuseId = d.coiffeuseId();
        this.prix = d.prix();
        this.source = d.source();
        this.statut = Statut.PREVU;
    }

    @PrePersist
    @PreUpdate
    void synchroniserIndexAveugle() {
        telephoneRecherche = FieldCrypto.requis().rechercheTelephone(telephone);
    }

    public void definirTypeCliente(String type) {
        if (type == null || type.isBlank()) {
            this.typeCliente = null;
            return;
        }
        String t = type.trim().toUpperCase();
        if (!t.equals("ABONNEE") && !t.equals("CLIENTE")) {
            throw DomainException.invalide("Type cliente : Abonnée ou Cliente.");
        }
        this.typeCliente = t;
    }

    public void marquerHorsCreneau(boolean hors) {
        this.horsCreneau = hors;
    }

    public void changerStatut(Statut cible, Instant maintenant) {
        if (!TRANSITIONS.get(statut).contains(cible)) {
            throw DomainException.etatInvalide("Transition impossible : " + statut + " → " + cible + ".");
        }
        switch (cible) {
            case ARRIVEE -> arriveeLe = maintenant;
            case EN_COURS -> debutLe = maintenant;
            case TERMINEE -> finLe = maintenant;
            default -> {
                // PREVU, ANNULE, ABSENTE : aucun horodatage spécifique à enregistrer
            }
        }
        statut = cible;
    }

    public void verifier(UUID par, Instant le, boolean conforme) {
        if (statut != Statut.TERMINEE) {
            throw DomainException.etatInvalide("Seule une prestation terminée peut être vérifiée.");
        }
        verifiePar = par;
        verifieLe = le;
        verifieConforme = conforme;
    }

    public void reaffecter(UUID coiffeuseId) {
        exigerModifiable();
        this.coiffeuseId = coiffeuseId;
    }

    /** Déplacement d'un rendez-vous pas encore commencé. */
    public void deplacer(LocalDate nouveauJour, LocalTime nouvelleHeure) {
        exigerModifiable();
        this.jour = nouveauJour;
        this.heure = nouvelleHeure;
    }

    public void annoter(String texte) {
        this.note = texte == null || texte.isBlank() ? null : texte.trim();
    }

    /** Confirme ou ajuste la prestation d'intention à l'arrivée. */
    public void definirIntention(String codePrestation, String note) {
        if (statut != Statut.ARRIVEE && statut != Statut.EN_COURS) {
            throw DomainException.etatInvalide("L'intention se saisit après l'arrivée de la cliente.");
        }
        this.prestationIntentionCode = codePrestation;
        this.noteArrivee = note == null || note.isBlank() ? null : note.trim();
    }

    /** Enregistre le réalisé et l'éventuel écart avant / à la clôture. */
    public void definirRealise(String codePrestation, long prixRealise, String observations, String motifEcart) {
        if (statut != Statut.EN_COURS && statut != Statut.TERMINEE) {
            throw DomainException.etatInvalide("Le réalisé se saisit pendant ou à la fin de la prestation.");
        }
        this.prestationRealiseeCode = codePrestation;
        this.prix = prixRealise;
        this.observationsCloture = observations == null || observations.isBlank() ? null : observations.trim();
        this.motifEcart = motifEcart == null || motifEcart.isBlank() ? null : motifEcart.trim();
    }

    /** Code prestation « demandée » initiale à la prise. */
    public String prestationDemandee() {
        return prestationCode;
    }

    /** Intention effective (arrivée) ou, à défaut, la demande initiale. */
    public String prestationEffective() {
        return prestationIntentionCode != null ? prestationIntentionCode : prestationCode;
    }

    /** Réalisé, sinon intention, sinon demande. */
    public String prestationFacturable() {
        if (prestationRealiseeCode != null) {
            return prestationRealiseeCode;
        }
        return prestationEffective();
    }

    public boolean estActif() {
        return statut != Statut.ANNULEE && statut != Statut.ABSENTE && statut != Statut.TERMINEE;
    }

    private void exigerModifiable() {
        if (statut != Statut.PREVU && statut != Statut.CONFIRME) {
            throw DomainException.etatInvalide("Rendez-vous déjà commencé ou clos : il ne peut plus être modifié.");
        }
    }

    /**
     * Référence courte communiquée à la cliente : 8 derniers caractères de l'identifiant (partie aléatoire
     * de l'UUID v7 — le début est un horodatage, identique pour des réservations rapprochées).
     */
    public String reference() {
        UUID id = getId();
        if (id == null) {
            throw new IllegalStateException("Référence indisponible avant l'enregistrement du rendez-vous.");
        }
        String hex = id.toString().replace("-", "");
        return hex.substring(hex.length() - 8).toUpperCase();
    }

    /** Temps d'attente en minutes, si la cliente a été accueillie puis prise en charge. */
    public Long attenteMinutes() {
        return arriveeLe == null || debutLe == null ? null : Duration.between(arriveeLe, debutLe).toMinutes();
    }

    public enum Statut {
        PREVU, CONFIRME, ARRIVEE, EN_COURS, TERMINEE, ANNULEE, ABSENTE
    }

    public enum Source {
        ACCUEIL, EN_LIGNE, TELEPHONE
    }
}
