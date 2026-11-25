package sn.arlettedebo.platform.stock.internal;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;
import sn.arlettedebo.platform.stock.internal.domain.Article;
import sn.arlettedebo.platform.stock.internal.domain.ArticleRepository;
import sn.arlettedebo.platform.stock.internal.domain.Commande;
import sn.arlettedebo.platform.stock.internal.domain.CommandeRepository;
import sn.arlettedebo.platform.stock.internal.domain.MouvementStock;
import sn.arlettedebo.platform.stock.internal.domain.MouvementStockRepository;

/**
 * Stocks & boutique.
 * <ul>
 *   <li>Visibilité : « produits » (Community) = produits boutique ; « limité » (Manager) = consommables de ses salons ;</li>
 *   <li>validation des commandes : DG (droit V) ou DO (arbitrage opérationnel), jamais le demandeur.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final ArticleRepository articles;
    private final CommandeRepository commandes;
    private final MouvementStockRepository mouvements;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Article> articlesVisibles(Set<String> perimetres) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        return articles.findActifs(perimetres, categorieEffective(null));
    }

    /**
     * Catalogue paginé en SQL (filtres périmètre / catégorie / alertes / recherche / vendables).
     * La restriction matrice (« produits », « limité ») est appliquée via la catégorie effective.
     */
    @Transactional(readOnly = true)
    public Page<Article> catalogue(Set<String> perimetres, String q, String categorie, boolean alertes, boolean inactifs,
            boolean vendables, String typeProduit, Pageable page) {
        if (inactifs) {
            access.exiger(ModuleCode.STOCKS, ActionCode.G);
        } else {
            access.exiger(ModuleCode.STOCKS, ActionCode.C);
        }
        String cat = categorieEffective(categorie);
        String recherche = q == null || q.isBlank() ? "" : q.trim();
        String type = typeProduit == null || typeProduit.isBlank() ? "" : typeProduit.trim().toUpperCase(java.util.Locale.ROOT);
        return articles.chercher(perimetres, !inactifs, cat, alertes, recherche, vendables, type, page);
    }

    /** Synthèse agrégée (pas de chargement du catalogue). */
    @Transactional(readOnly = true)
    public SyntheseCatalogue synthese(Set<String> perimetres) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        String cat = categorieEffective(null);
        long actifs = articles.compterActifs(perimetres, cat);
        long alertes = articles.compterAlertes(perimetres, cat);
        long valeur = articles.valeurStock(perimetres, cat);
        long inactifs = access.peut(ModuleCode.STOCKS, ActionCode.G) ? articles.compterInactifs(perimetres, cat) : 0;
        return new SyntheseCatalogue(actifs, alertes, inactifs, valeur);
    }

    /** Indicateurs dashboard : ruptures / sous-seuil / valeur sans hydrater les fiches. */
    @Transactional(readOnly = true)
    public IndicateursStock indicateurs(Set<String> perimetres) {
        String cat = categorieEffective(null);
        return new IndicateursStock(articles.compterRuptures(perimetres, cat), articles.compterSousSeuil(perimetres, cat),
                articles.valeurStock(perimetres, cat), articles.idsActifs(perimetres, cat));
    }

    /** Identifiants des articles visibles (journal / comptages) — projection id seule. */
    @Transactional(readOnly = true)
    public Set<UUID> idsVisibles(Set<String> perimetres) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        return Set.copyOf(articles.idsActifs(perimetres, categorieEffective(null)));
    }

    /** Articles désactivés (retirés du catalogue) : visibles en gestion seulement, pour pouvoir les réactiver. */
    @Transactional(readOnly = true)
    public List<Article> articlesInactifs(Set<String> perimetres) {
        access.exiger(ModuleCode.STOCKS, ActionCode.G);
        return articles.findByPerimetreCodeInAndActifFalseOrderByIdDesc(perimetres);
    }

    @Transactional(readOnly = true)
    public List<Article> alertes(Set<String> perimetres) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        return articles.enAlerte(perimetres, categorieEffective(null));
    }

    public record SyntheseCatalogue(long articles, long alertes, long inactifs, long valeur) {
    }

    public record IndicateursStock(long ruptures, long sousSeuil, long valeur, List<UUID> ids) {
    }

    @Transactional(readOnly = true)
    public List<MouvementStock> mouvements(UUID articleId, int limite) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        Article a = articles.findById(articleId).orElseThrow(() -> DomainException.introuvable("Article"));
        access.exigerPerimetre(a.getPerimetreCode());
        if (filtrer(List.of(a)).isEmpty()) {
            throw DomainException.introuvable("Article");
        }
        return mouvements.findByArticleIdOrderByHorodatageDesc(articleId, PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public List<Commande> commandes() {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        return commandes.findByPerimetreCodeInOrderByCreeLeDesc(access.utilisateur().perimetres());
    }

    /** Commandes de mes salons, paginées en base ; statut facultatif. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Commande> commandes(Commande.Statut statut, org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        Set<String> p = access.utilisateur().perimetres();
        return statut == null ? commandes.findByPerimetreCodeIn(p, page) : commandes.findByPerimetreCodeInAndStatut(p, statut, page);
    }

    public boolean peutValider(Commande c) {
        CurrentUser u = access.utilisateur();
        return (u.peut(ModuleCode.STOCKS, ActionCode.V) || u.estProfil(Profils.DO))
                && u.couvre(c.getPerimetreCode()) && !c.getCreeParId().equals(u.id());
    }

    public Commande decider(UUID id, boolean valider) {
        CurrentUser u = access.utilisateur();
        if (!u.peut(ModuleCode.STOCKS, ActionCode.V) && !u.estProfil(Profils.DO)) {
            throw DomainException.regle("VALIDATION_DIRECTION", "La validation des commandes revient à la DO / DG.");
        }
        Commande c = commandes.findById(id).orElseThrow(() -> DomainException.introuvable("Commande"));
        access.exigerPerimetre(c.getPerimetreCode());
        c.decider(valider, u.id(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, valider ? "Commande validée" : "Commande refusée",
                c.getReference() + " · " + c.getFournisseur() + " · " + c.getMontantEstime() + " FCFA"));
        return c;
    }

    @Transactional(readOnly = true)
    public List<Commande> aValider() {
        return commandes.findByPerimetreCodeInAndStatutInOrderByCreeLeDesc(access.utilisateur().perimetres(), EnumSet.of(Commande.Statut.BESOIN))
                .stream().filter(this::peutValider).toList();
    }

    /** Restriction fine de la matrice sur le module Stocks. */
    List<Article> filtrer(List<Article> liste) {
        String cat = categorieEffective(null);
        if (cat.isEmpty()) {
            return liste;
        }
        return liste.stream().filter(a -> cat.equals(a.getCategorieCode())).toList();
    }

    /**
     * Catégorie imposée par la restriction du profil, sinon celle demandée par le client.
     * « produits » → boutique seule ; « limité » (Manager) → consommables seuls.
     * Chaîne vide = pas de filtre (jamais null : Postgres / Hibernate).
     */
    public String categorieEffective(String demandee) {
        CurrentUser u = access.utilisateur();
        String r = u.restriction(ModuleCode.STOCKS);
        if ("produits".equals(r)) {
            return "PRODUIT_BOUTIQUE";
        }
        if ("limité".equals(r) && u.estProfil(Profils.MANAGER)) {
            return "CONSOMMABLE";
        }
        return demandee == null || demandee.isBlank() ? "" : demandee.trim();
    }
}
