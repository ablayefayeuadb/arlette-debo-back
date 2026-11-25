package sn.arlettedebo.platform.planning.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.caisse.Facturation;
import sn.arlettedebo.platform.commercial.Abonnees;
import sn.arlettedebo.platform.commercial.Clientele;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.planning.internal.domain.AttenteRdv;
import sn.arlettedebo.platform.planning.internal.domain.AttenteRdvRepository;
import sn.arlettedebo.platform.planning.internal.domain.Creneau;
import sn.arlettedebo.platform.planning.internal.domain.CreneauRepository;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousHistorique;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousHistoriqueRepository;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousSupplement;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousSupplementRepository;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;
import sn.arlettedebo.platform.shared.sms.SmsMessages;
import sn.arlettedebo.platform.shared.sms.SmsService;
import sn.arlettedebo.platform.shared.telephone.Telephones;

/** Planning & rendez-vous (consultation). Règle « Coiffeuses » : accès limité à leurs propres prestations. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanningService {

    /** Statuts qui ne retiennent plus de place dans un créneau. */
    private static final Set<RendezVous.Statut> LIBERES = EnumSet.of(RendezVous.Statut.ANNULEE, RendezVous.Statut.ABSENTE);

    /** Statuts exclus du signalement de doublon cliente (CLI-01). */
    private static final Set<RendezVous.Statut> HORS_DOUBLON = EnumSet.of(RendezVous.Statut.ANNULEE, RendezVous.Statut.ABSENTE);

    private final RendezVousRepository rdvs;
    private final RendezVousHistoriqueRepository historiques;
    private final AttenteRdvRepository attentes;
    private final RendezVousSupplementRepository supplements;
    private final CreneauRepository creneaux;
    private final Referentiels referentiels;
    private final Clientele clientele;
    private final Abonnees abonnees;
    private final Facturation facturation;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Effectifs effectifs;
    private final AccessPolicy access;
    private final SmsService sms;
    private final FieldCrypto crypto;

    /** Fenêtre maximale d'un agenda : la liste n'est pas paginée (vue calendrier), elle reste donc bornée. */
    static final int AGENDA_MAX_JOURS = 31;

    public List<RendezVous> rendezVous(Periode p, String perimetre) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        if (p.jours() > AGENDA_MAX_JOURS) {
            throw DomainException.invalide("Période maximale : " + AGENDA_MAX_JOURS + " jours.");
        }
        CurrentUser u = access.utilisateur();
        if ("personnel".equals(u.restriction(ModuleCode.PLANNING))) {
            UUID moi = effectifs.employeDuCompte(u.id())
                    .orElseThrow(() -> DomainException.etatInvalide("Aucun dossier du personnel n'est lié à votre compte."));
            return rdvs.findByCoiffeuseIdAndJourBetweenOrderByJourAscHeureAsc(moi, p.du(), p.au());
        }
        return rdvs.findByPerimetreCodeInAndJourBetweenOrderByJourAscHeureAsc(perimetres(perimetre), p.du(), p.au());
    }

    /** Coiffeuses actives d'un salon (affectation des prestations et des encaissements). */
    public List<Effectifs.EmployeRef> coiffeuses(String perimetre) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        access.exigerPerimetre(perimetre);
        return effectifs.coiffeuses(perimetre);
    }

    /** Capacité et plage horaire type du salon (paramétrés dans Salons). */
    public ParametresCreneaux parametresCreneaux(String perimetre) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        access.exigerPerimetre(perimetre);
        return referentiels.parametresCreneaux(perimetre)
                .map(p -> new ParametresCreneaux(p.capacite(), p.heureOuverture(), p.heureFermeture()))
                .orElseThrow(() -> DomainException.introuvable("Salon"));
    }

    public record ParametresCreneaux(int capacite, int heureOuverture, int heureFermeture) {
    }

    // ------------------------------------------------------------------ Gestion de l'agenda (planning G)

    /**
     * Prise de rendez-vous : durée et prix issus du catalogue du salon, coiffeuse du salon, fiche cliente
     * du périmètre si elle est indiquée. Pas de double réservation d'une coiffeuse (contrainte d'exclusion).
     */
    @Transactional
    public RendezVous creer(NouveauRdv n) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        access.exigerPerimetre(n.perimetre());
        if (n.jour().isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("Un rendez-vous ne peut pas être pris dans le passé.");
        }
        Referentiels.PrestationInfo p = referentiels.prestation(n.prestation())
                .filter(x -> x.perimetre().equals(n.perimetre()))
                .orElseThrow(() -> DomainException.invalide("Prestation inconnue pour ce salon : " + n.prestation()));
        exigerCoiffeuse(n.coiffeuseId(), n.perimetre());
        if (n.horsCreneau()) {
            if (!access.estProfil(Profils.MANAGER, Profils.DG)) {
                throw DomainException.regle("HORS_CRENEAU_MANAGER",
                        "Un rendez-vous hors créneau est réservé à la Manager ou à la DG.");
            }
        } else {
            exigerPlaceDansCreneau(n.perimetre(), n.jour(), n.heure(), null);
        }
        String tel = telephoneOuNull(n.telephone());
        UUID clientId = n.clientId();
        if (clientId == null && tel != null) {
            clientId = clientele.ficheParTelephone(tel, n.perimetre()).orElse(null);
        }
        String cliente = clientId != null ? clientele.exigerCliente(clientId, n.perimetre()) : n.clienteNom();
        if (cliente == null || cliente.isBlank()) {
            throw DomainException.invalide("Le nom de la cliente est obligatoire.");
        }
        exigerPasDeDoublon(n.jour(), tel, clientId, null, n.forcer());
        exigerCompetence(n.coiffeuseId(), p.code(), n.forcer());
        exigerPasDeChevauchementSansCoiffeuse(n.perimetre(), n.jour(), n.heure(), p.dureeMin(), n.coiffeuseId(), null);
        RendezVous.Source source = n.source() == null ? RendezVous.Source.ACCUEIL : n.source();
        RendezVous r = new RendezVous(new RendezVous.Donnees(n.perimetre(), n.jour(), n.heure(), p.dureeMin(), clientId,
                cliente.trim(), tel, p.code(), n.coiffeuseId(), p.prix(), source));
        r.annoter(n.note());
        if (n.horsCreneau()) {
            r.marquerHorsCreneau(true);
        }
        rdvs.saveAndFlush(r);
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Rendez-vous pris",
                n.perimetre() + " · " + n.jour() + " " + n.heure() + " · " + cliente.trim() + " · " + p.libelle()));
        if (tel != null) {
            String salon = referentiels.libelle(TypeReferentiel.PERIMETRE, n.perimetre()).orElse(n.perimetre());
            if (source == RendezVous.Source.EN_LIGNE) {
                sms.envoyer(tel, SmsMessages.rdvDemandeEnLigne(salon, n.jour(), n.heure(), p.libelle(), r.reference()));
            } else {
                sms.envoyer(tel, SmsMessages.rdvConfirme(salon, n.jour(), n.heure(), p.libelle(), r.reference()));
            }
        }
        return r;
    }

    /**
     * Statut : la gestion du planning (accueil, Manager) fait toutes les transitions ; une coiffeuse
     * (planning « personnel ») démarre et termine uniquement ses propres prestations.
     * Annulation : motif obligatoire.
     */
    @Transactional
    public RendezVous changerStatut(UUID id, RendezVous.Statut cible, String motif, boolean forcer) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        RendezVous r = charger(id);
        CurrentUser u = access.utilisateur();
        if (!u.peut(ModuleCode.PLANNING, ActionCode.G)) {
            UUID moi = effectifs.employeDuCompte(u.id()).orElse(null);
            boolean saPrestation = moi != null && moi.equals(r.getCoiffeuseId());
            if (!saPrestation || (cible != RendezVous.Statut.EN_COURS && cible != RendezVous.Statut.TERMINEE)) {
                throw new org.springframework.security.access.AccessDeniedException("Gestion du planning requise.");
            }
        }
        if (cible == RendezVous.Statut.CONFIRME && r.getCoiffeuseId() == null) {
            throw DomainException.regle("COIFFEUSE_REQUISE",
                    "Assignez une coiffeuse avant de confirmer le rendez-vous.");
        }
        if (cible == RendezVous.Statut.CONFIRME) {
            exigerCompetence(r.getCoiffeuseId(), r.prestationEffective(), forcer);
        }
        if (cible == RendezVous.Statut.ANNULEE && (motif == null || motif.isBlank())) {
            throw DomainException.invalide("Le motif d'annulation est obligatoire.");
        }
        if (cible == RendezVous.Statut.TERMINEE && r.getPrestationRealiseeCode() == null) {
            throw DomainException.regle("REALISE_REQUIS",
                    "Enregistrez la prestation réalisée (et les suppléments) avant de terminer.");
        }
        RendezVous.Statut avant = r.getStatut();
        r.changerStatut(cible, clock.instant());
        historiser(r.getId(), RendezVousHistorique.Champ.STATUT, avant.name(), cible.name(),
                cible == RendezVous.Statut.ANNULEE ? motif : null);
        if (cible == RendezVous.Statut.ANNULEE) {
            historiser(r.getId(), RendezVousHistorique.Champ.ANNULATION, avant.name(), cible.name(), motif);
        }
        if (cible == RendezVous.Statut.TERMINEE) {
            facturer(r);
            enregistrerSeanceAbonnee(r);
        }
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Rendez-vous : " + cible.name().toLowerCase().replace('_', ' '),
                r.getPerimetreCode() + " · " + r.getJour() + " " + r.getHeure() + " · " + r.getClienteNom()));
        return r;
    }

    @Transactional
    public RendezVous reaffecter(UUID id, UUID coiffeuseId, boolean forcer) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        RendezVous r = charger(id);
        exigerCoiffeuse(coiffeuseId, r.getPerimetreCode());
        exigerCompetence(coiffeuseId, r.prestationEffective(), forcer);
        exigerPasDeChevauchementSansCoiffeuse(r.getPerimetreCode(), r.getJour(), r.getHeure(), r.getDureeMin(),
                coiffeuseId, r.getId());
        String avant = r.getCoiffeuseId() == null ? null : r.getCoiffeuseId().toString();
        r.reaffecter(coiffeuseId);
        rdvs.saveAndFlush(r);
        historiser(r.getId(), RendezVousHistorique.Champ.COIFFEUSE, avant,
                coiffeuseId == null ? null : coiffeuseId.toString(), null);
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Rendez-vous réaffecté",
                r.getClienteNom() + " → coiffeuse " + (coiffeuseId == null ? "aucune" : coiffeuseId)));
        return r;
    }

    @Transactional
    public RendezVous deplacer(UUID id, LocalDate jour, java.time.LocalTime heure) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        if (jour.isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("Un rendez-vous ne peut pas être déplacé dans le passé.");
        }
        RendezVous r = charger(id);
        String avant = r.getJour() + " " + r.getHeure();
        exigerPlaceDansCreneau(r.getPerimetreCode(), jour, heure, id);
        exigerPasDeChevauchementSansCoiffeuse(r.getPerimetreCode(), jour, heure, r.getDureeMin(), r.getCoiffeuseId(),
                r.getId());
        r.deplacer(jour, heure);
        rdvs.saveAndFlush(r);
        historiser(r.getId(), RendezVousHistorique.Champ.HORAIRE, avant, jour + " " + heure, null);
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Rendez-vous déplacé", r.getClienteNom() + " → " + jour + " " + heure));
        return r;
    }

    /** CLI-04 : intention de prestation à l'arrivée. */
    @Transactional
    public RendezVous definirIntention(UUID id, String prestationCode, String note) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        RendezVous r = charger(id);
        Referentiels.PrestationInfo p = referentiels.prestation(prestationCode)
                .filter(x -> x.perimetre().equals(r.getPerimetreCode()))
                .orElseThrow(() -> DomainException.invalide("Prestation inconnue pour ce salon : " + prestationCode));
        String avant = r.prestationEffective();
        r.definirIntention(p.code(), note);
        historiser(r.getId(), RendezVousHistorique.Champ.INTENTION, avant, p.code(), note);
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Intention prestation",
                r.getClienteNom() + " · " + p.libelle()));
        return r;
    }

    /** CLI-06 : fiche d'attente / retard. */
    @Transactional
    public AttenteRdv enregistrerAttente(UUID id, String motif, boolean infoCliente, AttenteRdv.Decision decision) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        RendezVous r = charger(id);
        if (r.getArriveeLe() == null) {
            throw DomainException.etatInvalide("La cliente doit être arrivée pour enregistrer une attente.");
        }
        Instant finAttente = r.getDebutLe() != null ? r.getDebutLe() : clock.instant();
        int minutes = (int) java.time.Duration.between(r.getArriveeLe(), finAttente).toMinutes();
        if (minutes < AttenteRdv.SEUIL_MINUTES) {
            throw DomainException.invalide("Attente inférieure au seuil de " + AttenteRdv.SEUIL_MINUTES + " min.");
        }
        AttenteRdv a = attentes.save(new AttenteRdv(r.getId(), minutes, motif, infoCliente, decision,
                access.utilisateur().id(), clock.instant()));
        historiser(r.getId(), RendezVousHistorique.Champ.ATTENTE, null,
                decision.name() + " · " + minutes + " min", motif);
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Attente RDV",
                r.getClienteNom() + " · " + minutes + " min · " + decision));
        return a;
    }

    /** CLI-07 : réalisé + suppléments avant clôture. */
    @Transactional
    public RendezVous cloturerPreparation(UUID id, String prestationRealisee, String observations, String motifEcart,
            List<SupplementSaisi> lignes) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        RendezVous r = charger(id);
        if (r.getStatut() != RendezVous.Statut.EN_COURS) {
            throw DomainException.etatInvalide("La clôture se prépare pendant la prestation (EN_COURS).");
        }
        Referentiels.PrestationInfo p = referentiels.prestation(prestationRealisee)
                .filter(x -> x.perimetre().equals(r.getPerimetreCode()))
                .orElseThrow(() -> DomainException.invalide("Prestation inconnue pour ce salon : " + prestationRealisee));
        String avant = r.prestationFacturable();
        long totalSupplements = 0;
        for (RendezVousSupplement existant : supplements.findByRendezVousIdOrderByAccepteLeAsc(r.getId())) {
            supplements.delete(existant);
        }
        if (lignes != null) {
            for (SupplementSaisi s : lignes) {
                RendezVousSupplement ligne = supplements.save(new RendezVousSupplement(r.getId(), s.libelle(), s.montant(),
                        clock.instant()));
                totalSupplements += ligne.getMontant();
                historiser(r.getId(), RendezVousHistorique.Champ.SUPPLEMENT, null,
                        ligne.getLibelle() + " · " + ligne.getMontant(), null);
            }
        }
        long prix = p.prix() + totalSupplements;
        if (!p.code().equals(r.prestationEffective()) && (motifEcart == null || motifEcart.isBlank())) {
            throw DomainException.invalide("Expliquez l'écart entre l'intention et le réalisé.");
        }
        r.definirRealise(p.code(), prix, observations, motifEcart);
        historiser(r.getId(), RendezVousHistorique.Champ.REALISE, avant, p.code(), motifEcart);
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Réalisé prestation",
                r.getClienteNom() + " · " + p.libelle() + " · " + prix + " FCFA"));
        return r;
    }

    public List<RendezVousHistorique> historique(UUID id) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        charger(id);
        return historiques.findByRendezVousIdOrderByLeAsc(id);
    }

    public List<AttenteRdv> attentesDuRdv(UUID id) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        charger(id);
        return attentes.findByRendezVousIdOrderByLeDesc(id);
    }

    public List<RendezVousSupplement> supplementsDuRdv(UUID id) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        charger(id);
        return supplements.findByRendezVousIdOrderByAccepteLeAsc(id);
    }

    public long countAttentes(UUID id) {
        return attentes.countByRendezVousId(id);
    }

    /** Fiches d'attente par rendez-vous pour un lot (une requête) ; absent = 0. */
    public Map<UUID, Long> countAttentes(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        Map<UUID, Long> out = new java.util.HashMap<>();
        attentes.nombresParRdv(ids).forEach(n -> out.put(n.getRendezVousId(), n.getNombre()));
        return out;
    }

    /** Suppléments acceptés d'un lot de rendez-vous (une requête), groupés par rendez-vous. */
    public Map<UUID, List<RendezVousSupplement>> supplementsDesRdvs(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        return supplements.findByRendezVousIdInOrderByAccepteLeAsc(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(RendezVousSupplement::getRendezVousId));
    }

    private void facturer(RendezVous r) {
        String code = r.prestationFacturable();
        String libelle = referentiels.prestation(code).map(Referentiels.PrestationInfo::libelle).orElse(code);
        List<RendezVousSupplement> lignes = supplements.findByRendezVousIdOrderByAccepteLeAsc(r.getId());
        if (!lignes.isEmpty()) {
            libelle = libelle + " (+" + lignes.size() + " suppl.)";
        }
        facturation.etablirPrestation(r.getId(), r.getPerimetreCode(), r.getJour(), r.getClientId(), r.getClienteNom(),
                r.getTelephone(), libelle, r.getPrix());
    }

    /**
     * RDV terminé → séance dans le dossier abonnée si un dossier existe
     * (par fiche cliente ou téléphone). Idempotent si un suivi existe déjà ce jour.
     */
    private void enregistrerSeanceAbonnee(RendezVous r) {
        String code = r.prestationFacturable();
        String libelle = referentiels.prestation(code).map(Referentiels.PrestationInfo::libelle).orElse(code);
        String heure = r.getHeure() == null ? "" : r.getHeure().toString();
        if (heure.length() > 5) {
            heure = heure.substring(0, 5);
        }
        StringBuilder obs = new StringBuilder("RDV terminé");
        if (!heure.isBlank()) {
            obs.append(" · ").append(heure);
        }
        if (libelle != null && !libelle.isBlank()) {
            obs.append(" · ").append(libelle);
        }
        if (r.getObservationsCloture() != null && !r.getObservationsCloture().isBlank()) {
            obs.append(" — ").append(r.getObservationsCloture().trim());
        }
        abonnees.enregistrerSeanceDuRendezVous(r.getClientId(), r.getTelephone(), r.getPerimetreCode(), r.getJour(),
                obs.toString());
    }

    private void historiser(UUID rdvId, RendezVousHistorique.Champ champ, String avant, String apres, String motif) {
        historiques.save(new RendezVousHistorique(rdvId, champ, avant, apres, motif,
                access.utilisateur().id(), clock.instant()));
    }

    public record SupplementSaisi(String libelle, long montant) {
    }

    // ------------------------------------------------------------------ Créneaux de réservation (planning G)

    /**
     * Créneaux ouverts d'un jour avec places restantes (même logique que la réservation en ligne).
     * Filtre coiffeuse optionnel : un créneau où elle a déjà une prestation qui chevauche est affiché plein.
     */
    public List<Disponibilite> disponibilites(String perimetre, LocalDate jour, String prestation, UUID coiffeuseId) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        access.exigerPerimetre(perimetre);
        Referentiels.PrestationInfo p = referentiels.prestation(prestation)
                .filter(x -> x.perimetre().equals(perimetre))
                .orElseThrow(() -> DomainException.invalide("Prestation inconnue pour ce salon : " + prestation));
        exigerCoiffeuse(coiffeuseId, perimetre);
        List<RendezVous> agendaCoiffeuse = coiffeuseId == null ? List.of()
                : rdvs.findByCoiffeuseIdAndJourBetweenOrderByJourAscHeureAsc(coiffeuseId, jour, jour);
        int duree = p.dureeMin();
        List<RendezVousRepository.JourHeure> prises = rdvs.prisesEntre(perimetre, jour, jour, LIBERES);
        return creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour).stream()
                .filter(Creneau::isOuvert)
                .map(c -> {
                    long places = Math.max(0, c.getCapacite() - Occupation.dans(prises, c));
                    if (places > 0 && coiffeuseId != null && coiffeuseOccupee(agendaCoiffeuse, c.getHeureDebut(), duree)) {
                        places = 0;
                    }
                    return new Disponibilite(c.getHeureDebut(), c.getHeureFin(), places);
                })
                .toList();
    }

    /** Créneaux d'un jour (ouverts et fermés) avec occupation. */
    public List<CreneauDetail> creneaux(String perimetre, LocalDate jour) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        access.exigerPerimetre(perimetre);
        List<RendezVousRepository.JourHeure> prises = rdvs.prisesEntre(perimetre, jour, jour, LIBERES);
        return creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour).stream()
                .map(c -> new CreneauDetail(c, Occupation.dans(prises, c)))
                .toList();
    }

    /** Créneaux d'une période (ouverts et fermés) avec occupation — vue semaine. */
    public List<CreneauDetail> creneaux(String perimetre, LocalDate du, LocalDate au) {
        access.exiger(ModuleCode.PLANNING, ActionCode.C);
        access.exigerPerimetre(perimetre);
        if (au.isBefore(du)) {
            throw DomainException.invalide("La fin de période doit suivre le début.");
        }
        if (du.plusDays(31).isBefore(au)) {
            throw DomainException.invalide("Période maximale : 31 jours.");
        }
        List<RendezVousRepository.JourHeure> prises = rdvs.prisesEntre(perimetre, du, au, LIBERES);
        return creneaux.findByPerimetreCodeAndJourBetweenOrderByJourAscHeureDebutAsc(perimetre, du, au).stream()
                .map(c -> new CreneauDetail(c, Occupation.dans(prises, c)))
                .toList();
    }

    @Transactional
    public Creneau ouvrirCreneau(String perimetre, LocalDate jour, java.time.LocalTime debut, java.time.LocalTime fin, int capacite) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        access.exigerPerimetre(perimetre);
        if (jour.isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("Impossible d'ouvrir un créneau dans le passé.");
        }
        if (capacite < 1 || capacite > 20) {
            throw DomainException.invalide("Capacité entre 1 et 20.");
        }
        return creneaux.saveAndFlush(new Creneau(perimetre, jour, debut, fin, capacite));
    }

    /**
     * Grille métier retours v1 (créneaux fixes Sky / Kids) sur une plage de jours.
     * Sky : lun–jeu / sam–dim 10h &amp; 15h ; ven 9h30 &amp; 14h30.
     * Kids : mer 15h ; ven–dim 10h, 12h &amp; 15h.
     */
    @Transactional
    public int ouvrirGrilleMetier(String perimetre, LocalDate du, LocalDate au, int capacite) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        access.exigerPerimetre(perimetre);
        return genererGrilleMetier(perimetre, du, au, capacite);
    }

    /**
     * Ouverture de la grille métier sans contrôle d'accès (seed / tâches système). Idempotent : n'ajoute que les
     * heures absentes, ignore les jours passés, ne touche jamais aux créneaux existants.
     */
    @Transactional
    public int genererGrilleMetier(String perimetre, LocalDate du, LocalDate au, int capacite) {
        if (au.isBefore(du)) {
            throw DomainException.invalide("Période invalide.");
        }
        if (capacite < 1 || capacite > 20) {
            throw DomainException.invalide("Capacité entre 1 et 20.");
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        int n = 0;
        for (LocalDate jour = du; !jour.isAfter(au); jour = jour.plusDays(1)) {
            if (jour.isBefore(aujourdhui)) {
                continue;
            }
            var existants = creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour).stream()
                    .map(Creneau::getHeureDebut)
                    .collect(java.util.stream.Collectors.toSet());
            for (LocalTime debut : heuresMetier(perimetre, jour.getDayOfWeek())) {
                if (existants.contains(debut)) {
                    continue;
                }
                creneaux.save(new Creneau(perimetre, jour, debut, debut.plusHours(1), capacite));
                n++;
            }
        }
        if (n > 0) {
            creneaux.flush();
        }
        return n;
    }

    private static java.util.List<LocalTime> heuresMetier(String perimetre, java.time.DayOfWeek jour) {
        String p = perimetre == null ? "" : perimetre.toUpperCase(java.util.Locale.ROOT);
        if (p.startsWith("KIDS") || p.contains("KIDS")) {
            return switch (jour) {
                case WEDNESDAY -> java.util.List.of(LocalTime.of(15, 0));
                case FRIDAY, SATURDAY, SUNDAY -> java.util.List.of(LocalTime.of(10, 0), LocalTime.of(12, 0), LocalTime.of(15, 0));
                default -> java.util.List.of();
            };
        }
        // Sky (et autres salons adultes par défaut)
        return switch (jour) {
            case FRIDAY -> java.util.List.of(LocalTime.of(9, 30), LocalTime.of(14, 30));
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, SATURDAY, SUNDAY ->
                    java.util.List.of(LocalTime.of(10, 0), LocalTime.of(15, 0));
            default -> java.util.List.of();
        };
    }

    /**
     * Grille type horaire (1 h) : crée uniquement les créneaux absents entre {@code de} (inclus) et {@code a} (exclu).
     * Ex. Sky 9→18, Kids 10→17.
     */
    @Transactional
    public int ouvrirGrille(String perimetre, LocalDate jour, int de, int a, int capacite) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        access.exigerPerimetre(perimetre);
        if (jour.isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("Impossible d'ouvrir une grille dans le passé.");
        }
        if (de < 0 || a > 24 || de >= a) {
            throw DomainException.invalide("Plage horaire invalide.");
        }
        if (capacite < 1 || capacite > 20) {
            throw DomainException.invalide("Capacité entre 1 et 20.");
        }
        var existants = creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour).stream()
                .map(Creneau::getHeureDebut)
                .collect(java.util.stream.Collectors.toSet());
        int n = 0;
        for (int h = de; h < a; h++) {
            LocalTime debut = LocalTime.of(h, 0);
            if (existants.contains(debut)) {
                continue;
            }
            creneaux.save(new Creneau(perimetre, jour, debut, LocalTime.of(h + 1, 0), capacite));
            n++;
        }
        if (n > 0) {
            creneaux.flush();
        }
        return n;
    }

    /** Ouvre ou ferme tous les créneaux d'un jour. */
    @Transactional
    public int basculerJour(String perimetre, LocalDate jour, boolean ouvert) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        access.exigerPerimetre(perimetre);
        List<Creneau> liste = creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour);
        int n = 0;
        for (Creneau c : liste) {
            if (c.isOuvert() != ouvert) {
                c.ouvrir(ouvert);
                n++;
            }
        }
        return n;
    }

    /**
     * Recopie la structure de la veille (heures + capacité) sur le jour cible, sans écraser les créneaux déjà présents.
     */
    @Transactional
    public int recopierVeille(String perimetre, LocalDate jour) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        access.exigerPerimetre(perimetre);
        if (jour.isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("Impossible de recopier sur un jour passé.");
        }
        LocalDate source = jour.minusDays(1);
        var existants = creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour).stream()
                .map(Creneau::getHeureDebut)
                .collect(java.util.stream.Collectors.toSet());
        int n = 0;
        for (Creneau s : creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, source)) {
            if (existants.contains(s.getHeureDebut())) {
                continue;
            }
            Creneau c = new Creneau(perimetre, jour, s.getHeureDebut(), s.getHeureFin(), s.getCapacite());
            c.ouvrir(s.isOuvert());
            creneaux.save(c);
            n++;
        }
        if (n > 0) {
            creneaux.flush();
        }
        return n;
    }

    @Transactional
    public CreneauDetail basculerCreneau(UUID id, boolean ouvert) {
        Creneau c = chargerCreneau(id);
        c.ouvrir(ouvert);
        return new CreneauDetail(c, occupees(c));
    }

    @Transactional
    public CreneauDetail modifierCapacite(UUID id, int capacite) {
        Creneau c = chargerCreneau(id);
        long prises = occupees(c);
        if (capacite < prises) {
            throw DomainException.invalide("Capacité trop basse : " + prises + " place(s) déjà prise(s).");
        }
        c.definirCapacite(capacite);
        return new CreneauDetail(c, prises);
    }

    /** Détail d'un créneau nouvellement créé (aucune place prise). */
    public CreneauDetail detailNouveau(Creneau c) {
        return new CreneauDetail(c, 0);
    }

    @Transactional
    public void supprimerCreneau(UUID id) {
        creneaux.delete(chargerCreneau(id));
    }

    /** Créneau + places déjà prises (RDV non annulés / absents dans l'intervalle). */
    public record CreneauDetail(Creneau creneau, long placesPrises) {
    }

    /** Données d'une prise de rendez-vous. {@code forcer} contourne les alertes soft (doublon / compétence). */
    public record NouveauRdv(String perimetre, LocalDate jour, java.time.LocalTime heure, String prestation, UUID coiffeuseId,
            UUID clientId, String clienteNom, String telephone, RendezVous.Source source, String note,
            boolean horsCreneau, boolean forcer) {
    }

    /** Créneau proposé à l'accueil / Manager pour la prise de rendez-vous. */
    public record Disponibilite(LocalTime heure, LocalTime heureFin, long placesRestantes) {
    }

    /**
     * Si une grille de créneaux ouverts existe pour le jour : l'heure doit tomber dans l'un d'eux
     * et il doit rester de la place. Sans grille (tests / jour non ouvert), l'horaire libre reste accepté.
     */
    private void exigerPlaceDansCreneau(String perimetre, LocalDate jour, LocalTime heure, UUID exclureId) {
        List<Creneau> ouverts = creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour).stream()
                .filter(Creneau::isOuvert)
                .toList();
        if (ouverts.isEmpty()) {
            return;
        }
        Creneau c = ouverts.stream()
                .filter(x -> !heure.isBefore(x.getHeureDebut()) && heure.isBefore(x.getHeureFin()))
                .findFirst()
                .orElseThrow(() -> DomainException.invalide("Choisissez un créneau ouvert à la réservation."));
        long prises = occupees(c);
        if (exclureId != null) {
            RendezVous existant = rdvs.findById(exclureId).orElse(null);
            if (existant != null && existant.getPerimetreCode().equals(perimetre) && existant.getJour().equals(jour)
                    && !LIBERES.contains(existant.getStatut())
                    && !existant.getHeure().isBefore(c.getHeureDebut()) && existant.getHeure().isBefore(c.getHeureFin())) {
                prises--;
            }
        }
        if (prises >= c.getCapacite()) {
            throw DomainException.etatInvalide("Ce créneau est complet : choisissez-en un autre.");
        }
    }

    private long occupees(Creneau c) {
        return rdvs.countByPerimetreCodeAndJourAndHeureGreaterThanEqualAndHeureLessThanAndStatutNotIn(c.getPerimetreCode(), c.getJour(),
                c.getHeureDebut(), c.getHeureFin(), LIBERES);
    }

    private static boolean coiffeuseOccupee(List<RendezVous> agenda, LocalTime debut, int dureeMin) {
        LocalTime fin = debut.plusMinutes(dureeMin);
        for (RendezVous r : agenda) {
            if (LIBERES.contains(r.getStatut())) {
                continue;
            }
            LocalTime rFin = r.getHeure().plusMinutes(r.getDureeMin());
            if (r.getHeure().isBefore(fin) && debut.isBefore(rFin)) {
                return true;
            }
        }
        return false;
    }

    /** Numéro sénégalais à 9 chiffres (indicatif +221 accepté), stocké en chiffres seuls ; vide → null. */
    private static String telephoneOuNull(String brut) {
        return Telephones.chiffres(brut);
    }

    private RendezVous charger(UUID id) {
        RendezVous r = rdvs.findById(id).orElseThrow(() -> DomainException.introuvable("Rendez-vous"));
        if (!"personnel".equals(access.utilisateur().restriction(ModuleCode.PLANNING))) {
            access.exigerPerimetre(r.getPerimetreCode());
        }
        return r;
    }

    private Creneau chargerCreneau(UUID id) {
        access.exiger(ModuleCode.PLANNING, ActionCode.G);
        Creneau c = creneaux.findById(id).orElseThrow(() -> DomainException.introuvable("Créneau"));
        access.exigerPerimetre(c.getPerimetreCode());
        return c;
    }

    private void exigerCoiffeuse(UUID coiffeuseId, String perimetre) {
        if (coiffeuseId != null && effectifs.coiffeuses(perimetre).stream().noneMatch(c -> c.id().equals(coiffeuseId))) {
            throw DomainException.invalide("Cette coiffeuse n'est pas affectée à ce salon.");
        }
    }

    /**
     * Sans coiffeuse assignée, deux RDV actifs du même salon ne peuvent pas se chevaucher
     * (contrainte {@code ex_td_rendez_vous_sans_coiffeuse}).
     */
    private void exigerPasDeChevauchementSansCoiffeuse(String perimetre, LocalDate jour, java.time.LocalTime debut,
            int dureeMin, UUID coiffeuseId, UUID exclureId) {
        if (coiffeuseId != null) {
            return;
        }
        java.time.LocalTime fin = debut.plusMinutes(dureeMin);
        for (RendezVousRepository.PlageSansCoiffeuse e : rdvs.plagesSansCoiffeuse(perimetre, jour, LIBERES)) {
            if (exclureId != null && e.id().equals(exclureId)) {
                continue;
            }
            java.time.LocalTime eFin = e.heure().plusMinutes(e.dureeMin());
            if (e.heure().isBefore(fin) && debut.isBefore(eFin)) {
                throw DomainException.etatInvalide(
                        "Un rendez-vous sans coiffeuse occupe déjà ce créneau : assignez une coiffeuse ou choisissez un autre horaire.");
            }
        }
    }

    /**
     * CLI-01 : signale un RDV déjà actif le même jour pour le même téléphone ou la même fiche.
     * Contournable avec {@code forcer=true} (confirmation in-app côté front).
     */
    private void exigerPasDeDoublon(LocalDate jour, String telephone, UUID clientId, UUID exclureId, boolean forcer) {
        if (forcer) {
            return;
        }
        Map<UUID, RendezVous> hits = new LinkedHashMap<>();
        if (telephone != null && !telephone.isBlank()) {
            String telRecherche = crypto.rechercheTelephone(telephone);
            if (telRecherche != null) {
                for (RendezVous d : rdvs.findByJourAndTelephoneRechercheAndStatutNotInOrderByHeureAsc(jour, telRecherche, HORS_DOUBLON)) {
                    hits.putIfAbsent(d.getId(), d);
                }
            }
        }
        if (clientId != null) {
            for (RendezVous d : rdvs.findByJourAndClientIdAndStatutNotInOrderByHeureAsc(jour, clientId, HORS_DOUBLON)) {
                hits.putIfAbsent(d.getId(), d);
            }
        }
        List<RendezVous> autres = new ArrayList<>();
        for (RendezVous d : hits.values()) {
            if (exclureId == null || !d.getId().equals(exclureId)) {
                autres.add(d);
            }
        }
        if (autres.isEmpty()) {
            return;
        }
        RendezVous d = autres.get(0);
        String heure = d.getHeure() == null ? "" : d.getHeure().toString().substring(0, Math.min(5, d.getHeure().toString().length()));
        throw new DomainException(HttpStatus.CONFLICT, "DOUBLON_RDV",
                "Un rendez-vous existe déjà le " + jour + (heure.isBlank() ? "" : " à " + heure) + " pour "
                        + d.getClienteNom() + " (« " + d.getStatut().name().toLowerCase().replace('_', ' ')
                        + " »). Confirmez pour en créer un autre.");
    }

    /**
     * CLI-05 : si des compétences sont renseignées pour la coiffeuse, la prestation doit en faire partie.
     * Ensemble vide = pas encore paramétré → pas de blocage. Contournable avec {@code forcer=true}.
     */
    private void exigerCompetence(UUID coiffeuseId, String prestationCode, boolean forcer) {
        if (forcer || coiffeuseId == null || prestationCode == null || prestationCode.isBlank()) {
            return;
        }
        Set<String> comps = effectifs.competences(coiffeuseId);
        if (comps.isEmpty() || comps.contains(prestationCode)) {
            return;
        }
        String libelle = referentiels.prestation(prestationCode).map(Referentiels.PrestationInfo::libelle).orElse(prestationCode);
        throw new DomainException(HttpStatus.CONFLICT, "COMPETENCE_MANQUANTE",
                "Cette coiffeuse n'a pas la compétence « " + libelle
                        + " » validée. Confirmez pour forcer l'affectation.");
    }

    Set<String> perimetres(String perimetre) {
        if (perimetre == null) {
            return access.utilisateur().perimetres();
        }
        access.exigerPerimetre(perimetre);
        return Set.of(perimetre);
    }

}
