package sn.arlettedebo.platform.qualite.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.planning.Prestations;
import sn.arlettedebo.platform.qualite.internal.domain.ActionCorrective;
import sn.arlettedebo.platform.qualite.internal.domain.AvisClient;
import sn.arlettedebo.platform.qualite.internal.domain.AvisClientRepository;
import sn.arlettedebo.platform.qualite.internal.domain.ChecklistHygiene;
import sn.arlettedebo.platform.qualite.internal.domain.ChecklistHygieneRepository;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestation;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformite;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformiteRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Reclamation;
import sn.arlettedebo.platform.qualite.internal.domain.ReclamationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Signalement;
import sn.arlettedebo.platform.qualite.internal.domain.SignalementRepository;
import sn.arlettedebo.platform.qualite.internal.domain.TicketMaintenance;
import sn.arlettedebo.platform.qualite.internal.domain.TicketMaintenanceRepository;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Saisie et pilotage qualité : contrôles de prestations, non-conformités et actions correctives,
 * réclamations, avis, signalements, check-lists d'hygiène et incidents de maintenance.
 * <p>Saisie : qualité S (Coordinatrice, Manager ; coiffeuse limitée aux signalements).
 * Pilotage (NC, actions, traitement des réclamations, incidents) : qualité G (Manager, DO, Coordinatrice).
 * Un contrôle non conforme ou une check-list d'hygiène non conforme ouvre automatiquement une non-conformité.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QualiteGestionService {

    private final ControlePrestationRepository controles;
    private final NonConformiteRepository ncs;
    private final ReclamationRepository reclamations;
    private final AvisClientRepository avis;
    private final SignalementRepository signalements;
    private final ChecklistHygieneRepository checklists;
    private final TicketMaintenanceRepository tickets;
    private final Effectifs effectifs;
    private final Prestations prestations;
    private final Referentiels referentiels;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ------------------------------------------------------------------ Contrôles de prestations

    /** Grille de contrôle notée de 1 à 5 ; une note sous 3 rend le contrôle non conforme et ouvre une NC. */
    public ControlePrestation controler(String perimetre, String prestation, UUID coiffeuseId, UUID rendezVousId,
            Notes notes, String commentaire) {
        exigerSaisie(perimetre);
        notes.verifier();
        // Contrôle d'un rendez-vous terminé : prestation et coiffeuse viennent du planning (et la prestation est marquée vérifiée)
        Prestations.PrestationRealisee rdv = rendezVousId == null ? null : prestations.verifier(rendezVousId, notes.minimum() >= 3);
        if (rdv != null && !rdv.perimetre().equals(perimetre)) {
            throw DomainException.invalide("Le rendez-vous contrôlé relève d'un autre salon.");
        }
        String codePrestation = rdv == null ? prestation : rdv.prestation();
        UUID coiffeuse = rdv == null ? coiffeuseId : rdv.coiffeuseId();
        if (coiffeuse != null && effectifs.coiffeuses(perimetre).stream().noneMatch(c -> c.id().equals(coiffeuse))) {
            throw DomainException.invalide("Cette coiffeuse n'est pas affectée à ce salon.");
        }
        CurrentUser u = access.utilisateur();
        boolean parDelegation = !u.delegations().isEmpty() && !u.peut(ModuleCode.QUALITE, ActionCode.G);
        ControlePrestation c = controles.save(new ControlePrestation(new ControlePrestation.Donnees(perimetre, clock.instant(),
                rendezVousId, codePrestation, coiffeuse, u.id(), notes.accueil(), notes.technique(), notes.hygiene(), notes.delai(),
                vide(commentaire), parDelegation)));
        if (!c.isConforme()) {
            NonConformite.Gravite gravite = notes.minimum() == 1 ? NonConformite.Gravite.MAJEURE : NonConformite.Gravite.MINEURE;
            ncs.save(new NonConformite(perimetre, LocalDate.now(clock), NonConformite.Origine.CONTROLE,
                    "Contrôle non conforme · " + codePrestation + (commentaire == null || commentaire.isBlank() ? "" : " · " + commentaire.trim()),
                    gravite, c.getId(), u.id()));
        }
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, c.isConforme() ? "Contrôle conforme" : "Contrôle non conforme",
                perimetre + " · " + codePrestation));
        return c;
    }

    /** Notes de la grille (1 à 5). */
    public record Notes(int accueil, int technique, int hygiene, int delai) {

        void verifier() {
            for (int n : new int[] {accueil, technique, hygiene, delai}) {
                if (n < 1 || n > 5) {
                    throw DomainException.invalide("Chaque note va de 1 à 5.");
                }
            }
        }

        int minimum() {
            return Math.min(Math.min(accueil, technique), Math.min(hygiene, delai));
        }
    }

    // ------------------------------------------------------------------ Non-conformités et actions correctives

    public NonConformite declarerNc(String perimetre, NonConformite.Origine origine, NonConformite.Gravite gravite, String description) {
        exigerGestion(perimetre);
        NonConformite nc = ncs.save(new NonConformite(perimetre, LocalDate.now(clock), origine, description.trim(), gravite, null,
                access.utilisateur().id()));
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Non-conformité déclarée", perimetre + " · " + gravite + " · " + description.trim()));
        return nc;
    }

    public NonConformite planifierAction(UUID ncId, String libelle, UUID responsable, LocalDate echeance) {
        NonConformite nc = chargerNc(ncId);
        exigerGestion(nc.getPerimetreCode());
        if (echeance != null && echeance.isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("L'échéance ne peut pas être dans le passé.");
        }
        nc.planifier(libelle.trim(), responsable, echeance);
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Action corrective planifiée", libelle.trim()));
        return nc;
    }

    /** Réalisation : par le responsable désigné ou la gestion qualité. */
    public NonConformite realiserAction(UUID actionId) {
        NonConformite nc = ncDeLAction(actionId);
        ActionCorrective a = action(nc, actionId);
        CurrentUser u = access.utilisateur();
        if (!u.id().equals(a.getResponsableId())) {
            exigerGestion(nc.getPerimetreCode());
        }
        a.realiser();
        return nc;
    }

    /** Vérification indépendante : jamais par le responsable de l'action (règle du domaine). */
    public NonConformite verifierAction(UUID actionId) {
        NonConformite nc = ncDeLAction(actionId);
        exigerGestion(nc.getPerimetreCode());
        action(nc, actionId).verifier(access.utilisateur().id(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Action corrective vérifiée", action(nc, actionId).getLibelle()));
        return nc;
    }

    public NonConformite cloturerNc(UUID ncId) {
        NonConformite nc = chargerNc(ncId);
        exigerGestion(nc.getPerimetreCode());
        nc.cloturer(access.utilisateur().id(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Non-conformité clôturée", nc.getDescription()));
        return nc;
    }

    // ------------------------------------------------------------------ Réclamations et avis

    public Reclamation enregistrerReclamation(Reclamation.Donnees d) {
        exigerSaisie(d.perimetre());
        Reclamation r = reclamations.save(new Reclamation(new Reclamation.Donnees(d.perimetre(), clock.instant(), d.clientId(),
                d.clienteNom(), d.rendezVousId(), d.canal(), d.motif(), d.gravite(), vide(d.actionImmediate()))));
        if (d.gravite() != NonConformite.Gravite.MINEURE) {
            ncs.save(new NonConformite(d.perimetre(), LocalDate.now(clock), NonConformite.Origine.RECLAMATION,
                    "Réclamation · " + d.clienteNom() + " · " + d.motif(), d.gravite(), null, access.utilisateur().id()));
        }
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Réclamation enregistrée", d.perimetre() + " · " + d.clienteNom() + " · " + d.motif()));
        return r;
    }

    public Reclamation prendreEnCharge(UUID id) {
        Reclamation r = chargerReclamation(id);
        r.prendreEnCharge(access.utilisateur().id());
        return r;
    }

    public Reclamation resoudre(UUID id, String reponse) {
        Reclamation r = chargerReclamation(id);
        if (reponse == null || reponse.isBlank()) {
            throw DomainException.invalide("La réponse apportée à la cliente est obligatoire.");
        }
        r.resoudre(access.utilisateur().id(), reponse.trim(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Réclamation résolue", r.getClienteNom() + " · " + reponse.trim()));
        return r;
    }

    public AvisClient enregistrerAvis(String perimetre, UUID clientId, String cliente, int note, String commentaire) {
        exigerSaisie(perimetre);
        if (note < 1 || note > 5) {
            throw DomainException.invalide("La note va de 1 à 5.");
        }
        return avis.save(new AvisClient(new AvisClient.Donnees(perimetre, clock.instant(), clientId, vide(cliente), null, note,
                vide(commentaire), access.utilisateur().id())));
    }

    // ------------------------------------------------------------------ Signalements (coiffeuses comprises)

    public Signalement signaler(String perimetre, String type, String description) {
        access.exiger(ModuleCode.QUALITE, ActionCode.S);
        access.exigerPerimetre(perimetre);
        referentiels.exiger(TypeReferentiel.TYPE_SIGNALEMENT, type);
        Signalement s = signalements.save(new Signalement(perimetre, clock.instant(), access.utilisateur().id(), type, description.trim()));
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Signalement", perimetre + " · " + type + " · " + description.trim()));
        return s;
    }

    public Signalement avancerSignalement(UUID id, Signalement.Statut cible, String commentaire) {
        Signalement s = signalements.findById(id).orElseThrow(() -> DomainException.introuvable("Signalement"));
        exigerGestion(s.getPerimetreCode());
        CurrentUser moi = access.utilisateur();
        var t = s.avancer(cible, moi.id(), clock.instant(), commentaire);
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Traitement signalement",
                s.getTypeCode() + " · " + t.getStatutAvant() + " → " + t.getStatutApres() + " · " + moi.nom()));
        return s;
    }

    // ------------------------------------------------------------------ Hygiène

    @Transactional(readOnly = true)
    public List<ChecklistHygiene> checklists(String perimetre, LocalDate jour) {
        access.exiger(ModuleCode.QUALITE, ActionCode.C);
        access.exigerPerimetre(perimetre);
        return checklists.findByPerimetreCodeAndJourOrderByMomentAsc(perimetre, jour);
    }

    /** Check-list du jour (une par salon, moment et zone) — reprise si elle existe déjà. */
    public ChecklistHygiene ouvrirChecklist(String perimetre, ChecklistHygiene.Moment moment, String zone) {
        exigerSaisie(perimetre);
        LocalDate jour = LocalDate.now(clock);
        String z = zone == null || zone.isBlank() ? "Salon" : zone.trim();
        return checklists.findByPerimetreCodeAndJourOrderByMomentAsc(perimetre, jour).stream()
                .filter(c -> c.getMoment() == moment && c.getZone().equalsIgnoreCase(z)).findFirst()
                .orElseGet(() -> checklists.save(new ChecklistHygiene(perimetre, jour, moment, z, access.utilisateur().id())));
    }

    public ChecklistHygiene cocher(UUID checklistId, String item, boolean conforme, String commentaire) {
        ChecklistHygiene c = chargerChecklist(checklistId);
        exigerSaisie(c.getPerimetreCode());
        Set<String> items = referentiels.checklist(c.getMoment().name()).stream().map(Referentiels.ItemChecklist::code).collect(Collectors.toSet());
        if (!items.contains(item)) {
            throw DomainException.invalide("Point de contrôle inconnu pour ce moment : " + item);
        }
        if (!conforme && (commentaire == null || commentaire.isBlank())) {
            throw DomainException.invalide("Un point non conforme doit être commenté.");
        }
        c.cocher(item, conforme, vide(commentaire));
        return c;
    }

    /** Validation : tous les points du moment renseignés ; un point non conforme ouvre une non-conformité d'hygiène. */
    public ChecklistHygiene validerChecklist(UUID checklistId) {
        ChecklistHygiene c = chargerChecklist(checklistId);
        exigerSaisie(c.getPerimetreCode());
        Set<String> attendus = referentiels.checklist(c.getMoment().name()).stream().map(Referentiels.ItemChecklist::code)
                .collect(Collectors.toSet());
        Set<String> faits = c.getLignes().stream().map(l -> l.getItemCode()).collect(Collectors.toSet());
        if (!faits.containsAll(attendus)) {
            throw DomainException.invalide("Tous les points de la check-list doivent être renseignés avant validation.");
        }
        c.valider(clock.instant());
        if (!c.conforme()) {
            String points = c.getLignes().stream().filter(l -> !l.isConforme())
                    .map(l -> l.getItemCode() + (l.getCommentaire() == null ? "" : " (" + l.getCommentaire() + ")"))
                    .collect(Collectors.joining(", "));
            ncs.save(new NonConformite(c.getPerimetreCode(), c.getJour(), NonConformite.Origine.HYGIENE,
                    "Hygiène " + c.getMoment().name().toLowerCase() + " · " + c.getZone() + " · " + points, NonConformite.Gravite.MINEURE,
                    null, access.utilisateur().id()));
        }
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, c.conforme() ? "Check-list hygiène conforme" : "Check-list hygiène non conforme",
                c.getPerimetreCode() + " · " + c.getMoment() + " · " + c.getZone()));
        return c;
    }

    // ------------------------------------------------------------------ Incidents de maintenance

    @Transactional(readOnly = true)
    public List<TicketMaintenance> incidents(boolean ouvertsSeulement) {
        access.exiger(ModuleCode.QUALITE, ActionCode.C);
        Set<String> perimetres = access.utilisateur().perimetres();
        return ouvertsSeulement ? tickets.findByPerimetreCodeInAndStatutNot(perimetres, TicketMaintenance.Statut.VALIDE)
                : tickets.findByPerimetreCodeInOrderByCreeLeDesc(perimetres);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketMaintenance> incidents(boolean ouvertsSeulement, org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.QUALITE, ActionCode.C);
        Set<String> perimetres = access.utilisateur().perimetres();
        return ouvertsSeulement ? tickets.findByPerimetreCodeInAndStatutNot(perimetres, TicketMaintenance.Statut.VALIDE, page)
                : tickets.findByPerimetreCodeIn(perimetres, page);
    }

    public TicketMaintenance declarerIncident(String perimetre, String categorie, String local, String probleme, TicketMaintenance.Urgence urgence) {
        exigerSaisie(perimetre);
        String lieu = vide(local) == null ? "Non précisé" : local.trim();
        TicketMaintenance t = tickets.save(new TicketMaintenance(perimetre, categorie.trim(), lieu, probleme.trim(),
                urgence == null ? TicketMaintenance.Urgence.NORMALE : urgence, access.utilisateur().id()));
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Incident déclaré", perimetre + " · " + categorie + " · " + probleme.trim()));
        return t;
    }

    /** Suivi : assignation (prestataire / agent), démarrage, résolution, validation par la gestion qualité. */
    public TicketMaintenance avancerIncident(UUID id, String etape, UUID assigneA) {
        TicketMaintenance t = tickets.findById(id).orElseThrow(() -> DomainException.introuvable("Incident"));
        exigerGestion(t.getPerimetreCode());
        switch (etape) {
            case "ASSIGNER" -> t.assigner(assigneA);
            case "DEMARRER" -> t.demarrer();
            case "RESOUDRE" -> t.resoudre(clock.instant());
            case "VALIDER" -> t.valider(access.utilisateur().id(), clock.instant());
            default -> throw DomainException.invalide("Étape inconnue : " + etape);
        }
        events.publishEvent(AuditEvent.of(ModuleCode.QUALITE, "Incident : " + etape.toLowerCase(), t.getCategorie() + " · " + t.getProbleme()));
        return t;
    }

    // ------------------------------------------------------------------ Outils

    private void exigerSaisie(String perimetre) {
        access.exiger(ModuleCode.QUALITE, ActionCode.S);
        if ("limité".equals(access.utilisateur().restriction(ModuleCode.QUALITE))) {
            throw new org.springframework.security.access.AccessDeniedException("Saisie qualité limitée aux signalements.");
        }
        access.exigerPerimetre(perimetre);
    }

    private void exigerGestion(String perimetre) {
        access.exiger(ModuleCode.QUALITE, ActionCode.G);
        access.exigerPerimetre(perimetre);
    }

    private NonConformite chargerNc(UUID id) {
        return ncs.findById(id).orElseThrow(() -> DomainException.introuvable("Non-conformité"));
    }

    private NonConformite ncDeLAction(UUID actionId) {
        return ncs.findByActionId(actionId).orElseThrow(() -> DomainException.introuvable("Action corrective"));
    }

    private static ActionCorrective action(NonConformite nc, UUID actionId) {
        return nc.getActions().stream().filter(a -> a.getId().equals(actionId)).findFirst()
                .orElseThrow(() -> DomainException.introuvable("Action corrective"));
    }

    private Reclamation chargerReclamation(UUID id) {
        Reclamation r = reclamations.findById(id).orElseThrow(() -> DomainException.introuvable("Réclamation"));
        exigerGestion(r.getPerimetreCode());
        return r;
    }

    private ChecklistHygiene chargerChecklist(UUID id) {
        return checklists.findById(id).orElseThrow(() -> DomainException.introuvable("Check-list d'hygiène"));
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
