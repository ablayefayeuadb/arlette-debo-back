package sn.arlettedebo.platform.commercial.internal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbonnementRepository extends JpaRepository<Abonnement, UUID> {

    List<Abonnement> findByClientIdOrderByDebutDesc(UUID clientId);

    List<Abonnement> findByClientIdInOrderByDebutDesc(java.util.Collection<UUID> clientIds);

    boolean existsByClientId(UUID clientId);
}
