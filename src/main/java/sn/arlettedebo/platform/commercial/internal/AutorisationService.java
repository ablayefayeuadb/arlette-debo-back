package sn.arlettedebo.platform.commercial.internal;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisation;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisationRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Règle « Décisions commerciales » : remises, gratuités, remboursements et compensations exceptionnelles
 * sont demandées par l'accueil / la caisse et autorisées par la DG (droit V) ou la DO (gestion caisse « * »),
 * jamais par le demandeur.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AutorisationService {

    private final DemandeAutorisationRepository demandes;
    private final AccessPolicy access;
    private final Referentiels referentiels;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DemandeAutorisation> lister(boolean enAttenteSeulement) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        CurrentUser u = access.utilisateur();
        return enAttenteSeulement
                ? demandes.findByStatutAndPerimetreCodeInOrderByDemandeLeDesc(DemandeAutorisation.Statut.EN_ATTENTE, u.perimetres())
                : demandes.findByPerimetreCodeInOrderByDemandeLeDesc(u.perimetres());
    }

    /** Demandes de mes salons, paginées en base ; statut facultatif. */
    public org.springframework.data.domain.Page<DemandeAutorisation> lister(DemandeAutorisation.Statut statut,
            org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        CurrentUser u = access.utilisateur();
        return statut == null ? demandes.findByPerimetreCodeIn(u.perimetres(), page) : demandes.findByStatutAndPerimetreCodeIn(statut, u.perimetres(), page);
    }

    public DemandeAutorisation demander(String perimetre, String type, UUID clientId, String cliente, long montant, String motif) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        access.exigerPerimetre(perimetre);
        referentiels.exiger(TypeReferentiel.TYPE_DECISION, type);
        if (montant < 0) {
            throw DomainException.invalide("Montant négatif.");
        }
        DemandeAutorisation d = demandes.save(new DemandeAutorisation(new DemandeAutorisation.Donnees(perimetre, type, clientId, cliente.trim(), montant, motif.trim(),
                access.utilisateur().id(), clock.instant())));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Demande d'autorisation", type + " · " + cliente + " · " + montant + " FCFA"));
        return d;
    }

    /** DG : droit V ; DO : gestion caisse (accès « * » accordé). */
    public boolean peutDecider(DemandeAutorisation d) {
        CurrentUser u = access.utilisateur();
        boolean habilite = u.peut(ModuleCode.CAISSE, ActionCode.V) || (u.estProfil(Profils.DO) && u.peut(ModuleCode.CAISSE, ActionCode.G));
        return habilite && u.couvre(d.getPerimetreCode()) && !d.getDemandePar().equals(u.id());
    }

    public DemandeAutorisation decider(UUID id, boolean autoriser, String commentaire) {
        DemandeAutorisation d = charger(id);
        CurrentUser u = access.utilisateur();
        if (!(u.peut(ModuleCode.CAISSE, ActionCode.V) || (u.estProfil(Profils.DO) && u.peut(ModuleCode.CAISSE, ActionCode.G)))) {
            throw DomainException.regle("AUTORISATION_DIRECTION", "Les décisions commerciales sont autorisées par la DG ou la DO.");
        }
        access.exigerPerimetre(d.getPerimetreCode());
        d.decider(autoriser, u.id(), clock.instant(), commentaire);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, autoriser ? "Décision commerciale autorisée" : "Décision commerciale refusée",
                d.getTypeCode() + " · " + d.getClienteNom() + " · " + d.getMontant() + " FCFA"));
        return d;
    }

    public DemandeAutorisation appliquer(UUID id) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        DemandeAutorisation d = charger(id);
        access.exigerPerimetre(d.getPerimetreCode());
        d.appliquer(clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Décision commerciale appliquée", d.getTypeCode() + " · " + d.getClienteNom()));
        return d;
    }

    private DemandeAutorisation charger(UUID id) {
        return demandes.findById(id).orElseThrow(() -> DomainException.introuvable("Demande d'autorisation"));
    }
}
