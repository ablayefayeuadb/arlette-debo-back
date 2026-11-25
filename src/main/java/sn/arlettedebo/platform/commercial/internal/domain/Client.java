package sn.arlettedebo.platform.commercial.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.crypto.EncryptedStringConverter;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/** Fiche cliente (CRM / fidélisation) : prospect → cliente → abonnée. */
@Getter
@Entity
@Table(name = "td_client")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "telephone", columnDefinition = "text")
    private String telephone;

    /** HMAC du numéro national — unicité et findByTelephoneRecherche. */
    @Column(name = "telephone_recherche", length = 64)
    private String telephoneRecherche;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", columnDefinition = "text")
    private String email;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Column(name = "source")
    private String source;

    @Column(name = "coiffeuse_habituelle_id")
    private UUID coiffeuseHabituelleId;

    @Column(name = "preferences")
    private String preferences;

    @Column(name = "notes")
    private String notes;

    /** Consentement explicite aux communications commerciales. */
    @Column(name = "consentement_marketing", nullable = false)
    private boolean consentementMarketing;

    @Column(name = "derniere_visite")
    private LocalDate derniereVisite;

    /** Fiche cliente (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String perimetre, String nom, String telephone, String email, LocalDate dateNaissance,
            Statut statut, String source, boolean consentementMarketing) {
    }

    public Client(Donnees d) {
        this.perimetreCode = d.perimetre();
        this.nom = d.nom();
        this.telephone = d.telephone();
        this.email = d.email();
        this.dateNaissance = d.dateNaissance();
        this.statut = d.statut();
        this.source = d.source();
        this.consentementMarketing = d.consentementMarketing();
    }

    public void enregistrerVisite(LocalDate jour, UUID coiffeuseId) {
        if (derniereVisite == null || jour.isAfter(derniereVisite)) {
            derniereVisite = jour;
        }
        if (coiffeuseId != null) {
            coiffeuseHabituelleId = coiffeuseId;
        }
        if (statut == Statut.PROSPECT) {
            statut = Statut.CLIENTE;
        }
    }

    public void devenirAbonnee() {
        statut = Statut.ABONNEE;
    }

    /** Date de naissance : {@code null} laisse la valeur inchangée. */
    public void definirDateNaissance(LocalDate dateNaissance) {
        if (dateNaissance != null) {
            this.dateNaissance = dateNaissance;
        }
    }

    /** E-mail de contact / identifiant portail (stocké chiffré). */
    public void definirEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        this.email = email.trim().toLowerCase();
    }

    public void annoter(String preferences, String notes) {
        this.preferences = preferences;
        this.notes = notes;
    }

    @PrePersist
    @PreUpdate
    void synchroniserIndexAveugle() {
        telephoneRecherche = FieldCrypto.requis().rechercheTelephone(telephone);
    }

    public enum Statut { PROSPECT, CLIENTE, ABONNEE }
}
