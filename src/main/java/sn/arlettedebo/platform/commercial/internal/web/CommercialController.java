package sn.arlettedebo.platform.commercial.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import sn.arlettedebo.platform.shared.web.PageResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.commercial.internal.AutorisationService;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisation;
import sn.arlettedebo.platform.iam.UserDirectory;

@Tag(name = "Caisse & ventes — décisions commerciales")
@RestController
@RequestMapping("/api/v1/commercial/autorisations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('caisse:C')")
class CommercialController {

    private final AutorisationService service;
    private final UserDirectory annuaire;

    @Operation(summary = "Demandes de remise / gratuité / remboursement / compensation de mes salons, paginées (plus récentes d'abord)")
    @GetMapping
    PageResponse<DemandeVue> lister(@RequestParam(required = false) DemandeAutorisation.Statut statut,
            @RequestParam(defaultValue = "false") boolean enAttente,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.lister(enAttente ? DemandeAutorisation.Statut.EN_ATTENTE : statut,
                PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "demandeLe").and(Sort.by("id"))));
        return PageResponse.of(p, vues(p.getContent()));
    }

    @Operation(summary = "Demander une autorisation (accueil / caisse)")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DemandeVue demander(@Valid @RequestBody DemandeRequest r) {
        return vues(List.of(service.demander(r.perimetre(), r.type(), r.clientId(), r.cliente(), r.montant(), r.motif()))).getFirst();
    }

    @Operation(summary = "Autoriser ou refuser (DG ; DO par délégation) — jamais le demandeur")
    @PostMapping("/{id}/decision")
    DemandeVue decider(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        return vues(List.of(service.decider(id, r.autoriser(), r.commentaire()))).getFirst();
    }

    @Operation(summary = "Appliquer une décision autorisée en caisse")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/{id}/application")
    DemandeVue appliquer(@PathVariable UUID id) {
        return vues(List.of(service.appliquer(id))).getFirst();
    }

    private List<DemandeVue> vues(List<DemandeAutorisation> liste) {
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(d -> Stream.of(d.getDemandePar(), d.getDecidePar())).toList());
        return liste.stream().map(d -> new DemandeVue(d.getId(), d.getPerimetreCode(), d.getTypeCode(), d.getClienteNom(), d.getMontant(),
                d.getMotif(), d.getStatut().name(), noms.get(d.getDemandePar()), d.getDemandeLe(), noms.get(d.getDecidePar()), d.getDecideLe(),
                d.getCommentaire(), d.getStatut() == DemandeAutorisation.Statut.EN_ATTENTE && service.peutDecider(d))).toList();
    }

    record DemandeRequest(@NotBlank String perimetre, @NotBlank String type, UUID clientId, @NotBlank @Size(max = 120) String cliente,
            @PositiveOrZero long montant, @NotBlank @Size(max = 500) String motif) {
    }

    record DecisionRequest(@NotNull Boolean autoriser, @Size(max = 500) String commentaire) {
    }

    record DemandeVue(UUID id, String perimetre, String type, String cliente, long montant, String motif, String statut,
            String demandePar, Instant demandeLe, String decidePar, Instant decideLe, String commentaire, boolean peutDecider) {
    }
}
