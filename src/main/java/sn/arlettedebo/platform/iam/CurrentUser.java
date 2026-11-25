package sn.arlettedebo.platform.iam;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Utilisateur authentifié et ses droits effectifs (matrice + habilitations « * » + délégations actives).
 * Immuable : sert de principal Spring Security et de valeur de cache.
 *
 * @param droits       actions explicites par module (C/S/G/V, sans implication)
 * @param restrictions restriction textuelle par module : limité, personnel, statistiques, produits, administratif
 */
public record CurrentUser(
        UUID id,
        String nom,
        String email,
        String profil,
        Set<String> perimetres,
        Map<ModuleCode, Set<ActionCode>> droits,
        Map<ModuleCode, String> restrictions,
        List<String> delegations,
        boolean doitChangerMotDePasse) implements java.io.Serializable {

    /**
     * C : tout accès au module ; S : accordée par S ou G ; G et V : explicites.
     */
    public boolean peut(ModuleCode module, ActionCode action) {
        Set<ActionCode> a = droits.getOrDefault(module, Set.of());
        return switch (action) {
            case C -> !a.isEmpty();
            case S -> a.contains(ActionCode.S) || a.contains(ActionCode.G);
            case G, V -> a.contains(action);
        };
    }

    public String restriction(ModuleCode module) {
        return restrictions.get(module);
    }

    public boolean estProfil(String... profils) {
        return Arrays.asList(profils).contains(profil);
    }

    public boolean couvre(String perimetre) {
        return perimetres.contains(perimetre);
    }
}
