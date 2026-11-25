package sn.arlettedebo.platform.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jeu de données initial (préfixe {@code app.seed}).
 *
 * @param enabled             active le socle (IAM + dossiers RH)
 * @param demo                active aussi les seeders de démonstration métier (RDV, caisses, qualité…)
 * @param defaultPassword     mot de passe initial des comptes créés (obligatoire si enabled)
 * @param forcePasswordChange impose le changement de mot de passe à la première connexion
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean enabled, boolean demo, String defaultPassword, boolean forcePasswordChange) {
}
