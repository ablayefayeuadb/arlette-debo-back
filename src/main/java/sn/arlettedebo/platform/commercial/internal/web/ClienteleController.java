package sn.arlettedebo.platform.commercial.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.commercial.internal.ClienteleService;
import sn.arlettedebo.platform.commercial.internal.domain.Abonnement;
import sn.arlettedebo.platform.commercial.internal.domain.Client;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Caisse & ventes — prospects, clientes et abonnements")
@RestController
@Validated
@RequestMapping("/api/v1/commercial")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('caisse:C')")
class ClienteleController {

    private final ClienteleService service;

    @Operation(summary = "Rechercher dans mes périmètres (nom approché ou téléphone exact), filtre par statut")
    @GetMapping("/clients")
    PageResponse<ClientVue> rechercher(@RequestParam(required = false) @Size(max = 80) String recherche,
            @RequestParam(required = false) Client.Statut statut,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int taille) {
        Page<Client> resultat = service.rechercher(recherche, statut, PageRequest.of(page, taille));
        Map<UUID, List<Abonnement>> abos = service.abonnements(resultat.getContent().stream().map(Client::getId).toList()).stream()
                .collect(Collectors.groupingBy(Abonnement::getClientId));
        return PageResponse.of(resultat, c -> ClientVue.of(c, abos.getOrDefault(c.getId(), List.of())));
    }

    @Operation(summary = "Fiche cliente et abonnements")
    @GetMapping("/clients/{id}")
    ClientVue lire(@PathVariable UUID id) {
        return ClientVue.of(service.lire(id), service.abonnements(id));
    }

    @Operation(summary = "Créer une fiche prospect ou cliente (accueil / caisse)")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    ClientVue creer(@Valid @RequestBody ClientRequest r) {
        return ClientVue.of(service.creer(new Client.Donnees(r.perimetre(), r.nom(), r.telephone(), r.email(), r.dateNaissance(),
                r.statut(), r.source(), r.consentementMarketing())), List.of());
    }

    @Operation(summary = "Préférences et notes de la fiche")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PatchMapping("/clients/{id}/notes")
    ClientVue annoter(@PathVariable UUID id, @Valid @RequestBody NotesRequest r) {
        return ClientVue.of(service.annoter(id, r.preferences(), r.notes()), service.abonnements(id));
    }

    @Operation(summary = "Vendre un abonnement (formule du périmètre) — l'encaissement est saisi ensuite en caisse")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/clients/{id}/abonnements")
    @ResponseStatus(HttpStatus.CREATED)
    AbonnementVue vendre(@PathVariable UUID id, @Valid @RequestBody VenteRequest r) {
        return AbonnementVue.of(service.vendre(id, r.formule()));
    }

    @Operation(summary = "Consommer une séance d'abonnement")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/abonnements/{id}/seance")
    AbonnementVue consommer(@PathVariable UUID id) {
        return AbonnementVue.of(service.consommerSeance(id));
    }

    // ------------------------------------------------------------ DTO

    record ClientRequest(@NotBlank String perimetre, @NotBlank @Size(max = 120) String nom, @Size(max = 25) String telephone,
            @Email @Size(max = 160) String email, @Past LocalDate dateNaissance, Client.Statut statut, @Size(max = 60) String source,
            boolean consentementMarketing) {
    }

    record NotesRequest(@Size(max = 500) String preferences, @Size(max = 1000) String notes) {
    }

    record VenteRequest(@NotNull @NotBlank String formule) {
    }

    record AbonnementVue(UUID id, String formule, LocalDate debut, LocalDate fin, Short seancesRestantes, String statut) {

        static AbonnementVue of(Abonnement a) {
            return new AbonnementVue(a.getId(), a.getFormuleCode(), a.getDebut(), a.getFin(), a.getSeancesRestantes(), a.getStatut().name());
        }
    }

    record ClientVue(UUID id, String perimetre, String nom, String telephone, String email, LocalDate dateNaissance, String statut,
            String source, boolean consentementMarketing, LocalDate derniereVisite, String preferences, String notes,
            List<AbonnementVue> abonnements) {

        static ClientVue of(Client c, List<Abonnement> abos) {
            return new ClientVue(c.getId(), c.getPerimetreCode(), c.getNom(), c.getTelephone(), c.getEmail(), c.getDateNaissance(),
                    c.getStatut().name(), c.getSource(), c.isConsentementMarketing(), c.getDerniereVisite(), c.getPreferences(),
                    c.getNotes(), abos.stream().map(AbonnementVue::of).toList());
        }
    }
}
