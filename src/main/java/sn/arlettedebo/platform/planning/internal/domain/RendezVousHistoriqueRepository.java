package sn.arlettedebo.platform.planning.internal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RendezVousHistoriqueRepository extends JpaRepository<RendezVousHistorique, UUID> {

    List<RendezVousHistorique> findByRendezVousIdOrderByLeAsc(UUID rendezVousId);
}
