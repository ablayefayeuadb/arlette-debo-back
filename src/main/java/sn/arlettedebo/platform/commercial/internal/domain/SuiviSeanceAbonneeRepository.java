package sn.arlettedebo.platform.commercial.internal.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SuiviSeanceAbonneeRepository extends JpaRepository<SuiviSeanceAbonnee, UUID> {

    Page<SuiviSeanceAbonnee> findByDossierIdOrderByJourDescIdDesc(UUID dossierId, Pageable page);

    /** Ligne d'un suivi pour le portail : colonnes affichées uniquement (pas d'entité, pas de collection d'options). */
    record LignePortail(UUID id, LocalDate jour, Short numeroSemaine, UUID coiffeuseId, String prepoo, String shampoo,
            String masque, String soinPersonnalise, String leaveIn, String modeSechage, String coiffureProtectrice,
            String observation) {
    }

    @Query(value = """
            select new sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonneeRepository$LignePortail(
                s.id, s.jour, s.numeroSemaine, s.coiffeuseId, s.prepoo, s.shampoo, s.masque, s.soinPersonnalise,
                s.leaveIn, s.modeSechage, s.coiffureProtectrice, s.observation)
            from SuiviSeanceAbonnee s where s.dossierId = :dossierId
            order by s.jour desc, s.id desc
            """,
            countQuery = "select count(s) from SuiviSeanceAbonnee s where s.dossierId = :dossierId")
    Page<LignePortail> lignesPortail(UUID dossierId, Pageable page);

    boolean existsByDossierIdAndJour(UUID dossierId, LocalDate jour);

    Optional<SuiviSeanceAbonnee> findByIdAndDossierId(UUID id, UUID dossierId);

    @Query("select coalesce(max(s.numeroSemaine), 0) from SuiviSeanceAbonnee s where s.dossierId = :dossierId")
    int maxNumeroSemaine(UUID dossierId);
}
