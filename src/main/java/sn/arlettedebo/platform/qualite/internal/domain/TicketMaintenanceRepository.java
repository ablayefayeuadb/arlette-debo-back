package sn.arlettedebo.platform.qualite.internal.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMaintenanceRepository extends JpaRepository<TicketMaintenance, UUID> {

    java.util.List<TicketMaintenance> findByPerimetreCodeInAndStatutNot(java.util.Collection<String> perimetres, TicketMaintenance.Statut statut);

    java.util.List<TicketMaintenance> findByPerimetreCodeInOrderByCreeLeDesc(java.util.Collection<String> perimetres);

    org.springframework.data.domain.Page<TicketMaintenance> findByPerimetreCodeInAndStatutNot(java.util.Collection<String> perimetres, TicketMaintenance.Statut statut, org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<TicketMaintenance> findByPerimetreCodeIn(java.util.Collection<String> perimetres, org.springframework.data.domain.Pageable page);
}
