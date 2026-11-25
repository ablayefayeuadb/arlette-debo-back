package sn.arlettedebo.platform.communication.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.communication.internal.CommunicationService;
import sn.arlettedebo.platform.communication.internal.CommunicationService.Etape;
import sn.arlettedebo.platform.communication.internal.domain.Publication;
import sn.arlettedebo.platform.iam.UserDirectory;

@Tag(name = "Communication — calendrier éditorial et circuit de validation")
@RestController
@RequestMapping("/api/v1/communication/publications")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('communication:C')")
class CommunicationController {

    private final CommunicationService service;
    private final UserDirectory annuaire;

    @Operation(summary = "Calendrier éditorial d'une période")
    @GetMapping
    List<PublicationVue> calendrier(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return vues(service.calendrier(du, au));
    }

    @Operation(summary = "Créer un contenu (Community)")
    @PreAuthorize("hasAuthority('communication:S')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PublicationVue creer(@Valid @RequestBody CreationRequest r) {
        return vues(List.of(service.creer(r.titre(), r.contenu(), r.format(), r.canal(), r.perimetre(), r.datePrevue()))).getFirst();
    }

    @Operation(summary = "Modifier un brouillon ou un contenu renvoyé (auteur uniquement)")
    @PreAuthorize("hasAuthority('communication:S')")
    @PutMapping("/{id}")
    PublicationVue modifier(@PathVariable UUID id, @Valid @RequestBody CreationRequest r) {
        return vues(List.of(service.modifier(id, r.titre(), r.contenu(), r.format(), r.canal(), r.perimetre(), r.datePrevue()))).getFirst();
    }

    @Operation(summary = "Faire avancer le circuit : SOUMETTRE, VERIFIER (DO), VALIDER (DG), RENVOYER, PUBLIER, ANALYSER")
    @PostMapping("/{id}/etapes")
    PublicationVue avancer(@PathVariable UUID id, @Valid @RequestBody EtapeRequest r) {
        return vues(List.of(service.avancer(id, r.etape(), r.motif(),
                new CommunicationService.Statistiques(r.portee(), r.interactions(), r.nouveauxAbonnes(), r.clics())))).getFirst();
    }

    private List<PublicationVue> vues(List<Publication> liste) {
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(p -> Stream.of(p.getAuteurId(), p.getVerifiePar(), p.getValidePar())).toList());
        return liste.stream().map(p -> new PublicationVue(p.getId(), p.getTitre(), p.getContenu(), p.getFormat().name(), p.getCanalCode(),
                p.getPerimetreCode(), p.getDatePrevue(), p.getStatut().name(), noms.get(p.getAuteurId()), p.getCreeLe(),
                noms.get(p.getVerifiePar()), p.getVerifieLe(), noms.get(p.getValidePar()), p.getValideLe(), p.getPublieeLe(),
                p.getMotifRevoir(), p.getPortee(), p.getInteractions(), p.getNouveauxAbonnes(), p.getClics(), service.etapesPossibles(p),
                service.modifiable(p))).toList();
    }

    record CreationRequest(@NotBlank @Size(max = 160) String titre, @NotBlank @Size(max = 5000) String contenu, Publication.Format format,
            @NotBlank String canal, String perimetre, LocalDate datePrevue) {
    }

    record EtapeRequest(@NotNull Etape etape, @Size(max = 500) String motif, @PositiveOrZero Integer portee,
            @PositiveOrZero Integer interactions, Integer nouveauxAbonnes, @PositiveOrZero Integer clics) {
    }

    record PublicationVue(UUID id, String titre, String contenu, String format, String canal, String perimetre, LocalDate datePrevue,
            String statut, String auteur, Instant creeLe, String verifiePar, Instant verifieLe, String validePar, Instant valideLe,
            Instant publieeLe, String motifRevoir, Integer portee, Integer interactions, Integer nouveauxAbonnes, Integer clics,
            List<Etape> etapesPossibles, boolean modifiable) {
    }
}
