package sn.arlettedebo.platform.commercial.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemandeAutorisationRepository extends JpaRepository<DemandeAutorisation, UUID> {

    /** Demandes en attente — index partiel ix_td_demande_autorisation_attente. */
    List<DemandeAutorisation> findByStatutAndPerimetreCodeInOrderByDemandeLeDesc(DemandeAutorisation.Statut statut, Collection<String> perimetres);

    List<DemandeAutorisation> findByPerimetreCodeInOrderByDemandeLeDesc(Collection<String> perimetres);

    org.springframework.data.domain.Page<DemandeAutorisation> findByPerimetreCodeIn(Collection<String> perimetres,
            org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<DemandeAutorisation> findByStatutAndPerimetreCodeIn(DemandeAutorisation.Statut statut,
            Collection<String> perimetres, org.springframework.data.domain.Pageable page);

    List<DemandeAutorisation> findByPerimetreCodeInAndDemandeLeGreaterThanEqualAndDemandeLeLessThan(Collection<String> perimetres,
            java.time.Instant debut, java.time.Instant fin);
}
