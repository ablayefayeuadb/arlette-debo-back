package sn.arlettedebo.platform.qualite.internal.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistHygieneRepository extends JpaRepository<ChecklistHygiene, UUID> {

    List<ChecklistHygiene> findByPerimetreCodeAndJourOrderByMomentAsc(String perimetre, LocalDate jour);
}
