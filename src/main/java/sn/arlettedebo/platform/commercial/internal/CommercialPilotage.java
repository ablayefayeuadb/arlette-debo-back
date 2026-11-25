package sn.arlettedebo.platform.commercial.internal;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.internal.domain.Client;
import sn.arlettedebo.platform.commercial.internal.domain.ClientRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisation;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisationRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Niveau;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** Contribution commerciale : gestes commerciaux, clientèle, fidélisation ; décisions à arbitrer. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CommercialPilotage implements SourceIndicateurs, SourceDeTaches {

    private final DemandeAutorisationRepository demandes;
    private final ClientRepository clients;
    private final AutorisationService autorisations;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.CAISSE;
    }

    @Override
    public int ordre() {
        return 15;
    }

    @Override
    public String titre() {
        return "Commercial & fidélisation";
    }

    @Override
    public boolean pourVueAdministratif() {
        return false;
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        List<DemandeAutorisation> liste = demandes.findByPerimetreCodeInAndDemandeLeGreaterThanEqualAndDemandeLeLessThan(perimetres, p.debut(), p.fin());
        long accorde = liste.stream().filter(d -> d.getStatut() == DemandeAutorisation.Statut.AUTORISEE
                || d.getStatut() == DemandeAutorisation.Statut.APPLIQUEE).mapToLong(DemandeAutorisation::getMontant).sum();
        long enAttente = liste.stream().filter(d -> d.getStatut() == DemandeAutorisation.Statut.EN_ATTENTE).count();
        long inactives = clients.findByPerimetreCodeInAndDerniereVisiteBefore(perimetres, p.au().minusDays(90)).size();
        return List.of(
                Indicateur.nombre("commercial.demandes", "Gestes commerciaux demandés", liste.size(), Sens.BAISSE_FAVORABLE),
                Indicateur.montant("commercial.accorde", "Montant accordé", accorde, Sens.BAISSE_FAVORABLE),
                Indicateur.nombre("commercial.attente", "Décisions en attente", enAttente, Sens.NEUTRE).alerteSi(enAttente > 0, Niveau.ATTENTION),
                Indicateur.nombre("commercial.nouvelles", "Nouvelles fiches clientes",
                        clients.countByPerimetreCodeInAndCreeLeGreaterThanEqualAndCreeLeLessThan(perimetres, p.debut(), p.fin()), Sens.HAUSSE_FAVORABLE),
                Indicateur.nombre("commercial.abonnees", "Clientes abonnées", clients.countByPerimetreCodeInAndStatut(perimetres, Client.Statut.ABONNEE),
                        Sens.HAUSSE_FAVORABLE),
                Indicateur.nombre("commercial.inactives", "Clientes inactives (> 90 j)", inactives, Sens.BAISSE_FAVORABLE)
                        .detail("dernière visite avant le " + p.au().minusDays(90)).alerteSi(inactives > 0, Niveau.ATTENTION));
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!access.peut(ModuleCode.CAISSE, ActionCode.C)) {
            return List.of();
        }
        return demandes.findByStatutAndPerimetreCodeInOrderByDemandeLeDesc(DemandeAutorisation.Statut.EN_ATTENTE, access.utilisateur().perimetres())
                .stream()
                .filter(autorisations::peutDecider)
                .map(d -> new Tache(d.getId(), ModuleCode.CAISSE, "AUTORISER_" + d.getTypeCode(), ActionCode.V,
                        "Geste commercial à autoriser", d.getTypeCode() + " · " + d.getClienteNom() + " — " + d.getMotif(),
                        "/api/v1/commercial/autorisations/" + d.getId(), d.getMontant(), d.getPerimetreCode(), d.getDemandeLe()))
                .toList();
    }

}
