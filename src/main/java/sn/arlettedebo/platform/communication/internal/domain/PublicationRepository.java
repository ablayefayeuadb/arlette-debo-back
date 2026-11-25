package sn.arlettedebo.platform.communication.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PublicationRepository extends JpaRepository<Publication, UUID> {

    /** Calendrier éditorial — index ix_td_publication_statut_date. */
    List<Publication> findByDatePrevueBetweenOrderByDatePrevueAsc(LocalDate du, LocalDate au);

    List<Publication> findByStatutInOrderByDatePrevueAsc(Collection<Publication.Statut> statuts);

    @Query("select p from Publication p where p.publieeLe >= :debut and p.publieeLe < :fin")
    List<Publication> publieesEntre(Instant debut, Instant fin);
}
