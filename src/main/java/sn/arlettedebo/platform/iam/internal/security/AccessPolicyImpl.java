package sn.arlettedebo.platform.iam.internal.security;

import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

@Component
class AccessPolicyImpl implements AccessPolicy {

    @Override
    public CurrentUser utilisateur() {
        return utilisateurOptionnel().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Non authentifié"));
    }

    @Override
    public Optional<CurrentUser> utilisateurOptionnel() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CurrentUser u ? Optional.of(u) : Optional.empty();
    }

    @Override
    public boolean peut(ModuleCode module, ActionCode action) {
        return utilisateurOptionnel().map(u -> u.peut(module, action)).orElse(false);
    }

    @Override
    public void exiger(ModuleCode module, ActionCode action) {
        if (!peut(module, action)) {
            throw new AccessDeniedException("Droit requis : " + module.code() + ":" + action);
        }
    }

    @Override
    public boolean estProfil(String... profils) {
        return utilisateurOptionnel().map(u -> u.estProfil(profils)).orElse(false);
    }

    @Override
    public void exigerPerimetre(String perimetre) {
        if (perimetre != null && !utilisateur().couvre(perimetre)) {
            throw new AccessDeniedException("Périmètre hors habilitation : " + perimetre);
        }
    }
}
