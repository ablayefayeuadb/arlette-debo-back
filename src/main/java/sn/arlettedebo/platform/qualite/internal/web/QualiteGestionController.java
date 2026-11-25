package sn.arlettedebo.platform.qualite.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import sn.arlettedebo.platform.shared.web.PageResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.qualite.internal.QualiteGestionService;
import sn.arlettedebo.platform.qualite.internal.QualiteGestionService.Notes;
import sn.arlettedebo.platform.qualite.internal.domain.ChecklistHygiene;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestation;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformite;
import sn.arlettedebo.platform.qualite.internal.domain.Reclamation;
import sn.arlettedebo.platform.qualite.internal.domain.Signalement;
import sn.arlettedebo.platform.qualite.internal.domain.TicketMaintenance;
import sn.arlettedebo.platform.referentiel.Referentiels;

@Tag(name = "Prestations & qualité — saisie et pilotage")
@RestController
@RequestMapping("/api/v1/qualite")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('qualite:C')")
class QualiteGestionController {

    private final QualiteGestionService service;
    private final UserDirectory annuaire;
    private final Referentiels referentiels;

    // ------------------------------------------------------------ Contrôles

    @Operation(summary = "Contrôler une prestation (notes 1–5) — non conforme ⇒ non-conformité ouverte")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PostMapping("/controles")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> controler(@Valid @RequestBody ControleRequest r) {
        ControlePrestation c = service.controler(r.perimetre(), r.prestation(), r.coiffeuseId(), r.rendezVousId(),
                new Notes(r.accueil(), r.technique(), r.hygiene(), r.delai()), r.commentaire());
        return Map.of("id", c.getId(), "conforme", c.isConforme());
    }

    // ------------------------------------------------------------ Non-conformités et actions

    @Operation(summary = "Déclarer une non-conformité")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PostMapping("/non-conformites")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    QualiteController.NcVue declarer(@Valid @RequestBody NcRequest r) {
        return nc(service.declarerNc(r.perimetre(), r.origine(), r.gravite(), r.description()));
    }

    @Operation(summary = "Planifier une action corrective (responsable, échéance)")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PostMapping("/non-conformites/{id}/actions")
    @Transactional
    QualiteController.NcVue planifier(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        return nc(service.planifierAction(id, r.libelle(), r.responsableId(), r.echeance()));
    }

    @Operation(summary = "Action réalisée (responsable ou gestion qualité)")
    @PostMapping("/actions/{id}/realisation")
    @Transactional
    QualiteController.NcVue realiser(@PathVariable UUID id) {
        return nc(service.realiserAction(id));
    }

    @Operation(summary = "Vérifier une action réalisée (jamais par son responsable)")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PostMapping("/actions/{id}/verification")
    @Transactional
    QualiteController.NcVue verifier(@PathVariable UUID id) {
        return nc(service.verifierAction(id));
    }

    @Operation(summary = "Clôturer une non-conformité (toutes les actions vérifiées)")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PostMapping("/non-conformites/{id}/cloture")
    @Transactional
    QualiteController.NcVue cloturer(@PathVariable UUID id) {
        return nc(service.cloturerNc(id));
    }

    // ------------------------------------------------------------ Réclamations et avis

    @Operation(summary = "Enregistrer une réclamation (majeure ou critique ⇒ non-conformité)")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PostMapping("/reclamations")
    @ResponseStatus(HttpStatus.CREATED)
    QualiteController.ReclamationVue reclamer(@Valid @RequestBody ReclamationRequest r) {
        return reclamation(service.enregistrerReclamation(new Reclamation.Donnees(r.perimetre(), null, r.clientId(),
                r.cliente().trim(), r.rendezVousId(), r.canal(), r.motif().trim(), r.gravite(), r.actionImmediate())));
    }

    @Operation(summary = "Prendre en charge une réclamation")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PostMapping("/reclamations/{id}/prise-en-charge")
    QualiteController.ReclamationVue prendre(@PathVariable UUID id) {
        return reclamation(service.prendreEnCharge(id));
    }

    @Operation(summary = "Résoudre une réclamation (réponse apportée à la cliente)")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PostMapping("/reclamations/{id}/resolution")
    QualiteController.ReclamationVue resoudre(@PathVariable UUID id, @Valid @RequestBody ReponseRequest r) {
        return reclamation(service.resoudre(id, r.reponse()));
    }

    @Operation(summary = "Enregistrer un avis client (note 1–5)")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PostMapping("/avis")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> avis(@Valid @RequestBody AvisRequest r) {
        return Map.of("id", service.enregistrerAvis(r.perimetre(), r.clientId(), r.cliente(), r.note(), r.commentaire()).getId());
    }

    // ------------------------------------------------------------ Signalements

