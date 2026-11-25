package sn.arlettedebo.platform.rh.internal;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.referentiel.Parametres;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.rh.internal.domain.Absence;
import sn.arlettedebo.platform.rh.internal.domain.AbsenceRepository;
import sn.arlettedebo.platform.rh.internal.domain.Employe;
import sn.arlettedebo.platform.rh.internal.domain.EmployeRepository;
import sn.arlettedebo.platform.rh.internal.domain.Evaluation;
import sn.arlettedebo.platform.rh.internal.domain.EvaluationRepository;
import sn.arlettedebo.platform.rh.internal.domain.Pointage;
import sn.arlettedebo.platform.rh.internal.domain.PointageRepository;
import sn.arlettedebo.platform.rh.internal.domain.VariablePaie;
import sn.arlettedebo.platform.rh.internal.domain.VariablePaieRepository;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Équipe & RH. Règles appliquées :
 * <ul>
 *   <li>Manager (RH « C limité ») : personnel de ses salons, sans données de rémunération ;</li>
 *   <li>salaires et variables de paie : gestion RH ({@code rh:G}) ou DG uniquement ;</li>
 *   <li>absences &amp; congés : consultation et saisie / décision réservées à DG, DO et Manager ;</li>
 *   <li>évaluations : chaque employé est noté uniquement par son N+1
 *       (équipe salon → Manager ; Manager / transverse → DO ; DO → DG).</li>
 * </ul>
 */
@Service
@Transactional
public class RhService {

    private static final String RESTRICTION_LIMITE = "limité";

    /** Type d'absence « jour OFF » (repos planifié) soumis aux règles hebdomadaires. */
    private static final String TYPE_OFF = "OFF";

    /** Profils dont le N+1 est la DO (hors équipe terrain rattachée à un Manager). */
    private static final Set<String> N_PLUS_UN_DO = Set.of(
            Profils.MANAGER, Profils.COORDINATION, Profils.INVENTAIRE, Profils.COMMUNITY_MANAGER, Profils.ADMINISTRATION);

    private final EmployeRepository employes;
    private final AbsenceRepository absences;
    private final VariablePaieRepository variables;
    private final EvaluationRepository evaluations;
    private final PointageRepository pointages;
    private final AccessPolicy access;
    private final UserDirectory annuaire;
    private final Referentiels referentiels;
    private final ApplicationEventPublisher events;
    private final Parametres parametres;
    private final Clock clock;
    private final RhService self;

    public RhService(EmployeRepository employes, AbsenceRepository absences, VariablePaieRepository variables,
            EvaluationRepository evaluations, PointageRepository pointages, AccessPolicy access, UserDirectory annuaire,
            Referentiels referentiels, ApplicationEventPublisher events, Parametres parametres, Clock clock,
            @Lazy RhService self) {
        this.employes = employes;
        this.absences = absences;
        this.variables = variables;
        this.evaluations = evaluations;
        this.pointages = pointages;
        this.access = access;
        this.annuaire = annuaire;
        this.referentiels = referentiels;
        this.events = events;
        this.parametres = parametres;
        this.clock = clock;
        this.self = self;
    }

    // ------------------------------------------------------------------ Dossiers

    /** Dossiers visibles (Manager : ses salons), compétences comprises — une seule requête. */
    @Transactional(readOnly = true)
    public List<Employe> employesVisibles() {
        CurrentUser u = access.utilisateur();
        access.exiger(ModuleCode.RH, ActionCode.C);
        return limite(u) ? employes.dossiersDesPerimetres(u.perimetres()) : employes.dossiersTous();
    }

    /**
     * Dossiers visibles filtrés : recherche (nom, matricule, poste), salon, présence dans les effectifs.
     * Liste bornée (personnel) : filtrée en mémoire après le chargement en une requête avec les compétences.
     */
    @Transactional(readOnly = true)
    public List<Employe> employesVisibles(String recherche, String perimetre, Boolean actif) {
        return self.employesVisibles().stream()
                .filter(e -> sn.arlettedebo.platform.shared.web.Recherche.correspond(recherche, e.getNom(), e.getMatricule(), e.getPoste()))
                .filter(e -> perimetre == null || perimetre.isBlank() || perimetre.equals(e.getPerimetreCode()))
                .filter(e -> actif == null || e.isActif() == actif)
                .toList();
    }

