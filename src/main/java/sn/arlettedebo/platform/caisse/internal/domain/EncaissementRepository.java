package sn.arlettedebo.platform.caisse.internal.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncaissementRepository extends JpaRepository<Encaissement, UUID> {
}
