package sn.arlettedebo.platform.shared.security;

/**
 * Niveaux de droit de la matrice.
 * <ul>
 *   <li>C — consultation : accordée par tout accès au module ;</li>
 *   <li>S — saisie / enregistrement : accordée par S ou G ;</li>
 *   <li>G — gestion / modification : explicite ;</li>
 *   <li>V — validation / approbation : explicite.</li>
 * </ul>
 */
public enum ActionCode {
    C, S, G, V
}
