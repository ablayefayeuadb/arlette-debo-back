package sn.arlettedebo.platform.audit.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JournalRepository extends JpaRepository<EntreeJournal, Long> {

    /**
     * Filtres optionnels (module, utilisateur, fenêtre d'horodatage).
     * {@code filtrerJour} évite les paramètres Instant null (Postgres ne peut pas en déduire le type).
     * Index : ix_td_journal_module_horodatage / ix_td_journal_utilisateur_horodatage / BRIN horodatage.
     */
    @Query("""
            select j from EntreeJournal j
            where (:module = '' or j.moduleCode = :module)
              and (:utilisateur is null or j.utilisateurId = :utilisateur)
              and (:filtrerJour = false or (j.horodatage >= :debut and j.horodatage < :fin))
            order by j.horodatage desc, j.id desc
            """)
    Page<EntreeJournal> rechercher(@Param("module") String module, @Param("utilisateur") UUID utilisateur,
            @Param("filtrerJour") boolean filtrerJour, @Param("debut") Instant debut, @Param("fin") Instant fin,
            Pageable pageable);
}
