package sn.arlettedebo.platform.communication.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.communication.internal.domain.Publication;
import sn.arlettedebo.platform.communication.internal.domain.PublicationRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Règle « Communication » (circuit historique) : création → vérification → validation → publication → analyse.
 * Module retiré de la matrice : aucun profil n'y a accès ; le Community manager ne fait que pointer.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommunicationService {

    public enum Etape { SOUMETTRE, VERIFIER, VALIDER, RENVOYER, PUBLIER, ANALYSER }

    private final PublicationRepository publications;
    private final AccessPolicy access;
    private final Referentiels referentiels;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Publication> calendrier(LocalDate du, LocalDate au) {
        access.exiger(ModuleCode.COMMUNICATION, ActionCode.C);
        return publications.findByDatePrevueBetweenOrderByDatePrevueAsc(du, au);
    }

    public Publication creer(String titre, String contenu, Publication.Format format, String canal, String perimetre, LocalDate datePrevue) {
        access.exiger(ModuleCode.COMMUNICATION, ActionCode.S);
        verifierCiblage(canal, perimetre, datePrevue);
        Publication p = publications.save(new Publication(titre.trim(), contenu.trim(), format, canal, perimetre, datePrevue,
                access.utilisateur().id()));
        events.publishEvent(AuditEvent.of(ModuleCode.COMMUNICATION, "Contenu créé", p.getTitre()));
        return p;
    }

    /** Modification d'un brouillon ou d'un contenu renvoyé : par son auteur uniquement. */
    public Publication modifier(UUID id, String titre, String contenu, Publication.Format format, String canal, String perimetre,
            LocalDate datePrevue) {
        access.exiger(ModuleCode.COMMUNICATION, ActionCode.S);
        Publication p = publications.findById(id).orElseThrow(() -> DomainException.introuvable("Contenu"));
        if (!p.getAuteurId().equals(access.utilisateur().id())) {
            throw DomainException.regle("AUTEUR_SEUL", "Seul l'auteur modifie son contenu ; les autres le renvoient avec un motif.");
        }
        verifierCiblage(canal, perimetre, datePrevue);
        p.modifier(titre.trim(), contenu.trim(), format, canal, perimetre, datePrevue);
        return p;
    }

    /** L'auteur peut-il encore modifier ce contenu (brouillon ou renvoyé) ? */
    public boolean modifiable(Publication p) {
        return p.getAuteurId().equals(access.utilisateur().id())
                && (p.getStatut() == Publication.Statut.BROUILLON || p.getStatut() == Publication.Statut.A_REVOIR);
    }

    private void verifierCiblage(String canal, String perimetre, LocalDate datePrevue) {
        referentiels.exiger(TypeReferentiel.CANAL_PUBLICATION, canal);
        if (perimetre != null) {
            referentiels.exiger(TypeReferentiel.PERIMETRE, perimetre);
        }
        if (datePrevue != null && datePrevue.isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("La date de publication prévue ne peut pas être dans le passé.");
        }
    }

    /** Étapes que l'utilisateur courant peut déclencher sur ce contenu (jamais sur son propre contenu pour vérifier / valider). */
    public List<Etape> etapesPossibles(Publication p) {
        CurrentUser u = access.utilisateur();
        boolean auteur = p.getAuteurId().equals(u.id());
        boolean saisie = u.peut(ModuleCode.COMMUNICATION, ActionCode.S);
        boolean validation = u.peut(ModuleCode.COMMUNICATION, ActionCode.V);
        boolean verification = !auteur && u.estProfil(Profils.DO) && u.peut(ModuleCode.COMMUNICATION, ActionCode.G);
        boolean execution = saisie && !validation;
        return switch (p.getStatut()) {
            case BROUILLON, A_REVOIR -> auteur && saisie ? List.of(Etape.SOUMETTRE) : List.of();
            case A_VERIFIER -> verification ? List.of(Etape.VERIFIER, Etape.RENVOYER) : List.of();
            case A_VALIDER -> !auteur && validation ? List.of(Etape.VALIDER, Etape.RENVOYER) : List.of();
            case VALIDEE -> execution ? List.of(Etape.PUBLIER) : List.of();
            case PUBLIEE, ANALYSEE -> execution ? List.of(Etape.ANALYSER) : List.of();
        };
    }

    public Publication avancer(UUID id, Etape etape, String motif, Statistiques stats) {
        Publication p = publications.findById(id).orElseThrow(() -> DomainException.introuvable("Contenu"));
        if (!etapesPossibles(p).contains(etape)) {
            throw DomainException.regle("CIRCUIT_COMMUNICATION", message(p, etape));
        }
        UUID moi = access.utilisateur().id();
        switch (etape) {
            case SOUMETTRE -> p.soumettre();
            case VERIFIER -> p.verifier(moi, clock.instant());
            case VALIDER -> p.valider(moi, clock.instant());
            case RENVOYER -> {
                if (motif == null || motif.isBlank()) {
                    throw DomainException.invalide("Le motif de renvoi est obligatoire.");
                }
                p.renvoyer(motif);
            }
            case PUBLIER -> p.publier(clock.instant());
            case ANALYSER -> p.analyser(valeur(stats.portee()), valeur(stats.interactions()),
                    stats.nouveauxAbonnes() == null ? 0 : stats.nouveauxAbonnes(), stats.clics());
        }
        events.publishEvent(AuditEvent.of(ModuleCode.COMMUNICATION, "Contenu : " + etape.name().toLowerCase(), p.getTitre()
                + (motif == null ? "" : " — " + motif)));
        return p;
    }

    @Transactional(readOnly = true)
    public List<Publication> enCircuit() {
        return publications.findByStatutInOrderByDatePrevueAsc(EnumSet.of(Publication.Statut.BROUILLON, Publication.Statut.A_REVOIR,
                Publication.Statut.A_VERIFIER, Publication.Statut.A_VALIDER, Publication.Statut.VALIDEE));
    }

    private static int valeur(Integer v) {
        if (v == null || v < 0) {
            throw DomainException.invalide("Statistiques positives requises.");
        }
        return v;
    }

    private static String message(Publication p, Etape e) {
        return switch (p.getStatut()) {
            case A_VERIFIER -> e == Etape.VALIDER ? "Le contenu doit d'abord être vérifié par la DO." : "Vérification réservée à la DO.";
            case A_VALIDER -> "Validation réservée à la DG (jamais par l'auteur).";
            default -> "Étape « " + e.name().toLowerCase() + " » impossible au statut " + p.getStatut() + ".";
        };
    }

    /** Statistiques saisies à l'analyse d'une publication. */
    public record Statistiques(Integer portee, Integer interactions, Integer nouveauxAbonnes, Integer clics) {
    }
}
