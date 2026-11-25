package sn.arlettedebo.platform.rh.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import sn.arlettedebo.platform.shared.web.PageResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.rh.internal.RhService;
import sn.arlettedebo.platform.rh.internal.domain.Absence;
import sn.arlettedebo.platform.rh.internal.domain.Employe;
import sn.arlettedebo.platform.rh.internal.domain.Evaluation;
import sn.arlettedebo.platform.rh.internal.domain.Pointage;
import sn.arlettedebo.platform.rh.internal.domain.VariablePaie;

@Tag(name = "Équipe & RH")
@Validated
@RestController
@RequestMapping("/api/v1/rh")
@RequiredArgsConstructor
class RhController {

    private final RhService rh;
    private final UserDirectory annuaire;

    // ------------------------------------------------------------ Dossiers

    @Operation(summary = "Dossiers du personnel, paginés (Manager : ses salons, sans salaire) — recherche, salon, effectifs")
    @PreAuthorize("hasAuthority('rh:C')")
    @GetMapping("/employes")
    PageResponse<EmployeVue> employes(@RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(required = false) String perimetre, @RequestParam(required = false) Boolean actif,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        boolean paie = rh.peutVoirPaie();
        return PageResponse.decouper(rh.employesVisibles(q, perimetre, actif), page, taille,
                l -> l.stream().map(e -> EmployeVue.of(e, paie)).toList());
    }

    @Operation(summary = "Employés visibles pour les listes de choix (identifiant, nom, salon, présence) — non paginé")
    @PreAuthorize("hasAuthority('rh:C')")
    @GetMapping("/employes/options")
    List<EmployeOption> optionsEmployes(@RequestParam(defaultValue = "false") boolean evaluables) {
        var liste = evaluables ? rh.employesEvaluables() : rh.employesVisibles();
        return liste.stream()
                .map(e -> new EmployeOption(e.getId(), e.getNom(), e.getPerimetreCode(), e.isActif())).toList();
    }

    @Operation(summary = "Créer un dossier (RH)")
    @PreAuthorize("hasAuthority('rh:G')")
    @PostMapping("/employes")
    @ResponseStatus(HttpStatus.CREATED)
    EmployeVue creer(@Valid @RequestBody EmployeRequest r) {
        return EmployeVue.of(rh.creer(r.dossier()), true);
    }

    @Operation(summary = "Modifier un dossier (RH)")
    @PreAuthorize("hasAuthority('rh:G')")
    @PutMapping("/employes/{id}")
    EmployeVue modifier(@PathVariable UUID id, @Valid @RequestBody EmployeRequest r) {
        return EmployeVue.of(rh.modifier(id, r.dossier()), true);
    }

    @Operation(summary = "Sortie des effectifs (RH)")
    @PreAuthorize("hasAuthority('rh:G')")
    @PostMapping("/employes/{id}/sortie")
    EmployeVue sortie(@PathVariable UUID id, @RequestBody(required = false) SortieRequest r) {
        return EmployeVue.of(rh.sortir(id, r == null ? null : r.date()), true);
    }

    @Operation(summary = "Réintégration (RH)")
    @PreAuthorize("hasAuthority('rh:G')")
    @PostMapping("/employes/{id}/reintegration")
    EmployeVue reintegration(@PathVariable UUID id) {
        return EmployeVue.of(rh.reintegrer(id), true);
    }

    @Operation(summary = "Valider une compétence (matrice des compétences, RH)")
    @PreAuthorize("hasAuthority('rh:G')")
    @PutMapping("/employes/{id}/competences/{prestation}")
    EmployeVue competence(@PathVariable UUID id, @PathVariable String prestation, @Valid @RequestBody CompetenceRequest r) {
        return EmployeVue.of(rh.definirCompetence(id, prestation, r.niveau()), true);
    }

    // ------------------------------------------------------------ Absences