    @Operation(summary = "Signaler (incident, insatisfaction, besoin matériel) — coiffeuses comprises")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PostMapping("/signalements")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> signaler(@Valid @RequestBody SignalementRequest r) {
        Signalement s = service.signaler(r.perimetre(), r.type(), r.description());
        return Map.of("id", s.getId(), "statut", s.getStatut().name());
    }

    @Operation(summary = "Faire avancer un signalement (pris en compte, traité) — trace d'audit enregistrée")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PatchMapping("/signalements/{id}/statut")
    Map<String, Object> avancer(@PathVariable UUID id, @Valid @RequestBody StatutSignalementRequest r) {
        Signalement s = service.avancerSignalement(id, r.statut(), r.commentaire());
        return Map.of("id", s.getId(), "statut", s.getStatut().name());
    }

    // ------------------------------------------------------------ Hygiène

    @Operation(summary = "Check-lists d'hygiène d'un jour, avec les points attendus par moment")
    @GetMapping("/hygiene")
    @Transactional(readOnly = true)
    List<ChecklistVue> hygiene(@RequestParam String perimetre, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        return service.checklists(perimetre, jour).stream().map(this::checklist).toList();
    }

    @Operation(summary = "Points de contrôle d'hygiène par moment (ouverture, après prestation, fermeture)")
    @GetMapping("/hygiene/points")
    Map<String, List<Referentiels.ItemChecklist>> points() {
        return Map.of("OUVERTURE", referentiels.checklist("OUVERTURE"), "APRES_PRESTATION", referentiels.checklist("APRES_PRESTATION"),
                "FERMETURE", referentiels.checklist("FERMETURE"));
    }

    @Operation(summary = "Ouvrir (ou reprendre) la check-list du jour pour un moment et une zone")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PostMapping("/hygiene")
    @Transactional
    ChecklistVue ouvrir(@Valid @RequestBody ChecklistRequest r) {
        return checklist(service.ouvrirChecklist(r.perimetre(), r.moment(), r.zone()));
    }

    @Operation(summary = "Renseigner un point (non conforme ⇒ commentaire obligatoire)")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PutMapping("/hygiene/{id}/points/{item}")
    @Transactional
    ChecklistVue cocher(@PathVariable UUID id, @PathVariable String item, @Valid @RequestBody PointRequest r) {
        return checklist(service.cocher(id, item, r.conforme(), r.commentaire()));
    }

    @Operation(summary = "Valider la check-list (non conforme ⇒ non-conformité d'hygiène)")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PostMapping("/hygiene/{id}/validation")
    @Transactional
    ChecklistVue valider(@PathVariable UUID id) {
        return checklist(service.validerChecklist(id));
    }

    // ------------------------------------------------------------ Incidents de maintenance

