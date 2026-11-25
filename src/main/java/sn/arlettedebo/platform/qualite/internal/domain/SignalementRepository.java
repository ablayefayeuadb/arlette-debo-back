package sn.arlettedebo.platform.qualite.internal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalementRepository extends JpaRepository<Signalement, UUID> {

    /** Une coiffeuse ne voit que ses propres signalements. */
    List<Signalement> findByAuteurIdOrderByDateSignalementDesc(UUID auteurId);

    org.springframework.data.domain.Page<Signalement> findByAuteurId(UUID auteurId, org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<Signalement> findByPerimetreCodeIn(java.util.Collection<String> perimetres, org.springframework.data.domain.Pageable page);

    List<Signalement> findByPerimetreCodeInOrderByDateSignalementDesc(java.util.Collection<String> perimetres);

    java.util.List<Signalement> findByPerimetreCodeInAndStatutOrderByDateSignalementDesc(java.util.Collection<String> perimetres,
            Signalement.Statut statut);
}
