package sn.arlettedebo.platform.rh.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.crypto.EncryptedStringConverter;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Absence, congé, maladie, retard ou formation. Les congés passent par une décision RH. */
@Getter
@Entity
@Table(name = "td_absence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Absence extends AuditableEntity {

    @Column(name = "employe_id", nullable = false, updatable = false)
    private UUID employeId;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "du", nullable = false)
    private LocalDate du;

    @Column(name = "au", nullable = false)
    private LocalDate au;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "motif", columnDefinition = "text")
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Column(name = "saisi_par", nullable = false, updatable = false)
    private UUID saisiPar;

    @Column(name = "decide_par")
    private UUID decidePar;

    @Column(name = "decide_le")
    private Instant decideLe;

    /** Saisie d'absence ou demande de congé (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(UUID employeId, String typeCode, LocalDate du, LocalDate au, String motif,
            boolean soumisValidation, UUID saisiPar, Instant maintenant) {
    }

    public Absence(Donnees d) {
        if (d.au().isBefore(d.du())) {
            throw DomainException.invalide("La fin de période précède le début.");
        }
        this.employeId = d.employeId();
        this.typeCode = d.typeCode();
        this.du = d.du();
        this.au = d.au();
        this.motif = d.motif();
        this.saisiPar = d.saisiPar();
        this.statut = d.soumisValidation() ? Statut.DEMANDE : Statut.APPROUVEE;
        if (!d.soumisValidation()) {
            this.decidePar = d.saisiPar();
            this.decideLe = d.maintenant();
        }
    }

    public void decider(boolean approuver, UUID par, Instant le) {
        if (statut != Statut.DEMANDE) {
            throw DomainException.etatInvalide("Cette demande a déjà été traitée.");
        }
        this.statut = approuver ? Statut.APPROUVEE : Statut.REFUSEE;
        this.decidePar = par;
        this.decideLe = le;
    }

    public enum Statut {
        DEMANDE, APPROUVEE, REFUSEE
    }
}
