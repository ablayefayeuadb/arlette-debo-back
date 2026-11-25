package sn.arlettedebo.platform.rh.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeRepository extends JpaRepository<Employe, UUID> {

    List<Employe> findAllByOrderByNomAsc();

    /** Projection légère : identifiant et nom, sans les collections du dossier. */
    interface NomEmploye {
        UUID getId();

        String getNom();
    }

    @Query("select e.id as id, e.nom as nom from Employe e order by e.nom")
    List<NomEmploye> nomsTous();

    @Query("select e.id as id, e.nom as nom from Employe e where e.perimetreCode in :perimetres order by e.nom")
    List<NomEmploye> nomsDesPerimetres(@Param("perimetres") Collection<String> perimetres);

    @Query("select e.id as id, e.nom as nom from Employe e where e.id in :ids")
    List<NomEmploye> nomsParIds(@Param("ids") Collection<UUID> ids);

    /** Dossiers complets (avec la matrice des compétences) en une requête. */
    @EntityGraph(attributePaths = "competences")
    @Query("select e from Employe e order by e.id desc")
    List<Employe> dossiersTous();

    @EntityGraph(attributePaths = "competences")
    @Query("select e from Employe e where e.perimetreCode in :perimetres order by e.id desc")
    List<Employe> dossiersDesPerimetres(@Param("perimetres") Collection<String> perimetres);

    /** Accès « limité » (Manager) : personnel de ses périmètres uniquement, hors transverse. */
    List<Employe> findByPerimetreCodeInOrderByNomAsc(Collection<String> perimetres);

    Optional<Employe> findByUtilisateurId(UUID utilisateurId);

    boolean existsByUtilisateurId(UUID utilisateurId);

    @Query(value = "select nextval('sq_employe_matricule')", nativeQuery = true)
    long prochainNumero();
}
