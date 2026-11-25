package sn.arlettedebo.platform.qualite.internal.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AvisClientRepository extends JpaRepository<AvisClient, UUID> {

    /** Satisfaction moyenne sur une période — index ix_td_avis_perimetre_date. */
    @Query("select avg(a.note) from AvisClient a where a.perimetreCode in :perimetres and a.dateAvis >= :depuis")
    Double moyenne(Collection<String> perimetres, Instant depuis);

    org.springframework.data.domain.Page<AvisClient> findByPerimetreCodeInAndDateAvisGreaterThanEqualAndDateAvisLessThan(
            Collection<String> perimetres, Instant debut, Instant fin, org.springframework.data.domain.Pageable page);

    java.util.List<AvisClient> findByPerimetreCodeInAndDateAvisGreaterThanEqualAndDateAvisLessThanOrderByDateAvisDesc(
            Collection<String> perimetres, Instant debut, Instant fin);
}
