package sn.arlettedebo.platform.iam.internal.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JetonRafraichissementRepository extends JpaRepository<JetonRafraichissement, UUID> {

    Optional<JetonRafraichissement> findByEmpreinte(String empreinte);

    @Modifying(flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoqueLe = :le where j.famille = :famille and j.revoqueLe is null")
    int revoquerFamille(UUID famille, Instant le);

    @Modifying(flushAutomatically = true)
    @Query("update JetonRafraichissement j set j.revoqueLe = :le where j.utilisateurId = :utilisateurId and j.revoqueLe is null")
    int revoquerTous(UUID utilisateurId, Instant le);

    /** Purge des jetons expirés (index ix_td_jeton_expiration). */
    @Modifying
    @Query("delete from JetonRafraichissement j where j.expireLe < :avant")
    int purgerExpires(Instant avant);
}
