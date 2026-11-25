package sn.arlettedebo.platform.iam.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.crypto.EncryptedStringConverter;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/** Compte utilisateur de la plateforme interne. */
@Getter
@Entity
@Table(name = "td_utilisateur")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utilisateur extends AuditableEntity {

    @Column(name = "nom", nullable = false)
    private String nom;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", nullable = false, columnDefinition = "text")
    private String email;

    /** HMAC de l'e-mail normalisé — login et unicité. */
    @Column(name = "email_recherche", length = 64)
    private String emailRecherche;

    /** Format masqué « 77 123 45 67 » — SMS d'accès (AES-GCM en base). */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "telephone", columnDefinition = "text")
    private String telephone;

    @Column(name = "mot_de_passe_hash", nullable = false)
    private String motDePasseHash;

    @Column(name = "profil_code", nullable = false)
    private String profilCode;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "doit_changer_mdp", nullable = false)
    private boolean doitChangerMdp;

    /** Toute incrémentation invalide instantanément les JWT déjà émis (claim {@code ver}). */
    @Column(name = "version_jeton", nullable = false)
    private int versionJeton;

    @Column(name = "tentatives_echouees", nullable = false)
    private short tentativesEchouees;

    @Column(name = "verrouille_jusqua")
    private Instant verrouilleJusqua;

    @Column(name = "derniere_connexion")
    private Instant derniereConnexion;

    /** Périmètres propres au compte ; vide = périmètres du profil. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tr_utilisateur_perimetre", joinColumns = @JoinColumn(name = "utilisateur_id"))
    @Column(name = "perimetre_code", nullable = false)
    private Set<String> perimetres = new HashSet<>();

    public Utilisateur(String nom, String email, String motDePasseHash, String profilCode, Set<String> perimetres,
            boolean doitChangerMdp, String telephone) {
        this.nom = nom;
        this.email = email.trim().toLowerCase();
        this.motDePasseHash = motDePasseHash;
        this.profilCode = profilCode;
        this.doitChangerMdp = doitChangerMdp;
        this.telephone = telephone;
        if (perimetres != null) {
            this.perimetres.addAll(perimetres);
        }
    }

    public boolean estVerrouille(Instant maintenant) {
        return verrouilleJusqua != null && verrouilleJusqua.isAfter(maintenant);
    }

    /** @return {@code true} si le compte vient d'être verrouillé. */
    public boolean enregistrerEchec(int maxTentatives, Duration dureeVerrou, Instant maintenant) {
        tentativesEchouees++;
        if (tentativesEchouees >= maxTentatives) {
            verrouilleJusqua = maintenant.plus(dureeVerrou);
            tentativesEchouees = 0;
            return true;
        }
        return false;
    }

    public void enregistrerConnexion(Instant maintenant) {
        tentativesEchouees = 0;
        verrouilleJusqua = null;
        derniereConnexion = maintenant;
    }

    public void deverrouiller() {
        tentativesEchouees = 0;
        verrouilleJusqua = null;
    }

    /** Révocation globale : tous les jetons d'accès émis deviennent invalides. */
    public void revoquerJetons() {
        versionJeton++;
    }

    public void changerMotDePasse(String nouveauHash, boolean provisoire) {
        this.motDePasseHash = nouveauHash;
        this.doitChangerMdp = provisoire;
        revoquerJetons();
    }

    public void changerProfil(String profilCode) {
        this.profilCode = profilCode;
    }

    public void definirPerimetres(Set<String> perimetres) {
        this.perimetres.clear();
        this.perimetres.addAll(perimetres);
    }

    public void desactiver() {
        this.actif = false;
        revoquerJetons();
    }

    public void reactiver() {
        this.actif = true;
        deverrouiller();
    }

    public void renommer(String nom) {
        this.nom = nom;
    }

    /** Change l'e-mail de connexion ; {@code email_recherche} est resynchronisé au flush. */
    public void changerEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail obligatoire.");
        }
        this.email = email.trim().toLowerCase();
    }

    @PrePersist
    @PreUpdate
    void synchroniserIndexAveugle() {
        emailRecherche = FieldCrypto.requis().rechercheEmail(email);
    }
}
