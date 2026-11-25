package sn.arlettedebo.platform.planning.internal;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous.Statut;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Niveau;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** Indicateurs planning : volume, présence, annulations, no-show, temps d'attente, réservations en ligne. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PlanningPilotage implements SourceIndicateurs {

    private final RendezVousRepository rdvs;
    private final Effectifs effectifs;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.PLANNING;
    }

    @Override
    public int ordre() {
        return 20;
    }

    @Override
    public boolean contribueAuComparatif() {
        return true;
    }

    @Override
    public String titre() {
        return "Rendez-vous & fréquentation";
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        var u = access.utilisateur();
        List<RendezVous> liste = "personnel".equals(u.restriction(ModuleCode.PLANNING))
                ? effectifs.employeDuCompte(u.id()).map(id -> rdvs.findByCoiffeuseIdAndJourBetweenOrderByJourAscHeureAsc(id, p.du(), p.au()))
                        .orElse(List.of())
                : rdvs.findByPerimetreCodeInAndJourBetweenOrderByJourAscHeureAsc(perimetres, p.du(), p.au());
        long total = liste.size();
        long terminees = liste.stream().filter(r -> r.getStatut() == Statut.TERMINEE).count();
        long annulees = liste.stream().filter(r -> r.getStatut() == Statut.ANNULEE).count();
        long absentes = liste.stream().filter(r -> r.getStatut() == Statut.ABSENTE).count();
        long enLigne = liste.stream().filter(r -> r.getSource() == RendezVous.Source.EN_LIGNE).count();
        var attentes = liste.stream().map(RendezVous::attenteMinutes).filter(Objects::nonNull).toList();
        Double attente = attentes.isEmpty() ? null : Math.round(attentes.stream().mapToLong(Long::longValue).average().orElse(0) * 10) / 10.0;
        long caPrevu = liste.stream().filter(r -> r.getStatut() != Statut.ANNULEE && r.getStatut() != Statut.ABSENTE).mapToLong(RendezVous::getPrix).sum();
        return List.of(
                Indicateur.nombre("planning.rdv", "Rendez-vous", total, Sens.HAUSSE_FAVORABLE),
                Indicateur.taux("planning.presence", "Taux de réalisation", terminees, total, Sens.HAUSSE_FAVORABLE),
                Indicateur.taux("planning.annulation", "Taux d'annulation", annulees, total, Sens.BAISSE_FAVORABLE),
                Indicateur.taux("planning.no_show", "No-show", absentes, total, Sens.BAISSE_FAVORABLE).alerteSi(absentes > 0, Niveau.ATTENTION),
                new Indicateur("planning.attente", "Temps d'attente moyen", attente, "min",
                        attente != null && attente > 15 ? Niveau.ALERTE : Niveau.OK, Sens.BAISSE_FAVORABLE, attentes.size() + " mesure(s)"),
                Indicateur.taux("planning.en_ligne", "Réservations en ligne", enLigne, total, Sens.HAUSSE_FAVORABLE),
                Indicateur.montant("planning.ca_prevu", "CA des prestations prévues", caPrevu, Sens.HAUSSE_FAVORABLE));
    }
}
