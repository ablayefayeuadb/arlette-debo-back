package sn.arlettedebo.platform.rh.internal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointageRepository extends JpaRepository<Pointage, UUID> {

    Optional<Pointage> findByEmployeIdAndJour(UUID employeId, LocalDate jour);

    List<Pointage> findByJourAndEmployeIdIn(LocalDate jour, Collection<UUID> employes);

    /** Une requête pour toute la période — index ux_td_pointage_employe_jour / ix_td_pointage_jour. */
    List<Pointage> findByJourBetweenAndEmployeIdIn(LocalDate du, LocalDate au, Collection<UUID> employes);

    /** Arrivées sans départ jusqu'à un jour inclus, pas encore signalées — index ix_td_pointage_sans_depart. */
    List<Pointage> findByJourLessThanEqualAndArriveeLeIsNotNullAndDepartLeIsNullAndDepartNonPointeFalse(LocalDate jour);

    /** Départs non pointés à régulariser — index ix_td_pointage_depart_non_pointe. */
    List<Pointage> findByDepartNonPointeTrueAndRegulariseParIsNullAndEmployeIdInOrderByJourAsc(Collection<UUID> employes);
}
