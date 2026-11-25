package sn.arlettedebo.platform.commercial.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.internal.domain.Abonnement;
import sn.arlettedebo.platform.commercial.internal.domain.AbonnementRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.EcheanceAbonnement;
import sn.arlettedebo.platform.commercial.internal.domain.EcheanceAbonnementRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Paiements des abonnements (mensuel, bimensuel, locks) : une échéance attendue par mois de séance,
 * reconduite automatiquement à chaque séance suivie. Le personnel la marque payée (lien caisse facultatif plus tard).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EcheancesService {

    private final EcheanceAbonnementRepository echeances;
    private final DossierAbonneeRepository dossiers;
    private final AbonnementRepository abonnements;
    private final Referentiels referentiels;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Action du personnel sur une échéance : montant et/ou nouveau statut ({@code null} = inchangé). */
    public record Modification(Long montant, EcheanceAbonnement.Statut statut) {
    }

    @Transactional(readOnly = true)
    public Page<EcheanceAbonnement> lister(UUID dossierId, Pageable page) {
        dossier(dossierId);
        return echeances.findByDossierIdOrderByEcheanceLeDescIdDesc(dossierId, page);
    }

    /** Échéance saisie à la main (ex. rattrapage d'un mois sans séance). */
    public EcheanceAbonnement creer(UUID dossierId, LocalDate echeanceLe, Long montant) {
        DossierAbonnee d = dossier(dossierId);
        if (echeanceLe == null) {
            throw DomainException.invalide("La date d'échéance est obligatoire.");
        }
        long m = montant != null ? montant : montantAttendu(d).orElse(0L);
        if (m < 0) {
            throw DomainException.invalide("Le montant ne peut pas être négatif.");
        }
        verifierPeriodeLibre(dossierId, echeanceLe);
        EcheanceAbonnement e = echeances.save(new EcheanceAbonnement(dossierId, abonnementDe(d).map(Abonnement::getId).orElse(null),
                echeanceLe, m, null));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Échéance abonnement créée",
                dossierId + " · " + e.getPeriode() + " · " + m + " F"));
        return e;
    }

    public EcheanceAbonnement modifier(UUID dossierId, UUID id, Modification m) {
        dossier(dossierId);
        EcheanceAbonnement e = echeances.findByIdAndDossierId(id, dossierId)
                .orElseThrow(() -> DomainException.introuvable("Échéance"));
        if (m.montant() != null) {
            e.changerMontant(m.montant());
        }
        if (m.statut() != null && m.statut() != e.getStatut()) {
            switch (m.statut()) {
                case PAYEE -> e.payer(Instant.now(clock), access.utilisateur().id());
                case ANNULEE -> e.annuler();
                default -> throw DomainException.etatInvalide("Une échéance ne revient pas à l'état « attendue ».");
            }
        }
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Échéance abonnement modifiée",
                dossierId + " · " + e.getPeriode() + " · " + e.getStatut()));
        return e;
    }

    /**
     * Après une séance suivie : crée l'échéance du mois si aucune n'existe encore (hors annulée).
     * Sans effet quand le dossier n'a pas de type d'abonnement ou que le tarif de la formule est inconnu.
     */
    public Optional<EcheanceAbonnement> assurerEcheanceDuMois(DossierAbonnee dossier, LocalDate jour, UUID suiviId) {
        if (dossier.getTypeAbonnement() == null
                || echeances.existsByDossierIdAndPeriodeAndStatutNot(dossier.getId(), EcheanceAbonnement.periodeDe(jour),
                        EcheanceAbonnement.Statut.ANNULEE)) {
            return Optional.empty();
        }
        Optional<Abonnement> abo = abonnementDe(dossier);
        Optional<Long> montant = montantAttendu(dossier);
        if (montant.isEmpty()) {
            return Optional.empty();
        }
        EcheanceAbonnement e = echeances.save(new EcheanceAbonnement(dossier.getId(), abo.map(Abonnement::getId).orElse(null), jour,
                montant.get(), suiviId));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Échéance abonnement attendue",
                dossier.getId() + " · " + e.getPeriode() + " · " + e.getMontant() + " F"));
        return Optional.of(e);
    }

    private void verifierPeriodeLibre(UUID dossierId, LocalDate jour) {
        if (echeances.existsByDossierIdAndPeriodeAndStatutNot(dossierId, EcheanceAbonnement.periodeDe(jour),
                EcheanceAbonnement.Statut.ANNULEE)) {
            throw new DomainException(HttpStatus.CONFLICT, "ECHEANCE_EXISTANTE", "Une échéance existe déjà pour ce mois.");
        }
    }

    private Optional<Abonnement> abonnementDe(DossierAbonnee d) {
        List<Abonnement> liste = abonnements.findByClientIdOrderByDebutDesc(d.getClientId());
        return liste.stream().findFirst();
    }

    /** Montant du mois : prix de la formule de l'abonnement le plus récent. */
    private Optional<Long> montantAttendu(DossierAbonnee d) {
        return abonnementDe(d).flatMap(a -> referentiels.formule(a.getFormuleCode())).map(Referentiels.FormuleInfo::prix);
    }

    private DossierAbonnee dossier(UUID id) {
        DossierAbonnee d = dossiers.findById(id).orElseThrow(() -> DomainException.introuvable("Dossier abonnée"));
        access.exigerPerimetre(d.getPerimetreCode());
        return d;
    }
}
