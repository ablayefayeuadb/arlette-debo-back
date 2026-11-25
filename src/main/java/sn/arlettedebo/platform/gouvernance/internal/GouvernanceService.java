package sn.arlettedebo.platform.gouvernance.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.gouvernance.internal.domain.IncidentResponsabilite;
import sn.arlettedebo.platform.gouvernance.internal.domain.IncidentResponsabiliteRepository;
import sn.arlettedebo.platform.gouvernance.internal.domain.ProcedureDoc;
import sn.arlettedebo.platform.gouvernance.internal.domain.ProcedureDocRepository;
import sn.arlettedebo.platform.gouvernance.internal.domain.ProcedureLecture;
import sn.arlettedebo.platform.gouvernance.internal.domain.ProcedureLectureRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GouvernanceService {

    private final IncidentResponsabiliteRepository incidents;
    private final ProcedureDocRepository procedures;
    private final ProcedureLectureRepository lectures;
    private final UserDirectory annuaire;
    private final JustificatifStorage storage;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public Page<IncidentResponsabilite> listerIncidents(boolean ouvertsSeulement, Pageable page) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        List<IncidentResponsabilite> tous = ouvertsSeulement
                ? incidents.findByStatutInOrderByEcheanceAscCreeLeDesc(
                        List.of(IncidentResponsabilite.Statut.OUVERT, IncidentResponsabilite.Statut.EN_COURS))
                : incidents.findAllByOrderByCreeLeDesc();
        int from = (int) page.getOffset();
        int to = Math.min(from + page.getPageSize(), tous.size());
        List<IncidentResponsabilite> tranche = from >= tous.size() ? List.of() : tous.subList(from, to);
        return new PageImpl<>(tranche, page, tous.size());
    }

    public long countOuverts() {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        return incidents.countByStatutNot(IncidentResponsabilite.Statut.CLOTURE);
    }

    @Transactional
    public IncidentResponsabilite creerIncident(String sujet, String roles, String personnes, LocalDate echeance,
            String responsableNom, UUID rendezVousId) {
        exigerEcriture();
        IncidentResponsabilite i = incidents.save(new IncidentResponsabilite(sujet, roles, personnes, echeance, null,
                responsableNom, rendezVousId));
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Incident responsabilité", i.getSujet()));
        return i;
    }

    @Transactional
    public IncidentResponsabilite majIncident(UUID id, String arbitrage, String action, LocalDate echeance,
            String responsableNom) {
        exigerEcriture();
        IncidentResponsabilite i = chargerIncident(id);
        i.mettreAJour(arbitrage, action, echeance, responsableNom);
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Incident responsabilité mis à jour", i.getSujet()));
        return i;
    }

    @Transactional
    public IncidentResponsabilite cloturerIncident(UUID id) {
        exigerEcriture();
        IncidentResponsabilite i = chargerIncident(id);
        i.cloturer(clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Incident responsabilité clôturé", i.getSujet()));
        return i;
    }

    public Page<ProcedureDoc> listerProcedures(boolean publieesSeulement, Pageable page) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        List<ProcedureDoc> tous = publieesSeulement
                ? procedures.findByStatutOrderByTitreAsc(ProcedureDoc.Statut.PUBLIE)
                : procedures.findAllByOrderByTitreAscVersionNumDesc();
        int from = (int) page.getOffset();
        int to = Math.min(from + page.getPageSize(), tous.size());
        List<ProcedureDoc> tranche = from >= tous.size() ? List.of() : tous.subList(from, to);
        return new PageImpl<>(tranche, page, tous.size());
    }

    @Transactional
    public ProcedureDoc creerProcedure(String titre, String activite, String proprietaire, String resume) {
        exigerEcriture();
        ProcedureDoc p = procedures.save(new ProcedureDoc(titre, activite, proprietaire, resume));
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Procédure créée", p.getTitre()));
        return p;
    }

    @Transactional
    public ProcedureDoc majProcedure(UUID id, String titre, String activite, String proprietaire, String resume) {
        exigerEcriture();
        ProcedureDoc p = chargerProcedure(id);
        p.mettreAJour(titre, activite, proprietaire, resume);
        return p;
    }

    @Transactional
    public ProcedureDoc attacherFichier(UUID id, MultipartFile fichier) {
        exigerEcriture();
        ProcedureDoc p = chargerProcedure(id);
        JustificatifStorage.FichierStocke f = storage.stocker("procedures", fichier);
        String nom = fichier.getOriginalFilename() == null ? "document" : fichier.getOriginalFilename();
        p.attacherFichier(f.chemin(), nom, f.typeMime(), f.taille());
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Procédure fichier", p.getTitre()));
        return p;
    }

    @Transactional
    public ProcedureDoc publier(UUID id) {
        exigerEcriture();
        ProcedureDoc p = chargerProcedure(id);
        p.publier(clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Procédure publiée", p.getTitre() + " v" + p.getVersionNum()));
        return p;
    }

    @Transactional
    public ProcedureDoc obsoletiser(UUID id) {
        exigerEcriture();
        ProcedureDoc p = chargerProcedure(id);
        p.obsoletiser();
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Procédure obsolète", p.getTitre()));
        return p;
    }

    public Resource fichierProcedure(UUID id) {
        ProcedureDoc p = chargerProcedure(id);
        boolean admin = access.utilisateur().peut(ModuleCode.ADMINISTRATION, ActionCode.C);
        if (!admin && p.getStatut() != ProcedureDoc.Statut.PUBLIE) {
            throw new org.springframework.security.access.AccessDeniedException("Seules les procédures publiées sont consultables.");
        }
        if (p.getFichierChemin() == null) {
            throw DomainException.introuvable("Fichier");
        }
        return storage.lire(p.getFichierChemin());
    }

    public ProcedureDoc detailProcedure(UUID id) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        return chargerProcedure(id);
    }

    /** DOC-01 : consultation collaborateurs — procédures publiées uniquement. */
    public Page<ProcedureDoc> listerPubliees(Pageable page) {
        access.utilisateur(); // authentifié
        List<ProcedureDoc> tous = procedures.findByStatutOrderByTitreAsc(ProcedureDoc.Statut.PUBLIE);
        int from = (int) page.getOffset();
        int to = Math.min(from + page.getPageSize(), tous.size());
        List<ProcedureDoc> tranche = from >= tous.size() ? List.of() : tous.subList(from, to);
        return new PageImpl<>(tranche, page, tous.size());
    }

    public ProcedureDoc detailPubliee(UUID id) {
        access.utilisateur();
        ProcedureDoc p = chargerProcedure(id);
        if (p.getStatut() != ProcedureDoc.Statut.PUBLIE) {
            throw DomainException.introuvable("Procédure");
        }
        return p;
    }

    /** Fichier : publié pour tous, ou brouillon pour l'admin. */
    public ProcedureDoc detailPublieeOuAdmin(UUID id) {
        ProcedureDoc p = chargerProcedure(id);
        boolean admin = access.utilisateur().peut(ModuleCode.ADMINISTRATION, ActionCode.C);
        if (!admin && p.getStatut() != ProcedureDoc.Statut.PUBLIE) {
            throw new org.springframework.security.access.AccessDeniedException("Seules les procédures publiées sont consultables.");
        }
        return p;
    }

    public UUID utilisateurCourantId() {
        return access.utilisateur().id();
    }

    /** DOC-02 : accusé de lecture. */
    @Transactional
    public ProcedureLecture accuserLecture(UUID procedureId) {
        UUID moi = access.utilisateur().id();
        ProcedureDoc p = detailPubliee(procedureId);
        return lectures.findByProcedureIdAndUtilisateurId(p.getId(), moi)
                .orElseGet(() -> {
                    ProcedureLecture l = lectures.save(new ProcedureLecture(p.getId(), moi, clock.instant()));
                    events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Procédure lue", p.getTitre()));
                    return l;
                });
    }

    public boolean aLu(UUID procedureId, UUID utilisateurId) {
        return lectures.findByProcedureIdAndUtilisateurId(procedureId, utilisateurId).isPresent();
    }

    public List<LectureVue> lecturesDe(UUID procedureId) {
        exigerEcriture();
        chargerProcedure(procedureId);
        List<ProcedureLecture> liste = lectures.findByProcedureIdOrderByLuLeDesc(procedureId);
        Map<UUID, String> noms = annuaire.noms(liste.stream().map(ProcedureLecture::getUtilisateurId).toList());
        return liste.stream()
                .map(l -> new LectureVue(l.getUtilisateurId(), noms.getOrDefault(l.getUtilisateurId(), "—"), l.getLuLe()))
                .toList();
    }

    /** Collaborateurs actifs n'ayant pas encore accusé lecture d'une procédure publiée. */
    public List<ManquantVue> lecturesManquantes(UUID procedureId) {
        exigerEcriture();
        ProcedureDoc p = chargerProcedure(procedureId);
        if (p.getStatut() != ProcedureDoc.Statut.PUBLIE) {
            throw DomainException.etatInvalide("Seules les procédures publiées ont un suivi de lecture.");
        }
        var lus = lectures.findByProcedureIdOrderByLuLeDesc(procedureId).stream()
                .map(ProcedureLecture::getUtilisateurId)
                .collect(java.util.stream.Collectors.toSet());
        return annuaire.actifs().stream()
                .filter(u -> !lus.contains(u.id()))
                .map(u -> new ManquantVue(u.id(), u.nom(), u.profil()))
                .toList();
    }

    public record LectureVue(UUID utilisateurId, String nom, java.time.Instant luLe) {
    }

    public record ManquantVue(UUID utilisateurId, String nom, String profil) {
    }

    /** DG / Manager : V ; profil Administration : G. */
    private void exigerEcriture() {
        var u = access.utilisateur();
        if (!(u.peut(ModuleCode.ADMINISTRATION, ActionCode.V) || u.peut(ModuleCode.ADMINISTRATION, ActionCode.G))) {
            throw new org.springframework.security.access.AccessDeniedException("Écriture gouvernance réservée à la direction.");
        }
    }

    private IncidentResponsabilite chargerIncident(UUID id) {
        return incidents.findById(id).orElseThrow(() -> DomainException.introuvable("Incident de responsabilité"));
    }

    private ProcedureDoc chargerProcedure(UUID id) {
        return procedures.findById(id).orElseThrow(() -> DomainException.introuvable("Procédure"));
    }
}
