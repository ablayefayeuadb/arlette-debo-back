package sn.arlettedebo.platform.finance.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepenseRepository extends JpaRepository<Depense, UUID> {

    /** Index ix_td_depense_statut_date. */
    @Query("""
            select d from Depense d
            where (:statut is null or d.statut = :statut)
            order by d.demandeLe desc
            """)
    Page<Depense> rechercher(Depense.Statut statut, Pageable pageable);

    /** Dossiers en circuit — index partiel ix_td_depense_en_circuit. */
    List<Depense> findByStatutInOrderByDemandeLeDesc(Collection<Depense.Statut> statuts);

    @Query("select d.statut as statut, count(d) as nombre, coalesce(sum(d.montant), 0) as total from Depense d group by d.statut")
    List<TotalParStatut> totauxParStatut();

    /** Dépenses d'une période (transverses comprises) — index ix_td_depense_perimetre / statut_date. */
    @Query("""
            select d from Depense d
            where d.demandeLe >= :debut and d.demandeLe < :fin
              and (d.perimetreCode is null or d.perimetreCode in :perimetres)
            """)
    List<Depense> periode(java.time.Instant debut, java.time.Instant fin, Collection<String> perimetres);

    @Query(value = "select nextval('sq_depense_reference')", nativeQuery = true)
    long prochainNumero();

    interface TotalParStatut {
        Depense.Statut getStatut();

        long getNombre();

        long getTotal();
    }
}
