package sn.arlettedebo.platform.stock.internal;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.shared.pilotage.Indicateur;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Niveau;
import sn.arlettedebo.platform.shared.pilotage.Indicateur.Sens;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.pilotage.SourceDeTaches;
import sn.arlettedebo.platform.shared.pilotage.SourceIndicateurs;
import sn.arlettedebo.platform.shared.pilotage.Tache;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.stock.internal.domain.Article;
import sn.arlettedebo.platform.stock.internal.domain.ArticleRepository;
import sn.arlettedebo.platform.stock.internal.domain.Commande;
import sn.arlettedebo.platform.stock.internal.domain.CommandeRepository;
import sn.arlettedebo.platform.stock.internal.domain.Comptage;
import sn.arlettedebo.platform.stock.internal.domain.ComptageRepository;

/** Contribution stocks : ruptures, alertes, valeur, écarts d'inventaire, commandes ; commandes à valider. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class StockPilotage implements SourceIndicateurs, SourceDeTaches {

    private final ArticleRepository articles;
    private final ComptageRepository comptages;
    private final CommandeRepository commandes;
    private final StockService service;
    private final AccessPolicy access;

    @Override
    public ModuleCode module() {
        return ModuleCode.STOCKS;
    }

    @Override
    public int ordre() {
        return 40;
    }

    @Override
    public boolean contribueAuComparatif() {
        return true;
    }

    @Override
    public String titre() {
        return "Stocks & boutique";
    }

    @Override
    public List<Indicateur> calculer(Periode p, Set<String> perimetres) {
        var ind = service.indicateurs(perimetres);
        // Coin boutique (restriction « produits ») : ruptures / alertes seulement — pas de valeur, commandes ni écarts
        boolean coinBoutique = "produits".equals(access.utilisateur().restriction(ModuleCode.STOCKS));
        List<Indicateur> out = new java.util.ArrayList<>();
        out.add(Indicateur.nombre("stock.ruptures", "Ruptures", ind.ruptures(), Sens.BAISSE_FAVORABLE).alerteSi(ind.ruptures() > 0, Niveau.ALERTE));
        out.add(Indicateur.nombre("stock.alertes", "Sous le seuil", ind.sousSeuil(), Sens.BAISSE_FAVORABLE)
                .alerteSi(ind.sousSeuil() > 0, Niveau.ATTENTION));
        if (coinBoutique) {
            return out;
        }
        long ecarts = !access.peut(ModuleCode.STOCKS, ActionCode.G) ? 0
                : comptages.countParStatutEtPerimetre(Comptage.Statut.ECART_SIGNALE, perimetres,
                        service.categorieEffective(null));
        List<Commande> validees = commandes.valideesEntre(perimetres, p.debut(), p.fin()).stream()
                .filter(c -> c.getStatut() != Commande.Statut.REFUSEE).toList();
        long enCours = commandes.countByPerimetreCodeInAndStatutIn(perimetres,
                java.util.EnumSet.of(Commande.Statut.BESOIN, Commande.Statut.VALIDEE, Commande.Statut.COMMANDEE));
        out.add(Indicateur.montant("stock.valeur", "Valeur du stock (prix d'achat)", ind.valeur(), Sens.NEUTRE));
        if (access.peut(ModuleCode.STOCKS, ActionCode.G)) {
            out.add(Indicateur.nombre("stock.ecarts", "Écarts d'inventaire à traiter", ecarts, Sens.BAISSE_FAVORABLE)
                    .alerteSi(ecarts > 0, Niveau.ATTENTION));
        }
        if (access.peut(ModuleCode.STOCKS, ActionCode.G) || access.peut(ModuleCode.STOCKS, ActionCode.V)) {
            out.add(Indicateur.nombre("stock.commandes_en_cours", "Commandes en cours", enCours, Sens.NEUTRE));
            out.add(Indicateur.montant("stock.achats_valides", "Achats validés sur la période",
                    validees.stream().mapToLong(Commande::getMontantEstime).sum(), Sens.NEUTRE));
        }
        return out;
    }

    @Override
    public List<Tache> tachesEnAttente() {
        if (!access.peut(ModuleCode.STOCKS, ActionCode.C)) {
            return List.of();
        }
        List<Tache> out = new java.util.ArrayList<>(service.aValider().stream()
                .map(c -> new Tache(c.getId(), ModuleCode.STOCKS, "VALIDER_COMMANDE", ActionCode.V,
                        "Commande fournisseur à valider", c.getReference() + " · " + c.getFournisseur() + " · " + c.getLignes().size() + " ligne(s)",
                        "/api/v1/stock/commandes/" + c.getId(), c.getMontantEstime(), c.getPerimetreCode(), c.getCreeLe()))
                .toList());
        // Écarts d'inventaire : traités par la gestion des stocks, jamais par la personne qui a compté
        if (access.peut(ModuleCode.STOCKS, ActionCode.G)) {
            java.util.UUID moi = access.utilisateur().id();
            List<Comptage> ecarts = comptages.ecartsSignalesParPerimetre(Comptage.Statut.ECART_SIGNALE,
                    access.utilisateur().perimetres(), service.categorieEffective(null));
            if (!ecarts.isEmpty()) {
                java.util.Map<java.util.UUID, Article> parId = articles.findAllById(
                        ecarts.stream().map(Comptage::getArticleId).distinct().toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(Article::getId, a -> a));
                ecarts.stream().filter(c -> !moi.equals(c.getSaisiPar())).forEach(c -> {
                    Article a = parId.get(c.getArticleId());
                    if (a == null) {
                        return;
                    }
                    out.add(new Tache(c.getId(), ModuleCode.STOCKS, "TRAITER_ECART", ActionCode.G, "Écart d'inventaire à traiter",
                            a.getNom() + " · théorique " + c.getTheorique() + " · compté " + c.getCompte()
                                    + " (" + (c.ecart() > 0 ? "+" : "") + c.ecart() + " " + a.getUnite() + ")",
                            "/api/v1/stock/comptages/" + c.getId(), null, a.getPerimetreCode(), c.getHorodatage()));
                });
            }
        }
        return out;
    }
}
