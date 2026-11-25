package sn.arlettedebo.platform.qualite.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.qualite.internal.domain.Reclamation;
import sn.arlettedebo.platform.qualite.internal.domain.ReclamationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Signalement;
import sn.arlettedebo.platform.qualite.internal.domain.SignalementRepository;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** « À traiter » qualité (gestion G) : signalements nouveaux et réclamations non prises en charge. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class QualiteTaches implements SourceDeTaches {

    private final SignalementRepository signalements;
    private final ReclamationRepository reclamations;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.QUALITE;
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!access.peut(ModuleCode.QUALITE, ActionCode.G)) {
            return List.of();
        }
        Set<String> perimetres = access.utilisateur().perimetres();
        List<Tache> out = new ArrayList<>();
        signalements.findByPerimetreCodeInAndStatutOrderByDateSignalementDesc(perimetres, Signalement.Statut.NOUVEAU)
                .forEach(s -> out.add(new Tache(s.getId(), ModuleCode.QUALITE, "TRAITER_SIGNALEMENT", ActionCode.G,
                        "Signalement à prendre en compte", s.getTypeCode() + " · " + s.getDescription(),
                        "/api/v1/qualite/signalements/" + s.getId(), null, s.getPerimetreCode(), s.getDateSignalement())));
        reclamations.findByPerimetreCodeInAndStatutOrderByDateReclamationDesc(perimetres, Reclamation.Statut.NOUVELLE)
                .forEach(r -> out.add(new Tache(r.getId(), ModuleCode.QUALITE, "TRAITER_RECLAMATION", ActionCode.G,
                        "Réclamation à traiter", r.getClienteNom() + " · " + r.getMotif() + " · " + r.getGravite().name().toLowerCase(),
                        "/api/v1/qualite/reclamations/" + r.getId(), null, r.getPerimetreCode(), r.getDateReclamation())));
        return out;
    }
}
