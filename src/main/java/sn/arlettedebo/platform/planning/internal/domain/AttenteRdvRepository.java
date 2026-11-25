package sn.arlettedebo.platform.planning.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttenteRdvRepository extends JpaRepository<AttenteRdv, UUID> {

    List<AttenteRdv> findByRendezVousIdOrderByLeDesc(UUID rendezVousId);

    long countByRendezVousId(UUID rendezVousId);

    /** Nombre de fiches d'attente par rendez-vous pour un lot (une requête, pas de N+1). */
    interface NombreParRdv {
        UUID getRendezVousId();

        long getNombre();
    }

    @Query("select a.rendezVousId as rendezVousId, count(a) as nombre from AttenteRdv a "
            + "where a.rendezVousId in :ids group by a.rendezVousId")
    List<NombreParRdv> nombresParRdv(Collection<UUID> ids);
}
