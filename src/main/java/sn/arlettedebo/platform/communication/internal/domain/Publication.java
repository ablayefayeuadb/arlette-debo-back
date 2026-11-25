package sn.arlettedebo.platform.communication.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Règle « Communication » : création → vérification (DO) → validation (DG) → publication → analyse.
 * Un contenu peut être renvoyé « à revoir » à la vérification ou à la validation.
 */
@Getter
@Entity
@Table(name = "td_publication")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Publication extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private Format format = Format.POST;

    @Column(name = "clics")
    private Integer clics;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "contenu", nullable = false)
    private String contenu;

    @Column(name = "canal_code", nullable = false)
    private String canalCode;

    @Column(name = "perimetre_code")
    private String perimetreCode;

    @Column(name = "date_prevue")
    private LocalDate datePrevue;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.BROUILLON;

    @Column(name = "auteur_id", nullable = false, updatable = false)
    private UUID auteurId;

    @Column(name = "verifie_par")
    private UUID verifiePar;

    @Column(name = "verifie_le")
    private Instant verifieLe;

    @Column(name = "valide_par")
    private UUID validePar;

    @Column(name = "valide_le")
    private Instant valideLe;

    @Column(name = "publiee_le")
    private Instant publieeLe;

    @Column(name = "motif_revoir")
    private String motifRevoir;

    @Column(name = "portee")
    private Integer portee;

    @Column(name = "interactions")
    private Integer interactions;

    @Column(name = "nouveaux_abonnes")
    private Integer nouveauxAbonnes;

    public Publication(String titre, String contenu, Format format, String canal, String perimetre, LocalDate datePrevue, UUID auteur) {
        this.format = format == null ? Format.POST : format;
        this.titre = titre;
        this.contenu = contenu;
        this.canalCode = canal;
        this.perimetreCode = perimetre;
        this.datePrevue = datePrevue;
        this.auteurId = auteur;
    }

    public void modifier(String titre, String contenu, Format format, String canal, String perimetre, LocalDate datePrevue) {
        if (statut != Statut.BROUILLON && statut != Statut.A_REVOIR) {
            throw DomainException.etatInvalide("Contenu en circuit de validation : modification impossible.");
        }
        this.titre = titre;
        this.contenu = contenu;
        this.format = format == null ? this.format : format;
        this.canalCode = canal;
        this.perimetreCode = perimetre;
        this.datePrevue = datePrevue;
    }

    public void soumettre() {
        exiger(Statut.BROUILLON, Statut.A_REVOIR);
        statut = Statut.A_VERIFIER;
        motifRevoir = null;
    }

    public void verifier(UUID par, Instant le) {
        exiger(Statut.A_VERIFIER);
        statut = Statut.A_VALIDER;
        verifiePar = par;
        verifieLe = le;
    }

    public void valider(UUID par, Instant le) {
        exiger(Statut.A_VALIDER);
        if (Objects.equals(par, auteurId)) {
            throw DomainException.regle("SEPARATION_DES_TACHES", "L'auteur ne valide pas son propre contenu.");
        }
        statut = Statut.VALIDEE;
        validePar = par;
        valideLe = le;
    }

    public void renvoyer(String motif) {
        exiger(Statut.A_VERIFIER, Statut.A_VALIDER);
        statut = Statut.A_REVOIR;
        motifRevoir = motif;
    }

    public void publier(Instant le) {
        exiger(Statut.VALIDEE);
        statut = Statut.PUBLIEE;
        publieeLe = le;
    }

    public void analyser(int portee, int interactions, int nouveauxAbonnes, Integer clics) {
        exiger(Statut.PUBLIEE, Statut.ANALYSEE);
        statut = Statut.ANALYSEE;
        this.clics = clics;
        this.portee = portee;
        this.interactions = interactions;
        this.nouveauxAbonnes = nouveauxAbonnes;
    }

    private void exiger(Statut... attendus) {
        for (Statut s : attendus) {
            if (statut == s) {
                return;
            }
        }
        throw DomainException.etatInvalide("Étape impossible depuis le statut " + statut + ".");
    }

    public enum Format { POST, STORY, REEL, CAMPAGNE }

    public enum Statut { BROUILLON, A_VERIFIER, A_REVOIR, A_VALIDER, VALIDEE, PUBLIEE, ANALYSEE }
}
