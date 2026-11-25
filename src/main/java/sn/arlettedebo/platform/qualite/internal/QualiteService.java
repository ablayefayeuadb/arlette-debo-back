package sn.arlettedebo.platform.qualite.internal;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.qualite.internal.domain.AvisClient;
import sn.arlettedebo.platform.qualite.internal.domain.AvisClientRepository;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestation;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformite;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformiteRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Reclamation;
import sn.arlettedebo.platform.qualite.internal.domain.ReclamationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Signalement;
import sn.arlettedebo.platform.qualite.internal.domain.SignalementRepository;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** Prestations & qualité (consultation). Coiffeuse « S limité » : uniquement ses propres signalements. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualiteService {

    private static final String RESTRICTION_LIMITE = "limité";

    private final NonConformiteRepository ncs;
    private final ReclamationRepository reclamations;
    private final AvisClientRepository avis;
    private final ControlePrestationRepository controles;
    private final SignalementRepository signalements;
    private final AccessPolicy access;

    public List<NonConformite> nonConformites(boolean ouvertesSeulement, String perimetre) {
        exigerVueComplete();
        Set<String> p = perimetres(perimetre);
        return ouvertesSeulement
                ? ncs.findByPerimetreCodeInAndStatutNotOrderByDateConstatDesc(p, NonConformite.Statut.CLOTUREE)
                : ncs.findByPerimetreCodeInOrderByDateConstatDesc(p);
    }

    public List<Reclamation> reclamations(Periode periode, String perimetre) {
        exigerVueComplete();
        return reclamations.findByPerimetreCodeInAndDateReclamationGreaterThanEqualAndDateReclamationLessThanOrderByDateReclamationDesc(
                perimetres(perimetre), periode.debut(), periode.fin());
    }

    public List<AvisClient> avis(Periode periode, String perimetre) {
        exigerVueComplete();
        return avis.findByPerimetreCodeInAndDateAvisGreaterThanEqualAndDateAvisLessThanOrderByDateAvisDesc(perimetres(perimetre),
                periode.debut(), periode.fin());
    }

    public List<ControlePrestation> controles(Periode periode, String perimetre) {
        exigerVueComplete();
        return controles.findByPerimetreCodeInAndDateControleGreaterThanEqualAndDateControleLessThanOrderByDateControleDesc(
                perimetres(perimetre), periode.debut(), periode.fin());
    }

    public List<Signalement> signalements() {
        access.exiger(ModuleCode.QUALITE, ActionCode.C);
        CurrentUser u = access.utilisateur();
        return "limité".equals(u.restriction(ModuleCode.QUALITE))
                ? signalements.findByAuteurIdOrderByDateSignalementDesc(u.id())
                : signalements.findByPerimetreCodeInOrderByDateSignalementDesc(u.perimetres());
    }

    // ------------------------------------------------------------------ Listes paginées en base

    public org.springframework.data.domain.Page<NonConformite> nonConformites(boolean ouvertesSeulement, String perimetre, org.springframework.data.domain.Pageable page) {
        exigerVueComplete();
        Set<String> p = perimetres(perimetre);
        return ouvertesSeulement ? ncs.findByPerimetreCodeInAndStatutNot(p, NonConformite.Statut.CLOTUREE, page) : ncs.findByPerimetreCodeIn(p, page);
    }

    public org.springframework.data.domain.Page<Reclamation> reclamations(Periode periode, String perimetre, org.springframework.data.domain.Pageable page) {
        exigerVueComplete();
        return reclamations.findByPerimetreCodeInAndDateReclamationGreaterThanEqualAndDateReclamationLessThan(perimetres(perimetre),
                periode.debut(), periode.fin(), page);
    }

    public org.springframework.data.domain.Page<AvisClient> avis(Periode periode, String perimetre, org.springframework.data.domain.Pageable page) {
        exigerVueComplete();
        return avis.findByPerimetreCodeInAndDateAvisGreaterThanEqualAndDateAvisLessThan(perimetres(perimetre), periode.debut(), periode.fin(), page);
    }

    public org.springframework.data.domain.Page<ControlePrestation> controles(Periode periode, String perimetre, org.springframework.data.domain.Pageable page) {
        exigerVueComplete();
        return controles.findByPerimetreCodeInAndDateControleGreaterThanEqualAndDateControleLessThan(perimetres(perimetre),
                periode.debut(), periode.fin(), page);
    }

    /** Coiffeuse (restriction « limité ») : uniquement ses signalements. */
    public org.springframework.data.domain.Page<Signalement> signalements(org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.QUALITE, ActionCode.C);
        CurrentUser u = access.utilisateur();
        return "limité".equals(u.restriction(ModuleCode.QUALITE)) ? signalements.findByAuteurId(u.id(), page)
                : signalements.findByPerimetreCodeIn(u.perimetres(), page);
    }

    /** Contrôles, NC, réclamations, avis : pas pour la restriction « limité » (coiffeuse). */
    private void exigerVueComplete() {
        access.exiger(ModuleCode.QUALITE, ActionCode.C);
        if ("limité".equals(access.utilisateur().restriction(ModuleCode.QUALITE))) {
            throw new org.springframework.security.access.AccessDeniedException("Accès qualité limité aux signalements.");
        }
    }

    private Set<String> perimetres(String perimetre) {
        if (perimetre == null) {
            return access.utilisateur().perimetres();
        }
        access.exigerPerimetre(perimetre);
        return Set.of(perimetre);
    }
}
