package sn.arlettedebo.platform.iam.internal.admin;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.internal.domain.Delegation;
import sn.arlettedebo.platform.iam.internal.domain.DelegationRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilModule;
import sn.arlettedebo.platform.iam.internal.domain.ProfilModuleRepository;
import sn.arlettedebo.platform.iam.internal.security.RightsService;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/** Habilitations « * » et relais : pilotés par la DG et la Manager, effet immédiat sur les droits. */
@Service
@RequiredArgsConstructor
@Transactional
public class HabilitationService {

    private final ProfilModuleRepository cellules;
    private final DelegationRepository delegations;
    private final RightsService rights;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProfilModule definirHabilitation(String profil, String module, boolean accorder) {
        UUID auteur = exigerDirection();
        ProfilModule cellule = cellules.findById(new ProfilModule.Cle(profil, module))
                .orElseThrow(() -> DomainException.introuvable("Cellule " + profil + " × " + module));
        cellule.definirHabilitation(accorder, auteur, clock.instant());
        rights.invaliderTout();
        events.publishEvent(AuditEvent.of(ModuleCode.fromCode(module),
                accorder ? "Habilitation accordée" : "Habilitation suspendue", profil + " · " + cellule.getValeurBrute()));
        return cellule;
    }

    @Transactional(readOnly = true)
    public List<Delegation> delegations() {
        return delegations.findAllByOrderByTitreAsc();
    }

    public Delegation definirFormation(UUID id, boolean validee) {
        exigerDirection();
        Delegation d = charger(id);
        d.definirFormation(validee);
        rights.invaliderTout();
        events.publishEvent(AuditEvent.of(ModuleCode.RH, validee ? "Formation validée (relais)" : "Formation retirée (relais)", d.getTitre()));
        return d;
    }

    public Delegation activer(UUID id, boolean active) {
        UUID auteur = exigerDirection();
        Delegation d = charger(id);
        if (active) {
            d.activer(auteur, clock.instant());
        } else {
            d.desactiver();
        }
        rights.invaliderTout();
        events.publishEvent(AuditEvent.of(ModuleCode.RH, active ? "Relais activé" : "Relais désactivé", d.getTitre()));
        return d;
    }

    private Delegation charger(UUID id) {
        return delegations.findById(id).orElseThrow(() -> DomainException.introuvable("Délégation"));
    }

    private UUID exigerDirection() {
        if (!access.estProfil(Profils.DG, Profils.MANAGER)) {
            throw DomainException.regle("RESERVE_DG", "Les habilitations et relais sont pilotés par la Direction.");
        }
        return access.utilisateur().id();
    }
}