    @Operation(summary = "Incidents de maintenance de mes périmètres, paginés (plus récents d'abord)")
    @GetMapping("/incidents")
    PageResponse<IncidentVue> incidents(@RequestParam(defaultValue = "true") boolean ouverts,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.incidents(ouverts, PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "creeLe").and(Sort.by("id"))));
        List<TicketMaintenance> liste = p.getContent();
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(t -> Stream.of(t.getDeclarePar(), t.getAssigneA(), t.getValidePar())).toList());
        return PageResponse.of(p, liste.stream().map(t -> incident(t, noms)).toList());
    }

    @Operation(summary = "Déclarer un incident de maintenance")
    @PreAuthorize("hasAuthority('qualite:S')")
    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    IncidentVue declarerIncident(@Valid @RequestBody IncidentRequest r) {
        TicketMaintenance t = service.declarerIncident(r.perimetre(), r.categorie(), r.local(), r.probleme(), r.urgence());
        return incident(t, annuaire.noms(List.of(t.getDeclarePar())));
    }

    @Operation(summary = "Suivi d'un incident : ASSIGNER, DEMARRER, RESOUDRE, VALIDER")
    @PreAuthorize("hasAuthority('qualite:G')")
    @PostMapping("/incidents/{id}/etapes")
    IncidentVue avancerIncident(@PathVariable UUID id, @Valid @RequestBody EtapeIncidentRequest r) {
        TicketMaintenance t = service.avancerIncident(id, r.etape(), r.assigneA());
        return incident(t, annuaire.noms(Stream.of(t.getDeclarePar(), t.getAssigneA(), t.getValidePar()).toList()));
    }

    // ------------------------------------------------------------ Intervenants

    @Operation(summary = "Personnes des salons de l'utilisateur (responsables d'actions, assignation d'incidents)")
    @PreAuthorize("hasAuthority('qualite:G')")
    @GetMapping("/intervenants")
    List<IntervenantVue> intervenants(@org.springframework.security.core.annotation.AuthenticationPrincipal
            sn.arlettedebo.platform.iam.CurrentUser moi) {
        return annuaire.actifs().stream()
                .filter(u -> u.perimetres().stream().anyMatch(moi.perimetres()::contains))
                .map(u -> new IntervenantVue(u.id(), u.nom(), u.profil()))
                .sorted(java.util.Comparator.comparing(IntervenantVue::nom))
                .toList();
    }

    record IntervenantVue(UUID id, String nom, String profil) {
    }

    // ------------------------------------------------------------ Vues

    private QualiteController.NcVue nc(NonConformite n) {
        Map<UUID, String> noms = annuaire.noms(Stream.concat(Stream.of(n.getCreeParId()), n.getActions().stream().map(a -> a.getResponsableId())).toList());
        return new QualiteController.NcVue(n.getId(), n.getPerimetreCode(), n.getDateConstat(), n.getOrigine().name(), n.getDescription(),
                n.getGravite().name(), n.getStatut().name(), noms.get(n.getCreeParId()),
                n.getActions().stream().map(a -> new QualiteController.ActionVue(a.getId(), a.getLibelle(), noms.get(a.getResponsableId()),
                        a.getEcheance(), a.getStatut().name())).toList());
    }

    private static QualiteController.ReclamationVue reclamation(Reclamation r) {
        return new QualiteController.ReclamationVue(r.getId(), r.getPerimetreCode(), r.getDateReclamation(), r.getClienteNom(),
                r.getCanal().name(), r.getMotif(), r.getGravite().name(), r.getStatut().name(), r.getReponse());
    }

    private ChecklistVue checklist(ChecklistHygiene c) {
        return new ChecklistVue(c.getId(), c.getPerimetreCode(), c.getJour(), c.getMoment().name(), c.getZone(), c.getValideeLe(),
                c.getValideeLe() != null && c.conforme(),
                c.getLignes().stream().map(l -> new PointVue(l.getItemCode(), l.isConforme(), l.getCommentaire())).toList());
    }

    private static IncidentVue incident(TicketMaintenance t, Map<UUID, String> noms) {
        return new IncidentVue(t.getId(), t.getPerimetreCode(), t.getCategorie(), t.getLocal(), t.getProbleme(), t.getUrgence().name(),
                t.getStatut().name(), noms.get(t.getDeclarePar()), t.getCreeLe(), noms.get(t.getAssigneA()), t.getResoluLe(),
                noms.get(t.getValidePar()));
    }

    record ControleRequest(@NotBlank String perimetre, @NotBlank String prestation, UUID coiffeuseId, UUID rendezVousId,
            @Min(1) @Max(5) int accueil, @Min(1) @Max(5) int technique, @Min(1) @Max(5) int hygiene, @Min(1) @Max(5) int delai,
            @Size(max = 500) String commentaire) {
    }

    record NcRequest(@NotBlank String perimetre, @NotNull NonConformite.Origine origine, @NotNull NonConformite.Gravite gravite,
            @NotBlank @Size(max = 500) String description) {
    }

    record ActionRequest(@NotBlank @Size(max = 255) String libelle, UUID responsableId, LocalDate echeance) {
    }

    record ReclamationRequest(@NotBlank String perimetre, UUID clientId, @NotBlank @Size(max = 120) String cliente, UUID rendezVousId,
            @NotNull Reclamation.Canal canal, @NotBlank @Size(max = 500) String motif, @NotNull NonConformite.Gravite gravite,
            @Size(max = 255) String actionImmediate) {
    }

    record ReponseRequest(@NotBlank @Size(max = 500) String reponse) {
    }

    record AvisRequest(@NotBlank String perimetre, UUID clientId, @Size(max = 120) String cliente, @Min(1) @Max(5) int note,
            @Size(max = 500) String commentaire) {
    }

    record SignalementRequest(@NotBlank String perimetre, @NotBlank String type, @NotBlank @Size(max = 500) String description) {
    }

    record StatutSignalementRequest(@NotNull Signalement.Statut statut, @Size(max = 500) String commentaire) {
    }

    record ChecklistRequest(@NotBlank String perimetre, @NotNull ChecklistHygiene.Moment moment, @Size(max = 60) String zone) {
    }

    record PointRequest(@NotNull Boolean conforme, @Size(max = 255) String commentaire) {
    }

    record IncidentRequest(@NotBlank String perimetre, @NotBlank @Size(max = 60) String categorie, @Size(max = 60) String local,
            @NotBlank @Size(max = 500) String probleme, TicketMaintenance.Urgence urgence) {
    }

    record EtapeIncidentRequest(@NotBlank String etape, UUID assigneA) {
    }

    record PointVue(String item, boolean conforme, String commentaire) {
    }

    record ChecklistVue(UUID id, String perimetre, LocalDate jour, String moment, String zone, Instant valideeLe, boolean conforme,
            List<PointVue> points) {
    }

    record IncidentVue(UUID id, String perimetre, String categorie, String local, String probleme, String urgence, String statut,
            String declarePar, Instant declareLe, String assigneA, Instant resoluLe, String validePar) {
    }
}
