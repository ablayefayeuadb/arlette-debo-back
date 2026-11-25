package sn.arlettedebo.platform.stock.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.caisse.FactureDocuments;
import sn.arlettedebo.platform.caisse.Facturation;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.stock.internal.domain.Article;
import sn.arlettedebo.platform.stock.internal.domain.ArticleRepository;
import sn.arlettedebo.platform.stock.internal.domain.Commande;
import sn.arlettedebo.platform.stock.internal.domain.CommandeRepository;
import sn.arlettedebo.platform.stock.internal.domain.Comptage;
import sn.arlettedebo.platform.stock.internal.domain.ComptageRepository;
import sn.arlettedebo.platform.stock.internal.domain.LigneCommande;
import sn.arlettedebo.platform.stock.internal.domain.MouvementStock;
import sn.arlettedebo.platform.stock.internal.domain.MouvementStockRepository;

/**
 * Stocks & boutique — saisie et gestion :
 * <ul>
 *   <li>catalogue (gestion G : Boutique, DO) ;</li>
 *   <li>mouvements manuels — entrée, sortie, perte, casse, transfert (saisie S non restreinte : Boutique) ;</li>
 *   <li>comptages d'inventaire (saisie S, y compris l'Assistant inventaires « limité ») ; l'écart est traité
 *       par une autre personne (gestion G) : ajustement tracé ou rejet ;</li>
 *   <li>commandes fournisseurs : besoin exprimé (S), validation DO / DG (service existant), passation (G),
 *       réception ⇒ entrées en stock ;</li>
 *   <li>vente boutique : sorties de stock et facture à présenter au caissier, dans la même transaction.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockGestionService {

    /** Mouvements saisissables à la main ; VENTE et AJUSTEMENT ne passent que par la vente et l'inventaire. */
    private static final Set<MouvementStock.Type> MANUELS = EnumSet.of(MouvementStock.Type.ENTREE, MouvementStock.Type.SORTIE,
            MouvementStock.Type.PERTE, MouvementStock.Type.CASSE, MouvementStock.Type.TRANSFERT);

    private final ArticleRepository articles;
    private final MouvementStockRepository mouvements;
    private final ComptageRepository comptages;
    private final CommandeRepository commandes;
    private final StockService lecture;
    private final Facturation facturation;
    private final FactureDocuments factureDocuments;
    private final Referentiels referentiels;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ------------------------------------------------------------------ Catalogue (G)

    public Article creerArticle(NouvelArticle n) {
        access.exiger(ModuleCode.STOCKS, ActionCode.G);
        access.exigerPerimetre(n.perimetre());
        referentiels.exiger(TypeReferentiel.CATEGORIE_ARTICLE, n.categorie());
        if (n.nom() == null || n.nom().isBlank() || n.unite() == null || n.unite().isBlank()
                || n.fournisseur() == null || n.fournisseur().isBlank()) {
            throw DomainException.invalide("Nom, unité et fournisseur sont obligatoires.");
        }
        if (n.prixVente() == null || n.prixVente() <= 0) {
            throw DomainException.invalide("Le prix de vente doit être strictement positif.");
        }
        if (n.prixAchat() != null && n.prixAchat() <= 0) {
            throw DomainException.invalide("Le prix d'achat, s'il est renseigné, doit être strictement positif.");
        }
        verifierPrix(n.seuil(), n.prixAchat(), n.prixVente());
        String typeProduit = typeProduit(n.categorie(), n.typeProduit());
        String reference = "ART-" + String.format("%04d", articles.prochainNumero());
        Article a = articles.save(new Article(new Article.Donnees(reference, n.nom().trim(), n.categorie(), n.perimetre(), n.unite().trim(),
                n.seuil(), n.prixAchat(), n.prixVente(), n.fournisseur().trim(), typeProduit)));
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, "Article créé", reference + " · " + a.getNom()));
        return a;
    }

    /** Création d'un article du catalogue (objet-paramètre : champs nommés). */
    public record NouvelArticle(String nom, String categorie, String perimetre, String unite, int seuil, Long prixAchat, Long prixVente,
            String fournisseur, String typeProduit) {
    }

    public Article modifierArticle(UUID id, int seuil, Long prixAchat, Long prixVente, String fournisseur, boolean actif) {
        access.exiger(ModuleCode.STOCKS, ActionCode.G);
        Article a = article(id);
        access.exigerPerimetre(a.getPerimetreCode());
        verifierPrix(seuil, prixAchat, prixVente);
        a.parametrer(seuil, prixAchat, prixVente, vide(fournisseur), actif);
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, actif ? "Article modifié" : "Article désactivé",
                a.getReference() + " · " + a.getNom()));
        return a;
    }

    // ------------------------------------------------------------------ Mouvements manuels (S non restreinte)

    public MouvementStock mouvement(UUID articleId, MouvementStock.Type type, int quantite, String motif) {
        exigerSaisieComplete();
        if (!MANUELS.contains(type)) {
            throw DomainException.invalide("Les ventes passent par la caisse boutique et les ajustements par l'inventaire.");
        }
        if ((type == MouvementStock.Type.PERTE || type == MouvementStock.Type.CASSE || type == MouvementStock.Type.SORTIE)
                && (motif == null || motif.isBlank())) {
            throw DomainException.invalide("Le motif est obligatoire pour une sortie, une perte ou une casse.");
        }
        Article a = article(articleId);
        MouvementStock m = mouvements.save(MouvementStock.appliquer(new MouvementStock.Donnees(a, type, quantite,
                vide(motif), access.utilisateur().id(), null, clock.instant(), AuditingConfig.ZONE)));
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, "Mouvement " + type.name().toLowerCase(),
                a.getNom() + " · " + m.getQuantite() + " " + a.getUnite() + (motif == null ? "" : " · " + motif.trim())));
        return m;
    }

    /** Mouvements des articles visibles, paginés en base (plus récents d'abord) ; type facultatif. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MouvementStock> journal(MouvementStock.Type type, org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        Set<String> p = access.utilisateur().perimetres();
        String cat = lecture.categorieEffective(null);
        return type == null ? mouvements.parPerimetre(p, cat, page) : mouvements.parTypeEtPerimetre(type, p, cat, page);
    }

    /** Comptages des articles visibles, paginés en base ; écarts à traiter seulement si demandé. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Comptage> comptages(boolean ecartsSeulement, org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        Set<String> p = access.utilisateur().perimetres();
        String cat = lecture.categorieEffective(null);
        return ecartsSeulement ? comptages.parStatutEtPerimetre(Comptage.Statut.ECART_SIGNALE, p, cat, page)
                : comptages.parPerimetre(p, cat, page);
    }

    /** Indicateurs des comptages des articles visibles : total, conformes, écarts à traiter (requêtes de comptage). */
    @Transactional(readOnly = true)
    public long[] syntheseComptages() {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        Set<String> p = access.utilisateur().perimetres();
        if (p.isEmpty()) {
            return new long[] {0, 0, 0};
        }
        String cat = lecture.categorieEffective(null);
        return new long[] {comptages.countParPerimetre(p, cat),
                comptages.countParStatutEtPerimetre(Comptage.Statut.CONFORME, p, cat),
                comptages.countParStatutEtPerimetre(Comptage.Statut.ECART_SIGNALE, p, cat)};
    }

    /**
     * Articles à compter (saisie inventaire) : page SQL, DTO sans stock théorique.
     * {@code ecartOuvert} calculé uniquement sur le lot de la page.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ArticleACompter> articlesACompter(Set<String> perimetres, String q,
            org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.STOCKS, ActionCode.S);
        String cat = lecture.categorieEffective(null);
        String recherche = q == null || q.isBlank() ? "" : q.trim();
        var resultat = articles.chercher(perimetres, true, cat, false, recherche, false, "", page);
        List<UUID> ids = resultat.getContent().stream().map(Article::getId).toList();
        Set<UUID> ouverts = ids.isEmpty() ? Set.of()
                : Set.copyOf(comptages.articleIdsEcartsOuverts(Comptage.Statut.ECART_SIGNALE, ids));
        return resultat.map(a -> new ArticleACompter(a.getId(), a.getNom(), a.getUnite(), a.getPerimetreCode(),
                ouverts.contains(a.getId())));
    }

    public record ArticleACompter(UUID id, String nom, String unite, String perimetre, boolean ecartOuvert) {
    }

    /** Articles avec un écart d'inventaire encore ouvert (blocage d'un nouveau comptage). */
    @Transactional(readOnly = true)
    public Set<UUID> articleIdsEcartsOuverts() {
        access.exiger(ModuleCode.STOCKS, ActionCode.C);
        Set<String> p = access.utilisateur().perimetres();
        if (p.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(comptages.articleIdsEcartsOuvertsParPerimetre(Comptage.Statut.ECART_SIGNALE, p,
                lecture.categorieEffective(null)));
    }

    // ------------------------------------------------------------------ Inventaire

    /** Comptage physique : conforme, ou écart signalé (un seul écart ouvert par article). */
    public Comptage compter(UUID articleId, int compte, String commentaire) {
        access.exiger(ModuleCode.STOCKS, ActionCode.S);
        Article a = article(articleId);
        if (!a.isActif()) {
            throw DomainException.invalide("Article désactivé : comptage impossible.");
        }
        if (comptages.existsByArticleIdAndStatut(a.getId(), Comptage.Statut.ECART_SIGNALE)) {
            throw DomainException.etatInvalide("Un écart est déjà en attente de traitement sur cet article.");
        }
        Comptage c = comptages.save(new Comptage(a, compte, access.utilisateur().id(), clock.instant()));
        c.commenter(vide(commentaire));
        if (c.getStatut() == Comptage.Statut.ECART_SIGNALE) {
            events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, "Écart d'inventaire signalé",
                    a.getNom() + " · théorique " + c.getTheorique() + " · compté " + compte));
        }
        return c;
    }

    /** Traitement d'un écart par une autre personne que le compteur : ajustement tracé (écart constaté) ou rejet. */
    public Comptage traiterEcart(UUID comptageId, boolean ajuster, String commentaire) {
        access.exiger(ModuleCode.STOCKS, ActionCode.G);
        Comptage c = comptages.findById(comptageId).orElseThrow(() -> DomainException.introuvable("Comptage"));
        Article a = article(c.getArticleId());
        if (!ajuster && (commentaire == null || commentaire.isBlank())) {
            throw DomainException.invalide("Un rejet d'écart doit être motivé (ex. recomptage demandé).");
        }
        c.traiter(ajuster, access.utilisateur().id(), clock.instant(), vide(commentaire));
        if (ajuster && c.ecart() != 0) {
            mouvements.save(MouvementStock.appliquer(new MouvementStock.Donnees(a, MouvementStock.Type.AJUSTEMENT, c.ecart(),
                    "Inventaire : écart constaté", c.getSaisiPar(), access.utilisateur().id(), clock.instant(), AuditingConfig.ZONE))
                    .lier(null, c.getId(), null));
        }
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, ajuster ? "Écart d'inventaire ajusté" : "Écart d'inventaire rejeté",
                a.getNom() + " · écart " + c.ecart()));
        return c;
    }

    // ------------------------------------------------------------------ Commandes fournisseurs

    /** Expression d'un besoin de réapprovisionnement (lignes d'articles d'un même périmètre). */
    public Commande exprimerBesoin(String perimetre, String fournisseur, Map<UUID, Integer> lignes) {
        exigerSaisieComplete();
        access.exigerPerimetre(perimetre);
        if (lignes.isEmpty()) {
            throw DomainException.invalide("Une commande comporte au moins une ligne.");
        }
        Map<UUID, Article> parId = articles.findAllById(lignes.keySet()).stream().collect(Collectors.toMap(Article::getId, Function.identity()));
        Commande c = new Commande("CMD-" + java.time.Year.now(clock) + "-" + String.format("%05d", commandes.prochainNumero()),
                perimetre, fournisseur.trim(), access.utilisateur().id());
        lignes.forEach((id, qte) -> {
            Article a = parId.get(id);
            if (a == null || !a.getPerimetreCode().equals(perimetre)) {
                throw DomainException.invalide("Article inconnu dans ce périmètre.");
            }
            c.ajouterLigne(a, qte);
        });
        commandes.save(c);
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, "Besoin de réapprovisionnement",
                c.getReference() + " · " + c.getFournisseur() + " · " + c.getMontantEstime() + " FCFA"));
        return c;
    }

    /** Commande validée par la Direction, transmise au fournisseur. */
    public Commande passer(UUID id) {
        access.exiger(ModuleCode.STOCKS, ActionCode.G);
        Commande c = commande(id);
        c.passer(clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, "Commande passée", c.getReference() + " · " + c.getFournisseur()));
        return c;
    }

    /** Réception : chaque ligne entre en stock (mouvement ENTREE lié à la commande). */
    public Commande receptionner(UUID id) {
        exigerSaisieComplete();
        Commande c = commande(id);
        Instant maintenant = clock.instant();
        c.receptionner(access.utilisateur().id(), maintenant);
        Map<UUID, Article> parId = articles.findAllById(c.getLignes().stream().map(LigneCommande::getArticleId).toList()).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        for (LigneCommande l : c.getLignes()) {
            mouvements.save(MouvementStock.appliquer(new MouvementStock.Donnees(parId.get(l.getArticleId()), MouvementStock.Type.ENTREE,
                    l.getQuantite(), "Réception " + c.getReference(), access.utilisateur().id(), c.getValidePar(), maintenant,
                    AuditingConfig.ZONE)).lier(null, null, c.getId()));
        }
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, "Commande réceptionnée", c.getReference() + " · " + c.getLignes().size() + " ligne(s)"));
        return c;
    }

    // ------------------------------------------------------------------ Vente boutique

    /**
     * Vente : prix catalogue, sorties VENTE et facture impayée, atomiques
     * (stock insuffisant ⇒ rien n'est enregistré).
     * <p>Avec {@code modePaiement}, la vente est encaissée dans la même transaction (vente en une étape, sur la
     * caisse ouverte du jour du salon — voir {@link Facturation#encaisserVenteBoutique}) : sans caisse ouverte, rien
     * n'est enregistré. Sans mode de paiement, la facture reste à encaisser au caissier du salon.
     */
    public Facturation.FactureEmise vendre(String perimetre, Map<UUID, Integer> lignes, UUID clientId, String clienteNom,
            String telephone, String modePaiement) {
        // Coin boutique (restriction « produits ») : vente autorisée ; inventaire « limité » reste bloqué.
        exigerVenteOuSaisieComplete();
        access.exigerPerimetre(perimetre);
        if (lignes.isEmpty() || lignes.values().stream().anyMatch(q -> q == null || q <= 0)) {
            throw DomainException.invalide("Panier vide ou quantité invalide.");
        }
        Map<UUID, Article> parId = articles.findAllById(lignes.keySet()).stream().collect(Collectors.toMap(Article::getId, Function.identity()));
        Instant maintenant = clock.instant();
        long total = 0;
        List<String> libelles = new ArrayList<>();
        List<MouvementStock> sorties = new ArrayList<>();
        List<Facturation.LigneVente> lignesFacture = new ArrayList<>();
        for (Map.Entry<UUID, Integer> l : lignes.entrySet()) {
            Article a = parId.get(l.getKey());
            if (a == null || !a.isActif() || !a.getPerimetreCode().equals(perimetre) || a.getPrixVente() == null) {
                throw DomainException.invalide("Article non vendable dans ce périmètre.");
            }
            total += a.getPrixVente() * l.getValue();
            libelles.add(l.getValue() + " × " + a.getNom());
            sorties.add(MouvementStock.appliquer(new MouvementStock.Donnees(a, MouvementStock.Type.VENTE, l.getValue(), "Vente boutique",
                    access.utilisateur().id(), null, maintenant, AuditingConfig.ZONE)));
            lignesFacture.add(new Facturation.LigneVente(a.getId(), a.getNom(), l.getValue(), a.getPrixVente()));
        }
        String libelle = tronquer(String.join(", ", libelles), 100);
        Facturation.FactureEmise facture = facturation.etablirVenteBoutique(perimetre, libelle, total, clientId, clienteNom,
                telephone, lignesFacture);
        sorties.forEach(m -> mouvements.save(m.lier(null, null, null)));
        if (modePaiement != null && !modePaiement.isBlank()) {
            facture = facturation.encaisserVenteBoutique(facture.id(), modePaiement.trim());
        }
        return facture;
    }

    /**
     * Annulation motivée d'une facture boutique (impayée ou payée) : facture annulée, encaissement annulé si besoin
     * (caisse ouverte exigée) et remise en stock des articles vendus (ajustement positif tracé), atomiques.
     * Autorisée au vendeur / coin boutique (saisie S) et à la Direction / Manager (validation V).
     */
    public Facturation.FactureEmise annulerFacture(UUID id, String motif) {
        if (!access.peut(ModuleCode.STOCKS, ActionCode.V)) {
            exigerVenteOuSaisieComplete();
        }
        Facturation.FactureEmise f = facturation.annulerVenteBoutique(id, motif);
        Instant maintenant = clock.instant();
        Map<UUID, Integer> aRemettre = new java.util.LinkedHashMap<>();
        f.lignes().stream().filter(l -> l.articleId() != null).forEach(l -> aRemettre.merge(l.articleId(), l.quantite(), Integer::sum));
        Map<UUID, Article> parId = articles.findAllById(aRemettre.keySet()).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        aRemettre.forEach((articleId, quantite) -> {
            Article a = parId.get(articleId);
            if (a != null) {
                mouvements.save(MouvementStock.appliquer(new MouvementStock.Donnees(a, MouvementStock.Type.AJUSTEMENT, quantite,
                        "Annulation facture " + f.numero(), access.utilisateur().id(), null, maintenant, AuditingConfig.ZONE))
                        .lier(null, null, null));
            }
        });
        events.publishEvent(AuditEvent.of(ModuleCode.STOCKS, "Vente boutique annulée",
                f.numero() + " · " + aRemettre.size() + " article(s) remis en stock · motif : " + motif.trim()));
        return f;
    }

    @Transactional(readOnly = true)
    public Facturation.FactureEmise facture(UUID id) {
        return facturation.lire(id);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Facturation.FactureEmise> facturesBoutique(String perimetre,
            org.springframework.data.domain.Pageable page) {
        return facturation.listerBoutique(perimetre, page);
    }

    @Transactional(readOnly = true)
    public byte[] facturePdf(UUID id) {
        return factureDocuments.pdf(id);
    }

    // ------------------------------------------------------------------ Outils

    /** Saisie de mouvements, commandes : S sans restriction (l'Assistant inventaires « limité » ne fait que compter). */
    private void exigerSaisieComplete() {
        access.exiger(ModuleCode.STOCKS, ActionCode.S);
        CurrentUser u = access.utilisateur();
        String r = u.restriction(ModuleCode.STOCKS);
        if (r != null && !r.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Saisie de stock limitée aux comptages d'inventaire.");
        }
    }

    /** Vente : S, ou restriction « produits » (caissier coin boutique). */
    private void exigerVenteOuSaisieComplete() {
        access.exiger(ModuleCode.STOCKS, ActionCode.S);
        CurrentUser u = access.utilisateur();
        String r = u.restriction(ModuleCode.STOCKS);
        if (r != null && !r.isBlank() && !"produits".equalsIgnoreCase(r.trim())) {
            throw new org.springframework.security.access.AccessDeniedException("Saisie de stock limitée aux comptages d'inventaire.");
        }
    }

    /** Article visible par l'utilisateur (périmètre et restriction « produits » / « limité »). */
    private Article article(UUID id) {
        Article a = articles.findById(id).orElseThrow(() -> DomainException.introuvable("Article"));
        access.exigerPerimetre(a.getPerimetreCode());
        if (lecture.filtrer(List.of(a)).isEmpty()) {
            throw DomainException.introuvable("Article");
        }
        return a;
    }

    private Commande commande(UUID id) {
        Commande c = commandes.findById(id).orElseThrow(() -> DomainException.introuvable("Commande"));
        access.exigerPerimetre(c.getPerimetreCode());
        return c;
    }

    private static void verifierPrix(int seuil, Long prixAchat, Long prixVente) {
        if (seuil < 0 || (prixAchat != null && prixAchat < 0) || (prixVente != null && prixVente < 0)) {
            throw DomainException.invalide("Seuil et prix doivent être positifs.");
        }
    }

    /** Types de produit boutique autorisés (catégorie PRODUIT_BOUTIQUE) ; ignoré pour les autres catégories. */
    private static final Set<String> TYPES_PRODUIT = Set.of("MASQUES", "CHANTILLY", "SHAMPOING", "LAIT", "HUILES", "SPRAYS",
            "CHANTILLY_CORPS");

    private static String typeProduit(String categorie, String typeProduit) {
        if (!"PRODUIT_BOUTIQUE".equals(categorie)) {
            return null;
        }
        String t = vide(typeProduit);
        if (t == null) {
            return null;
        }
        t = t.toUpperCase(java.util.Locale.ROOT);
        if (!TYPES_PRODUIT.contains(t)) {
            throw DomainException.invalide("Type de produit inconnu : " + typeProduit + ".");
        }
        return t;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String tronquer(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
