package sn.arlettedebo.platform.iam.internal.security;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.iam.internal.security.RightsService.EffectiveRights;

/**
 * Transforme un JWT (déjà vérifié : signature, émetteur, audience, dates) en authentification.
 * Les droits ne sont PAS lus dans le jeton mais recalculés (cache) depuis la base, et le jeton est
 * refusé si le compte est désactivé ou si sa version a été révoquée (déconnexion globale,
 * changement de mot de passe, désactivation, rejeu détecté).
 */
@Component
@RequiredArgsConstructor
class PlatformJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String CLAIM_VERSION = "ver";

    private final RightsService rights;

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Sujet du jeton invalide");
        }
        EffectiveRights droits = rights.calculer(id)
                .orElseThrow(() -> new InvalidBearerTokenException("Compte introuvable"));
        if (!droits.actif()) {
            throw new InvalidBearerTokenException("Compte désactivé");
        }
        Number version = jwt.getClaim(CLAIM_VERSION);
        if (version == null || version.intValue() != droits.versionJeton()) {
            throw new InvalidBearerTokenException("Jeton révoqué");
        }
        return new PlatformAuthentication(droits.utilisateur(), jwt, droits.autorites());
    }
}
