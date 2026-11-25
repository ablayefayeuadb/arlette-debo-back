package sn.arlettedebo.platform.planning.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RendezVousSupplementRepository extends JpaRepository<RendezVousSupplement, UUID> {

    List<RendezVousSupplement> findByRendezVousIdOrderByAccepteLeAsc(UUID rendezVousId);

    /** Suppléments d'un lot de rendez-vous (une requête {@code IN}). */
    List<RendezVousSupplement> findByRendezVousIdInOrderByAccepteLeAsc(Collection<UUID> rendezVousIds);
}
