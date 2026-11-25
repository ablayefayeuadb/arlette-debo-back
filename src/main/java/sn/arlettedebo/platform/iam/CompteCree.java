package sn.arlettedebo.platform.iam;

import java.util.UUID;

/**
 * Événement publié à la création d'un compte, dans la même transaction. Le module RH y crée le dossier du
 * personnel correspondant : tout utilisateur du système pointe, il lui faut donc un dossier.
 */
public record CompteCree(UUID utilisateurId) {
}
