package sn.arlettedebo.platform.stock.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import sn.arlettedebo.platform.shared.web.PageResponse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.stock.internal.StockService;
import sn.arlettedebo.platform.stock.internal.domain.Article;
import sn.arlettedebo.platform.stock.internal.domain.Commande;

@Tag(name = "Stocks & boutique")
@Validated
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('stocks:C')")
class StockController {

    private final StockService service;
    private final AccessPolicy access;
    private final UserDirectory annuaire;

    @Operation(summary = "Catalogue paginé en base — recherche, catégorie, type de produit, état ALERTES/INACTIFS, vendables (prix vente > 0)")
    @GetMapping("/articles")
    PageResponse<ArticleVue> articles(@RequestParam(required = false) String perimetre,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 80) String q,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 40) String categorie,
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 40) String typeProduit,
            @RequestParam(required = false) EtatArticle etat,
            @RequestParam(defaultValue = "false") boolean vendables,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        Set<String> p = perimetres(perimetre);
        boolean inactifs = etat == EtatArticle.INACTIFS;
        boolean alertes = etat == EtatArticle.ALERTES;
        var resultat = service.catalogue(p, q, categorie, alertes, inactifs, vendables, typeProduit,
                PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(resultat, resultat.getContent().stream().map(ArticleVue::of).toList());
    }

    @Operation(summary = "Articles actifs d'un périmètre (inventaire / besoin commande) — périmètre recommandé")
    @GetMapping("/articles/tous")
    List<ArticleVue> tousLesArticles(@RequestParam(required = false) String perimetre) {
        return service.articlesVisibles(perimetres(perimetre)).stream().map(ArticleVue::of).toList();
    }

    @Operation(summary = "Synthèse du catalogue (agrégats SQL) : articles, alertes, désactivés, valeur au prix d'achat")
    @GetMapping("/articles/synthese")
    SyntheseArticles synthese(@RequestParam(required = false) String perimetre) {
        var s = service.synthese(perimetres(perimetre));
        return new SyntheseArticles(s.articles(), s.alertes(), s.inactifs(), s.valeur());
    }

    enum EtatArticle { ALERTES, INACTIFS }

    record SyntheseArticles(long articles, long alertes, long inactifs, long valeur) {
    }

    @Operation(summary = "Articles en rupture ou sous le seuil")
    @GetMapping("/alertes")
    List<ArticleVue> alertes(@RequestParam(required = false) String perimetre) {
        return service.alertes(perimetres(perimetre)).stream().map(ArticleVue::of).toList();
    }

    @Operation(summary = "Historique des mouvements d'un article")
    @GetMapping("/articles/{id}/mouvements")
    List<MouvementVue> mouvements(@PathVariable UUID id, @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limite) {
        var liste = service.mouvements(id, limite);
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(m -> Stream.of(m.getEffectuePar(), m.getAutorisePar())).toList());
        return liste.stream().map(m -> new MouvementVue(m.getId(), m.getHorodatage(), m.getType().name(), m.getQuantite(), m.getMotif(),
                noms.get(m.getEffectuePar()), noms.get(m.getAutorisePar()))).toList();
    }

    @Operation(summary = "Commandes fournisseurs de mes salons, paginées (plus récentes d'abord) — statut facultatif")
    @GetMapping("/commandes")
    @Transactional(readOnly = true)
    PageResponse<CommandeVue> commandes(@RequestParam(required = false) Commande.Statut statut,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.commandes(statut, PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "creeLe").and(Sort.by("id"))));
        return PageResponse.of(p, vues(p.getContent()));
    }

    @Operation(summary = "Valider ou refuser une commande (DG ; DO) — jamais le demandeur")
    @PostMapping("/commandes/{id}/decision")
    @Transactional
    CommandeVue decider(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        return vues(List.of(service.decider(id, r.valider()))).getFirst();
    }

    private Set<String> perimetres(String perimetre) {
        if (perimetre == null) {
            return access.utilisateur().perimetres();
        }
        access.exigerPerimetre(perimetre);
        return Set.of(perimetre);
    }

    private List<CommandeVue> vues(List<Commande> liste) {
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(c -> Stream.of(c.getCreeParId(), c.getValidePar())).toList());
        return liste.stream().map(c -> new CommandeVue(c.getId(), c.getReference(), c.getPerimetreCode(), c.getFournisseur(),
                c.getMontantEstime(), c.getStatut().name(), c.getLignes().size(), noms.get(c.getCreeParId()), c.getCreeLe(),
                noms.get(c.getValidePar()), c.getValideLe(), c.getStatut() == Commande.Statut.BESOIN && service.peutValider(c))).toList();
    }

    record DecisionRequest(@NotNull Boolean valider) {
    }

    record ArticleVue(UUID id, String reference, String nom, String categorie, String perimetre, String unite, int stock, int seuil,
            Long prixAchat, Long prixVente, String fournisseur, LocalDate dernierAppro, String niveau, boolean actif,
            @JsonInclude(JsonInclude.Include.NON_NULL) String typeProduit) {

        static ArticleVue of(Article a) {
            String niveau = "OK";
            if (a.getStockActuel() == 0) {
                niveau = "RUPTURE";
            } else if (a.enAlerte()) {
                niveau = "SOUS_SEUIL";
            }
            return new ArticleVue(a.getId(), a.getReference(), a.getNom(), a.getCategorieCode(), a.getPerimetreCode(), a.getUnite(),
                    a.getStockActuel(), a.getSeuil(), a.getPrixAchat(), a.getPrixVente(), a.getFournisseur(), a.getDernierApproLe(), niveau, a.isActif(),
                    a.getTypeProduit());
        }
    }

    record MouvementVue(UUID id, Instant horodatage, String type, int quantite, String motif, String effectuePar, String autorisePar) {
    }

    record CommandeVue(UUID id, String reference, String perimetre, String fournisseur, long montantEstime, String statut, int lignes,
            String creePar, Instant creeLe, String validePar, Instant valideLe, boolean peutValider) {
    }
}
