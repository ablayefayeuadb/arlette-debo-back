package sn.arlettedebo.platform.referentiel.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.referentiel.Parametres;
import sn.arlettedebo.platform.referentiel.Parametres.ParametreVue;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Référentiels", description = "Catalogues de référence et paramètres")
@Validated
@RestController
@RequestMapping("/api/v1/referentiels")
@RequiredArgsConstructor
class ReferentielController {

    private final Referentiels referentiels;
    private final Parametres parametres;
    private final PerimetreService salons;
    private final PrestationService prestations;

    @Operation(summary = "Tous les catalogues de référence (périmètres, prestations, modes de paiement…)")
    @GetMapping
    Map<String, List<Object>> tous() {
        return referentiels.tous();
    }

    @Operation(summary = "Paramètres applicatifs")
    @GetMapping("/parametres")
    List<ParametreVue> parametres() {
        return parametres.tous();
    }

    @Operation(summary = "Modifier un paramètre (DG uniquement) — ex. seuil de validation DG")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
    @CacheEvict(value = "referentiels", allEntries = true)
    @PutMapping("/parametres/{cle}")
    ParametreVue modifier(@PathVariable String cle, @RequestBody ValeurRequest body, Authentication auth) {
        return parametres.modifier(cle, body.valeur(), UUID.fromString(auth.getName()));
    }

    // ------------------------------------------------------------------ Salons (DG)

    @Operation(summary = "Salons et boutiques, actifs et désactivés")
    @GetMapping("/salons")
    List<SalonVue> salons() {
        return salons.lister().stream().map(SalonVue::of).toList();
    }

    @Operation(summary = "Créer un salon ou une boutique autonome (DG, Manager) — rattaché aux profils transverses")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
    @PostMapping("/salons")
    @ResponseStatus(HttpStatus.CREATED)
    SalonVue creerSalon(@Valid @RequestBody NouveauSalon r) {
        return SalonVue.of(salons.creer(r.libelle(), r.type(), r.responsableId()));
    }

    @Operation(summary = "Renommer, désactiver ou réactiver ; paramètres de créneaux pour un salon (DG, Manager)")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
    @PutMapping("/salons/{code}")
    SalonVue modifierSalon(@PathVariable String code, @Valid @RequestBody ModificationSalon r) {
        return SalonVue.of(salons.modifier(code, r.libelle(), r.actif(), r.capaciteCreneau(), r.heureOuverture(), r.heureFermeture()));
    }

    // ------------------------------------------------------------------ Prestations (Manager)

    @Operation(summary = "Prestations d’un salon, paginées (y compris désactivées) — recherche q et catégorie facultatives")
    @GetMapping("/prestations")
    PageResponse<PrestationVue> prestations(@RequestParam(required = false) String perimetre,
            @RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(required = false) @Size(max = 40) String categorie,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = prestations.rechercher(perimetre, q, categorie,
                PageResponse.requete(page, taille, Sort.by("perimetreCode", "ordre", "code")));
        return PageResponse.of(p, PrestationVue::of);
    }

    @Operation(summary = "Créer une prestation pour un salon (Manager, DG)")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
    @PostMapping("/prestations")
    @ResponseStatus(HttpStatus.CREATED)
    PrestationVue creerPrestation(@Valid @RequestBody NouvellePrestation r) {
        return PrestationVue.of(prestations.creer(r.libelle(), r.perimetreCode(), r.dureeMin(), r.prix(), r.categorieCode()));
    }

    @Operation(summary = "Modifier une prestation : nom, durée, prix, actif, ordre (Manager, DG)")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
    @PutMapping("/prestations/{code}")
    PrestationVue modifierPrestation(@PathVariable String code, @Valid @RequestBody ModificationPrestation r) {
        return PrestationVue.of(prestations.modifier(code, r.libelle(), r.dureeMin(), r.prix(), r.actif(), r.ordre(), r.categorieCode()));
    }

    /** {@code responsableId} : vendeur boutique désigné (obligatoire si type = BOUTIQUE). */
    record NouveauSalon(@NotBlank @Size(max = 60) String libelle, @NotBlank @Pattern(regexp = "SALON|BOUTIQUE") String type,
            java.util.UUID responsableId) {
    }

    record ModificationSalon(@NotBlank @Size(max = 60) String libelle, boolean actif, Integer capaciteCreneau, Integer heureOuverture,
            Integer heureFermeture) {
    }

    record SalonVue(String code, String libelle, String type, int ordre, boolean actif, int capaciteCreneau, int heureOuverture,
            int heureFermeture) {
        static SalonVue of(Perimetre p) {
            return new SalonVue(p.getCode(), p.getLibelle(), p.getType(), p.getOrdre(), p.isActif(), p.getCapaciteCreneau(),
                    p.getHeureOuverture(), p.getHeureFermeture());
        }
    }

    record NouvellePrestation(@NotBlank @Size(max = 120) String libelle, @NotBlank @Size(max = 30) String perimetreCode,
            @jakarta.validation.constraints.Min(5) @jakarta.validation.constraints.Max(480) int dureeMin,
            @jakarta.validation.constraints.Min(0) long prix, @Size(max = 40) String categorieCode) {
    }

    record ModificationPrestation(@NotBlank @Size(max = 120) String libelle,
            @jakarta.validation.constraints.Min(5) @jakarta.validation.constraints.Max(480) int dureeMin,
            @jakarta.validation.constraints.Min(0) long prix, boolean actif, Integer ordre,
            @Size(max = 40) String categorieCode) {
    }

    record PrestationVue(String code, String libelle, String perimetreCode, int dureeMin, long prix, int ordre, boolean actif,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String categorieCode) {
        static PrestationVue of(Prestation p) {
            return new PrestationVue(p.getCode(), p.getLibelle(), p.getPerimetreCode(), p.getDureeMin(), p.getPrix(), p.getOrdre(),
                    p.isActif(), p.getCategorieCode());
        }
    }

    record ValeurRequest(@NotBlank String valeur) {
    }
}
