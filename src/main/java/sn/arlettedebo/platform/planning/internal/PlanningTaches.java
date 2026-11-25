package sn.arlettedebo.platform.planning.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** « À traiter » planning (gestion G : accueil, Manager) : réservations en ligne à confirmer. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PlanningTaches implements SourceDeTaches {

    private final RendezVousRepository rdvs;
    private final AccessPolicy access;
    private final Clock clock;
    private final Referentiels referentiels;

    @Override
    public ModuleCode module() {
        return ModuleCode.PLANNING;
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!access.peut(ModuleCode.PLANNING, ActionCode.G)) {
            return List.of();
        }
        return rdvs.findByPerimetreCodeInAndSourceAndStatutAndJourGreaterThanEqualOrderByJourAscHeureAsc(access.utilisateur().perimetres(),
                        RendezVous.Source.EN_LIGNE, RendezVous.Statut.PREVU, LocalDate.now(clock)).stream()
                .map(r -> {
                    String prestation = referentiels.prestation(r.getPrestationCode()).map(Referentiels.PrestationInfo::libelle)
                            .orElse(r.getPrestationCode());
                    String heure = r.getHeure().toString().substring(0, 5);
                    return new Tache(r.getId(), ModuleCode.PLANNING, "CONFIRMER_RDV_EN_LIGNE", ActionCode.G,
                            "Réservation en ligne à confirmer",
                            r.getClienteNom() + " · " + prestation + " · " + r.getJour() + " " + heure,
                            "/api/v1/planning/rendez-vous/" + r.getId(), r.getPrix(), r.getPerimetreCode(), r.getCreeLe());
                })
                .toList();
    }
}
