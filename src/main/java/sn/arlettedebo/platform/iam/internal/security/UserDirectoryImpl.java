package sn.arlettedebo.platform.iam.internal.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.iam.internal.domain.Profil;
import sn.arlettedebo.platform.iam.internal.domain.ProfilRepository;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;
import sn.arlettedebo.platform.iam.internal.domain.UtilisateurRepository;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class UserDirectoryImpl implements UserDirectory {

    private final UtilisateurRepository repository;
    private final ProfilRepository profils;

    @Override
    public Optional<UserRef> parId(UUID id) {
        return repository.findById(id).map(u -> ref(u, perimetresProfils()));
    }

    @Override
    public Map<UUID, String> noms(Collection<UUID> ids) {
        Set<UUID> uniques = new HashSet<>(ids);
        uniques.remove(null);
        if (uniques.isEmpty()) {
            return java.util.Collections.emptyMap(); // get(null) autorisé, contrairement à Map.of()
        }
        return repository.findNoms(uniques).stream()
                .collect(Collectors.toMap(UtilisateurRepository.IdNom::getId, UtilisateurRepository.IdNom::getNom));
    }

    @Override
    public Map<String, String> libellesProfils() {
        return profils.findAll().stream().collect(java.util.stream.Collectors.toMap(
                sn.arlettedebo.platform.iam.internal.domain.Profil::getCode, sn.arlettedebo.platform.iam.internal.domain.Profil::getLibelle));
    }

    @Override
    public Set<String> perimetresDuProfil(String profilCode) {
        return profils.findById(profilCode).map(p -> Set.copyOf(p.getPerimetres())).orElse(Set.of());
    }

    @Override
    public List<UserRef> actifs() {
        Map<String, Set<String>> parProfil = perimetresProfils();
        return repository.findAllByActifTrueOrderByNomAsc().stream()
                .filter(u -> !sn.arlettedebo.platform.shared.security.Profils.ABONNEE.equals(u.getProfilCode()))
                .map(u -> ref(u, parProfil))
                .toList();
    }

    private Map<String, Set<String>> perimetresProfils() {
        return profils.findAll().stream().collect(Collectors.toMap(Profil::getCode, p -> Set.copyOf(p.getPerimetres())));
    }

    /** Périmètres du compte, à défaut ceux du profil. */
    private static UserRef ref(Utilisateur u, Map<String, Set<String>> parProfil) {
        Set<String> perimetres = u.getPerimetres().isEmpty() ? parProfil.getOrDefault(u.getProfilCode(), Set.of()) : Set.copyOf(u.getPerimetres());
        return new UserRef(u.getId(), u.getNom(), u.getEmail(), u.getProfilCode(), perimetres);
    }
}
