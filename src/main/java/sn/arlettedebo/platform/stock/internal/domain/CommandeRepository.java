package sn.arlettedebo.platform.stock.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommandeRepository extends JpaRepository<Commande, UUID> {

    List<Commande> findByPerimetreCodeInAndStatutInOrderByCreeLeDesc(Collection<String> perimetres, Collection<Commande.Statut> statuts);

    List<Commande> findByPerimetreCodeInOrderByCreeLeDesc(Collection<String> perimetres);

    org.springframework.data.domain.Page<Commande> findByPerimetreCodeIn(Collection<String> perimetres, org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<Commande> findByPerimetreCodeInAndStatut(Collection<String> perimetres, Commande.Statut statut, org.springframework.data.domain.Pageable page);

    @Query("select c from Commande c where c.perimetreCode in :perimetres and c.valideLe >= :debut and c.valideLe < :fin")
    List<Commande> valideesEntre(Collection<String> perimetres, java.time.Instant debut, java.time.Instant fin);

    @Query(value = "select nextval('sq_commande_reference')", nativeQuery = true)
    long prochainNumero();

    long countByPerimetreCodeInAndStatutIn(java.util.Collection<String> perimetres, java.util.Collection<Commande.Statut> statuts);
}
