package sn.arlettedebo.platform.finance.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.finance.internal.DepenseService;
import sn.arlettedebo.platform.finance.internal.DepenseService.ActionPossible;
import sn.arlettedebo.platform.finance.internal.DepenseService.Fichier;
import sn.arlettedebo.platform.finance.internal.DepenseService.Synthese;
import sn.arlettedebo.platform.finance.internal.domain.Depense;
import sn.arlettedebo.platform.finance.internal.domain.Justificatif;
import sn.arlettedebo.platform.finance.internal.domain.JustificatifRepository;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.referentiel.Parametres;
import sn.arlettedebo.platform.referentiel.Parametres.ParametreVue;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Administration — achats & dépenses")
@Validated
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('administration:C')")
class FinanceController {

    private final DepenseService service;
    private final JustificatifRepository justificatifs;
    private final UserDirectory annuaire;
    private final Parametres parametres;

    @Operation(summary = "Dépenses (filtre par statut, pagination)")
    @GetMapping("/depenses")
    PageResponse<DepenseVue> lister(@RequestParam(required = false) Depense.Statut statut,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int taille) {
        Page<Depense> resultat = service.lister(statut, PageRequest.of(page, taille));
        Vues vues = vues(resultat.getContent());
        return PageResponse.of(resultat, vues::de);
    }

    @Operation(summary = "Détail d'une dépense, avec ses justificatifs et les actions possibles")
    @GetMapping("/depenses/{id}")
    DepenseVue lire(@PathVariable UUID id) {
        return vue(service.lire(id));
    }

    @Operation(summary = "Préparer une demande de dépense (Administration)")
    @PreAuthorize("hasAuthority('administration:S')")
    @PostMapping("/depenses")
    @ResponseStatus(HttpStatus.CREATED)
    DepenseVue demander(@Valid @RequestBody DemandeRequest r) {
        return vue(service.demander(r.objet(), r.categorie(), r.perimetre(), r.montant(), r.fournisseur()));
    }

    @Operation(summary = "Valider (DG ; DO jusqu'au seuil DG)")
    @PostMapping("/depenses/{id}/validation")
    DepenseVue valider(@PathVariable UUID id) {
        return vue(service.valider(id));
    }

    @Operation(summary = "Refuser (DG ; DO jusqu'au seuil DG)")
    @PostMapping("/depenses/{id}/refus")
    DepenseVue refuser(@PathVariable UUID id, @Valid @RequestBody RefusRequest r) {
        return vue(service.refuser(id, r.motif()));
    }

    @Operation(summary = "Exécuter le paiement d'une dépense validée (Administration)")
    @PreAuthorize("hasAuthority('administration:G')")
    @PostMapping("/depenses/{id}/paiement")
    DepenseVue payer(@PathVariable UUID id, @Valid @RequestBody PaiementRequest r) {
        return vue(service.payer(id, r.modePaiement()));
    }

    @Operation(summary = "Contrôler un paiement (DG / DO indépendant, justificatif requis)")
    @PostMapping("/depenses/{id}/controle")
    DepenseVue controler(@PathVariable UUID id, @RequestBody(required = false) ControleRequest r) {
        return vue(service.controler(id, r == null ? null : r.commentaire()));
    }

    @Operation(summary = "Joindre un justificatif (PDF, PNG, JPEG — 10 Mo max)")
    @PreAuthorize("hasAuthority('administration:S')")
    @PostMapping(path = "/depenses/{id}/justificatifs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    JustificatifVue joindre(@PathVariable UUID id, @RequestPart("fichier") MultipartFile fichier) {
        return JustificatifVue.of(service.joindre(id, fichier));
    }

