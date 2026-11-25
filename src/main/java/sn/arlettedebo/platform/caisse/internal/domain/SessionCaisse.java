package sn.arlettedebo.platform.caisse.internal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Session de caisse journalière d'un périmètre.
 * <p>Règle « Séparation des tâches de caisse » : la personne qui encaisse ne valide pas seule sa clôture ;
 * le contrôle journalier est fait par une personne indépendante (aussi garanti par CHECK en base).
 */
@Getter
@Entity
@Table(name = "td_session_caisse")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionCaisse extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false, updatable = false)
    private String perimetreCode;

    @Column(name = "jour", nullable = false, updatable = false)
    private LocalDate jour;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.OUVERTE;

    @Column(name = "fond_ouverture", nullable = false, columnDefinition = "numeric(12,0)")
    private long fondOuverture;

    @Column(name = "ouverte_par", nullable = false, updatable = false)
    private UUID ouvertePar;

    @Column(name = "ouverte_le", nullable = false, updatable = false)
    private Instant ouverteLe;

    @Column(name = "cloturee_par")
    private UUID clotureePar;

    @Column(name = "cloturee_le")
    private Instant clotureeLe;

    @Column(name = "montant_compte", columnDefinition = "numeric(12,0)")
    private Long montantCompte;

    @Column(name = "ecart", columnDefinition = "numeric(12,0)")
    private Long ecart;

    @Column(name = "controle_par")
    private UUID controlePar;

    @Column(name = "controle_le")
    private Instant controleLe;

    @Column(name = "controle_conforme")
    private Boolean controleConforme;

    @Column(name = "controle_commentaire")
    private String controleCommentaire;

    @OrderBy("horodatage asc")
    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    private List<Encaissement> encaissements = new ArrayList<>();

    public SessionCaisse(String perimetre, LocalDate jour, long fondOuverture, UUID par, Instant le) {
        if (fondOuverture < 0) {
            throw DomainException.invalide("Le fond de caisse ne peut pas être négatif.");
        }
        this.perimetreCode = perimetre;
        this.jour = jour;
        this.fondOuverture = fondOuverture;
        this.ouvertePar = par;
        this.ouverteLe = le;
    }

    public Encaissement encaisser(String libelle, long montant, String modePaiement, UUID par, Instant le) {
        if (statut != Statut.OUVERTE) {
            throw DomainException.etatInvalide("Caisse clôturée : aucun encaissement possible.");
        }
        Encaissement e = new Encaissement(this, libelle, montant, modePaiement, par, le);
        encaissements.add(e);
        return e;
    }

    public long totalEspeces() {
        return encaissements.stream().filter(e -> !e.isAnnule() && "ESPECES".equals(e.getModePaiementCode())).mapToLong(Encaissement::getMontant).sum();
    }

    public long total() {
        return encaissements.stream().filter(e -> !e.isAnnule()).mapToLong(Encaissement::getMontant).sum();
    }

    /** Clôture : comptage des espèces ; écart = compté − (fond + espèces encaissées). */
    public void cloturer(long montantCompte, UUID par, Instant le) {
        if (statut != Statut.OUVERTE) {
            throw DomainException.etatInvalide("Caisse déjà clôturée.");
        }
        this.montantCompte = montantCompte;
        this.ecart = montantCompte - (fondOuverture + totalEspeces());
        this.clotureePar = par;
        this.clotureeLe = le;
        this.statut = Statut.CLOTUREE;
    }

    public Set<UUID> intervenants() {
        Set<UUID> out = new HashSet<>();
        out.add(ouvertePar);
        if (clotureePar != null) {
            out.add(clotureePar);
        }
        encaissements.forEach(e -> out.add(e.getEncaissePar()));
        return out;
    }

    public void controler(UUID par, Instant le, boolean conforme, String commentaire) {
        if (statut != Statut.CLOTUREE) {
            throw DomainException.etatInvalide("Seule une caisse clôturée peut être contrôlée.");
        }
        if (controlePar != null) {
            throw DomainException.etatInvalide("Clôture déjà contrôlée.");
        }
        if (intervenants().contains(par)) {
            throw DomainException.regle("SEPARATION_DES_TACHES",
                    "Vous êtes intervenu(e) sur cette caisse : le contrôle doit être indépendant.");
        }
        this.controlePar = Objects.requireNonNull(par);
        this.controleLe = le;
        this.controleConforme = conforme;
        this.controleCommentaire = commentaire;
    }

    public enum Statut { OUVERTE, CLOTUREE }
}
