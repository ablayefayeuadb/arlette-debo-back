package sn.arlettedebo.platform.qualite.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonConformiteRepository extends JpaRepository<NonConformite, UUID> {

    /** NC ouvertes d'un ensemble de périmètres — index partiel ix_td_nc_ouvertes. */
    List<NonConformite> findByPerimetreCodeInAndStatutNotOrderByDateConstatDesc(Collection<String> perimetres, NonConformite.Statut statut);

    List<NonConformite> findByPerimetreCodeInOrderByDateConstatDesc(Collection<String> perimetres);

    org.springframework.data.domain.Page<NonConformite> findByPerimetreCodeInAndStatutNot(Collection<String> perimetres, NonConformite.Statut statut, org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<NonConformite> findByPerimetreCodeIn(Collection<String> perimetres, org.springframework.data.domain.Pageable page);

    /** Non-conformité portant une action corrective donnée. */
    @org.springframework.data.jpa.repository.Query("select n from NonConformite n join n.actions a where a.id = :actionId")
    java.util.Optional<NonConformite> findByActionId(@org.springframework.data.repository.query.Param("actionId") java.util.UUID actionId);
}
