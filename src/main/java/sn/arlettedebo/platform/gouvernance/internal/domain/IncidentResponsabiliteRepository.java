package sn.arlettedebo.platform.gouvernance.internal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentResponsabiliteRepository extends JpaRepository<IncidentResponsabilite, UUID> {

    List<IncidentResponsabilite> findAllByOrderByCreeLeDesc();

    List<IncidentResponsabilite> findByStatutInOrderByEcheanceAscCreeLeDesc(
            List<IncidentResponsabilite.Statut> statuts);

    long countByStatutNot(IncidentResponsabilite.Statut statut);
}
