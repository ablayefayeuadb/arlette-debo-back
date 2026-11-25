package sn.arlettedebo.platform.rh.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.rh.internal.domain.Absence;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** « À traiter » RH : demandes de congé en attente (DG, DO, Manager). */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class RhTaches implements SourceDeTaches {

    private final RhService rh;

    @Override
    public ModuleCode module() {
        return ModuleCode.RH;
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!rh.peutVoirAbsences()) {
            return List.of();
        }
        Map<UUID, String> noms = rh.nomsVisibles();
        return rh.absencesVisibles().stream()
                .filter(a -> a.getStatut() == Absence.Statut.DEMANDE)
                .map(a -> new Tache(a.getId(), ModuleCode.RH, "DECIDER_CONGE", ActionCode.G, "Congé à décider",
                        noms.getOrDefault(a.getEmployeId(), "—") + " · " + a.getDu() + " → " + a.getAu()
                                + (a.getMotif() == null ? "" : " · " + a.getMotif()),
                        "/api/v1/rh/absences/" + a.getId(), null, null, a.getCreeLe()))
                .toList();
    }
}
