package sn.arlettedebo.platform.pilotage.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.pilotage.internal.PilotageService.Reporting;
import sn.arlettedebo.platform.pilotage.internal.PilotageService.TableauDeBord;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.Tache;

@Tag(name = "Pilotage — tableau de bord, reporting, à traiter")
@RestController
@RequestMapping("/api/v1/pilotage")
@RequiredArgsConstructor
class PilotageController {

    /** Marque d'ordre d'octet UTF-8 : Excel reconnaît l'encodage et affiche correctement les accents. */
    private static final String BOM = "\uFEFF";

    private final PilotageService service;

    @Operation(summary = "Tableau de bord (par défaut : aujourd'hui, tous mes périmètres) — comparatif des périmètres pour la Direction")
    @GetMapping("/tableau-de-bord")
    TableauDeBord tableauDeBord(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String perimetre) {
        return service.tableauDeBord(periode(du, au, 0), perimetre);
    }

    @Operation(summary = "Reporting d'une période (par défaut : 30 derniers jours) avec évolution vs période précédente")
    @GetMapping("/reporting")
    Reporting reporting(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String perimetre) {
        return service.reporting(periode(du, au, 29), perimetre);
    }

    @Operation(summary = "Export CSV du reporting (séparateur ; — compatible Excel)")
    @GetMapping(value = "/reporting.csv", produces = "text/csv")
    ResponseEntity<byte[]> reportingCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String perimetre) {
        Reporting r = service.reporting(periode(du, au, 29), perimetre);
        StringBuilder csv = new StringBuilder(BOM + "Section;Indicateur;Valeur;Unité;Période précédente;Évolution (%);Tendance;Détail\n");
        r.sections().forEach(s -> s.indicateurs().forEach(e -> csv.append(String.join(";",
                cellule(s.titre()), cellule(e.indicateur().libelle()), nombre(e.indicateur().valeur()), cellule(e.indicateur().unite()),
                nombre(e.precedente()), nombre(e.evolutionPct()), e.tendance(), cellule(e.indicateur().detail()))).append('\n')));
        String nom = "reporting-" + (perimetre == null ? "consolide" : perimetre.toLowerCase()) + "-" + r.periode().du() + "_" + r.periode().au() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom).build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "À traiter : validations, contrôles et arbitrages qui attendent l'utilisateur")
    @GetMapping("/a-traiter")
    List<Tache> aTraiter() {
        return service.aTraiter();
    }

    private Periode periode(LocalDate du, LocalDate au, int joursParDefaut) {
        LocalDate fin = au == null ? service.aujourdhui() : au;
        return new Periode(du == null ? fin.minusDays(joursParDefaut) : du, fin);
    }

    /** Neutralise l'injection de formules (=, +, -, @) et échappe les guillemets. */
    private static String cellule(String v) {
        if (v == null) {
            return "";
        }
        String s = v.matches("^[=+\\-@].*") ? "'" + v : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String nombre(Double v) {
        return v == null ? "" : String.format(Locale.FRANCE, "%.1f", v).replace(",0", "");
    }
}
