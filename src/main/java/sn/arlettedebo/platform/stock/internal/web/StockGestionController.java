package sn.arlettedebo.platform.stock.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.data.domain.Sort;
import sn.arlettedebo.platform.shared.web.PageResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.stock.internal.StockGestionService;
import sn.arlettedebo.platform.stock.internal.domain.Article;
import sn.arlettedebo.platform.stock.internal.domain.ArticleRepository;
import sn.arlettedebo.platform.stock.internal.domain.Commande;
import sn.arlettedebo.platform.stock.internal.domain.Comptage;
import sn.arlettedebo.platform.stock.internal.domain.MouvementStock;
import com.fasterxml.jackson.annotation.JsonInclude;

@Tag(name = "Stocks & boutique — catalogue, mouvements, inventaire, commandes, vente")
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('stocks:C')")
class StockGestionController {

    private static final String STATUT = "statut";

    private final StockGestionService service;
    private final ArticleRepository articles;
    private final UserDirectory annuaire;
    private final AccessPolicy access;

    // ------------------------------------------------------------ Catalogue

    @Operation(summary = "Créer un article (référence générée)")
    @PreAuthorize("hasAuthority('stocks:G')")
    @PostMapping("/articles")
    @ResponseStatus(HttpStatus.CREATED)
    StockController.ArticleVue creer(@Valid @RequestBody ArticleRequest r) {
        return StockController.ArticleVue.of(service.creerArticle(new StockGestionService.NouvelArticle(r.nom(), r.categorie(), r.perimetre(),
                r.unite(), r.seuil(), r.prixAchat(), r.prixVente(), r.fournisseur(), r.typeProduit())));
    }

    @Operation(summary = "Paramétrer un article (seuil, prix, fournisseur, actif) — le stock ne change que par mouvement")
    @PreAuthorize("hasAuthority('stocks:G')")
    @PutMapping("/articles/{id}")
    StockController.ArticleVue modifier(@PathVariable UUID id, @Valid @RequestBody ParametrageRequest r) {
        return StockController.ArticleVue.of(service.modifierArticle(id, r.seuil(), r.prixAchat(), r.prixVente(), r.fournisseur(), r.actif()));
    }

    // ------------------------------------------------------------ Mouvements

