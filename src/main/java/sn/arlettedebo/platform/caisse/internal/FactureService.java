package sn.arlettedebo.platform.caisse.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.caisse.Facturation;
import sn.arlettedebo.platform.caisse.internal.domain.Encaissement;
import sn.arlettedebo.platform.caisse.internal.domain.EncaissementRepository;
import sn.arlettedebo.platform.caisse.internal.domain.Facture;
import sn.arlettedebo.platform.caisse.internal.domain.FactureRepository;
import sn.arlettedebo.platform.caisse.internal.domain.LigneFacture;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisseRepository;
import sn.arlettedebo.platform.commercial.Clientele;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Factures : émission (boutique / prestation) et paiement au caissier du salon.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FactureService implements Facturation {

    private final FactureRepository factures;
    private final SessionCaisseRepository sessions;
    private final EncaissementRepository encaissements;
    private final Clientele clientele;
    private final Referentiels referentiels;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public FactureEmise etablirVenteBoutique(String perimetre, String libelle, long montant, UUID clientId, String clienteNom,
            String telephone, List<LigneVente> lignes) {
        if (lignes == null || lignes.isEmpty()) {
            throw DomainException.invalide("Aucune ligne de facture.");
        }
        String nom = clienteNom;
        if (clientId != null) {
            nom = clientele.exigerCliente(clientId, perimetre);
        }
        if (nom == null || nom.isBlank()) {
            throw DomainException.invalide("Le nom complet de la cliente est obligatoire sur la facture.");
        }
        String tel = telephoneOuNull(telephone);
        if (tel == null) {
            throw DomainException.invalide("Le téléphone de la cliente est obligatoire sur la facture.");
        }
        Facture f = new Facture(nouveauNumero(), perimetre, LocalDate.now(clock), Facture.Origine.BOUTIQUE, libelle, montant,
                clientId, nom.trim(), tel, null);
        for (LigneVente l : lignes) {
            f.ajouterLigne(l.articleId(), l.libelle(), l.quantite(), l.prixUnitaire());
        }
        factures.save(f);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Facture boutique",
                f.getNumero() + " · " + perimetre + " · " + montant + " FCFA"));
        return emise(f);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public FactureEmise etablirPrestation(UUID rendezVousId, String perimetre, LocalDate jour, UUID clientId, String clienteNom,
            String telephone, String libelle, long montant) {
        return factures.findByRendezVousIdAndStatutNot(rendezVousId, Facture.Statut.ANNULEE)
                .map(FactureService::emise)
                .orElseGet(() -> {
                    Facture f = new Facture(nouveauNumero(), perimetre, jour, Facture.Origine.PRESTATION, libelle, montant,
                            clientId, clienteNom, telephoneOuNull(telephone), rendezVousId);
                    f.ajouterLigne(null, libelle, 1, montant);
                    factures.save(f);
                    events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Facture prestation",
                            f.getNumero() + " · " + perimetre + " · " + clienteNom + " · " + montant + " FCFA"));
                    return emise(f);
                });
    }

    /** Factures à encaisser : prestations des salons couverts + toutes les ventes boutique (paginé). */
    @Transactional(readOnly = true)
    public Page<Facture> impayees(String perimetreSalon, Pageable page) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        Set<String> perimetres = perimetreSalon == null
                ? access.utilisateur().perimetres()
                : Set.of(exigerSalon(perimetreSalon));
        return factures.findImpayees(perimetres, Facture.Statut.IMPAYEE, Facture.Origine.BOUTIQUE, page);
    }

    /** Liste des factures produit (coin boutique) du salon : stocks S sur le périmètre. */
    @Override
    @Transactional(readOnly = true)
    public Page<FactureEmise> listerBoutique(String perimetre, Pageable page) {
        access.exiger(ModuleCode.STOCKS, ActionCode.S);
        access.exigerPerimetre(perimetre);
        return factures.findByOrigineAndPerimetreOrigineOrderByJourDescCreeLeDesc(Facture.Origine.BOUTIQUE, perimetre, page)
                .map(FactureService::emiseResume);
    }

    /** Lecture pour réimpression : vendeur boutique (ses factures) ou caissier (salon + boutique). */
    @Override
    @Transactional(readOnly = true)
    public FactureEmise lire(UUID id) {
        Facture f = factures.findWithLignesById(id).orElseThrow(() -> DomainException.introuvable("Facture"));
        if (f.getOrigine() == Facture.Origine.BOUTIQUE && access.utilisateur().peut(ModuleCode.STOCKS, ActionCode.S)
                && access.utilisateur().couvre(f.getPerimetreOrigine())) {
            return emise(f);
        }
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        if (f.getOrigine() == Facture.Origine.BOUTIQUE) {
            return emise(f);
        }
        access.exigerPerimetre(f.getPerimetreOrigine());
        return emise(f);
    }

    /**
     * Vente boutique en une étape. Choix retenu (le moins invasif) : le paiement est un encaissement normal
     * rattaché à la caisse OUVERTE du jour du salon de la vente (même session que le caissier), sans ouvrir
     * de caisse à la place du caissier (fond compté + séparation des tâches). Sans caisse ouverte, on refuse
     * plutôt que de créer un paiement hors session : le vendeur peut alors établir la facture à encaisser plus tard.
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public FactureEmise encaisserVenteBoutique(UUID factureId, String modePaiement) {
        referentiels.exiger(TypeReferentiel.MODE_PAIEMENT, modePaiement);
        Facture f = factures.findWithLignesById(factureId).orElseThrow(() -> DomainException.introuvable("Facture"));
        if (f.getOrigine() != Facture.Origine.BOUTIQUE) {
            throw DomainException.invalide("Seule une facture boutique peut être encaissée ici.");
        }
        access.exigerPerimetre(f.getPerimetreOrigine());
        SessionCaisse s = sessions.findByPerimetreCodeAndStatut(f.getPerimetreOrigine(), SessionCaisse.Statut.OUVERTE)
                .orElseThrow(() -> DomainException.etatInvalide("Aucune caisse ouverte sur ce salon : "
                        + "demandez au caissier d'ouvrir la caisse, ou établissez la facture sans paiement."));
        LocalDate aujourdhui = LocalDate.now(clock);
        if (!s.getJour().equals(aujourdhui)) {
            throw DomainException.etatInvalide("La caisse du " + s.getJour()
                    + " est encore ouverte : le caissier doit la clôturer et ouvrir celle du jour avant tout paiement.");
        }
        regler(s, f, modePaiement, null);
        return emise(f);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public FactureEmise annulerVenteBoutique(UUID factureId, String motif) {
        Facture f = factures.findWithLignesById(factureId).orElseThrow(() -> DomainException.introuvable("Facture"));
        if (f.getOrigine() != Facture.Origine.BOUTIQUE) {
            throw DomainException.invalide("Seule une facture boutique peut être annulée ici.");
        }
        access.exigerPerimetre(f.getPerimetreOrigine());
        if (f.getStatut() == Facture.Statut.ANNULEE) {
            throw DomainException.etatInvalide("Cette facture est déjà annulée.");
        }
        if (motif == null || motif.isBlank()) {
            throw DomainException.invalide("Le motif d'annulation est obligatoire.");
        }
        String m = motif.trim();
        if (f.getStatut() == Facture.Statut.PAYEE && f.getEncaissementId() != null) {
            Encaissement e = encaissements.findById(f.getEncaissementId())
                    .orElseThrow(() -> DomainException.introuvable("Encaissement"));
            if (!e.isAnnule()) {
                SessionCaisse s = e.getSession();
                if (s.getStatut() != SessionCaisse.Statut.OUVERTE) {
                    throw DomainException.etatInvalide("La caisse du " + s.getJour()
                            + " est clôturée : la facture payée ne peut plus être annulée.");
                }
                e.annuler("annulation facture " + f.getNumero() + " : " + m);
            }
        }
        f.annulerEmise(m, access.utilisateur().id(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Facture boutique annulée",
                f.getNumero() + " · " + f.getPerimetreOrigine() + " · " + f.getMontant() + " FCFA · motif : " + m));
        return emise(f);
    }

    /** Paiement d'une facture sur la caisse ouverte du salon. */
    public Encaissement encaisser(UUID sessionId, UUID factureId, String modePaiement, String note) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        referentiels.exiger(TypeReferentiel.MODE_PAIEMENT, modePaiement);
        SessionCaisse s = sessions.findWithEncaissementsById(sessionId)
                .orElseThrow(() -> DomainException.introuvable("Session de caisse"));
        access.exigerPerimetre(s.getPerimetreCode());
        Facture f = factures.findWithLignesById(factureId).orElseThrow(() -> DomainException.introuvable("Facture"));
        if (f.getStatut() != Facture.Statut.IMPAYEE) {
            throw DomainException.etatInvalide("Cette facture n'est plus à encaisser.");
        }
        if (f.getOrigine() == Facture.Origine.PRESTATION && !f.getPerimetreOrigine().equals(s.getPerimetreCode())) {
            throw DomainException.invalide("Cette facture appartient à un autre salon.");
        }
        return regler(s, f, modePaiement, note);
    }

    /** Écriture commune du paiement : encaissement sur la session, facture soldée, audit. */
    private Encaissement regler(SessionCaisse s, Facture f, String modePaiement, String note) {
        String libelle = f.getNumero() + " · " + f.getLibelle();
        if (f.getClienteNom() != null) {
            libelle = libelle + " · " + f.getClienteNom();
        }
        Encaissement e = s.encaisser(tronquer(libelle, 100), f.getMontant(), modePaiement, access.utilisateur().id(), clock.instant());
        e.rattacher(f.getRendezVousId(), f.getClientId(), null, null);
        e.lierFacture(f.getId());
        e.annoter(note);
        if (f.getClientId() != null) {
            clientele.enregistrerVisite(f.getClientId(), s.getJour(), null);
        }
        encaissements.save(e);
        f.payer(e.getId(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Facture encaissée",
                s.getPerimetreCode() + " · " + f.getNumero() + " · " + f.getMontant() + " FCFA · " + modePaiement));
        return e;
    }

    public static FactureEmise emise(Facture f) {
        return new FactureEmise(f.getId(), f.getNumero(), f.getPerimetreOrigine(), f.getJour(), f.getOrigine().name(),
                f.getStatut().name(), f.getMontant(), f.getLibelle(), f.getClienteNom(), f.getTelephone(), f.getClientId(),
                f.getLignes().stream().map(FactureService::ligneEmise).toList());
    }

    /** Liste : sans charger les lignes (PDF / détail les rechargent). */
    private static FactureEmise emiseResume(Facture f) {
        return new FactureEmise(f.getId(), f.getNumero(), f.getPerimetreOrigine(), f.getJour(), f.getOrigine().name(),
                f.getStatut().name(), f.getMontant(), f.getLibelle(), f.getClienteNom(), f.getTelephone(), f.getClientId(),
                List.of());
    }

    private static LigneEmise ligneEmise(LigneFacture l) {
        return new LigneEmise(l.getArticleId(), l.getLibelle(), l.getQuantite(), l.getPrixUnitaire(), l.getMontant());
    }

    private String exigerSalon(String perimetre) {
        access.exigerPerimetre(perimetre);
        return perimetre;
    }

    private String nouveauNumero() {
        long n = factures.prochainNumero();
        return "FAC-" + Year.now(clock).getValue() + "-" + String.format("%04d", n);
    }

    private static String telephoneOuNull(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        String chiffres = brut.replaceAll("\\D", "");
        if (chiffres.length() == 12 && chiffres.startsWith("221")) {
            chiffres = chiffres.substring(3);
        }
        if (!chiffres.matches("[37]\\d{8}")) {
            throw DomainException.invalide("Numéro de téléphone invalide (9 chiffres, ex. 77 123 45 67).");
        }
        return chiffres;
    }

    private static String tronquer(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
