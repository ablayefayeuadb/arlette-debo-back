package sn.arlettedebo.platform.rh.internal;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/** « À traiter » : départs non pointés à régulariser (Manager de ses salons, Administration). */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PointageTaches implements SourceDeTaches {

    private static final java.time.format.DateTimeFormatter HEURE = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    private final PointageService pointage;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.RH;
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!access.peut(ModuleCode.RH, ActionCode.G) && !access.estProfil(Profils.MANAGER)) {
            return List.of();
        }
        return pointage.aRegulariser().stream().map(p -> {
            var x = p.pointage();
            String arrivee = x.getArriveeLe() == null ? "—" : HEURE.format(x.getArriveeLe().atZone(AuditingConfig.ZONE));
            return new Tache(x.getId(), ModuleCode.RH, "REGULARISER_DEPART", ActionCode.G, "Départ non pointé",
                    p.nom() + " · " + x.getJour() + " · arrivée " + arrivee, "/api/v1/rh/pointage/" + x.getId(), null, p.perimetre(),
                    x.getJour().atTime(x.getHeureFinPrevue() == null ? java.time.LocalTime.of(19, 0) : x.getHeureFinPrevue())
                            .atZone(AuditingConfig.ZONE).toInstant());
        }).toList();
    }
}
