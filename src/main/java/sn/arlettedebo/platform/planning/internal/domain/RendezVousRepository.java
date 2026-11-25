package sn.arlettedebo.platform.planning.internal.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RendezVousRepository extends JpaRepository<RendezVous, UUID> {

    /** Agenda d'un salon — index ix_td_rendez_vous_perimetre_jour. */
    List<RendezVous> findByPerimetreCodeInAndJourBetweenOrderByJourAscHeureAsc(Collection<String> perimetres, LocalDate du, LocalDate au);

    /** Agenda personnel d'une coiffeuse — index ix_td_rendez_vous_coiffeuse_jour. */
    List<RendezVous> findByCoiffeuseIdAndJourBetweenOrderByJourAscHeureAsc(UUID coiffeuseId, LocalDate du, LocalDate au);

    List<RendezVous> findByClientIdOrderByJourDesc(UUID clientId);

    /** Ligne « mes rendez-vous » du portail : colonnes utiles uniquement. */
    record LigneAVenir(UUID id, LocalDate jour, LocalTime heure, int dureeMin, String prestationCode, RendezVous.Statut statut) {
    }

    /** RDV à venir d'une cliente (hors statut exclus) — portail abonnée, projection paginée en SQL. */
    @Query(value = """
            select new sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository$LigneAVenir(
                r.id, r.jour, r.heure, cast(r.dureeMin as integer), r.prestationCode, r.statut)
            from RendezVous r
            where r.clientId = :clientId and r.jour >= :depuis and r.statut not in :exclus
            order by r.jour asc, r.heure asc
            """,
            countQuery = """
            select count(r) from RendezVous r
            where r.clientId = :clientId and r.jour >= :depuis and r.statut not in :exclus
            """)
    org.springframework.data.domain.Page<LigneAVenir> aVenirDeLaCliente(UUID clientId, LocalDate depuis,
            Collection<RendezVous.Statut> exclus, org.springframework.data.domain.Pageable page);

    /** Jour et heure d'un rendez-vous qui occupe une place (statut non libéré). */
    record JourHeure(LocalDate jour, LocalTime heure) {
    }

    /**
     * Rendez-vous occupant une place d'un salon sur une période : seulement jour et heure
     * (une requête pour toute la grille, au lieu d'un comptage par créneau).
     */
    @Query("""
            select new sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository$JourHeure(r.jour, r.heure)
            from RendezVous r
            where r.perimetreCode = :perimetre and r.jour between :du and :au and r.statut not in :exclus
            """)
    List<JourHeure> prisesEntre(String perimetre, LocalDate du, LocalDate au, Collection<RendezVous.Statut> exclus);

    /** Plage d'un RDV actif sans coiffeuse (une seule est permise par plage qui se chevauche). */
    record PlageSansCoiffeuse(UUID id, LocalTime heure, int dureeMin) {
    }

    @Query("""
            select new sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository$PlageSansCoiffeuse(
                r.id, r.heure, cast(r.dureeMin as integer))
            from RendezVous r
            where r.perimetreCode = :perimetre and r.jour = :jour and r.coiffeuseId is null and r.statut not in :exclus
            """)
    List<PlageSansCoiffeuse> plagesSansCoiffeuse(String perimetre, LocalDate jour, Collection<RendezVous.Statut> exclus);

    /** Places prises dans un créneau : rendez-vous non annulés commençant dans [debut, fin[. */
    long countByPerimetreCodeAndJourAndHeureGreaterThanEqualAndHeureLessThanAndStatutNotIn(String perimetre, LocalDate jour,
            java.time.LocalTime debut, java.time.LocalTime fin, Collection<RendezVous.Statut> exclus);

    /** Réservations en ligne encore à venir d'un même numéro (plafond anti-abus). */
    long countByTelephoneRechercheAndSourceAndJourGreaterThanEqualAndStatutIn(String telephoneRecherche, RendezVous.Source source,
            LocalDate depuis, Collection<RendezVous.Statut> statuts);

    /** Réservations en ligne à confirmer par l'accueil. */
    List<RendezVous> findByPerimetreCodeInAndSourceAndStatutAndJourGreaterThanEqualOrderByJourAscHeureAsc(Collection<String> perimetres,
            RendezVous.Source source, RendezVous.Statut statut, LocalDate depuis);

    /** Doublons potentiels CLI-01 : même téléphone, même jour (hors annulés / absents). */
    List<RendezVous> findByJourAndTelephoneRechercheAndStatutNotInOrderByHeureAsc(LocalDate jour, String telephoneRecherche,
            Collection<RendezVous.Statut> exclus);

    /** Doublons potentiels CLI-01 : même fiche cliente, même jour (hors annulés / absents). */
    List<RendezVous> findByJourAndClientIdAndStatutNotInOrderByHeureAsc(LocalDate jour, UUID clientId,
            Collection<RendezVous.Statut> exclus);
}