    @Operation(summary = "Journal des mouvements des articles visibles, paginé (plus récents d'abord) — type facultatif")
    @GetMapping("/mouvements")
    PageResponse<JournalVue> journal(@RequestParam(required = false) MouvementStock.Type type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.journal(type, PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "horodatage").and(Sort.by("id"))));
        List<MouvementStock> liste = p.getContent();
        Map<UUID, Article> parId = new LinkedHashMap<>();
        articles.findAllById(liste.stream().map(MouvementStock::getArticleId).distinct().toList()).forEach(a -> parId.put(a.getId(), a));
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(m -> Stream.of(m.getEffectuePar(), m.getAutorisePar())).toList());
        return PageResponse.of(p, liste.stream().map(m -> {
            Article a = parId.get(m.getArticleId());
            return new JournalVue(m.getId(), m.getHorodatage(), m.getArticleId(), a == null ? "—" : a.getNom(), a == null ? "" : a.getUnite(),
                    m.getType().name(), m.getQuantite(), m.getMotif(), noms.get(m.getEffectuePar()), noms.get(m.getAutorisePar()));
        }).toList());
    }

    @Operation(summary = "Mouvement manuel : ENTREE, SORTIE, PERTE, CASSE, TRANSFERT (motif obligatoire pour les sorties)")
    @PreAuthorize("hasAuthority('stocks:S')")
    @PostMapping("/articles/{id}/mouvements")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> mouvement(@PathVariable UUID id, @Valid @RequestBody MouvementRequest r) {
        MouvementStock m = service.mouvement(id, r.type(), r.quantite(), r.motif());
        return Map.of("id", m.getId(), "quantite", m.getQuantite());
    }

    // ------------------------------------------------------------ Inventaire

    @Operation(summary = "Articles à compter (paginé, sans stock théorique) — saisie inventaire à l'aveugle")
    @PreAuthorize("hasAuthority('stocks:S')")
    @GetMapping("/inventaire/articles")
    PageResponse<ArticleACompterVue> articlesACompter(@RequestParam(required = false) String perimetre,
            @RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        java.util.Set<String> p;
        if (perimetre == null || perimetre.isBlank()) {
            p = access.utilisateur().perimetres();
        } else {
            access.exigerPerimetre(perimetre);
            p = java.util.Set.of(perimetre);
        }
        var resultat = service.articlesACompter(p, q,
                PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(resultat, resultat.getContent().stream()
                .map(a -> new ArticleACompterVue(a.id(), a.nom(), a.unite(), a.perimetre(), a.ecartOuvert())).toList());
    }

    @Operation(summary = "Synthèse des comptages : total, conformes, écarts à traiter")
    @GetMapping("/comptages/synthese")
    SyntheseComptages syntheseComptages() {
        long[] s = service.syntheseComptages();
        return new SyntheseComptages(s[0], s[1], s[2]);
    }

    record SyntheseComptages(long total, long conformes, long ecarts) {
    }

    record ArticleACompterVue(UUID id, String nom, String unite, String perimetre, boolean ecartOuvert) {
    }

    @Operation(summary = "Identifiants d'articles avec un écart encore ouvert (blocage d'un nouveau comptage)")
    @GetMapping("/comptages/ecarts-ouverts")
    java.util.Set<UUID> ecartsOuverts() {
        return service.articleIdsEcartsOuverts();
    }

    @Operation(summary = "Comptages, paginés (plus récents d'abord) — ou écarts à traiter seulement")
    @GetMapping("/comptages")
    PageResponse<ComptageVue> comptages(@RequestParam(defaultValue = "false") boolean ecarts,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.comptages(ecarts, PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "horodatage").and(Sort.by("id"))));
        List<Comptage> liste = p.getContent();
        Map<UUID, String> nomsArticles = new LinkedHashMap<>();
        articles.findAllById(liste.stream().map(Comptage::getArticleId).distinct().toList()).forEach(a -> nomsArticles.put(a.getId(), a.getNom()));
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(c -> Stream.of(c.getSaisiPar(), c.getTraitePar())).toList());
        return PageResponse.of(p, liste.stream().map(c -> comptage(c, nomsArticles, noms)).toList());
    }

    @Operation(summary = "Saisir un comptage (l'Assistant inventaires compte ; l'écart est traité par une autre personne)")
    @PreAuthorize("hasAuthority('stocks:S')")
    @PostMapping("/articles/{id}/comptages")
    @ResponseStatus(HttpStatus.CREATED)
    ComptageVue compter(@PathVariable UUID id, @Valid @RequestBody ComptageRequest r) {
        Comptage c = service.compter(id, r.compte(), r.commentaire());
        return comptage(c, Map.of(), annuaire.noms(List.of(c.getSaisiPar())));
    }

    @Operation(summary = "Traiter un écart : ajuster le stock (mouvement tracé) ou rejeter (motif)")
    @PreAuthorize("hasAuthority('stocks:G')")
    @PostMapping("/comptages/{id}/traitement")
    ComptageVue traiter(@PathVariable UUID id, @Valid @RequestBody TraitementRequest r) {
        Comptage c = service.traiterEcart(id, r.ajuster(), r.commentaire());
        return comptage(c, Map.of(), annuaire.noms(Stream.of(c.getSaisiPar(), c.getTraitePar()).toList()));
    }

    // ------------------------------------------------------------ Commandes

    @Operation(summary = "Exprimer un besoin de réapprovisionnement (validation DO / DG ensuite)")
    @PreAuthorize("hasAuthority('stocks:S')")
    @PostMapping("/commandes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> besoin(@Valid @RequestBody BesoinRequest r) {
        Map<UUID, Integer> lignes = new LinkedHashMap<>();
        r.lignes().forEach(l -> lignes.merge(l.articleId(), l.quantite(), Integer::sum));
        Commande c = service.exprimerBesoin(r.perimetre(), r.fournisseur(), lignes);
        return Map.of("id", c.getId(), "reference", c.getReference(), "montantEstime", c.getMontantEstime(), STATUT, c.getStatut().name());
    }

    @Operation(summary = "Passer une commande validée au fournisseur")
    @PreAuthorize("hasAuthority('stocks:G')")
    @PostMapping("/commandes/{id}/passation")
    @Transactional
    Map<String, Object> passer(@PathVariable UUID id) {
        Commande c = service.passer(id);
        return Map.of("id", c.getId(), STATUT, c.getStatut().name());
    }

    @Operation(summary = "Réceptionner une commande : entrées en stock ligne par ligne")
    @PreAuthorize("hasAuthority('stocks:S')")
    @PostMapping("/commandes/{id}/reception")
    @Transactional
    Map<String, Object> receptionner(@PathVariable UUID id) {
        Commande c = service.receptionner(id);
        return Map.of("id", c.getId(), STATUT, c.getStatut().name());
    }

    // ------------------------------------------------------------ Vente boutique

    @Operation(summary = "Vente boutique : sorties de stock + facture (atomique) ; modePaiement facultatif = encaissement immédiat sur la caisse ouverte du jour")
    @PreAuthorize("hasAuthority('stocks:S')")
    @PostMapping("/ventes")
    @ResponseStatus(HttpStatus.CREATED)
    sn.arlettedebo.platform.caisse.Facturation.FactureEmise vendre(@Valid @RequestBody VenteRequest r) {
        Map<UUID, Integer> lignes = new LinkedHashMap<>();
        r.lignes().forEach(l -> lignes.merge(l.articleId(), l.quantite(), Integer::sum));
        return service.vendre(r.perimetre(), lignes, r.clientId(), r.clienteNom(), r.telephone(), r.modePaiement());
    }

    @Operation(summary = "Factures boutique du périmètre, paginées (transmises aussi à la caisse tant qu'impayées)")
    @PreAuthorize("hasAuthority('stocks:S')")
    @GetMapping("/factures")
    PageResponse<sn.arlettedebo.platform.caisse.Facturation.FactureEmise> factures(
            @RequestParam @NotBlank String perimetre,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.facturesBoutique(perimetre, PageResponse.requete(page, taille,
                Sort.by(Sort.Direction.DESC, "jour").and(Sort.by(Sort.Direction.DESC, "creeLe"))));
        return PageResponse.of(p, p.getContent());
    }

    @Operation(summary = "Annuler une facture boutique impayée ou payée (motif obligatoire, remise en stock, 409 si déjà annulée)")
    @PreAuthorize("hasAnyAuthority('stocks:S', 'stocks:V')")
    @PostMapping("/factures/{id}/annulation")
    sn.arlettedebo.platform.caisse.Facturation.FactureEmise annulerFacture(@PathVariable UUID id,
            @Valid @RequestBody AnnulationFactureRequest r) {
        return service.annulerFacture(id, r.motif());
    }

    @Operation(summary = "Détail d'une facture boutique (réimpression)")
    @PreAuthorize("hasAuthority('stocks:S')")
    @GetMapping("/factures/{id}")
    sn.arlettedebo.platform.caisse.Facturation.FactureEmise facture(@PathVariable UUID id) {
        return service.facture(id);
    }

    @Operation(summary = "PDF facture (template carnet Jasper, généré à la demande)")
    @PreAuthorize("hasAuthority('stocks:S')")
    @GetMapping("/factures/{id}/pdf")
    ResponseEntity<byte[]> facturePdf(@PathVariable UUID id) {
        byte[] pdf = service.facturePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"facture-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private ComptageVue comptage(Comptage c, Map<UUID, String> articles, Map<UUID, String> noms) {
        boolean voitChiffres = access.peut(ModuleCode.STOCKS, ActionCode.G);
        return new ComptageVue(c.getId(), c.getArticleId(), articles.get(c.getArticleId()), c.getHorodatage(),
                voitChiffres ? c.getTheorique() : null, c.getCompte(), voitChiffres ? c.ecart() : null,
                c.getStatut().name(), noms.get(c.getSaisiPar()), noms.get(c.getTraitePar()), c.getCommentaire());
    }

    record ArticleRequest(@NotBlank @Size(max = 160) String nom, @NotBlank String categorie, @NotBlank String perimetre,
            @NotBlank @Size(max = 20) String unite, @PositiveOrZero int seuil,
            @Positive Long prixAchat, @NotNull @Positive Long prixVente,
            @NotBlank @Size(max = 120) String fournisseur, @Size(max = 40) String typeProduit) {
    }

    record ParametrageRequest(@PositiveOrZero int seuil, @PositiveOrZero Long prixAchat, @PositiveOrZero Long prixVente,
            @Size(max = 120) String fournisseur, boolean actif) {
    }

    record MouvementRequest(@NotNull MouvementStock.Type type, @Min(-10_000) @Max(10_000) int quantite, @Size(max = 255) String motif) {
    }

    record ComptageRequest(@Min(0) @Max(100_000) int compte, @Size(max = 255) String commentaire) {
    }

    record TraitementRequest(@NotNull Boolean ajuster, @Size(max = 255) String commentaire) {
    }

    record Ligne(@NotNull UUID articleId, @Min(1) @Max(10_000) int quantite) {
    }

    record BesoinRequest(@NotBlank String perimetre, @NotBlank @Size(max = 120) String fournisseur, @NotEmpty List<@Valid Ligne> lignes) {
    }

    record VenteRequest(@NotBlank String perimetre, @NotEmpty List<@Valid Ligne> lignes, UUID clientId,
            @NotBlank @Size(max = 120) String clienteNom, @NotBlank @Size(max = 25) String telephone,
            @Size(max = 30) String modePaiement) {
    }

    record AnnulationFactureRequest(@NotBlank @Size(max = 120) String motif) {
    }

    record JournalVue(UUID id, Instant horodatage, UUID articleId, String article, String unite, String type, int quantite, String motif,
            String effectuePar, String autorisePar) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ComptageVue(UUID id, UUID articleId, String article, Instant horodatage, Integer theorique, int compte, Integer ecart,
            String statut, String saisiPar, String traitePar, String commentaire) {
    }
}
