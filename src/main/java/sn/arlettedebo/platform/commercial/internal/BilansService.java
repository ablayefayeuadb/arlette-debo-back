package sn.arlettedebo.platform.commercial.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.internal.domain.BilanTrimestrielAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.BilanTrimestrielAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Bilans de soins trimestriels des abonnées. L'accès est réservé par profil au niveau du contrôleur ;
 * le périmètre du dossier est contrôlé ici.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BilansService {

    private final BilanTrimestrielAbonneeRepository bilans;
    private final DossierAbonneeRepository dossiers;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Saisie d'un bilan ; en modification, les champs {@code null} restent inchangés. */
    public record Saisie(LocalDate trimestreDebut, String analyseObjectifs, String progres, String difficultes,
            String habitudesACorriger, String recommandations, BilanTrimestrielAbonnee.Conclusion conclusion) {
    }

    @Transactional(readOnly = true)
    public Page<BilanTrimestrielAbonnee> lister(UUID dossierId, Pageable page) {
        dossier(dossierId);
        return bilans.findByDossierIdOrderByTrimestreDebutDescIdDesc(dossierId, page);
    }

    @Transactional(readOnly = true)
    public BilanTrimestrielAbonnee lire(UUID dossierId, UUID id) {
        dossier(dossierId);
        return trouver(dossierId, id);
    }

    public BilanTrimestrielAbonnee creer(UUID dossierId, Saisie s) {
        dossier(dossierId);
        if (s.trimestreDebut() == null) {
            throw DomainException.invalide("La date du bilan est obligatoire.");
        }
        if (s.conclusion() == null) {
            throw DomainException.invalide("La conclusion est obligatoire.");
        }
        validerDate(s.trimestreDebut());
        BilanTrimestrielAbonnee b = bilans.save(new BilanTrimestrielAbonnee(donnees(dossierId, s)));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Bilan trimestriel abonnée créé",
                dossierId + " · " + b.getTrimestreDebut() + " · " + b.getConclusion()));
        return b;
    }

    public BilanTrimestrielAbonnee modifier(UUID dossierId, UUID id, Saisie s) {
        dossier(dossierId);
        BilanTrimestrielAbonnee b = trouver(dossierId, id);
        if (s.trimestreDebut() != null) {
            validerDate(s.trimestreDebut());
        }
        b.mettreAJour(donnees(dossierId, s));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Bilan trimestriel abonnée modifié",
                dossierId + " · " + b.getTrimestreDebut()));
        return b;
    }

    private BilanTrimestrielAbonnee trouver(UUID dossierId, UUID id) {
        return bilans.findByIdAndDossierId(id, dossierId).orElseThrow(() -> DomainException.introuvable("Bilan trimestriel"));
    }

    private static BilanTrimestrielAbonnee.Donnees donnees(UUID dossierId, Saisie s) {
        return new BilanTrimestrielAbonnee.Donnees(dossierId, s.trimestreDebut(), s.analyseObjectifs(), s.progres(),
                s.difficultes(), s.habitudesACorriger(), s.recommandations(), s.conclusion());
    }

    private void validerDate(LocalDate jour) {
        if (jour.isAfter(LocalDate.now(clock))) {
            throw DomainException.invalide("La date du bilan ne peut pas être dans le futur.");
        }
    }

    private DossierAbonnee dossier(UUID id) {
        DossierAbonnee d = dossiers.findById(id).orElseThrow(() -> DomainException.introuvable("Dossier abonnée"));
        access.exigerPerimetre(d.getPerimetreCode());
        return d;
    }
}
