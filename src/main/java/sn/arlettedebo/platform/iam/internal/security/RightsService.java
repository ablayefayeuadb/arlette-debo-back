package sn.arlettedebo.platform.iam.internal.security;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.iam.internal.domain.Delegation;
import sn.arlettedebo.platform.iam.internal.domain.DelegationRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilModule;
import sn.arlettedebo.platform.iam.internal.domain.ProfilModuleRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilRepository;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;
import sn.arlettedebo.platform.iam.internal.domain.UtilisateurRepository;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.Authorities;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Calcule les droits effectifs d'un compte à partir de la base :
 * matrice ({@code tr_profil_module}) − habilitations « * » suspendues + délégations actives.
 *
 * <p>Résultat mis en cache (Caffeine, clé = id utilisateur) et invalidé à chaque changement
 * de matrice, d'habilitation, de délégation ou de compte : une suspension par la DG s'applique
 * à la requête suivante, sans attendre l'expiration du JWT.
 */
@Service
@RequiredArgsConstructor
public class RightsService {

    public static final String CACHE = "droits";

    private final UtilisateurRepository utilisateurs;
    private final ProfilRepository profils;
    private final ProfilModuleRepository cellules;
    private final DelegationRepository delegations;
    private final CacheManager cacheManager;
    private final sn.arlettedebo.platform.referentiel.Referentiels referentiels;

    @Cacheable(cacheNames = CACHE, key = "#utilisateurId")
    @Transactional(readOnly = true)
    public Optional<EffectiveRights> calculer(UUID utilisateurId) {
        return utilisateurs.findById(utilisateurId).map(this::construire);
    }

    /**
     * Éviction immédiate puis à nouveau après le commit : une requête concurrente ne peut pas
     * réinsérer en cache un état antérieur à la transaction en cours.
     */
    public void invalider(UUID utilisateurId) {
        executerMaintenantEtApresCommit(() -> cache().evict(utilisateurId));
    }

    public void invaliderTout() {
        executerMaintenantEtApresCommit(() -> cache().clear());
    }

    private Cache cache() {
        return Objects.requireNonNull(cacheManager.getCache(CACHE), "Cache " + CACHE + " absent");
    }

    private static void executerMaintenantEtApresCommit(Runnable action) {
        action.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        }
    }

    private EffectiveRights construire(Utilisateur u) {
        Map<ModuleCode, Set<ActionCode>> droits = new EnumMap<>(ModuleCode.class);
        Map<ModuleCode, String> restrictions = new EnumMap<>(ModuleCode.class);
        for (ProfilModule cellule : cellules.findByIdProfilCode(u.getProfilCode())) {
            ModuleCode module = ModuleCode.fromCode(cellule.getId().getModuleCode());
            Set<ActionCode> actions = actions(cellule.actionsEffectives());
            if (!actions.isEmpty()) {
                droits.put(module, actions);
            }
            if (cellule.getRestriction() != null) {
                restrictions.put(module, cellule.getRestriction());
            }
        }

        Set<String> perimetres = new LinkedHashSet<>(u.getPerimetres().isEmpty()
                ? profils.findById(u.getProfilCode()).map(p -> p.getPerimetres()).orElse(Set.of())
                : u.getPerimetres());
        List<String> titres = new ArrayList<>();
        for (Delegation d : delegations.findByBeneficiaireProfilCodeAndActiveTrue(u.getProfilCode())) {
            if (!d.isFormationValidee()) {
                continue;
            }
            titres.add(d.getTitre());
            perimetres.addAll(d.getPerimetres());
            d.getDroits().forEach(dr -> droits
                    .computeIfAbsent(ModuleCode.fromCode(dr.getModuleCode()), k -> EnumSet.noneOf(ActionCode.class))
                    .add(ActionCode.valueOf(dr.getActionCode())));
        }

        // Un salon désactivé par la DG n'ouvre plus aucun accès (ses données sont conservées)
        perimetres.retainAll(referentiels.perimetresActifs());

        Map<ModuleCode, Set<ActionCode>> figes = new EnumMap<>(ModuleCode.class);
        droits.forEach((k, v) -> figes.put(k, Set.copyOf(v)));
        CurrentUser utilisateur = new CurrentUser(u.getId(), u.getNom(), u.getEmail(), u.getProfilCode(),
                Set.copyOf(perimetres), Map.copyOf(figes), Map.copyOf(restrictions), List.copyOf(titres), u.isDoitChangerMdp());
        return new EffectiveRights(utilisateur, u.isActif(), u.getVersionJeton(), autorites(utilisateur));
    }

    private static Set<ActionCode> actions(Set<String> codes) {
        Set<ActionCode> out = EnumSet.noneOf(ActionCode.class);
        codes.forEach(c -> out.add(ActionCode.valueOf(c.trim())));
        return out;
    }

    /** Autorités avec implications : tout accès ⇒ C ; G ⇒ S. */
    static List<GrantedAuthority> autorites(CurrentUser u) {
        List<GrantedAuthority> out = new ArrayList<>();
        out.add(new SimpleGrantedAuthority(Authorities.profil(u.profil())));
        for (ModuleCode m : ModuleCode.values()) {
            for (ActionCode a : ActionCode.values()) {
                if (u.peut(m, a)) {
                    out.add(new SimpleGrantedAuthority(Authorities.droit(m, a)));
                }
            }
        }
        return List.copyOf(out);
    }

    /** Valeur immuable mise en cache. */
    public record EffectiveRights(CurrentUser utilisateur, boolean actif, int versionJeton, List<GrantedAuthority> autorites) {
    }
}
