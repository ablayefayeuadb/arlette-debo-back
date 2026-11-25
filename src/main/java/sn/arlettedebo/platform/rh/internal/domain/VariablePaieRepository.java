package sn.arlettedebo.platform.rh.internal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariablePaieRepository extends JpaRepository<VariablePaie, UUID> {

    /** Index ix_td_variable_paie_mois_statut. */
    List<VariablePaie> findByMoisOrderByCreeLeAsc(String mois);

    List<VariablePaie> findByMoisAndStatut(String mois, VariablePaie.Statut statut);
}