    /**
     * Dossier minimal d'un compte qui n'en a pas (création de compte, rattrapage au démarrage) : poste = libellé du profil,
     * salon = celui du compte s'il n'en a qu'un, horaires par défaut. L'Administration le complète ensuite (contrat, salaire…).
     * Sans contrôle de droits : déclenché par le système, jamais par une requête.
     *
     * @return le dossier créé, ou vide si le compte en a déjà un
     */
    public java.util.Optional<Employe> creerDossierAutomatique(sn.arlettedebo.platform.iam.UserDirectory.UserRef u, String poste) {
        if (employes.existsByUtilisateurId(u.id())) {
            return java.util.Optional.empty();
        }
        String perimetre = u.perimetres().size() == 1 ? u.perimetres().iterator().next() : null;
        String matricule = "AD-" + Year.now(clock) + "-" + String.format("%04d", employes.prochainNumero());
        Employe e = employes.save(new Employe(matricule, new Employe.Dossier(u.nom(), poste, null, perimetre, null, LocalDate.now(clock),
                Employe.Contrat.CDI, 0L, null, parametres.heure(Parametres.HEURE_FIN_DEFAUT), u.id())));
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Dossier créé automatiquement", e.getMatricule() + " · " + e.getNom() + " · " + poste));
        return java.util.Optional.of(e);
    }

    /** Noms des employés visibles (projection id + nom, sans charger les dossiers). */
    @Transactional(readOnly = true)
    public Map<UUID, String> nomsVisibles() {
        return noms();
    }

    private Map<UUID, String> noms() {
        CurrentUser u = access.utilisateur();
        access.exiger(ModuleCode.RH, ActionCode.C);
        var liste = limite(u) ? employes.nomsDesPerimetres(u.perimetres()) : employes.nomsTous();
        Map<UUID, String> out = new java.util.LinkedHashMap<>();
        liste.forEach(n -> out.put(n.getId(), n.getNom()));
        return out;
    }

    private static boolean limite(CurrentUser u) {
        return RESTRICTION_LIMITE.equals(u.restriction(ModuleCode.RH));
    }

    public boolean peutVoirPaie() {
        return access.peut(ModuleCode.RH, ActionCode.G) || access.estProfil(Profils.DG, Profils.MANAGER);
    }

    /** Absences & congés : DG, DO et Manager uniquement (pas l'Administration). */
    public boolean peutVoirAbsences() {
        return access.estProfil(Profils.DG, Profils.DO, Profils.MANAGER);
    }

    public Employe creer(Employe.Dossier d) {
        access.exiger(ModuleCode.RH, ActionCode.G);
        if (d.perimetreCode() != null) {
            referentiels.exiger(TypeReferentiel.PERIMETRE, d.perimetreCode());
        }
        String matricule = "AD-" + Year.now(clock) + "-" + String.format("%04d", employes.prochainNumero());
        if (d.heureFinPoste() == null) {
            d = new Employe.Dossier(d.nom(), d.poste(), d.grade(), d.perimetreCode(), d.telephone(), d.dateEntree(), d.contrat(),
                    d.salaireBase(), d.heurePrisePoste(), parametres.heure(Parametres.HEURE_FIN_DEFAUT), d.utilisateurId());
        }
        Employe e = employes.save(new Employe(matricule, d));
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Dossier créé", e.getMatricule() + " · " + e.getNom()));
        return e;
    }

    public Employe modifier(UUID id, Employe.Dossier d) {
        access.exiger(ModuleCode.RH, ActionCode.G);
        if (d.perimetreCode() != null) {
            referentiels.exiger(TypeReferentiel.PERIMETRE, d.perimetreCode());
        }
        Employe e = employe(id);
        e.modifier(d);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Dossier modifié", e.getNom()));
        return e;
    }

