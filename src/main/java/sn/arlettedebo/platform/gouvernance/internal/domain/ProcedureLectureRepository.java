package sn.arlettedebo.platform.gouvernance.internal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcedureLectureRepository extends JpaRepository<ProcedureLecture, UUID> {

    Optional<ProcedureLecture> findByProcedureIdAndUtilisateurId(UUID procedureId, UUID utilisateurId);

    List<ProcedureLecture> findByProcedureIdOrderByLuLeDesc(UUID procedureId);

    List<ProcedureLecture> findByUtilisateurIdOrderByLuLeDesc(UUID utilisateurId);

    long countByProcedureId(UUID procedureId);
}
