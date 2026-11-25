package sn.arlettedebo.platform.shared.event;

import java.util.UUID;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Événement métier à tracer dans le journal d'audit. Publié par tous les modules,
 * consommé de façon synchrone (même transaction) par le module {@code audit}.
 *
 * @param acteurId  auteur explicite (connexion) ; {@code null} = utilisateur courant
 * @param acteurNom libellé de l'auteur explicite
 */
public record AuditEvent(ModuleCode module, String action, String detail, UUID acteurId, String acteurNom) {

    public static AuditEvent of(ModuleCode module, String action, String detail) {
        return new AuditEvent(module, action, detail, null, null);
    }
}