    public Employe sortir(UUID id, LocalDate le) {
        access.exiger(ModuleCode.RH, ActionCode.G);
        Employe e = employe(id);
        e.sortir(le == null ? LocalDate.now(clock) : le);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Sortie des effectifs", e.getNom()));
        return e;
    }

    public Employe reintegrer(UUID id) {
        access.exiger(ModuleCode.RH, ActionCode.G);
        Employe e = employe(id);
        e.reintegrer();
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Réintégration", e.getNom()));
        return e;
    }

    /** Matrice des compétences : alimente l'affectation des prestations au planning. */
    public Employe definirCompetence(UUID id, String prestation, int niveau) {
        access.exiger(ModuleCode.RH, ActionCode.G);
        referentiels.exiger(TypeReferentiel.PRESTATION, prestation);
        if (niveau < 1 || niveau > 3) {
            throw DomainException.invalide("Niveau de compétence attendu entre 1 et 3.");
        }
        Employe e = employe(id);
        e.definirCompetence(prestation, (short) niveau, access.utilisateur().id(), LocalDate.now(clock));
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Compétence validée", e.getNom() + " · " + prestation + " (niv. " + niveau + ")"));
        return e;
    }

    // ------------------------------------------------------------------ Absences & congés

    @Transactional(readOnly = true)
    public List<Absence> absencesVisibles() {
        exigerAbsences();
        return absences.findByEmployeIdInOrderByDuDesc(idsVisibles());
    }