    @Operation(summary = "Télécharger un justificatif")
    @GetMapping("/justificatifs/{id}")
    ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        Fichier f = service.telecharger(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.meta().getTypeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.meta().getNomFichier(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", f.meta().getEmpreinteSha256())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.contenu());
    }

    @Operation(summary = "Synthèse : montants par statut et seuil DG")
    @GetMapping("/synthese")
    Synthese synthese() {
        return service.synthese();
    }

    @Operation(summary = "Modifier le seuil de validation DG (DG uniquement)")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
    @PutMapping("/parametres/seuil-dg")
    ParametreVue seuil(@Valid @RequestBody SeuilRequest r, @AuthenticationPrincipal CurrentUser moi) {
        return parametres.modifier(Parametres.SEUIL_DG, String.valueOf(r.montant()), moi.id());
    }

    // ------------------------------------------------------------ Vues

    private DepenseVue vue(Depense d) {
        return vues(List.of(d)).de(d);
    }

    /** Résolution groupée des noms et justificatifs (2 requêtes pour toute la page, pas de N+1). */
    private Vues vues(List<Depense> liste) {
        Map<UUID, String> noms = annuaire.noms(liste.stream()
                .flatMap(d -> Stream.of(d.getDemandePar(), d.getValidePar(), d.getPayePar(), d.getControlePar())).toList());
        Map<UUID, List<JustificatifVue>> pieces = justificatifs.findByDepenseIdIn(liste.stream().map(Depense::getId).toList())
                .stream().collect(Collectors.groupingBy(Justificatif::getDepenseId, Collectors.mapping(JustificatifVue::of, Collectors.toList())));
        return new Vues(noms, pieces, service.seuilDg());
    }

    private final class Vues {
        private final Map<UUID, String> noms;
        private final Map<UUID, List<JustificatifVue>> pieces;
        private final long seuil;

        Vues(Map<UUID, String> noms, Map<UUID, List<JustificatifVue>> pieces, long seuil) {
            this.noms = noms;
            this.pieces = pieces;
            this.seuil = seuil;
        }

        DepenseVue de(Depense d) {
            return new DepenseVue(d.getId(), d.getReference(), d.getObjet(), d.getCategorieCode(), d.getPerimetreCode(), d.getMontant(),
                    d.getFournisseur(), d.getStatut().name(),
                    new Intervenant(d.getDemandePar(), noms.get(d.getDemandePar()), d.getDemandeLe()),
                    d.getValidePar() == null ? null : new Intervenant(d.getValidePar(), noms.get(d.getValidePar()), d.getValideLe()),
                    d.getPayePar() == null ? null : new Intervenant(d.getPayePar(), noms.get(d.getPayePar()), d.getPayeLe()),
                    d.getControlePar() == null ? null : new Intervenant(d.getControlePar(), noms.get(d.getControlePar()), d.getControleLe()),
                    d.getModePaiementCode(), d.getMotifRefus(), d.getCommentaire(), pieces.getOrDefault(d.getId(), List.of()),
                    service.actionsPossibles(d, seuil, pieces.containsKey(d.getId())), d.getVersion());
        }
    }

    record DemandeRequest(@NotBlank @Size(max = 255) String objet, @NotBlank String categorie, String perimetre,
            @Positive long montant, @NotBlank @Size(max = 160) String fournisseur) {
    }

    record RefusRequest(@NotBlank @Size(max = 500) String motif) {
    }

    record PaiementRequest(@NotBlank String modePaiement) {
    }

    record ControleRequest(@Size(max = 500) String commentaire) {
    }

    record SeuilRequest(@PositiveOrZero long montant) {
    }

    record Intervenant(UUID id, String nom, Instant le) {
    }

    record JustificatifVue(UUID id, String nomFichier, String typeMime, long taille, String sha256, Instant deposeLe) {

        static JustificatifVue of(Justificatif j) {
            return new JustificatifVue(j.getId(), j.getNomFichier(), j.getTypeMime(), j.getTailleOctets(), j.getEmpreinteSha256(), j.getDeposeLe());
        }
    }

    record DepenseVue(UUID id, String reference, String objet, String categorie, String perimetre, long montant, String fournisseur,
            String statut, Intervenant demande, Intervenant validation, Intervenant paiement, Intervenant controle,
            String modePaiement, String motifRefus, String commentaire, List<JustificatifVue> justificatifs,
            List<ActionPossible> actions, long version) {
    }
}
