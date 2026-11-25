package sn.arlettedebo.platform.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Stockage des pièces jointes dans un dossier (préfixe {@code app.storage}).
 * En production, ce dossier est un répertoire du serveur monté dans le conteneur de l'API :
 * les fichiers survivent à la recréation du conteneur.
 *
 * @param root dossier racine des justificatifs
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(@NotBlank String root) {
}
