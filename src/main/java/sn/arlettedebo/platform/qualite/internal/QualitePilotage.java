package sn.arlettedebo.platform.qualite.internal;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.qualite.internal.domain.AvisClient;
import sn.arlettedebo.platform.qualite.internal.domain.AvisClientRepository;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestation;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformite;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformiteRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Reclamation;
import sn.arlettedebo.platform.qualite.internal.domain.ReclamationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.TicketMaintenance;
import sn.arlettedebo.platform.qualite.internal.domain.TicketMaintenanceRepository;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Niveau;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** Indicateurs qualité : satisfaction, contrôles, non-conformités, réclamations, maintenance. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class QualitePilotage implements SourceIndicateurs {

    private final AvisClientRepository avis;
    private final ControlePrestationRepository controles;
    private final NonConformiteRepository ncs;
    private final ReclamationRepository reclamations;
    private final TicketMaintenanceRepository tickets;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.QUALITE;
    }

    @Override
    public int ordre() {
        return 30;
    }

    @Override
    public boolean contribueAuComparatif() {
        return true;
    }

    @Override
    public String titre() {
        return "Qualité & satisfaction";
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        if ("limité".equals(access.utilisateur().restriction(ModuleCode.QUALITE))) {
            return List.of();
        }
        List<AvisClient> notes = avis.findByPerimetreCodeInAndDateAvisGreaterThanEqualAndDateAvisLessThanOrderByDateAvisDesc(perimetres, p.debut(), p.fin());
        Double satisfaction = notes.isEmpty() ? null
                : Math.round(notes.stream().mapToInt(AvisClient::getNote).average().orElse(0) * 10) / 10.0;
        List<ControlePrestation> ctrl = controles.findByPerimetreCodeInAndDateControleGreaterThanEqualAndDateControleLessThanOrderByDateControleDesc(
                perimetres, p.debut(), p.fin());
        List<NonConformite> ouvertes = ncs.findByPerimetreCodeInAndStatutNotOrderByDateConstatDesc(perimetres, NonConformite.Statut.CLOTUREE);
        long critiques = ouvertes.stream().filter(n -> n.getGravite() == NonConformite.Gravite.CRITIQUE).count();
        List<Reclamation> recl = reclamations.findByPerimetreCodeInAndDateReclamationGreaterThanEqualAndDateReclamationLessThanOrderByDateReclamationDesc(
                perimetres, p.debut(), p.fin());
        long nonResolues = recl.stream().filter(r -> r.getStatut() != Reclamation.Statut.RESOLUE).count();
        long maintenance = tickets.findByPerimetreCodeInAndStatutNot(perimetres, TicketMaintenance.Statut.VALIDE).size();
        return List.of(
                new Indicateur("qualite.satisfaction", "Satisfaction cliente", satisfaction, "/5",
                        niveauSatisfaction(satisfaction), Sens.HAUSSE_FAVORABLE, notes.size() + " avis"),
                Indicateur.taux("qualite.controles_conformes", "Prestations contrôlées conformes",
                        ctrl.stream().filter(ControlePrestation::isConforme).count(), ctrl.size(), Sens.HAUSSE_FAVORABLE),
                Indicateur.nombre("qualite.nc_ouvertes", "Non-conformités ouvertes", ouvertes.size(), Sens.BAISSE_FAVORABLE)
                        .detail(critiques + " critique(s)").alerteSi(!ouvertes.isEmpty(), critiques > 0 ? Niveau.ALERTE : Niveau.ATTENTION),
                Indicateur.nombre("qualite.reclamations", "Réclamations", recl.size(), Sens.BAISSE_FAVORABLE)
                        .detail(nonResolues + " non résolue(s)").alerteSi(nonResolues > 0, Niveau.ATTENTION),
                Indicateur.nombre("qualite.maintenance", "Incidents de maintenance ouverts", maintenance, Sens.BAISSE_FAVORABLE)
                        .alerteSi(maintenance > 0, Niveau.ATTENTION));
    }

    /** Satisfaction : alerte sous 4/5 ; neutre sans avis sur la période. */
    private static Niveau niveauSatisfaction(Double satisfaction) {
        if (satisfaction == null) {
            return Niveau.INFO;
        }
        return satisfaction < 4 ? Niveau.ALERTE : Niveau.OK;
    }
}
