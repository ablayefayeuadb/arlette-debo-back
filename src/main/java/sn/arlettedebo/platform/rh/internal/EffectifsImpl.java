package sn.arlettedebo.platform.rh.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.rh.internal.domain.Employe;
import sn.arlettedebo.platform.rh.internal.domain.EmployeRepository;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class EffectifsImpl implements Effectifs {

    private final EmployeRepository employes;

    @Override
    public Optional<UUID> employeDuCompte(UUID utilisateurId) {
        return employes.findByUtilisateurId(utilisateurId).map(Employe::getId);
    }

    @Override
    public Map<UUID, String> noms(Collection<UUID> ids) {
        var uniques = new HashSet<>(ids);
        uniques.remove(null);
        // Carte tolérant la clé nulle (Map.of().get(null) lève une NullPointerException)
        return uniques.isEmpty() ? java.util.Collections.emptyMap()
                : employes.nomsParIds(uniques).stream().collect(Collectors.toMap(EmployeRepository.NomEmploye::getId,
                        EmployeRepository.NomEmploye::getNom));
    }

    @Override
    public List<EmployeRef> coiffeuses(String perimetre) {
        return employes.findByPerimetreCodeInOrderByNomAsc(List.of(perimetre)).stream()
                .filter(e -> e.isActif() && e.getPoste().toLowerCase().startsWith("coiffeuse"))
                .map(this::ref)
                .toList();
    }

    @Override
    public Set<String> competences(UUID employeId) {
        if (employeId == null) {
            return Set.of();
        }
        return employes.findById(employeId).map(this::codesCompetences).orElse(Set.of());
    }

    private EmployeRef ref(Employe e) {
        return new EmployeRef(e.getId(), e.getNom(), e.getPerimetreCode(), e.getUtilisateurId(), codesCompetences(e));
    }

    private Set<String> codesCompetences(Employe e) {
        Hibernate.initialize(e.getCompetences());
        return e.getCompetences().stream().map(Employe.Competence::getPrestationCode).collect(Collectors.toUnmodifiableSet());
    }
}
