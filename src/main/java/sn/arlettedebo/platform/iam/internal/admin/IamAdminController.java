package sn.arlettedebo.platform.iam.internal.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import sn.arlettedebo.platform.shared.web.PageResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.internal.admin.CompteService.CompteCree;
import sn.arlettedebo.platform.iam.internal.domain.Delegation;
import sn.arlettedebo.platform.iam.internal.domain.ModuleRefRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilFonctionnalite;
import sn.arlettedebo.platform.iam.internal.domain.ProfilFonctionnaliteRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilModule;
import sn.arlettedebo.platform.iam.internal.domain.ProfilModuleRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilRepository;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;

/**
 * Administration des accès : matrice, habilitations « * », relais et comptes.
 * Consultation : DG, Manager, DO, Administration. Modifications : DG et Manager (création Coiffeur : RH).
 */
@Tag(name = "Habilitations & comptes")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/iam")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER', 'PROFIL_DO', 'PROFIL_ADMINISTRATION')")
class IamAdminController {

    private final CompteService comptes;
    private final HabilitationService habilitations;
    private final ProfilRepository profils;
    private final ProfilFonctionnaliteRepository fonctionnalites;
    private final ModuleRefRepository modules;
    private final ProfilModuleRepository cellules;

    // ---------------------------------------------------------------- Matrice

    @Operation(summary = "Matrice des profils & fonctionnalités (profils, modules, cellules C/S/G/V)")
    @GetMapping("/matrice")
    MatriceResponse matrice() {
        Map<String, List<String>> fonctions = fonctionnalites.findAllByOrderByProfilCodeAscOrdreAsc().stream()
                .collect(Collectors.groupingBy(ProfilFonctionnalite::getProfilCode,
                        Collectors.mapping(ProfilFonctionnalite::getLibelle, Collectors.toList())));
        List<ProfilVue> p = profils.findAllByOrderByOrdreAsc().stream()
                .filter(x -> !sn.arlettedebo.platform.shared.security.Profils.ABONNEE.equals(x.getCode()))
                .map(x -> new ProfilVue(x.getCode(), x.getLibelle(), x.getLibelleCourt(), x.getFinalite(), x.getIcone(),
                        x.getPerimetres(), fonctions.getOrDefault(x.getCode(), List.of())))
                .toList();
        List<ModuleVue> m = modules.findAllByOrderByOrdreAsc().stream()
                .map(x -> new ModuleVue(x.getCode(), x.getLibelle(), x.getDescription(), x.getIcone())).toList();
        List<CelluleVue> c = cellules.findAll().stream().map(CelluleVue::of).toList();
        return new MatriceResponse(p, m, c);
    }

    @Operation(summary = "Accorder / suspendre un accès « * » (DG, Manager)")
    @PutMapping("/habilitations/{profil}/{module}")
    CelluleVue habilitation(@PathVariable String profil, @PathVariable String module, @Valid @RequestBody HabilitationRequest req) {
        return CelluleVue.of(habilitations.definirHabilitation(profil, module, req.accordee()));
    }

    // ---------------------------------------------------------------- Relais

    @Operation(summary = "Relais & délégations")
    @GetMapping("/delegations")
    List<DelegationVue> delegations() {
        return habilitations.delegations().stream().map(DelegationVue::of).toList();
    }

    @Operation(summary = "Valider / retirer la formation d'un relais (DG, Manager)")
    @PutMapping("/delegations/{id}/formation")
    DelegationVue formation(@PathVariable UUID id, @Valid @RequestBody EtatRequest req) {
        return DelegationVue.of(habilitations.definirFormation(id, req.valeur()));
    }

    @Operation(summary = "Activer / désactiver un relais (DG, Manager, après formation)")
    @PutMapping("/delegations/{id}/activation")
    DelegationVue activation(@PathVariable UUID id, @Valid @RequestBody EtatRequest req) {
        return DelegationVue.of(habilitations.activer(id, req.valeur()));
    }

    // ---------------------------------------------------------------- Comptes

    @Operation(summary = "Comptes utilisateurs, paginés — recherche (nom, e-mail), profil, salon, statut")
    @GetMapping("/utilisateurs")
    PageResponse<CompteVue> utilisateurs(@RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(required = false) String profil, @RequestParam(required = false) String salon,
            @RequestParam(required = false) CompteService.StatutCompte statut,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.decouper(comptes.lister(new CompteService.FiltreComptes(q, profil, salon, statut)), page, taille,
                l -> l.stream().map(CompteVue::of).toList());
    }

    @Operation(summary = "Créer un compte (DG / Manager : tout profil ; RH : Coiffeur) — mot de passe provisoire renvoyé une seule fois")
    @PostMapping("/utilisateurs")
    @ResponseStatus(HttpStatus.CREATED)
    CompteCreeVue creer(@Valid @RequestBody CreationCompteRequest req) {
        return CompteCreeVue.of(comptes.creer(req.nom(), req.email(), req.profil(),
                req.perimetres() == null ? Set.of() : req.perimetres(), req.motDePasse(), req.telephone()));
    }

