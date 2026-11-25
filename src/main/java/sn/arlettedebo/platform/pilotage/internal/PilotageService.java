package sn.arlettedebo.platform.pilotage.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Agrège les contributions des modules selon les droits de l'utilisateur :
 * <ul>
 *   <li>une section n'est calculée que si l'utilisateur a au moins C sur le module ;</li>
 *   <li>restriction du tableau de bord : « administratif » (finance, caisse, stocks — hors commercial),
 *       « statistiques » (communication / stocks), « personnel » (planning personnel), « limité »
 *       (modules où l'utilisateur a S ou G — vue opérationnelle, sans simple consultation) ;</li>
 *   <li>reporting : DG / DO (dashboard G) ou reporting administratif (Administration).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PilotageService {

    private static final String RESTRICTION_ADMINISTRATIF = "administratif";

    /** Indicateurs repris dans le comparatif des périmètres. */
    static final List<String> COMPARATIF = List.of("caisse.ca", "caisse.encaissements", "caisse.panier_moyen", "planning.rdv",
            "planning.no_show", "qualite.satisfaction", "qualite.reclamations", "stock.ruptures", "stock.alertes");

    private final List<SourceIndicateurs> sources;
    private final List<SourceDeTaches> sourcesDeTaches;
    private final AccessPolicy access;
    private final Clock clock;

    // ------------------------------------------------------------------ Tableau de bord

    public TableauDeBord tableauDeBord(Periode periode, String perimetre) {
        access.exiger(ModuleCode.DASHBOARD, ActionCode.C);
        CurrentUser u = access.utilisateur();
        Set<String> perims = perimetres(perimetre);
        List<Section> sections = sections(periode, perims);
        List<Comparaison> comparatif = u.peut(ModuleCode.DASHBOARD, ActionCode.G) && perimetre == null && perims.size() > 1
                ? comparer(periode, perims) : List.of();
        List<Tache> taches = aTraiter();
        return new TableauDeBord(periode, perims, u.restriction(ModuleCode.DASHBOARD), sections, comparatif, taches.size(),
                taches.stream().limit(5).toList());
    }

    // ------------------------------------------------------------------ Reporting

    public Reporting reporting(Periode periode, String perimetre) {
        exigerReporting();
        Set<String> perims = perimetres(perimetre);
        Periode prec = periode.precedente();
        List<SectionComparee> sections = sources().stream().map(src -> {
            Map<String, Indicateur> avant = src.calculer(prec, perims).stream()
                    .collect(Collectors.toMap(Indicateur::code, Function.identity(), (a, b) -> a));
            List<Evolution> lignes = src.calculer(periode, perims).stream()
                    .map(i -> Evolution.de(i, avant.get(i.code()))).toList();
            return new SectionComparee(src.module(), src.titre(), lignes);
        }).filter(s -> !s.indicateurs().isEmpty()).toList();
        return new Reporting(periode, prec, perims, administratif() ? "ADMINISTRATIF" : "CONSOLIDE", sections,
                perimetre == null && perims.size() > 1 ? comparer(periode, perims) : List.of());
    }

    // ------------------------------------------------------------------ À traiter

    /** Tout ce qui attend une action de l'utilisateur : validations (V) d'abord, puis par enjeu financier et ancienneté. */
    public List<Tache> aTraiter() {
        Set<ModuleCode> modules = modulesDashboard();
        return sourcesDeTaches.stream()
                .filter(s -> modules.contains(s.module()))
                .flatMap(s -> s.tachesEnAttente().stream())
                .filter(t -> modules.contains(t.module()))
                .filter(this::tacheActionnable)
                .sorted(Comparator.comparing((Tache t) -> t.niveau() != ActionCode.V)
                        .thenComparing(t -> t.montant() == null ? 0L : -t.montant())
                        .thenComparing(t -> t.depuis() == null ? java.time.Instant.MAX : t.depuis()))
                .toList();
    }

    /**
     * La tâche n'apparaît que si le profil a le niveau d'action requis (V / G / S / C).
     * Exception DO : validation des dépenses sous seuil (matrice administration = C seulement ;
     * la règle métier est dans {@code DepenseService}).
     */
    private boolean tacheActionnable(Tache t) {
        CurrentUser u = access.utilisateur();
        if (t.niveau() == null || u.peut(t.module(), t.niveau())) {
            return true;
        }
        return "VALIDER_DEPENSE".equals(t.type())
                && t.niveau() == ActionCode.V
                && u.estProfil(Profils.DO)
                && u.peut(ModuleCode.ADMINISTRATION, ActionCode.C);
    }

    // ------------------------------------------------------------------ Outils

    private List<Section> sections(Periode periode, Set<String> perims) {
        return sources().stream()
                .map(src -> new Section(src.module(), src.titre(), src.calculer(periode, perims)))
                .filter(s -> !s.indicateurs().isEmpty())
                .toList();
    }

    private List<Comparaison> comparer(Periode periode, Set<String> perims) {
        List<SourceIndicateurs> contributrices = sources().stream().filter(SourceIndicateurs::contribueAuComparatif).toList();
        return perims.stream().sorted().map(p -> new Comparaison(p, contributrices.stream()
                        .flatMap(src -> src.calculer(periode, Set.of(p)).stream())
                        .filter(i -> COMPARATIF.contains(i.code()))
                        .sorted(Comparator.comparingInt(i -> COMPARATIF.indexOf(i.code())))
                        .toList()))
                .toList();
    }

    /** Sources accessibles : droit C sur le module + restriction du tableau de bord. */
    private List<SourceIndicateurs> sources() {
        CurrentUser u = access.utilisateur();
        Set<ModuleCode> autorises = modulesDashboard();
        boolean admin = RESTRICTION_ADMINISTRATIF.equals(u.restriction(ModuleCode.DASHBOARD));
        return sources.stream()
                .filter(s -> autorises.contains(s.module()) && u.peut(s.module(), ActionCode.C))
                .filter(s -> !admin || s.pourVueAdministratif())
                .sorted(Comparator.comparingInt(SourceIndicateurs::ordre))
                .toList();
    }

    /** Modules autorisés sur le tableau de bord selon la restriction du profil. */
    private Set<ModuleCode> modulesDashboard() {
        CurrentUser u = access.utilisateur();
        String r = u.restriction(ModuleCode.DASHBOARD);
        return switch (r == null ? "" : r) {
            case RESTRICTION_ADMINISTRATIF -> EnumSet.of(ModuleCode.ADMINISTRATION, ModuleCode.CAISSE, ModuleCode.STOCKS, ModuleCode.RH);
            case "statistiques" -> EnumSet.of(ModuleCode.COMMUNICATION, ModuleCode.STOCKS);
            case "personnel" -> EnumSet.of(ModuleCode.PLANNING);
            case "limité" -> modulesOperationnels(u);
            default -> EnumSet.allOf(ModuleCode.class);
        };
    }

    /**
     * Vue « limité » : uniquement les modules où le poste a une responsabilité opérationnelle (S ou G).
     * Les modules en simple consultation (C) restent accessibles dans le menu, mais hors du tableau de bord.
     */
    private static Set<ModuleCode> modulesOperationnels(CurrentUser u) {
        EnumSet<ModuleCode> ops = EnumSet.noneOf(ModuleCode.class);
        for (ModuleCode m : ModuleCode.values()) {
            if (m == ModuleCode.DASHBOARD) {
                continue;
            }
            if (u.peut(m, ActionCode.S) || u.peut(m, ActionCode.G)) {
                ops.add(m);
            }
        }
        return ops.isEmpty() ? EnumSet.allOf(ModuleCode.class) : ops;
    }

    private Set<String> perimetres(String perimetre) {
        Set<String> miens = new LinkedHashSet<>(access.utilisateur().perimetres());
        if (perimetre == null) {
            return miens;
        }
        access.exigerPerimetre(perimetre);
        return Set.of(perimetre);
    }

    private boolean administratif() {
        return RESTRICTION_ADMINISTRATIF.equals(access.utilisateur().restriction(ModuleCode.DASHBOARD));
    }

    private void exigerReporting() {
        if (!access.peut(ModuleCode.DASHBOARD, ActionCode.G) && !administratif()) {
            throw new AccessDeniedException("Reporting : Direction (DG / DO) ou reporting administratif.");
        }
    }

    public LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------------ Vues

    public record Section(ModuleCode module, String titre, List<Indicateur> indicateurs) {
    }

    public record Comparaison(String perimetre, List<Indicateur> indicateurs) {
    }

    public record TableauDeBord(Periode periode, Set<String> perimetres, String restriction, List<Section> sections,
            List<Comparaison> comparatif, int nombreATraiter, List<Tache> prioritaires) {
    }

    /**
     * @param evolutionPct variation relative vs période précédente (null si non calculable)
     * @param tendance     FAVORABLE / DEFAVORABLE / STABLE selon le sens de l'indicateur
     */
    public record Evolution(Indicateur indicateur, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS) Double precedente,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS) Double evolutionPct, String tendance) {

        static Evolution de(Indicateur actuel, Indicateur avant) {
            Double prec = avant == null ? null : avant.valeur();
            Double pct = null;
            String tendance = "STABLE";
            if (actuel.valeur() != null && prec != null) {
                double delta = actuel.valeur() - prec;
                pct = prec == 0 ? null : Math.round(1000.0 * delta / Math.abs(prec)) / 10.0;
                if (delta != 0 && actuel.sens() != Sens.NEUTRE) {
                    boolean hausse = delta > 0;
                    tendance = hausse == (actuel.sens() == Sens.HAUSSE_FAVORABLE) ? "FAVORABLE" : "DEFAVORABLE";
                }
            }
            return new Evolution(actuel, prec, pct, tendance);
        }
    }

    public record SectionComparee(ModuleCode module, String titre, List<Evolution> indicateurs) {
    }

    public record Reporting(Periode periode, Periode periodePrecedente, Set<String> perimetres, String type,
            List<SectionComparee> sections, List<Comparaison> comparatif) {
    }
}
