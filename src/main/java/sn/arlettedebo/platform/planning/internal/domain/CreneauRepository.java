package sn.arlettedebo.platform.planning.internal.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreneauRepository extends JpaRepository<Creneau, UUID> {

    List<Creneau> findByPerimetreCodeAndJourBetweenAndOuvertTrueOrderByJourAscHeureDebutAsc(String perimetre, LocalDate du, LocalDate au);

    /** Grille d'un jour (ouverts et fermés) — index unique ux_td_creneau. */
    List<Creneau> findByPerimetreCodeAndJourOrderByHeureDebutAsc(String perimetre, LocalDate jour);

    /** Grille d'une période (ouverts et fermés), pour la vue semaine Manager. */
    List<Creneau> findByPerimetreCodeAndJourBetweenOrderByJourAscHeureDebutAsc(String perimetre, LocalDate du, LocalDate au);

    /** Créneau ouvert verrouillé pendant la réservation : deux réservations simultanées ne dépassent pas la capacité. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Creneau> findFirstByPerimetreCodeAndJourAndHeureDebutAndOuvertTrue(String perimetre, LocalDate jour,
            java.time.LocalTime heureDebut);
}
