package sn.arlettedebo.platform.iam;

import java.util.Optional;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Point d'entrée unique des contrôles d'accès pour les modules métier.
 * Complète les annotations {@code @PreAuthorize} pour les règles qui dépendent des données
 * (périmètre, profil, restriction « limité »…).
 */
public interface AccessPolicy {

    /** Utilisateur courant (401 si absent). */
    CurrentUser utilisateur();

    Optional<CurrentUser> utilisateurOptionnel();

    boolean peut(ModuleCode module, ActionCode action);

    /** 403 si le droit n'est pas accordé. */
    void exiger(ModuleCode module, ActionCode action);

    boolean estProfil(String... profils);

    /** 403 si le périmètre n'est pas couvert par le compte (profil, compte ou relais actif). */
    void exigerPerimetre(String perimetre);
}