    /** Absences visibles, paginées en base (plus récentes d'abord) ; filtres statut et employé facultatifs. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Absence> absencesVisibles(Absence.Statut statut, UUID employe,
            org.springframework.data.domain.Pageable page) {
        exigerAbsences();
        java.util.Collection<UUID> ids = parmiVisibles(employe);
        return statut == null ? absences.findByEmployeIdIn(ids, page) : absences.findByEmployeIdInAndStatut(ids, statut, page);
    }

    @Transactional(readOnly = true)
    public List<Absence> absentsAujourdhui() {
        exigerAbsences();
        return absences.absentsLe(LocalDate.now(clock), idsVisibles());
    }

    public Absence saisirAbsence(UUID employeId, String type, LocalDate du, LocalDate au, String motif) {
        exigerAbsences();
        Employe e = employe(employeId);
        boolean soumis = referentiels.absenceSoumiseAValidation(type);
        exigerReglesOff(e, type, du, au);
        Absence a = absences.save(new Absence(new Absence.Donnees(e.getId(), type, du, au, motif, soumis, access.utilisateur().id(), clock.instant())));
        events.publishEvent(AuditEvent.of(ModuleCode.RH, soumis ? "Demande de congé" : "Absence saisie",
                e.getNom() + " · " + type + " " + du + " → " + au));
        return a;
    }

    /**
     * Règles des jours OFF (retours v1) : DG et DO sont OFF le week-end (samedi / dimanche uniquement) ;
     * les autres profils ont au plus 1 jour OFF par semaine (lundi → dimanche).
     */
    private void exigerReglesOff(Employe e, String type, LocalDate du, LocalDate au) {
        if (!TYPE_OFF.equals(type) || au.isBefore(du)) {
            return;
        }
        String profil = profilDe(e);
        if (Profils.DG.equals(profil) || Profils.DO.equals(profil)) {
            for (LocalDate j = du; !j.isAfter(au); j = j.plusDays(1)) {
                if (j.getDayOfWeek() != DayOfWeek.SATURDAY && j.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    throw DomainException.regle("OFF_WEEKEND_DIRECTION",
                            "La Direction (DG / DO) est OFF le week-end : seuls le samedi et le dimanche sont possibles.");
                }
            }
            return;
        }
        LocalDate debutSemaine = du.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Absence> existantes = absences.chevauchant(e.getId(), TYPE_OFF, debutSemaine,
                au.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
        for (LocalDate semaine = debutSemaine; !semaine.isAfter(au); semaine = semaine.plusWeeks(1)) {
            LocalDate lundi = semaine;
            LocalDate dimanche = lundi.plusDays(6);
            long demandes = joursDansSemaine(du, au, lundi, dimanche);
            if (demandes == 0) {
                continue;
            }
            long deja = existantes.stream().mapToLong(a -> joursDansSemaine(a.getDu(), a.getAu(), lundi, dimanche)).sum();
            if (deja + demandes > 1) {
                throw DomainException.regle("OFF_UN_PAR_SEMAINE",
                        "Un seul jour OFF par semaine est autorisé (semaine du " + lundi + ").");
            }
        }
    }

    /** Nombre de jours de [du ; au] compris dans la semaine [lundi ; dimanche]. */
    private static long joursDansSemaine(LocalDate du, LocalDate au, LocalDate lundi, LocalDate dimanche) {
        LocalDate debut = du.isAfter(lundi) ? du : lundi;
        LocalDate fin = au.isBefore(dimanche) ? au : dimanche;
        return fin.isBefore(debut) ? 0 : java.time.temporal.ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    public Absence deciderAbsence(UUID id, boolean approuver) {
        exigerAbsences();
        Absence a = absences.findById(id).orElseThrow(() -> DomainException.introuvable("Absence"));
        a.decider(approuver, access.utilisateur().id(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.RH, approuver ? "Congé approuvé" : "Congé refusé", nom(a.getEmployeId())));
        return a;
    }

    // ------------------------------------------------------------------ Paie

    @Transactional(readOnly = true)
    public List<VariablePaie> variables(String mois) {
        exigerPaie();
        return variables.findByMoisOrderByCreeLeAsc(mois);
    }

    public VariablePaie saisirVariable(UUID employeId, String mois, String type, long montant, String commentaire) {
        access.exiger(ModuleCode.RH, ActionCode.G);
        referentiels.exiger(TypeReferentiel.TYPE_VARIABLE_PAIE, type);
        Employe e = employe(employeId);
        VariablePaie v = variables.save(new VariablePaie(e.getId(), mois, type, montant, commentaire, access.utilisateur().id()));
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Variable de paie saisie", e.getNom() + " · " + type + " " + montant));
        return v;
    }

    /** Transmet au traitement de la paie toutes les lignes saisies du mois. */
    public int transmettre(String mois) {
        access.exiger(ModuleCode.RH, ActionCode.G);
        List<VariablePaie> lignes = variables.findByMoisAndStatut(mois, VariablePaie.Statut.SAISIE);
        lignes.forEach(v -> v.transmettre(clock.instant()));
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Éléments variables transmis", mois + " · " + lignes.size() + " ligne(s)"));
        return lignes.size();
    }

    // ------------------------------------------------------------------ Évaluations

    @Transactional(readOnly = true)
    public List<Evaluation> evaluationsVisibles() {
        return evaluations.findByEmployeIdInOrderByDateEvaluationDesc(idsVisibles());
    }

    /** Évaluations visibles, paginées en base (plus récentes d'abord) ; filtre employé facultatif. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Evaluation> evaluationsVisibles(UUID employe, org.springframework.data.domain.Pageable page) {
        return evaluations.findByEmployeIdIn(parmiVisibles(employe), page);
    }

    /** Employés visibles, ou le seul employé demandé s'il est visible (sinon aucun : pas de fuite hors périmètre). */
    private java.util.Collection<UUID> parmiVisibles(UUID employe) {
        Set<UUID> ids = idsVisibles();
        if (employe == null) {
            return ids;
        }
        return ids.contains(employe) ? Set.of(employe) : Set.of();
    }

    /** Collaborateurs actifs que l'utilisateur courant peut noter (ses N-1). */
    @Transactional(readOnly = true)
    public List<Employe> employesEvaluables() {
        CurrentUser u = access.utilisateur();
        access.exiger(ModuleCode.RH, ActionCode.C);
        return self.employesVisibles().stream().filter(Employe::isActif).filter(e -> estNPlusUn(u, e)).toList();
    }

    /** Enregistre une évaluation : réservé au N+1 de l'employé. */
    public Evaluation evaluer(UUID employeId, int note, String pointsForts, String axes) {
        access.exiger(ModuleCode.RH, ActionCode.C);
        CurrentUser u = access.utilisateur();
        Employe e = employe(employeId);
        if (!estNPlusUn(u, e)) {
            throw DomainException.regle("EVALUATION_N_PLUS_1", "Seul le N+1 peut noter ce collaborateur.");
        }
        Evaluation ev = evaluations.save(new Evaluation(e.getId(), LocalDate.now(clock), note, pointsForts, axes, u.id()));
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Évaluation", e.getNom() + " · " + note + "/5"));
        return ev;
    }

    /**
     * Hiérarchie opérationnelle :
     * <ul>
     *   <li>équipe salon ({@link Profils#EQUIPE_SALON}) sur un salon couvert par le profil Manager → Manager ;</li>
     *   <li>équipe sur un salon hors Manager (ex. Boutique) → DO ;</li>
     *   <li>Manager, Coordination, Inventaire, Community, Administration → DO ;</li>
     *   <li>DO → DG ; DG non noté.</li>
     * </ul>
     */
    boolean estNPlusUn(CurrentUser evaluateur, Employe e) {
        if (Objects.equals(e.getUtilisateurId(), evaluateur.id())) {
            return false;
        }
        String profil = profilDe(e);
        if (Profils.DG.equals(profil)) {
            return false;
        }
        if (Profils.DO.equals(profil)) {
            return evaluateur.estProfil(Profils.DG);
        }
        boolean terrain = profil == null
                ? e.getPerimetreCode() != null
                : Profils.EQUIPE_SALON.contains(profil);
        if (terrain && e.getPerimetreCode() != null) {
            if (evaluateur.estProfil(Profils.MANAGER) && evaluateur.couvre(e.getPerimetreCode())) {
                return true;
            }
            // Salon non rattaché au profil Manager (Boutique…) → DO
            return evaluateur.estProfil(Profils.DO) && !annuaire.perimetresDuProfil(Profils.MANAGER).contains(e.getPerimetreCode());
        }
        if (profil == null || N_PLUS_UN_DO.contains(profil)) {
            return evaluateur.estProfil(Profils.DO);
        }
        return false;
    }

    private String profilDe(Employe e) {
        if (e.getUtilisateurId() == null) {
            return null;
        }
        return annuaire.parId(e.getUtilisateurId()).map(UserDirectory.UserRef::profil).orElse(null);
    }

    // ------------------------------------------------------------------ Pointage

    @Transactional(readOnly = true)
    public List<Pointage> pointagesDuJour(LocalDate jour) {
        return pointages.findByJourAndEmployeIdIn(jour, idsVisibles());
    }

    // ------------------------------------------------------------------ Outils

    private Set<UUID> idsVisibles() {
        return noms().keySet();
    }

    private void exigerPaie() {
        if (!peutVoirPaie()) {
            throw new AccessDeniedException("Données de paie : gestion RH ou DG.");
        }
    }

    private void exigerAbsences() {
        if (!peutVoirAbsences()) {
            throw new AccessDeniedException("Absences et congés : réservés à la DG, la DO et le Manager.");
        }
    }

    /** Dossier complet : la matrice des compétences est chargée (renvoyée au client après la transaction). */
    Employe employe(UUID id) {
        Employe e = employes.findById(id).orElseThrow(() -> DomainException.introuvable("Dossier du personnel"));
        Hibernate.initialize(e.getCompetences());
        return e;
    }

    private String nom(UUID employeId) {
        return employes.findById(employeId).map(Employe::getNom).orElse("—");
    }
}