    @Operation(summary = "Changer le profil d'un compte (DG, Manager)")
    @PatchMapping("/utilisateurs/{id}/profil")
    CompteVue profil(@PathVariable UUID id, @Valid @RequestBody ProfilRequest req) {
        return CompteVue.of(comptes.changerProfil(id, req.profil()));
    }

    @Operation(summary = "Définir les salons d'un compte (DG, Manager)")
    @PutMapping("/utilisateurs/{id}/perimetres")
    CompteVue perimetres(@PathVariable UUID id, @Valid @RequestBody PerimetresRequest req) {
        return CompteVue.of(comptes.definirPerimetres(id, req.perimetres()));
    }

    @Operation(summary = "Désactiver un compte (DG, Manager) — révoque immédiatement ses sessions")
    @PostMapping("/utilisateurs/{id}/desactivation")
    CompteVue desactiver(@PathVariable UUID id) {
        return CompteVue.of(comptes.desactiver(id));
    }

    @Operation(summary = "Réactiver un compte (DG, Manager)")
    @PostMapping("/utilisateurs/{id}/reactivation")
    CompteVue reactiver(@PathVariable UUID id) {
        return CompteVue.of(comptes.reactiver(id));
    }

    @Operation(summary = "Lever un verrouillage après échecs de connexion (DG, Manager, RH)")
    @PostMapping("/utilisateurs/{id}/deverrouillage")
    CompteVue deverrouiller(@PathVariable UUID id) {
        return CompteVue.of(comptes.deverrouiller(id));
    }

    @Operation(summary = "Réinitialiser le mot de passe (DG, Manager) — mot de passe provisoire renvoyé une seule fois")
    @PostMapping("/utilisateurs/{id}/reinitialisation-mot-de-passe")
    CompteCreeVue reinitialiser(@PathVariable UUID id) {
        return CompteCreeVue.of(comptes.reinitialiserMotDePasse(id));
    }

    // ---------------------------------------------------------------- DTO

    record HabilitationRequest(@NotNull Boolean accordee) {
    }

    record EtatRequest(@NotNull Boolean valeur) {
    }

    record ProfilRequest(@NotBlank String profil) {
    }

    record PerimetresRequest(@NotNull Set<String> perimetres) {
    }

    record CreationCompteRequest(@NotBlank @Size(max = 120) String nom, @NotBlank @Email @Size(max = 160) String email,
            @NotBlank String profil, Set<String> perimetres, @Size(max = 72) String motDePasse,
            @NotBlank @Size(max = 13) String telephone) {
    }

    record MatriceResponse(List<ProfilVue> profils, List<ModuleVue> modules, List<CelluleVue> cellules) {
    }

    record ProfilVue(String code, String libelle, String libelleCourt, String finalite, String icone, Set<String> perimetres,
            List<String> fonctionnalites) {
    }

    record ModuleVue(String code, String libelle, String description, String icone) {
    }

    record CelluleVue(String profil, String module, String valeurBrute, Set<String> actions, String restriction,
            boolean sousHabilitation, boolean accordee, Instant modifieLe) {

        static CelluleVue of(ProfilModule c) {
            return new CelluleVue(c.getId().getProfilCode(), c.getId().getModuleCode(), c.getValeurBrute(), Set.copyOf(c.getActions()),
                    c.getRestriction(), c.isSousHabilitation(), c.isAccordee(), c.getModifieLe());
        }
    }

    record DelegationVue(UUID id, String code, String beneficiaire, String titre, String description, Set<String> perimetres,
            Map<String, List<String>> droits, boolean formationValidee, boolean active, UUID activeePar, Instant activeeLe) {

        static DelegationVue of(Delegation d) {
            Map<String, List<String>> droits = d.getDroits().stream().collect(Collectors.groupingBy(
                    Delegation.DroitDelegue::getModuleCode, Collectors.mapping(Delegation.DroitDelegue::getActionCode, Collectors.toList())));
            return new DelegationVue(d.getId(), d.getCode(), d.getBeneficiaireProfilCode(), d.getTitre(), d.getDescription(),
                    Set.copyOf(d.getPerimetres()), droits, d.isFormationValidee(), d.isActive(), d.getActiveePar(), d.getActiveeLe());
        }
    }

    record CompteVue(UUID id, String nom, String email, String telephone, String profil, Set<String> perimetres, boolean actif,
            boolean verrouille, boolean doitChangerMotDePasse, Instant derniereConnexion) {

        static CompteVue of(Utilisateur u) {
            return new CompteVue(u.getId(), u.getNom(), u.getEmail(), u.getTelephone(), u.getProfilCode(), Set.copyOf(u.getPerimetres()),
                    u.isActif(), u.estVerrouille(Instant.now()), u.isDoitChangerMdp(), u.getDerniereConnexion());
        }
    }

    record CompteCreeVue(CompteVue compte, String motDePasseProvisoire) {

        static CompteCreeVue of(CompteCree c) {
            return new CompteCreeVue(CompteVue.of(c.utilisateur()), c.motDePasseProvisoire());
        }
    }
}
