package sn.arlettedebo.platform.commercial.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.Abonnees;
import sn.arlettedebo.platform.commercial.internal.domain.Client;
import sn.arlettedebo.platform.commercial.internal.domain.ClientRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonneeRepository;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** Implémentation de l'API publique {@link Abonnees} (appelée depuis le planning). */
@Component
@RequiredArgsConstructor
class AbonneesApi implements Abonnees {

    private final DossierAbonneeRepository dossiers;
    private final SuiviSeanceAbonneeRepository suivis;
    private final ClientRepository clients;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final FieldCrypto crypto;
    private final EcheancesService echeances;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enregistrerSeanceDuRendezVous(UUID clientId, String telephone, String perimetre, LocalDate jour,
            String observation) {
        Optional<DossierAbonnee> dossier = trouverDossier(clientId, telephone, perimetre);
        if (dossier.isEmpty()) {
            return false;
        }
        DossierAbonnee d = dossier.get();
        LocalDate seanceJour = jour != null ? jour : LocalDate.now(clock);
        if (suivis.existsByDossierIdAndJour(d.getId(), seanceJour)) {
            return false;
        }
        String obs = observation == null || observation.isBlank() ? null : observation.trim();
        if (obs != null && obs.length() > 1000) {
            obs = obs.substring(0, 1000);
        }
        short semaine = (short) (suivis.maxNumeroSemaine(d.getId()) + 1);
        SuiviSeanceAbonnee suivi = suivis.save(new SuiviSeanceAbonnee(new SuiviSeanceAbonnee.Donnees(d.getId(), seanceJour, semaine, null, List.of(), null,
                obs, null, null, null, null, null, null)));
        echeances.assurerEcheanceDuMois(d, seanceJour, suivi.getId());
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Séance abonnée (depuis RDV)",
                d.getId() + " · " + seanceJour));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> clientIdsAvecDossier(Collection<UUID> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return Collections.emptySet();
        }
        return dossiers.findClientIdsByClientIdIn(clientIds);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean aDossier(UUID clientId, String telephone, String perimetre) {
        return trouverDossier(clientId, telephone, perimetre).isPresent();
    }

    private Optional<DossierAbonnee> trouverDossier(UUID clientId, String telephone, String perimetre) {
        if (clientId != null) {
            Optional<DossierAbonnee> parClient = dossiers.findByClientId(clientId);
            if (parClient.isPresent()) {
                return parClient;
            }
        }
        String tel = chiffresOuNull(telephone);
        if (tel == null || perimetre == null || perimetre.isBlank()) {
            return Optional.empty();
        }
        String cle = crypto.rechercheTelephone(tel);
        if (cle == null) {
            return Optional.empty();
        }
        return clients.findByTelephoneRecherche(cle)
                .filter(c -> c.getPerimetreCode().equals(perimetre))
                .map(Client::getId)
                .flatMap(dossiers::findByClientId);
    }

    private static String chiffresOuNull(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        String d = brut.replaceAll("\\D", "");
        if (d.length() == 12 && d.startsWith("221")) {
            d = d.substring(3);
        }
        return d.isBlank() ? null : d;
    }
}
