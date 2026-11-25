package sn.arlettedebo.platform.caisse.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisseRepository;
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

/** Contribution caisse : chiffre d'affaires, panier moyen, écarts, clôtures à contrôler. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CaissePilotage implements SourceIndicateurs, SourceDeTaches {

    private final SessionCaisseRepository sessions;
    private final CaisseService service;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.CAISSE;
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public boolean contribueAuComparatif() {
        return true;
    }

    @Override
    public String titre() {
        return "Activité & caisse";
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        var total = sessions.totalPeriode(perimetres, p.du(), p.au());
        var clotures = sessions.synthese(perimetres, p.du(), p.au());
        long ca = total.getTotal();
        long nb = total.getNombre();
        long ecarts = clotures.getEcarts();
        long aControler = clotures.getNbAControler();
        long anomalies = clotures.getAnomalies();
        Map<String, Long> parMode = sessions.parMode(perimetres, p.du(), p.au()).stream()
                .collect(Collectors.toMap(SessionCaisseRepository.TotalMode::getMode, SessionCaisseRepository.TotalMode::getTotal));
        List<Indicateur> out = new java.util.ArrayList<>();
        out.add(Indicateur.montant("caisse.ca", "Chiffre d'affaires", ca, Sens.HAUSSE_FAVORABLE).detail(detailModes(parMode)));
        out.add(Indicateur.nombre("caisse.encaissements", "Encaissements", nb, Sens.HAUSSE_FAVORABLE));
        out.add(Indicateur.montant("caisse.panier_moyen", "Panier moyen", nb == 0 ? 0 : Math.round((double) ca / nb), Sens.HAUSSE_FAVORABLE));
        out.add(Indicateur.montant("caisse.ecarts", "Écarts de caisse (cumul)", ecarts, Sens.BAISSE_FAVORABLE).alerteSi(ecarts > 0, Niveau.ATTENTION));
        // Contrôle de clôture : direction / profils sans saisie caisse — pas le caissier opérationnel
        if (voitControleCloture()) {
            out.add(Indicateur.nombre("caisse.a_controler", "Clôtures à contrôler", aControler, Sens.BAISSE_FAVORABLE)
                    .detail(anomalies + " anomalie(s) relevée(s)").alerteSi(aControler > 0, Niveau.ATTENTION));
        }
        return out;
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!voitControleCloture()) {
            return List.of();
        }
        var liste = service.aControler();
        Map<UUID, SessionCaisseRepository.TotalSession> totaux = service.totaux(liste.stream().map(SessionCaisse::getId).toList());
        return liste.stream().map(s -> new Tache(s.getId(), ModuleCode.CAISSE, "CONTROLER_CLOTURE", ActionCode.C,
                        "Clôture de caisse à contrôler",
                        s.getPerimetreCode() + " · " + s.getJour() + " · écart " + (s.getEcart() == null ? 0 : s.getEcart()) + " FCFA",
                        "/api/v1/caisse/sessions/" + s.getId(), totaux.containsKey(s.getId()) ? totaux.get(s.getId()).getTotal() : 0L,
                        s.getPerimetreCode(), s.getClotureeLe()))
                .toList();
    }

    /** Contrôle indépendant : V caisse, G dashboard, ou lecture caisse sans saisie (pas caissier / clientèle). */
    private boolean voitControleCloture() {
        var u = access.utilisateur();
        if (!u.peut(ModuleCode.CAISSE, ActionCode.C)) {
            return false;
        }
        if (u.peut(ModuleCode.CAISSE, ActionCode.V) || u.peut(ModuleCode.DASHBOARD, ActionCode.G)) {
            return true;
        }
        // Caissier / chargé de clientèle (S ou G opérationnels) : pas de file de contrôle
        return !u.peut(ModuleCode.CAISSE, ActionCode.S) && !u.peut(ModuleCode.CAISSE, ActionCode.G);
    }

    private static String detailModes(Map<String, Long> parMode) {
        return parMode.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(" · "));
    }
}
