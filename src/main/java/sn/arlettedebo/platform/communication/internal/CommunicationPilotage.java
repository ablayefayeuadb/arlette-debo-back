package sn.arlettedebo.platform.communication.internal;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.communication.internal.CommunicationService.Etape;
import sn.arlettedebo.platform.communication.internal.domain.Publication;
import sn.arlettedebo.platform.communication.internal.domain.PublicationRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** Contribution communication : performances digitales ; contenus à vérifier / valider / publier. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CommunicationPilotage implements SourceIndicateurs, SourceDeTaches {

    private final PublicationRepository publications;
    private final CommunicationService service;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.COMMUNICATION;
    }

    @Override
    public int ordre() {
        return 60;
    }

    @Override
    public String titre() {
        return "Communication & performances digitales";
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        List<Publication> publiees = publications.publieesEntre(p.debut(), p.fin()).stream()
                .filter(x -> x.getPerimetreCode() == null || perimetres.contains(x.getPerimetreCode())).toList();
        long portee = publiees.stream().map(Publication::getPortee).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long interactions = publiees.stream().map(Publication::getInteractions).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long abonnes = publiees.stream().map(Publication::getNouveauxAbonnes).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        return List.of(
                Indicateur.nombre("communication.publiees", "Contenus publiés", publiees.size(), Sens.HAUSSE_FAVORABLE),
                Indicateur.nombre("communication.portee", "Portée", portee, Sens.HAUSSE_FAVORABLE),
                Indicateur.taux("communication.engagement", "Taux d'engagement", interactions, portee, Sens.HAUSSE_FAVORABLE),
                Indicateur.nombre("communication.abonnes", "Nouveaux abonnés", abonnes, Sens.HAUSSE_FAVORABLE),
                Indicateur.nombre("communication.en_circuit", "Contenus en circuit de validation", service.enCircuit().size(), Sens.NEUTRE));
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!access.peut(ModuleCode.COMMUNICATION, ActionCode.C)) {
            return List.of();
        }
        return service.enCircuit().stream().flatMap(p -> service.etapesPossibles(p).stream()
                .filter(e -> e != Etape.RENVOYER)
                .map(e -> new Tache(p.getId(), ModuleCode.COMMUNICATION, e.name() + "_CONTENU",
                        niveau(e),
                        switch (e) {
                            case VERIFIER -> "Contenu à vérifier";
                            case VALIDER -> "Publication à valider";
                            case PUBLIER -> "Publication validée à publier";
                            default -> "Contenu à soumettre";
                        },
                        p.getTitre() + " · " + p.getCanalCode() + (p.getDatePrevue() == null ? "" : " · prévu le " + p.getDatePrevue()),
                        "/api/v1/communication/publications/" + p.getId(), null, p.getPerimetreCode(), p.getCreeLe())))
                .toList();
    }

    /** Droit mobilisé par l'étape : validation (DG), vérification (DO), sinon saisie (Community). */
    private static ActionCode niveau(Etape e) {
        return switch (e) {
            case VALIDER -> ActionCode.V;
            case VERIFIER -> ActionCode.G;
            default -> ActionCode.S;
        };
    }
}
