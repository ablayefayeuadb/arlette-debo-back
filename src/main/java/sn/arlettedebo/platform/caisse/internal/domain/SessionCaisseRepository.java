package sn.arlettedebo.platform.caisse.internal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SessionCaisseRepository extends JpaRepository<SessionCaisse, UUID> {

    /** Index unique ux_td_session_caisse_perimetre_jour ; encaissements chargés en une requête. */
    @EntityGraph(attributePaths = "encaissements")
    Optional<SessionCaisse> findByPerimetreCodeAndJour(String perimetre, LocalDate jour);

    /** Une seule session OUVERTE par périmètre (index ux_td_session_caisse_ouverte). */
    Optional<SessionCaisse> findByPerimetreCodeAndStatut(String perimetre, SessionCaisse.Statut statut);

    @EntityGraph(attributePaths = "encaissements")
    Optional<SessionCaisse> findWithEncaissementsById(UUID id);

    /** Sessions encore ouvertes des périmètres (une seule par périmètre), plus anciennes d'abord. */
    @EntityGraph(attributePaths = "encaissements")
    List<SessionCaisse> findWithEncaissementsByPerimetreCodeInAndStatutOrderByJourAsc(Collection<String> perimetres,
            SessionCaisse.Statut statut);

    /** Clôtures à contrôler — index partiel ix_td_session_caisse_a_controler. */
    @EntityGraph(attributePaths = "encaissements")
    @Query("select s from SessionCaisse s where s.statut = sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse.Statut.CLOTUREE "
            + "and s.controlePar is null and s.perimetreCode in :perimetres order by s.jour desc")
    List<SessionCaisse> aControler(Collection<String> perimetres);

    List<SessionCaisse> findByPerimetreCodeInAndJourBetweenOrderByJourDescPerimetreCodeAsc(Collection<String> perimetres, LocalDate du, LocalDate au);

    /** Page de sessions (encaissements chargés par lots : voir @BatchSize sur SessionCaisse). */
    org.springframework.data.domain.Page<SessionCaisse> findByPerimetreCodeInAndJourBetween(Collection<String> perimetres, LocalDate du, LocalDate au,
            org.springframework.data.domain.Pageable page);

    /** Agrégats par session (CA, nombre, espèces) sans charger les encaissements. */
    @Query("""
            select e.session.id as sessionId, coalesce(sum(e.montant), 0) as total, count(e) as nombre
            from Encaissement e
            where e.annule = false and e.session.id in :sessions
            group by e.session.id
            """)
    List<TotalSession> totaux(Collection<UUID> sessions);

    /** CA par mode de paiement sur une période. */
    @Query("""
            select e.modePaiementCode as mode, coalesce(sum(e.montant), 0) as total, count(e) as nombre
            from Encaissement e join e.session s
            where e.annule = false and s.perimetreCode in :perimetres and s.jour between :du and :au
            group by e.modePaiementCode
            """)
    List<TotalMode> parMode(Collection<String> perimetres, LocalDate du, LocalDate au);

    /** Synthèse des clôtures d'une période calculée en SQL (pas de chargement des sessions). */
    interface SyntheseClotures {
        long getNbAControler();

        long getControlees();

        long getEcarts();

        long getAnomalies();
    }

    @Query("""
            select coalesce(sum(case when s.statut = sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse.Statut.CLOTUREE
                                      and s.controlePar is null then 1 else 0 end), 0) as nbAControler,
                   coalesce(sum(case when s.controlePar is not null then 1 else 0 end), 0) as controlees,
                   coalesce(sum(abs(coalesce(s.ecart, 0))), 0) as ecarts,
                   coalesce(sum(case when s.controleConforme = false then 1 else 0 end), 0) as anomalies
            from SessionCaisse s
            where s.perimetreCode in :perimetres and s.jour between :du and :au
            """)
    SyntheseClotures synthese(Collection<String> perimetres, LocalDate du, LocalDate au);

    /** Chiffre d'affaires et nombre d'encaissements d'une période (une ligne d'agrégat). */
    interface TotalPeriode {
        long getTotal();

        long getNombre();
    }

    @Query("""
            select coalesce(sum(e.montant), 0) as total, count(e) as nombre
            from Encaissement e join e.session s
            where e.annule = false and s.perimetreCode in :perimetres and s.jour between :du and :au
            """)
    TotalPeriode totalPeriode(Collection<String> perimetres, LocalDate du, LocalDate au);

    interface TotalSession {
        UUID getSessionId();

        long getTotal();

        long getNombre();
    }

    interface TotalMode {
        String getMode();

        long getTotal();

        long getNombre();
    }
}
