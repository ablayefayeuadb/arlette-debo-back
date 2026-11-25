package sn.arlettedebo.platform.communication.internal.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvenementRepository extends JpaRepository<Evenement, UUID> {
}
