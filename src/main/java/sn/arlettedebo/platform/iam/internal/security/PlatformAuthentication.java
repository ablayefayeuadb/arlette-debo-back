package sn.arlettedebo.platform.iam.internal.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import sn.arlettedebo.platform.iam.CurrentUser;

/** Authentification issue d'un JWT valide ; le principal porte les droits effectifs. */
public class PlatformAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser utilisateur;
    private final transient Jwt jwt;

    public PlatformAuthentication(CurrentUser utilisateur, Jwt jwt, Collection<? extends GrantedAuthority> autorites) {
        super(autorites);
        this.utilisateur = utilisateur;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public CurrentUser getPrincipal() {
        return utilisateur;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlatformAuthentication autre && super.equals(o) && utilisateur.equals(autre.utilisateur);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + utilisateur.hashCode();
    }

    /** Nom = identifiant UUID (utilisé par l'audit JPA). */
    @Override
    public String getName() {
        return utilisateur.id().toString();
    }
}
