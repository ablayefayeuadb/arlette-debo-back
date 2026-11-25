package sn.arlettedebo.platform.finance.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JustificatifRepository extends JpaRepository<Justificatif, UUID> {

    List<Justificatif> findByDepenseIdOrderByDeposeLeAsc(UUID depenseId);

    /** Chargement groupé pour une liste de dépenses (index ix_td_justificatif_depense). */
    List<Justificatif> findByDepenseIdIn(Collection<UUID> depenses);

    boolean existsByDepenseId(UUID depenseId);
}
