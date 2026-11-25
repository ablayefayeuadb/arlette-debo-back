package sn.arlettedebo.platform.finance.internal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.finance.internal.domain.Depense;
import sn.arlettedebo.platform.finance.internal.domain.DepenseRepository;
import sn.arlettedebo.platform.finance.internal.domain.Justificatif;
import sn.arlettedebo.platform.finance.internal.domain.JustificatifRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Niveau;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.finance.internal.DepenseService.ActionPossible;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** Contribution Finance au pilotage : indicateurs de dépenses et dossiers en attente d'action. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FinancePilotage implements SourceIndicateurs, SourceDeTaches {

    private static final Set<Depense.Statut> ENGAGEES = EnumSet.of(Depense.Statut.VALIDEE, Depense.Statut.PAYEE, Depense.Statut.CONTROLEE);

    private final DepenseRepository depenses;
    private final JustificatifRepository justificatifs;
    private final DepenseService service;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.ADMINISTRATION;
    }

    @Override
    public int ordre() {
        return 70;
    }

    @Override
    public String titre() {
        return "Achats & dépenses";
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        List<Depense> liste = depenses.periode(p.debut(), p.fin(), perimetres);
        Set<UUID> avecPiece = pieces(liste);
        long engage = liste.stream().filter(d -> ENGAGEES.contains(d.getStatut())).mapToLong(Depense::getMontant).sum();
        long enAttente = liste.stream().filter(d -> d.getStatut() == Depense.Statut.DEMANDEE).mapToLong(Depense::getMontant).sum();
        long nbAttente = liste.stream().filter(d -> d.getStatut() == Depense.Statut.DEMANDEE).count();
        long aControler = liste.stream().filter(d -> d.getStatut() == Depense.Statut.PAYEE).count();
        long sansPiece = liste.stream().filter(d -> d.getStatut() != Depense.Statut.REFUSEE && !avecPiece.contains(d.getId())).count();
        return List.of(
                Indicateur.montant("finance.engage", "Dépenses engagées", engage, Sens.NEUTRE),
                Indicateur.montant("finance.attente", "En attente de validation", enAttente, Sens.NEUTRE)
                        .detail(nbAttente + " dossier(s)").alerteSi(nbAttente > 0, Niveau.ATTENTION),
                Indicateur.nombre("finance.a_controler", "Paiements à contrôler", aControler, Sens.BAISSE_FAVORABLE)
                        .alerteSi(aControler > 0, Niveau.ATTENTION),
                Indicateur.nombre("finance.sans_justificatif", "Justificatifs manquants", sansPiece, Sens.BAISSE_FAVORABLE)
                        .alerteSi(sansPiece > 0, Niveau.ALERTE));
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!access.peut(ModuleCode.ADMINISTRATION, ActionCode.C)) {
            return List.of();
        }
        List<Depense> enCircuit = depenses.findByStatutInOrderByDemandeLeDesc(
                EnumSet.of(Depense.Statut.DEMANDEE, Depense.Statut.VALIDEE, Depense.Statut.PAYEE));
        Set<UUID> avecPiece = pieces(enCircuit);
        long seuil = service.seuilDg();
        List<Tache> out = new ArrayList<>();
        for (Depense d : enCircuit) {
            for (var a : service.actionsPossibles(d, seuil, avecPiece.contains(d.getId()))) {
                if (!a.autorisee() || ActionPossible.REFUSER.equals(a.action())) {
                    continue;
                }
                Etape e = Etape.de(a.action());
                out.add(new Tache(d.getId(), ModuleCode.ADMINISTRATION, e.type, e.niveau, e.titre,
                        d.getReference() + " · " + d.getObjet() + " · " + d.getFournisseur(),
                        "/api/v1/finance/depenses/" + d.getId(), d.getMontant(), d.getPerimetreCode(), d.getDemandeLe()));
            }
        }
        return out;
    }

    private Set<UUID> pieces(List<Depense> liste) {
        Set<UUID> out = new HashSet<>();
        justificatifs.findByDepenseIdIn(liste.stream().map(Depense::getId).toList()).stream().map(Justificatif::getDepenseId).forEach(out::add);
        return out;
    }

    /** Correspondance action possible → tâche « À traiter ». */
    private enum Etape {
        VALIDER("VALIDER_DEPENSE", ActionCode.V, "Dépense à valider"),
        PAYER("PAYER_DEPENSE", ActionCode.G, "Dépense à payer"),
        CONTROLER("CONTROLER_PAIEMENT", ActionCode.C, "Paiement à contrôler");

        private final String type;
        private final ActionCode niveau;
        private final String titre;

        Etape(String type, ActionCode niveau, String titre) {
            this.type = type;
            this.niveau = niveau;
            this.titre = titre;
        }

        static Etape de(String action) {
            return switch (action) {
                case ActionPossible.VALIDER -> VALIDER;
                case ActionPossible.PAYER -> PAYER;
                default -> CONTROLER;
            };
        }
    }
}
