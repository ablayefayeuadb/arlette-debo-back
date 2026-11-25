package sn.arlettedebo.platform.caisse.internal;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.caisse.internal.domain.Encaissement;
import sn.arlettedebo.platform.caisse.internal.domain.EncaissementRepository;
import sn.arlettedebo.platform.caisse.internal.domain.JustificatifCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.JustificatifCaisse.Nature;
import sn.arlettedebo.platform.caisse.internal.domain.JustificatifCaisseRepository;
import sn.arlettedebo.platform.caisse.internal.domain.JustificatifCaisseRepository.NombreParSession;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisseRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage.FichierStocke;

/**
 * Justificatifs de caisse : ticket TPE, reçu Wave / Orange Money, bordereau de remise en banque, fiche de comptage.
 * <ul>
 *   <li>dépôt par la caisse (S) de son salon, jusqu'au contrôle indépendant de la clôture : après, la journée est figée ;</li>
 *   <li>une pièce rattachée à un encaissement doit appartenir à la même journée et à un encaissement non annulé ;</li>
 *   <li>retrait possible uniquement par la personne qui l'a déposée, tant que la caisse est ouverte
 *       (après la clôture, les pièces sont celles que la personne qui contrôle examine) ;</li>
 *   <li>consultation et téléchargement : droit de consultation caisse (C) sur le salon.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JustificatifCaisseService {

    private final JustificatifCaisseRepository justificatifs;
    private final SessionCaisseRepository sessions;
    private final EncaissementRepository encaissements;
    private final JustificatifStorage stockage;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public JustificatifCaisse deposer(UUID sessionId, UUID encaissementId, Nature nature, String commentaire, MultipartFile fichier) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        SessionCaisse s = session(sessionId);
        if (s.getControlePar() != null) {
            throw DomainException.etatInvalide("Clôture déjà contrôlée : plus aucun justificatif ne peut être ajouté.");
        }
        if (nature == null) {
            throw DomainException.invalide("Précisez la nature du justificatif.");
        }
        String precision = commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
        String objet = "journée";
        if (encaissementId != null) {
            Encaissement e = encaissements.findById(encaissementId).orElseThrow(() -> DomainException.introuvable("Encaissement"));
            if (!e.getSession().getId().equals(s.getId())) {
                throw DomainException.invalide("Cet encaissement n'appartient pas à cette journée de caisse.");
            }
            if (e.isAnnule()) {
                throw DomainException.etatInvalide("Encaissement annulé : aucun justificatif à joindre.");
            }
            objet = e.getLibelle() + " · " + e.getMontant() + " FCFA";
        }
        String nom = nomFichier(fichier.getOriginalFilename());
        FichierStocke f = stockage.stocker("caisse", fichier);
        JustificatifCaisse j = justificatifs.save(new JustificatifCaisse(new JustificatifCaisse.Donnees(s.getId(), encaissementId, nature,
                precision, nom, f.typeMime(), f.taille(), f.sha256(), f.chemin(), access.utilisateur().id(), clock.instant())));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Justificatif de caisse déposé",
                s.getPerimetreCode() + " · " + s.getJour() + " · " + objet + " · " + nom));
        return j;
    }

    @Transactional(readOnly = true)
    public List<JustificatifCaisse> lister(UUID sessionId) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        return justificatifs.findBySessionIdOrderByDeposeLeAsc(session(sessionId).getId());
    }

    /** Pièces d'une session déjà chargée et contrôlée par l'appelant (détail de session). */
    @Transactional(readOnly = true)
    public List<JustificatifCaisse> deLaSession(UUID sessionId) {
        return justificatifs.findBySessionIdOrderByDeposeLeAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public Fichier telecharger(UUID id) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        JustificatifCaisse j = justificatifs.findById(id).orElseThrow(() -> DomainException.introuvable("Justificatif"));
        session(j.getSessionId());
        return new Fichier(j, stockage.lire(j.getCheminStockage()));
    }

    public void retirer(UUID id) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        JustificatifCaisse j = justificatifs.findById(id).orElseThrow(() -> DomainException.introuvable("Justificatif"));
        SessionCaisse s = session(j.getSessionId());
        if (s.getStatut() != SessionCaisse.Statut.OUVERTE) {
            throw DomainException.etatInvalide("Caisse clôturée : les justificatifs ne peuvent plus être retirés.");
        }
        if (!j.getDeposePar().equals(access.utilisateur().id())) {
            throw DomainException.regle("JUSTIFICATIF_AUTRUI", "Seule la personne qui a déposé ce justificatif peut le retirer.");
        }
        justificatifs.delete(j);
        String chemin = j.getCheminStockage();
        // Le fichier n'est effacé qu'une fois la suppression validée en base
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    stockage.supprimer(chemin);
                } catch (RuntimeException e) {
                    // Retrait validé : un fichier resté sur le disque n'est plus référencé ni accessible par l'API
                    log.warn("Justificatif retiré mais objet non supprimé du stockage : {}", chemin);
                }
            }
        });
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Justificatif de caisse retiré",
                s.getPerimetreCode() + " · " + s.getJour() + " · " + j.getNomFichier()));
    }

    /** Compteurs groupés pour une liste de sessions : pièces déposées et paiements électroniques sans pièce. */
    @Transactional(readOnly = true)
    public Map<UUID, Compteurs> compteurs(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Long> pieces = parSession(justificatifs.pieces(ids));
        Map<UUID, Long> manquants = parSession(justificatifs.sansJustificatif(ids));
        return ids.stream().distinct().collect(Collectors.toMap(id -> id,
                id -> new Compteurs(pieces.getOrDefault(id, 0L), manquants.getOrDefault(id, 0L))));
    }

    private static Map<UUID, Long> parSession(List<NombreParSession> l) {
        return l.stream().collect(Collectors.toMap(NombreParSession::getSessionId, NombreParSession::getNombre));
    }

    private SessionCaisse session(UUID id) {
        SessionCaisse s = sessions.findById(id).orElseThrow(() -> DomainException.introuvable("Session de caisse"));
        access.exigerPerimetre(s.getPerimetreCode());
        return s;
    }

    /** Nom affiché : base du nom client, sans chemin, borné à 120 caractères. */
    private static String nomFichier(String original) {
        String nom = original == null ? "" : original.replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (nom.isEmpty()) {
            nom = "justificatif";
        }
        return nom.length() > 120 ? nom.substring(nom.length() - 120) : nom;
    }

    public record Fichier(JustificatifCaisse meta, Resource contenu) {
    }

    public record Compteurs(long pieces, long paiementsSansJustificatif) {
    }
}
