package sn.arlettedebo.platform.commercial.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BilanTrimestrielAbonneeRepository extends JpaRepository<BilanTrimestrielAbonnee, UUID> {

    Page<BilanTrimestrielAbonnee> findByDossierIdOrderByTrimestreDebutDescIdDesc(UUID dossierId, Pageable page);

    Optional<BilanTrimestrielAbonnee> findByIdAndDossierId(UUID id, UUID dossierId);

    /** Ligne de bilan pour le portail : colonnes affichées uniquement. */
    record LignePortail(UUID id, LocalDate trimestreDebut, String analyseObjectifs, String progres, String difficultes,
            String habitudesACorriger, String recommandations, BilanTrimestrielAbonnee.Conclusion conclusion, Instant creeLe) {
    }

    @Query(value = """
            select new sn.arlettedebo.platform.commercial.internal.domain.BilanTrimestrielAbonneeRepository$LignePortail(
                b.id, b.trimestreDebut, b.analyseObjectifs, b.progres, b.difficultes,
                b.habitudesACorriger, b.recommandations, b.conclusion, b.creeLe)
            from BilanTrimestrielAbonnee b where b.dossierId = :dossierId
            order by b.trimestreDebut desc, b.id desc
            """,
            countQuery = "select count(b) from BilanTrimestrielAbonnee b where b.dossierId = :dossierId")
    Page<LignePortail> lignesPortail(UUID dossierId, Pageable page);
}
