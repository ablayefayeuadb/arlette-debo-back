package sn.arlettedebo.platform.rh.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.rh.internal.PointageService;
import sn.arlettedebo.platform.rh.internal.PointageService.Presence;
import sn.arlettedebo.platform.rh.internal.domain.Pointage;

@Tag(name = "Équipe & RH — pointage par QR (borne site)")
@RestController
@RequestMapping("/api/v1/rh/pointage")
@RequiredArgsConstructor
class PointageController {

    private final PointageService service;

    @Operation(summary = "QR courant de la borne (Manager, accueil, RH) — unique pour tout le site, change à chaque rotation")
    @GetMapping("/borne")
    ResponseEntity<BorneVue> borne(@RequestParam(required = false) String perimetre) {
        // perimetre ignoré (rétrocompatibilité clients) : le QR ne dépend plus du salon
        var e = service.borne();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new BorneVue(e.jeton(), e.expireLe(), e.rotationSecondes()));
    }

    @Operation(summary = "Pointer en scannant le QR de la borne : arrivée, début / fin de pause, départ (sur son propre dossier)")
    @PostMapping("/scan")
    PresenceVue scanner(@Valid @RequestBody ScanRequest r) {
        Pointage p = service.scanner(r.jeton(), r.action());
        Presence moi = service.maPresence();
        return PresenceVue.of(new Presence(moi.employeId(), moi.nom(), moi.perimetre(), moi.heurePrevue(), moi.heureFin(), p));
    }

    @Operation(summary = "Ma présence du jour et les actions possibles")
    @GetMapping("/moi")
    PresenceVue moi() {
        return PresenceVue.of(service.maPresence());
    }

    @Operation(summary = "Tableau des présences d'un jour (tout le personnel visible)")
    @PreAuthorize("hasAuthority('rh:C')")
    @GetMapping("/equipe")
    List<PresenceVue> equipe(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) String perimetre) {
        // perimetre ignoré : le pointage n'est plus filtré par salon
        return service.equipe(jour).stream().map(PresenceVue::of).toList();
    }

    @Operation(summary = "Départs non pointés à régulariser (Manager : ses salons ; Administration)")
    @GetMapping("/a-regulariser")
    List<PresenceVue> aRegulariser() {
        return service.aRegulariser().stream().map(PresenceVue::of).toList();
    }

    @Operation(summary = "Régulariser un départ oublié : heure réelle et motif obligatoire (Manager, Administration)")
    @PostMapping("/{id}/regularisation")
    PresenceVue regulariser(@PathVariable UUID id, @Valid @RequestBody RegularisationRequest r) {
        return vue(service.regulariserDepart(id, r.heureDepart(), r.motif()));
    }

    @Operation(summary = "Justifier un retard ou un départ anticipé (Manager, Administration)")
    @PostMapping("/{id}/justification")
    PresenceVue justifier(@PathVariable UUID id, @Valid @RequestBody JustificationRequest r) {
        return vue(service.justifier(id, r.motif()));
    }

    private PresenceVue vue(Pointage p) {
        return PresenceVue.of(service.presence(p));
    }

    record RegularisationRequest(@NotNull java.time.LocalTime heureDepart,
            @NotBlank @Size(max = 255) String motif) {
    }

    record JustificationRequest(@NotBlank @Size(max = 255) String motif) {
    }

    record ScanRequest(@NotBlank @Size(max = 200) String jeton, @NotNull Pointage.Action action) {
    }

    record BorneVue(String jeton, Instant expireLe, int rotationSecondes) {
    }

    record PresenceVue(UUID employeId, String nom, String perimetre, String heurePrevue, String statut, Instant arrivee,
            Instant pauseDebut, Instant pauseFin, Instant depart, int retardMin, List<Pointage.Action> actionsPossibles,
            UUID pointageId, LocalDate jour, String heureFinPrevue, int departAnticipeMin, String justification, boolean regularise,
            String motifRegularisation) {

        static PresenceVue of(Presence p) {
            Pointage x = p.pointage();
            if (x == null) {
                return new PresenceVue(p.employeId(), p.nom(), p.perimetre(), p.heurePrevue(), Pointage.Statut.ABSENTE.name(), null, null,
                        null, null, 0, List.of(Pointage.Action.ARRIVEE), null, null, p.heureFin(), 0, null, false, null);
            }
            return new PresenceVue(p.employeId(), p.nom(), p.perimetre(), p.heurePrevue(), x.statut().name(), x.getArriveeLe(),
                    x.getPauseDebut(), x.getPauseFin(), x.getDepartLe(), x.getRetardMin(), x.actionsPossibles(), x.getId(), x.getJour(),
                    x.getHeureFinPrevue() == null ? p.heureFin() : x.getHeureFinPrevue().toString(), x.getDepartAnticipeMin(),
                    x.getJustification(), x.getRegularisePar() != null, x.getMotifRegularisation());
        }
    }
}
