package sn.arlettedebo.platform.qualite.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReclamationRepository extends JpaRepository<Reclamation, UUID> {

    List<Reclamation> findByPerimetreCodeInAndStatutNotOrderByDateReclamationDesc(Collection<String> perimetres, Reclamation.Statut statut);

    List<Reclamation> findByPerimetreCodeInAndDateReclamationGreaterThanEqualAndDateReclamationLessThanOrderByDateReclamationDesc(
            Collection<String> perimetres, java.time.Instant debut, java.time.Instant fin);

    org.springframework.data.domain.Page<Reclamation> findByPerimetreCodeInAndDateReclamationGreaterThanEqualAndDateReclamationLessThan(
            Collection<String> perimetres, java.time.Instant debut, java.time.Instant fin, org.springframework.data.domain.Pageable page);

    List<Reclamation> findByPerimetreCodeInAndStatutOrderByDateReclamationDesc(Collection<String> perimetres, Reclamation.Statut statut);
}
