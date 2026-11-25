package sn.arlettedebo.platform.gouvernance.internal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcedureDocRepository extends JpaRepository<ProcedureDoc, UUID> {

    List<ProcedureDoc> findAllByOrderByTitreAscVersionNumDesc();

    List<ProcedureDoc> findByStatutOrderByTitreAsc(ProcedureDoc.Statut statut);
}