    @Operation(summary = "Absences & congés, paginés (plus récents d'abord) — filtres statut et employé")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_DO', 'PROFIL_MANAGER')")
    @GetMapping("/absences")
    PageResponse<AbsenceVue> absences(@RequestParam(required = false) Absence.Statut statut,
            @RequestParam(required = false) UUID employe,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        Page<Absence> p = rh.absencesVisibles(statut, employe, PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "du")));
        return PageResponse.of(p, absencesVues(p.getContent()));
    }

    @Operation(summary = "Absents aujourd'hui")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_DO', 'PROFIL_MANAGER')")
    @GetMapping("/absences/aujourdhui")
    List<AbsenceVue> absents() {
        return absencesVues(rh.absentsAujourdhui());
    }

    @Operation(summary = "Saisir une absence ou une demande de congé (DG, DO, Manager)")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_DO', 'PROFIL_MANAGER')")
    @PostMapping("/absences")
    @ResponseStatus(HttpStatus.CREATED)
    AbsenceVue saisir(@Valid @RequestBody AbsenceRequest r) {
        return absencesVues(List.of(rh.saisirAbsence(r.employeId(), r.type(), r.du(), r.au(), r.motif()))).getFirst();
    }

    @Operation(summary = "Approuver / refuser un congé (DG, DO, Manager)")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_DO', 'PROFIL_MANAGER')")
    @PostMapping("/absences/{id}/decision")
    AbsenceVue decider(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        return absencesVues(List.of(rh.deciderAbsence(id, r.approuver()))).getFirst();
    }

    // ------------------------------------------------------------ Paie

    @Operation(summary = "Éléments variables de paie d'un mois (RH, DG)")
    @PreAuthorize("hasAuthority('rh:G') or hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
    @GetMapping("/paie/variables")
    List<VariableVue> variables(@RequestParam @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String mois) {
        List<VariablePaie> lignes = rh.variables(mois);
        Map<UUID, String> noms = nomsEmployes();
        return lignes.stream().map(v -> VariableVue.of(v, noms)).toList();
    }

    @Operation(summary = "Saisir un élément variable (RH)")
    @PreAuthorize("hasAuthority('rh:G')")
    @PostMapping("/paie/variables")
    @ResponseStatus(HttpStatus.CREATED)
    VariableVue saisirVariable(@Valid @RequestBody VariableRequest r) {
        return VariableVue.of(rh.saisirVariable(r.employeId(), r.mois(), r.type(), r.montant(), r.commentaire()), nomsEmployes());
    }

    @Operation(summary = "Transmettre les éléments du mois au traitement de la paie (RH)")
    @PreAuthorize("hasAuthority('rh:G')")
    @PostMapping("/paie/transmission")
    Map<String, Integer> transmettre(@Valid @RequestBody TransmissionRequest r) {
        return Map.of("lignesTransmises", rh.transmettre(r.mois()));
    }

    // ------------------------------------------------------------ Évaluations

    @Operation(summary = "Évaluations, paginées (plus récentes d'abord) — filtre employé")
    @PreAuthorize("hasAuthority('rh:C')")
    @GetMapping("/evaluations")
    PageResponse<EvaluationVue> evaluations(@RequestParam(required = false) UUID employe,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        Page<Evaluation> p = rh.evaluationsVisibles(employe,
                PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "dateEvaluation").and(Sort.by("id"))));
        List<Evaluation> liste = p.getContent();
        Map<UUID, String> noms = nomsEmployes();
        Map<UUID, String> evaluateurs = annuaire.noms(liste.stream().map(Evaluation::getEvaluateurId).toList());
        return PageResponse.of(p, liste.stream().map(e -> new EvaluationVue(e.getId(), e.getEmployeId(), noms.get(e.getEmployeId()),
                e.getDateEvaluation(), e.getNote(), e.getPointsForts(), e.getAxesProgres(), evaluateurs.get(e.getEvaluateurId()))).toList());
    }

    @Operation(summary = "Enregistrer une évaluation (réservé au N+1)")
    @PreAuthorize("hasAuthority('rh:C')")
    @PostMapping("/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> evaluer(@Valid @RequestBody EvaluationRequest r) {
        return Map.of("id", rh.evaluer(r.employeId(), r.note(), r.pointsForts(), r.axesProgres()).getId());
    }

    // ------------------------------------------------------------ Pointage

    @Operation(summary = "Pointages d'une journée (tableau Manager : prévu / réel / écart)")
    @PreAuthorize("hasAuthority('rh:C')")
    @GetMapping("/pointages")
    List<PointageVue> pointages(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        return rh.pointagesDuJour(jour).stream().map(PointageVue::of).toList();
    }

    // ------------------------------------------------------------ Outils & DTO

    private Map<UUID, String> nomsEmployes() {
        return rh.nomsVisibles();
    }

    private List<AbsenceVue> absencesVues(List<Absence> liste) {
        Map<UUID, String> noms = nomsEmployes();
        Map<UUID, String> utilisateurs = annuaire.noms(liste.stream()
                .flatMap(a -> Stream.of(a.getSaisiPar(), a.getDecidePar())).toList());
        return liste.stream().map(a -> new AbsenceVue(a.getId(), a.getEmployeId(), noms.get(a.getEmployeId()), a.getTypeCode(),
                a.getDu(), a.getAu(), a.getMotif(), a.getStatut().name(), utilisateurs.get(a.getSaisiPar()),
                utilisateurs.get(a.getDecidePar()), a.getDecideLe())).toList();
    }

    record EmployeRequest(@NotBlank @Size(max = 120) String nom, @NotBlank @Size(max = 120) String poste, String grade,
            String perimetre, @Pattern(regexp = "^[0-9 +]{7,20}$") String telephone, @NotNull LocalDate dateEntree,
            @NotNull Employe.Contrat contrat, @PositiveOrZero Long salaireBase, LocalTime heurePrisePoste, LocalTime heureFinPoste,
            UUID utilisateurId) {

        Employe.Dossier dossier() {
            return new Employe.Dossier(nom.trim(), poste.trim(), grade, perimetre, telephone, dateEntree, contrat, salaireBase,
                    heurePrisePoste, heureFinPoste, utilisateurId);
        }
    }

    record SortieRequest(LocalDate date) {
    }

    record CompetenceRequest(@Min(1) @Max(3) int niveau) {
    }

    record AbsenceRequest(@NotNull UUID employeId, @NotBlank String type, @NotNull LocalDate du, @NotNull LocalDate au,
            @Size(max = 255) String motif) {
    }

    record DecisionRequest(@NotNull Boolean approuver) {
    }

    record VariableRequest(@NotNull UUID employeId, @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String mois,
            @NotBlank String type, @Positive long montant, @Size(max = 255) String commentaire) {
    }

    record TransmissionRequest(@NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String mois) {
    }

    record EvaluationRequest(@NotNull UUID employeId, @Min(1) @Max(5) int note, @Size(max = 1000) String pointsForts,
            @Size(max = 1000) String axesProgres) {
    }

    /** {@code salaireBase} absent (non sérialisé) quand l'appelant n'a pas accès aux données de paie. */
    record EmployeOption(UUID id, String nom, String perimetre, boolean actif) {
    }

    record EmployeVue(UUID id, String matricule, String nom, String poste, String grade, String perimetre, String telephone,
            LocalDate dateEntree, LocalDate dateSortie, String contrat, Long salaireBase, LocalTime heurePrisePoste,
            LocalTime heureFinPoste, boolean actif, UUID utilisateurId, Map<String, Short> competences) {

        static EmployeVue of(Employe e, boolean paie) {
            return new EmployeVue(e.getId(), e.getMatricule(), e.getNom(), e.getPoste(), e.getGrade(), e.getPerimetreCode(),
                    e.getTelephone(), e.getDateEntree(), e.getDateSortie(), e.getContrat().name(), paie ? e.getSalaireBase() : null,
                    e.getHeurePrisePoste(), e.getHeureFinPoste(), e.isActif(), e.getUtilisateurId(),
                    e.getCompetences().stream().collect(Collectors.toMap(Employe.Competence::getPrestationCode, Employe.Competence::getNiveau)));
        }
    }

    record AbsenceVue(UUID id, UUID employeId, String employe, String type, LocalDate du, LocalDate au, String motif, String statut,
            String saisiPar, String decidePar, Instant decideLe) {
    }

    record VariableVue(UUID id, UUID employeId, String employe, String mois, String type, long montant, String commentaire,
            String statut, Instant transmiseLe) {

        static VariableVue of(VariablePaie v, Map<UUID, String> noms) {
            return new VariableVue(v.getId(), v.getEmployeId(), noms.get(v.getEmployeId()), v.getMois(), v.getTypeCode(), v.getMontant(),
                    v.getCommentaire(), v.getStatut().name(), v.getTransmiseLe());
        }
    }

    record EvaluationVue(UUID id, UUID employeId, String employe, LocalDate date, short note, String pointsForts, String axesProgres,
            String evaluateur) {
    }

    record PointageVue(UUID id, UUID employeId, LocalDate jour, LocalTime heurePrevue, Instant arrivee, Instant depart, int retardMin,
            String justification) {

        static PointageVue of(Pointage p) {
            return new PointageVue(p.getId(), p.getEmployeId(), p.getJour(), p.getHeurePrevue(), p.getArriveeLe(), p.getDepartLe(),
                    p.getRetardMin(), p.getJustification());
        }
    }
}
