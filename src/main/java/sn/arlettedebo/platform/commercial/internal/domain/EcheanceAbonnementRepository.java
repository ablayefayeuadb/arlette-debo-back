package sn.arlettedebo.platform.commercial.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EcheanceAbonnementRepository extends JpaRepository<EcheanceAbonnement, UUID> {

    Page<EcheanceAbonnement> findByDossierIdOrderByEcheanceLeDescIdDesc(UUID dossierId, Pageable page);

    /** Ligne d'échéance pour le portail : colonnes affichées uniquement. */
    record LignePortail(UUID id, LocalDate echeanceLe, long montant, EcheanceAbonnement.Statut statut, Instant payeeLe) {
    }

    @Query(value = """
            select new sn.arlettedebo.platform.commercial.internal.domain.EcheanceAbonnementRepository$LignePortail(
                e.id, e.echeanceLe, e.montant, e.statut, e.payeeLe)
            from EcheanceAbonnement e where e.dossierId = :dossierId
            order by e.echeanceLe desc, e.id desc
            """,
            countQuery = "select count(e) from EcheanceAbonnement e where e.dossierId = :dossierId")
    Page<LignePortail> lignesPortail(UUID dossierId, Pageable page);

    Optional<EcheanceAbonnement> findByIdAndDossierId(UUID id, UUID dossierId);

    boolean existsByDossierIdAndPeriodeAndStatutNot(UUID dossierId, String periode, EcheanceAbonnement.Statut statut);
}
