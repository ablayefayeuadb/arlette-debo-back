package sn.arlettedebo.platform.caisse.internal.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactureRepository extends JpaRepository<Facture, UUID> {

    @Query(value = "select nextval('sq_facture_numero')", nativeQuery = true)
    long prochainNumero();

    Optional<Facture> findByRendezVousIdAndStatutNot(UUID rendezVousId, Facture.Statut statut);

    Page<Facture> findByOrigineAndPerimetreOrigineOrderByJourDescCreeLeDesc(
            Facture.Origine origine, String perimetreOrigine, Pageable page);

    @Query(value = """
            select f from Facture f
            where f.statut = :impayee
              and (f.perimetreOrigine in :perimetres or f.origine = :boutique)
            """,
            countQuery = """
            select count(f) from Facture f
            where f.statut = :impayee
              and (f.perimetreOrigine in :perimetres or f.origine = :boutique)
            """)
    Page<Facture> findImpayees(@Param("perimetres") Collection<String> perimetres,
            @Param("impayee") Facture.Statut impayee, @Param("boutique") Facture.Origine boutique, Pageable page);

    @Query("select f from Facture f left join fetch f.lignes where f.id = :id")
    Optional<Facture> findWithLignesById(UUID id);
}
