package sn.arlettedebo.platform.commercial.internal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhotoEvolutionAbonneeRepository extends JpaRepository<PhotoEvolutionAbonnee, UUID> {

    /** Méta d'une photo pour les écrans : jamais le chemin de stockage, l'empreinte ni le déposant. */
    record Ligne(UUID id, UUID suiviId, LocalDate jour, PhotoEvolutionAbonnee.Type type, String legende, String nomFichier) {
    }

    /** Photos des séances données (une requête {@code IN}, filtre en SQL). */
    @Query("""
            select new sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonneeRepository$Ligne(
                p.id, p.suiviId, p.jour, p.typePhoto, p.legende, p.nomFichier)
            from PhotoEvolutionAbonnee p
            where p.suiviId in :suiviIds
            order by p.jour desc, p.deposeLe desc
            """)
    List<Ligne> lignesDesSuivis(Collection<UUID> suiviIds);

    /** Photos d'un dossier, paginées en SQL (plus récentes d'abord). */
    @Query(value = """
            select new sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonneeRepository$Ligne(
                p.id, p.suiviId, p.jour, p.typePhoto, p.legende, p.nomFichier)
            from PhotoEvolutionAbonnee p
            where p.dossierId = :dossierId
            order by p.jour desc, p.deposeLe desc
            """,
            countQuery = "select count(p) from PhotoEvolutionAbonnee p where p.dossierId = :dossierId")
    Page<Ligne> lignesDuDossier(UUID dossierId, Pageable page);

    /** Photos des séances données, limitées au dossier ouvert (écran staff). */
    @Query("""
            select new sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonneeRepository$Ligne(
                p.id, p.suiviId, p.jour, p.typePhoto, p.legende, p.nomFichier)
            from PhotoEvolutionAbonnee p
            where p.dossierId = :dossierId and p.suiviId in :suiviIds
            order by p.jour desc, p.deposeLe desc
            """)
    List<Ligne> lignesDuDossierParSuivis(UUID dossierId, Collection<UUID> suiviIds);

    Optional<PhotoEvolutionAbonnee> findByIdAndDossierId(UUID id, UUID dossierId);

    long countByDossierId(UUID dossierId);

    long countBySuiviId(UUID suiviId);
}
