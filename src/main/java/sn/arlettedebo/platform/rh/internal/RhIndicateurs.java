package sn.arlettedebo.platform.rh.internal;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.rh.internal.domain.Absence;
import sn.arlettedebo.platform.rh.internal.domain.AbsenceRepository;
import sn.arlettedebo.platform.rh.internal.domain.Employe;
import sn.arlettedebo.platform.rh.internal.domain.EmployeRepository;
import sn.arlettedebo.platform.rh.internal.domain.Pointage;
import sn.arlettedebo.platform.rh.internal.domain.PointageRepository;
import sn.arlettedebo.platform.rh.internal.domain.VariablePaieRepository;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Niveau;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/** Indicateurs RH : effectif, absences, retards, ponctualité, congés en attente, masse variable. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class RhIndicateurs implements SourceIndicateurs {

    private final EmployeRepository employes;
    private final AbsenceRepository absences;
    private final PointageRepository pointages;
    private final VariablePaieRepository variables;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.RH;
    }

    @Override
    public int ordre() {
        return 50;
    }

    @Override
    public String titre() {
        return "Ressources humaines";
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        boolean limite = "limité".equals(access.utilisateur().restriction(ModuleCode.RH));
        List<Employe> equipe = employes.findAllByOrderByNomAsc().stream()
                .filter(e -> e.getPerimetreCode() == null ? !limite : perimetres.contains(e.getPerimetreCode()))
                .toList();
        Set<UUID> ids = equipe.stream().map(Employe::getId).collect(Collectors.toSet());
        List<Absence> toutes = absences.findByEmployeIdInOrderByDuDesc(ids);
        long absencesApprouvees = toutes.stream()
                .filter(a -> !a.getAu().isBefore(p.du()) && !a.getDu().isAfter(p.au()))
                .filter(a -> a.getStatut() == Absence.Statut.APPROUVEE && !"RETARD".equals(a.getTypeCode())).count();
        long congesEnAttente = toutes.stream().filter(a -> a.getStatut() == Absence.Statut.DEMANDE).count();

        var periode = pointages.findByJourBetweenAndEmployeIdIn(p.du(), p.au(), ids).stream().filter(pt -> pt.getArriveeLe() != null).toList();
        long pointes = periode.size();
        long retards = periode.stream().filter(pt -> pt.getRetardMin() > 0).count();
        long minutesRetard = periode.stream().mapToLong(Pointage::getRetardMin).sum();
        long departsAnticipes = periode.stream().filter(pt -> pt.getDepartAnticipeMin() > 0).count();
        long minutesAnticipees = periode.stream().mapToLong(Pointage::getDepartAnticipeMin).sum();
        long nonPointes = periode.stream().filter(pt -> pt.isDepartNonPointe() && pt.getRegularisePar() == null).count();

        List<Indicateur> out = new ArrayList<>();
        out.add(Indicateur.nombre("rh.effectif", "Effectif actif", equipe.stream().filter(Employe::isActif).count(), Sens.NEUTRE));
        if (access.estProfil(Profils.DG, Profils.DO, Profils.MANAGER)) {
            out.add(Indicateur.nombre("rh.absences", "Absences", absencesApprouvees, Sens.BAISSE_FAVORABLE));
            out.add(Indicateur.nombre("rh.conges_attente", "Congés à traiter", congesEnAttente, Sens.NEUTRE)
                    .alerteSi(congesEnAttente > 0, Niveau.ATTENTION));
        }
        out.add(Indicateur.nombre("rh.retards", "Retards", retards, Sens.BAISSE_FAVORABLE)
                .detail(minutesRetard + " min cumulées").alerteSi(retards > 0, Niveau.ATTENTION));
        out.add(Indicateur.nombre("rh.departs_anticipes", "Départs anticipés", departsAnticipes, Sens.BAISSE_FAVORABLE)
                .detail(minutesAnticipees + " min cumulées").alerteSi(departsAnticipes > 0, Niveau.ATTENTION));
        out.add(Indicateur.nombre("rh.departs_non_pointes", "Départs non pointés", nonPointes, Sens.BAISSE_FAVORABLE)
                .detail("à régulariser").alerteSi(nonPointes > 0, Niveau.ATTENTION));
        out.add(Indicateur.taux("rh.ponctualite", "Ponctualité", pointes - retards, pointes, Sens.HAUSSE_FAVORABLE));
        if (access.peut(ModuleCode.RH, sn.arlettedebo.platform.shared.security.ActionCode.G) || access.estProfil(Profils.DG, Profils.MANAGER)) {
            String mois = YearMonth.from(p.au()).toString();
            long masse = variables.findByMoisOrderByCreeLeAsc(mois).stream().filter(v -> ids.contains(v.getEmployeId()))
                    .mapToLong(v -> "RETENUE".equals(v.getTypeCode()) || "AVANCE".equals(v.getTypeCode()) ? -v.getMontant() : v.getMontant())
                    .sum();
            out.add(Indicateur.montant("rh.variables_paie", "Variables de paie " + mois, masse, Sens.NEUTRE));
        }
        return out;
    }
}
