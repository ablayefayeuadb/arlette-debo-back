package sn.arlettedebo.platform.audit.internal;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.shared.event.AuditEvent;

/**
 * Écrit le journal dans la transaction de l'opération métier : si l'opération échoue,
 * aucune trace fantôme ; si elle réussit, la trace est garantie (atomicité).
 */
@Component
@RequiredArgsConstructor
class AuditListener {

    private final JournalRepository journal;
    private final AccessPolicy access;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void enregistrer(AuditEvent e) {
        CurrentUser u = access.utilisateurOptionnel().orElse(null);
        // Acteur explicite (ex. connexion, avant authentification) sinon utilisateur courant, sinon système
        UUID acteurId = e.acteurId();
        String acteurNom = e.acteurNom();
        if (u != null) {
            acteurId = acteurId != null ? acteurId : u.id();
            acteurNom = acteurNom != null ? acteurNom : u.nom();
        }
        journal.save(new EntreeJournal(new EntreeJournal.Donnees(
                clock.instant(),
                acteurId,
                acteurNom != null ? acteurNom : "Système",
                u == null ? null : u.profil(),
                e.module().code(),
                e.action(),
                e.detail(),
                ip())));
    }

    private static String ip() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                ? a.getRequest().getRemoteAddr()
                : null;
    }
}
