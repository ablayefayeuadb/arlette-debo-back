package sn.arlettedebo.platform.qualite.internal.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlePrestationRepository extends JpaRepository<ControlePrestation, UUID> {

    org.springframework.data.domain.Page<ControlePrestation> findByPerimetreCodeInAndDateControleGreaterThanEqualAndDateControleLessThan(
            java.util.Collection<String> perimetres, java.time.Instant debut, java.time.Instant fin, org.springframework.data.domain.Pageable page);

    java.util.List<ControlePrestation> findByPerimetreCodeInAndDateControleGreaterThanEqualAndDateControleLessThanOrderByDateControleDesc(
            java.util.Collection<String> perimetres, java.time.Instant debut, java.time.Instant fin);
}
