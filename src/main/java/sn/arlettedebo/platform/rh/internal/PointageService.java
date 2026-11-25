package sn.arlettedebo.platform.rh.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.referentiel.Parametres;
import sn.arlettedebo.platform.rh.internal.domain.Employe;
import sn.arlettedebo.platform.rh.internal.domain.EmployeRepository;
import sn.arlettedebo.platform.rh.internal.domain.Pointage;
import sn.arlettedebo.platform.rh.internal.domain.PointageRepository;
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Pointage par QR, entièrement côté serveur :
 * <ol>
 *   <li>la borne (Manager / accueil / RH) affiche un QR signé unique pour tout le site, qui change à chaque rotation ;</li>
 *   <li>tout employé(e) connecté(e) scanne ce QR avec son téléphone ;</li>
 *   <li>le serveur vérifie la signature et la fraîcheur, puis enregistre l'arrivée, la pause ou le départ.</li>
 * </ol>
 * Sans QR valide affiché sur place, aucun pointage n'est possible (pas de pointage à distance). Le salon n'intervient pas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PointageService {

    private final PointageRepository pointages;
    private final EmployeRepository employes;
    private final RhService rh;
    private final JetonBorne jetons;
    private final Parametres parametres;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** QR courant de la borne (même jeton pour tout le monde) — jamais accessible au personnel qui pointe. */
    @Transactional(readOnly = true)
    public JetonBorne.Emis borne() {
        CurrentUser u = access.utilisateur();
        boolean borne = (u.peut(ModuleCode.RH, ActionCode.C) || u.peut(ModuleCode.PLANNING, ActionCode.G))
                && !"personnel".equals(u.restriction(ModuleCode.PLANNING));
        if (!borne) {
            throw new AccessDeniedException("L'affichage de la borne de pointage est réservé à la Manager, à l'accueil et aux RH.");
        }
        return jetons.emettre(clock.instant(), rotation());
    }

    /** Scan du QR par l'employé(e) connecté(e) : action enregistrée sur son propre dossier. */
    public Pointage scanner(String jeton, Pointage.Action action) {
        Employe e = monDossier();
        Instant maintenant = clock.instant();
        jetons.verifier(jeton, maintenant, rotation());
        LocalDate jour = LocalDate.now(clock);
        Pointage p = pointages.findByEmployeIdAndJour(e.getId(), jour)
                .orElseGet(() -> pointages.save(new Pointage(e.getId(), jour, e.getHeurePrisePoste(), e.getHeureFinPoste())));
        if (!p.actionsPossibles().contains(action)) {
            throw DomainException.etatInvalide("Action impossible dans l'état actuel (" + p.statut().name().toLowerCase().replace('_', ' ') + ").");
        }
        switch (action) {
            case ARRIVEE -> p.arriver(maintenant, AuditingConfig.ZONE, parametres.entier(Parametres.TOLERANCE_RETARD));
            case PAUSE_DEBUT -> p.commencerPause(maintenant);
            case PAUSE_FIN -> p.finirPause(maintenant);
            case DEPART -> p.partir(maintenant, AuditingConfig.ZONE, parametres.entier(Parametres.TOLERANCE_DEPART));
        }
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Pointage " + action.name().toLowerCase().replace('_', ' '),
                e.getNom() + detailEcart(action, p)));
        return p;
    }

    /** Ma présence du jour (vide si aucun pointage). */
    @Transactional(readOnly = true)
    public Presence maPresence() {
        Employe e = monDossier();
        Optional<Pointage> p = pointages.findByEmployeIdAndJour(e.getId(), LocalDate.now(clock));
        return new Presence(e.getId(), e.getNom(), e.getPerimetreCode(), e.getHeurePrisePoste().toString(), e.getHeureFinPoste().toString(),
                p.orElse(null));
    }

    /** Tableau des présences d'un jour : tout le personnel actif visible (indépendant du salon). */
    @Transactional(readOnly = true)
    public List<Presence> equipe(LocalDate jour) {
        List<Employe> equipe = rh.employesVisibles().stream().filter(Employe::isActif).toList();
        Map<UUID, Pointage> parEmploye = pointages.findByJourAndEmployeIdIn(jour, equipe.stream().map(Employe::getId).toList()).stream()
                .collect(Collectors.toMap(Pointage::getEmployeId, Function.identity()));
        return equipe.stream()
                .map(e -> new Presence(e.getId(), e.getNom(), e.getPerimetreCode(), e.getHeurePrisePoste().toString(),
                        e.getHeureFinPoste().toString(), parEmploye.get(e.getId())))
                .toList();
    }

    // ------------------------------------------------------------------ Départs non pointés, régularisation, justification

    /**
     * Contrôle planifié (sans utilisateur connecté) : les arrivées sans départ des jours passés, et du jour une fois
     * l'heure de contrôle fixée par la DG dépassée, passent en « départ non pointé ». Aucune heure n'est inventée.
     *
     * @return nombre de journées signalées
     */
    public int signalerDepartsNonPointes() {
        java.time.LocalDateTime maintenant = java.time.LocalDateTime.now(clock);
        LocalDate jusquA = maintenant.toLocalTime().isBefore(parametres.heure(Parametres.CONTROLE_DEPART))
                ? maintenant.toLocalDate().minusDays(1) : maintenant.toLocalDate();
        int n = 0;
        for (Pointage p : pointages.findByJourLessThanEqualAndArriveeLeIsNotNullAndDepartLeIsNullAndDepartNonPointeFalse(jusquA)) {
            if (p.signalerDepartNonPointe()) {
                n++;
                String nom = employes.findById(p.getEmployeId()).map(Employe::getNom).orElse("—");
                events.publishEvent(AuditEvent.of(ModuleCode.RH, "Départ non pointé", nom + " · " + p.getJour()));
            }
        }
        return n;
    }

    /** Départs non pointés à régulariser, du plus ancien au plus récent (Manager : ses salons ; Administration). */
    @Transactional(readOnly = true)
    public List<Presence> aRegulariser() {
        exigerGestionPresences();
        Map<UUID, Employe> visibles = rh.employesVisibles().stream().collect(Collectors.toMap(Employe::getId, Function.identity()));
        return pointages.findByDepartNonPointeTrueAndRegulariseParIsNullAndEmployeIdInOrderByJourAsc(visibles.keySet()).stream()
                .map(p -> {
                    Employe e = visibles.get(p.getEmployeId());
                    return new Presence(e.getId(), e.getNom(), e.getPerimetreCode(), p.getHeurePrevue().toString(),
                            e.getHeureFinPoste().toString(), p);
                }).toList();
    }

    /** Régularisation d'un départ oublié : heure réelle et motif obligatoire, tracés au journal. */
    public Pointage regulariserDepart(UUID pointageId, java.time.LocalTime heure, String motif) {
        Pointage p = pointageGerable(pointageId);
        p.regulariserDepart(heure, AuditingConfig.ZONE, parametres.entier(Parametres.TOLERANCE_DEPART), access.utilisateur().id(), motif,
                clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Départ régularisé", nomDe(p) + " · " + p.getJour() + " · départ " + heure
                + (p.getDepartAnticipeMin() > 0 ? " (anticipé de " + p.getDepartAnticipeMin() + " min)" : "") + " · motif : " + motif.trim()));
        return p;
    }

    /** Justification d'un retard ou d'un départ anticipé. */
    public Pointage justifier(UUID pointageId, String motif) {
        Pointage p = pointageGerable(pointageId);
        p.justifier(motif);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Pointage justifié", nomDe(p) + " · " + p.getJour() + " · " + motif.trim()));
        return p;
    }

    /** Gestion des présences : Manager (ses salons) ou Administration (gestion RH). */
    private void exigerGestionPresences() {
        CurrentUser u = access.utilisateur();
        if (!u.peut(ModuleCode.RH, ActionCode.G) && !u.estProfil(Profils.MANAGER)) {
            throw new AccessDeniedException("Régularisation des présences : Manager du salon ou Administration.");
        }
    }

    private Pointage pointageGerable(UUID id) {
        exigerGestionPresences();
        Pointage p = pointages.findById(id).orElseThrow(() -> DomainException.introuvable("Pointage"));
        if (!rh.nomsVisibles().containsKey(p.getEmployeId())) {
            throw DomainException.introuvable("Pointage");
        }
        return p;
    }

    private String nomDe(Pointage p) {
        return employes.findById(p.getEmployeId()).map(Employe::getNom).orElse("—");
    }

    private Employe monDossier() {
        return employes.findByUtilisateurId(access.utilisateur().id())
                .orElseThrow(() -> DomainException.etatInvalide("Aucun dossier du personnel n'est lié à votre compte."));
    }

    private int rotation() {
        return Math.clamp(parametres.entier(Parametres.ROTATION_QR), 30, 300);
    }

    /** Présence correspondant à un pointage (après une régularisation ou une justification). */
    @Transactional(readOnly = true)
    public Presence presence(Pointage p) {
        Employe e = employes.findById(p.getEmployeId()).orElseThrow(() -> DomainException.introuvable("Employé"));
        return new Presence(e.getId(), e.getNom(), e.getPerimetreCode(), p.getHeurePrevue().toString(), e.getHeureFinPoste().toString(), p);
    }

    private static String detailEcart(Pointage.Action action, Pointage p) {
        if (action == Pointage.Action.ARRIVEE && p.getRetardMin() > 0) {
            return " · retard " + p.getRetardMin() + " min";
        }
        if (action == Pointage.Action.DEPART && p.getDepartAnticipeMin() > 0) {
            return " · départ anticipé de " + p.getDepartAnticipeMin() + " min";
        }
        return "";
    }

    /** Présence d'un(e) employé(e) un jour donné ({@code pointage} nul : rien de pointé). */
    public record Presence(UUID employeId, String nom, String perimetre, String heurePrevue, String heureFin, Pointage pointage) {
    }
}
