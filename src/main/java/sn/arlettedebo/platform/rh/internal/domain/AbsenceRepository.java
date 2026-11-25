package sn.arlettedebo.platform.rh.internal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AbsenceRepository extends JpaRepository<Absence, UUID> {

    List<Absence> findByEmployeIdInOrderByDuDesc(Collection<UUID> employes);

    org.springframework.data.domain.Page<Absence> findByEmployeIdIn(Collection<UUID> employes, org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<Absence> findByEmployeIdInAndStatut(Collection<UUID> employes, Absence.Statut statut,
            org.springframework.data.domain.Pageable page);

    /** Absences d'un type chevauchant une période, hors demandes refusées (règle des jours OFF). */
    @Query("""
            select a from Absence a
            where a.employeId = :employe and a.typeCode = :type
              and a.statut <> sn.arlettedebo.platform.rh.internal.domain.Absence.Statut.REFUSEE
              and a.du <= :au and a.au >= :du
            """)
    List<Absence> chevauchant(UUID employe, String type, LocalDate du, LocalDate au);

    /** Absences approuvées couvrant une date (index GiST ix_td_absence_periode_gist). */
    @Query("""
            select a from Absence a
            where a.statut = sn.arlettedebo.platform.rh.internal.domain.Absence.Statut.APPROUVEE
              and a.du <= :jour and a.au >= :jour and a.employeId in :employes
            """)
    List<Absence> absentsLe(LocalDate jour, Collection<UUID> employes);
}
