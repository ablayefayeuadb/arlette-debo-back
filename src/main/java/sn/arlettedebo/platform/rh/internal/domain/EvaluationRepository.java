package sn.arlettedebo.platform.rh.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    List<Evaluation> findByEmployeIdInOrderByDateEvaluationDesc(Collection<UUID> employes);

    org.springframework.data.domain.Page<Evaluation> findByEmployeIdIn(Collection<UUID> employes, org.springframework.data.domain.Pageable page);
}
