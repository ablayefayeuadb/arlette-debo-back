package sn.arlettedebo.platform.caisse.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JustificatifCaisseRepository extends JpaRepository<JustificatifCaisse, UUID> {

    List<JustificatifCaisse> findBySessionIdOrderByDeposeLeAsc(UUID sessionId);

    /**
     * Paiements électroniques (Wave, Orange Money, carte) non annulés et sans justificatif, par session.
     * Index ix_td_encaissement_session et ix_td_justificatif_caisse_encaissement.
     */
    @Query("""
            select e.session.id as sessionId, count(e) as nombre
            from Encaissement e
            where e.session.id in :sessions and e.annule = false
              and e.modePaiementCode in ('WAVE', 'ORANGE_MONEY', 'CARTE')
              and not exists (select 1 from JustificatifCaisse j where j.encaissementId = e.id)
            group by e.session.id
            """)
    List<NombreParSession> sansJustificatif(Collection<UUID> sessions);

    /** Nombre de pièces par session. */
    @Query("select j.sessionId as sessionId, count(j) as nombre from JustificatifCaisse j where j.sessionId in :sessions group by j.sessionId")
    List<NombreParSession> pieces(Collection<UUID> sessions);

    interface NombreParSession {
        UUID getSessionId();

        long getNombre();
    }
}
