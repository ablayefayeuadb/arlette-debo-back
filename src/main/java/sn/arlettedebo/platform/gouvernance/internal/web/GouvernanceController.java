package sn.arlettedebo.platform.gouvernance.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.gouvernance.internal.GouvernanceService;
import sn.arlettedebo.platform.gouvernance.internal.domain.IncidentResponsabilite;
import sn.arlettedebo.platform.gouvernance.internal.domain.ProcedureDoc;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Gouvernance")
@RestController
@RequestMapping("/api/v1/gouvernance")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('administration:C')")
class GouvernanceController {

    private final GouvernanceService service;

    @Operation(summary = "Incidents de responsabilité (ORG-03)")
    @GetMapping("/incidents-responsabilite")
    PageResponse<IncidentVue> incidents(@RequestParam(defaultValue = "true") boolean ouverts,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int taille) {
        return PageResponse.of(service.listerIncidents(ouverts, PageRequest.of(page, taille)), IncidentVue::of);
    }

    @GetMapping("/incidents-responsabilite/compteurs")
    CompteursVue compteurs() {
        return new CompteursVue(service.countOuverts());
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PostMapping("/incidents-responsabilite")
    @ResponseStatus(HttpStatus.CREATED)
    IncidentVue creerIncident(@Valid @RequestBody IncidentCreateRequest r) {
        return IncidentVue.of(service.creerIncident(r.sujet(), r.rolesConcernes(), r.personnes(), r.echeance(),
                r.responsableNom(), r.rendezVousId()));
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PatchMapping("/incidents-responsabilite/{id}")
    IncidentVue majIncident(@PathVariable UUID id, @Valid @RequestBody IncidentUpdateRequest r) {
        return IncidentVue.of(service.majIncident(id, r.arbitrage(), r.actionCorrective(), r.echeance(), r.responsableNom()));
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PostMapping("/incidents-responsabilite/{id}/cloture")
    IncidentVue cloturer(@PathVariable UUID id) {
        return IncidentVue.of(service.cloturerIncident(id));
    }

    @Operation(summary = "Procédures (DOC-01)")
    @GetMapping("/procedures")
    PageResponse<ProcedureVue> procedures(@RequestParam(defaultValue = "false") boolean publiees,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int taille) {
        return PageResponse.of(service.listerProcedures(publiees, PageRequest.of(page, taille)), ProcedureVue::of);
    }

    @GetMapping("/procedures/{id}")
    ProcedureVue detailProcedure(@PathVariable UUID id) {
        return ProcedureVue.of(service.detailProcedure(id));
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PostMapping("/procedures")
    @ResponseStatus(HttpStatus.CREATED)
    ProcedureVue creerProcedure(@Valid @RequestBody ProcedureRequest r) {
        return ProcedureVue.of(service.creerProcedure(r.titre(), r.activite(), r.proprietaire(), r.resume()));
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PatchMapping("/procedures/{id}")
    ProcedureVue majProcedure(@PathVariable UUID id, @Valid @RequestBody ProcedureRequest r) {
        return ProcedureVue.of(service.majProcedure(id, r.titre(), r.activite(), r.proprietaire(), r.resume()));
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PostMapping(value = "/procedures/{id}/fichier", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProcedureVue fichier(@PathVariable UUID id, @RequestPart("fichier") MultipartFile fichier) {
        return ProcedureVue.of(service.attacherFichier(id, fichier));
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PostMapping("/procedures/{id}/publication")
    ProcedureVue publier(@PathVariable UUID id) {
        return ProcedureVue.of(service.publier(id));
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @PostMapping("/procedures/{id}/obsolescence")
    ProcedureVue obsoletiser(@PathVariable UUID id) {
        return ProcedureVue.of(service.obsoletiser(id));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/procedures/{id}/fichier")
    ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        ProcedureDoc p = service.detailPublieeOuAdmin(id);
        Resource res = service.fichierProcedure(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + (p.getFichierNom() == null ? "document" : p.getFichierNom()) + "\"")
                .contentType(MediaType.parseMediaType(p.getFichierType() == null ? "application/octet-stream" : p.getFichierType()))
                .body(res);
    }

    @Operation(summary = "Procédures publiées (consultation collaborateurs)")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/procedures-publiees")
    PageResponse<ProcedureConsultVue> proceduresPubliees(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        UUID moi = service.utilisateurCourantId();
        return PageResponse.of(service.listerPubliees(PageRequest.of(page, taille)),
                p -> ProcedureConsultVue.of(p, service.aLu(p.getId(), moi)));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/procedures-publiees/{id}")
    ProcedureConsultVue detailPubliee(@PathVariable UUID id) {
        ProcedureDoc p = service.detailPubliee(id);
        return ProcedureConsultVue.of(p, service.aLu(p.getId(), service.utilisateurCourantId()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/procedures-publiees/{id}/lecture")
    @ResponseStatus(HttpStatus.CREATED)
    LectureVue accuser(@PathVariable UUID id) {
        var l = service.accuserLecture(id);
        return new LectureVue(l.getUtilisateurId(), null, l.getLuLe());
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @GetMapping("/procedures/{id}/lectures")
    java.util.List<LectureVue> lectures(@PathVariable UUID id) {
        return service.lecturesDe(id).stream().map(x -> new LectureVue(x.utilisateurId(), x.nom(), x.luLe())).toList();
    }

    @PreAuthorize("hasAnyAuthority('administration:V','administration:G')")
    @GetMapping("/procedures/{id}/lectures-manquantes")
    java.util.List<ManquantVue> manquants(@PathVariable UUID id) {
        return service.lecturesManquantes(id).stream().map(x -> new ManquantVue(x.utilisateurId(), x.nom(), x.profil())).toList();
    }

    record CompteursVue(long ouverts) {
    }

    record IncidentVue(UUID id, String sujet, String rolesConcernes, String personnes, String arbitrage,
            String actionCorrective, String statut, LocalDate echeance, String responsableNom, UUID rendezVousId,
            Instant creeLe, Instant clotureLe, boolean enRetard) {
        static IncidentVue of(IncidentResponsabilite i) {
            boolean retard = i.getStatut() != IncidentResponsabilite.Statut.CLOTURE && i.getEcheance() != null
                    && i.getEcheance().isBefore(LocalDate.now());
            return new IncidentVue(i.getId(), i.getSujet(), i.getRolesConcernes(), i.getPersonnes(), i.getArbitrage(),
                    i.getActionCorrective(), i.getStatut().name(), i.getEcheance(), i.getResponsableNom(),
                    i.getRendezVousId(), i.getCreeLe(), i.getClotureLe(), retard);
        }
    }

    record IncidentCreateRequest(@NotBlank @Size(max = 200) String sujet, @NotBlank @Size(max = 500) String rolesConcernes,
            @Size(max = 500) String personnes, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate echeance,
            @Size(max = 120) String responsableNom, UUID rendezVousId) {
    }

    record IncidentUpdateRequest(@Size(max = 1000) String arbitrage, @Size(max = 1000) String actionCorrective,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate echeance, @Size(max = 120) String responsableNom) {
    }

    record ProcedureVue(UUID id, String titre, String activite, String proprietaire, int versionNum, String statut,
            String resume, String fichierNom, Instant publieLe, Instant creeLe) {
        static ProcedureVue of(ProcedureDoc p) {
            return new ProcedureVue(p.getId(), p.getTitre(), p.getActivite(), p.getProprietaire(), p.getVersionNum(),
                    p.getStatut().name(), p.getResume(), p.getFichierNom(), p.getPublieLe(), p.getCreeLe());
        }
    }

    record ProcedureConsultVue(UUID id, String titre, String activite, String proprietaire, int versionNum,
            String resume, String fichierNom, Instant publieLe, boolean lue) {
        static ProcedureConsultVue of(ProcedureDoc p, boolean lue) {
            return new ProcedureConsultVue(p.getId(), p.getTitre(), p.getActivite(), p.getProprietaire(), p.getVersionNum(),
                    p.getResume(), p.getFichierNom(), p.getPublieLe(), lue);
        }
    }

    record LectureVue(UUID utilisateurId, String nom, Instant luLe) {
    }

    record ManquantVue(UUID utilisateurId, String nom, String profil) {
    }

    record ProcedureRequest(@NotBlank @Size(max = 200) String titre, @NotBlank @Size(max = 80) String activite,
            @NotBlank @Size(max = 120) String proprietaire, @Size(max = 2000) String resume) {
    }
}
