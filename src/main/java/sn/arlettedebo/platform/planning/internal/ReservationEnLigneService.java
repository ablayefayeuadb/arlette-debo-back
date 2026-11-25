package sn.arlettedebo.platform.planning.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.Clientele;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.planning.internal.domain.Creneau;
import sn.arlettedebo.platform.planning.internal.domain.CreneauRepository;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.mail.MailService;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;
import sn.arlettedebo.platform.shared.sms.SmsMessages;
import sn.arlettedebo.platform.shared.sms.SmsService;

/**
 * Réservation en ligne, sans compte : la cliente choisit un salon, une prestation et un créneau ouvert
 * par l'accueil, puis laisse son nom et son téléphone. Le rendez-vous est créé « prévu » (source EN_LIGNE,
 * sans coiffeuse) et apparaît dans « À traiter » de l'accueil, qui le confirme et l'affecte.
 * <p>
 * Garde-fous d'un formulaire public : capacité du créneau vérifiée sous verrou, horizon de réservation,
 * délai minimal avant le créneau, plafond de réservations à venir par numéro et par adresse IP.
 */
@Service
@Transactional(readOnly = true)
public class ReservationEnLigneService {

    private static final Logger log = LoggerFactory.getLogger(ReservationEnLigneService.class);

    /** Statuts qui ne retiennent plus de place dans un créneau. */
    private static final Set<RendezVous.Statut> LIBERES = EnumSet.of(RendezVous.Statut.ANNULEE, RendezVous.Statut.ABSENTE);
    private static final Set<RendezVous.Statut> A_VENIR = EnumSet.of(RendezVous.Statut.PREVU, RendezVous.Statut.CONFIRME);
    static final int HORIZON_JOURS = 60;
    static final int DELAI_MINIMAL_MIN = 60;
    static final int MAX_A_VENIR_PAR_NUMERO = 2;
    static final int MAX_PAR_IP_PAR_HEURE = 20;

    private final RendezVousRepository rdvs;
    private final CreneauRepository creneaux;
    private final Referentiels referentiels;
    private final Clientele clientele;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SmsService sms;
    private final MailService mail;
    private final UserDirectory annuaire;
    private final FieldCrypto crypto;
    private final String publicAppUrl;
    private final Cache<String, AtomicInteger> parIp = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1)).maximumSize(100_000).build();

    ReservationEnLigneService(RendezVousRepository rdvs, CreneauRepository creneaux, Referentiels referentiels, Clientele clientele,
            ApplicationEventPublisher events, Clock clock, SmsService sms, MailService mail, UserDirectory annuaire,
            FieldCrypto crypto, @Value("${app.public-app-url:https://arlettedebo.sn}") String publicAppUrl) {
        this.rdvs = rdvs;
        this.creneaux = creneaux;
        this.referentiels = referentiels;
        this.clientele = clientele;
        this.events = events;
        this.clock = clock;
        this.sms = sms;
        this.mail = mail;
        this.annuaire = annuaire;
        this.crypto = crypto;
        this.publicAppUrl = publicAppUrl == null || publicAppUrl.isBlank()
                ? "https://arlettedebo.sn"
                : publicAppUrl.trim().replaceAll("/+$", "");
    }

    /** Prestations réservables en ligne (code, salon, durée, prix). */
    public List<Referentiels.PrestationInfo> catalogue() {
        return referentiels.prestationsActives();
    }

    /** Prestations réservables d'un seul salon (filtre SQL). */
    public List<Referentiels.PrestationInfo> catalogue(String perimetre) {
        return referentiels.prestationsActives(perimetre);
    }

    /**
     * Créneaux ouverts d'un jour avec les places restantes ; ceux trop proches ou passés sont exclus.
     * Un créneau déjà bloqué par un RDV sans coiffeuse (réservation en ligne / à affecter) est affiché
     * plein : la contrainte SQL n'autorise qu'un seul RDV sans coiffeuse sur une plage qui se chevauche.
     */
    public List<Disponibilite> disponibilites(String perimetre, LocalDate jour, String prestation) {
        Referentiels.PrestationInfo p = exigerPrestation(perimetre, prestation);
        exigerJour(jour);
        List<RendezVousRepository.PlageSansCoiffeuse> sansCoiffeuse = rdvs.plagesSansCoiffeuse(perimetre, jour, LIBERES);
        List<RendezVousRepository.JourHeure> prises = rdvs.prisesEntre(perimetre, jour, jour, LIBERES);
        return creneaux.findByPerimetreCodeAndJourOrderByHeureDebutAsc(perimetre, jour).stream()
                .filter(Creneau::isOuvert)
                .filter(c -> assezTot(jour, c.getHeureDebut()))
                .map(c -> {
                    long places = Math.max(0, c.getCapacite() - Occupation.dans(prises, c));
                    if (places > 0 && chevaucheSansCoiffeuse(sansCoiffeuse, c.getHeureDebut(), p.dureeMin())) {
                        places = 0;
                    }
                    return new Disponibilite(c.getHeureDebut(), c.getHeureFin(), places);
                })
                .toList();
    }

    @Transactional
    public Confirmation reserver(Demande d, String ip) {
        limiterIp(ip);
        Referentiels.PrestationInfo p = exigerPrestation(d.perimetre(), d.prestation());
        exigerJour(d.jour());
        String type = d.typeCliente() == null ? "" : d.typeCliente().trim().toUpperCase();
        if (!type.equals("ABONNEE") && !type.equals("CLIENTE")) {
            throw DomainException.invalide("Indiquez si vous êtes abonnée ou cliente.");
        }
        String telephone = telephoneSenegal(d.telephone());
        if (!assezTot(d.jour(), d.heure())) {
            throw DomainException.invalide("Ce créneau est trop proche : réservez au moins " + DELAI_MINIMAL_MIN + " minutes à l'avance.");
        }
        Creneau c = creneaux.findFirstByPerimetreCodeAndJourAndHeureDebutAndOuvertTrue(d.perimetre(), d.jour(), d.heure())
                .orElseThrow(() -> DomainException.etatInvalide("Ce créneau n'est pas ouvert à la réservation."));
        if (occupees(c) >= c.getCapacite()) {
            throw DomainException.etatInvalide("Ce créneau vient d'être réservé : choisissez-en un autre.");
        }
        if (chevaucheSansCoiffeuse(rdvs.plagesSansCoiffeuse(d.perimetre(), d.jour(), LIBERES), d.heure(), p.dureeMin())) {
            throw DomainException.etatInvalide("Ce créneau n'est plus disponible : choisissez-en un autre.");
        }
        String cleTel = crypto.rechercheTelephone(telephone);
        long aVenir = cleTel == null ? 0
                : rdvs.countByTelephoneRechercheAndSourceAndJourGreaterThanEqualAndStatutIn(
                        cleTel, RendezVous.Source.EN_LIGNE, LocalDate.now(clock), A_VENIR);
        if (aVenir >= MAX_A_VENIR_PAR_NUMERO) {
            throw DomainException.regle("RESERVATIONS_MAX",
                    "Vous avez déjà " + aVenir + " rendez-vous à venir : contactez le salon pour en prendre davantage.");
        }

        // Abonnée ou cliente : le RDV reste possible dans les deux cas.
        // Si le téléphone correspond déjà à une abonnée, on reprend sa fiche ;
        // sinon on enregistre quand même (intention « abonnée » = typeCliente, pas un blocage).
        String nomSaisi = d.nom() == null ? "" : d.nom().trim().replaceAll("\\s+", " ");
        String nom;
        UUID fiche;
        if (type.equals("ABONNEE")) {
            var abo = clientele.abonneeParTelephone(telephone, d.perimetre());
            if (abo.isPresent()) {
                nom = abo.get().nom();
                fiche = abo.get().clientId();
            } else {
                if (nomSaisi.length() < 2) {
                    throw DomainException.invalide("Indiquez votre nom pour enregistrer la demande.");
                }
                nom = nomSaisi;
                fiche = clientele.ficheParTelephone(telephone, d.perimetre()).orElse(null);
            }
        } else {
            if (nomSaisi.length() < 2) {
                throw DomainException.invalide("Indiquez votre nom.");
            }
            nom = nomSaisi;
            fiche = clientele.ficheParTelephone(telephone, d.perimetre()).orElse(null);
        }

        RendezVous r = new RendezVous(new RendezVous.Donnees(d.perimetre(), d.jour(), d.heure(), p.dureeMin(), fiche, nom, telephone,
                p.code(), null, p.prix(), RendezVous.Source.EN_LIGNE));
        r.definirTypeCliente(type);
        r.annoter(d.note());
        rdvs.saveAndFlush(r);
        events.publishEvent(new AuditEvent(ModuleCode.PLANNING, "Réservation en ligne",
                d.perimetre() + " · " + d.jour() + " " + d.heure() + " · " + nom + " · " + p.libelle(), null, "Réservation en ligne"));
        String salon = referentiels.libelle(TypeReferentiel.PERIMETRE, d.perimetre()).orElse(d.perimetre());
        sms.envoyer(telephone, SmsMessages.rdvDemandeEnLigne(salon, d.jour(), d.heure(), p.libelle(), r.reference()));
        alerterManagers(r, salon, p.libelle(), telephone, nom);
        return new Confirmation(r.reference(), d.perimetre(), d.jour(), d.heure(), p.libelle(),
                p.dureeMin(), p.prix());
    }

    /** E-mail aux managers du salon : la réservation reste valide même si l’envoi échoue. */
    private void alerterManagers(RendezVous r, String salon, String prestation, String telephone, String nom) {
        String lien = publicAppUrl + "/espace/planning/rdv?rdv=" + r.getId()
                + "&jour=" + r.getJour()
                + "&perimetre=" + r.getPerimetreCode();
        try {
            List<UserDirectory.UserRef> managers = annuaire.actifs().stream()
                    .filter(u -> Profils.MANAGER.equals(u.profil()))
                    .filter(u -> u.email() != null && !u.email().isBlank())
                    .filter(u -> u.perimetres() == null || u.perimetres().isEmpty()
                            || u.perimetres().contains(r.getPerimetreCode()))
                    .toList();
            if (managers.isEmpty()) {
                log.warn("Aucun manager à alerter pour la réservation en ligne {}", r.reference());
                return;
            }
            for (UserDirectory.UserRef m : managers) {
                mail.nouvelleReservationEnLigne(m.email(), m.nom(), salon, r.getJour(), r.getHeure(),
                        nom, telephone, prestation, r.reference(), lien);
            }
        } catch (Exception e) {
            log.error("Alerte managers (réservation {}) : {}", r.reference(), e.getMessage());
        }
    }

    private long occupees(Creneau c) {
        return rdvs.countByPerimetreCodeAndJourAndHeureGreaterThanEqualAndHeureLessThanAndStatutNotIn(c.getPerimetreCode(), c.getJour(),
                c.getHeureDebut(), c.getHeureFin(), LIBERES);
    }

    /** Même logique que la contrainte {@code ex_td_rendez_vous_sans_coiffeuse}. */
    private static boolean chevaucheSansCoiffeuse(List<RendezVousRepository.PlageSansCoiffeuse> sansCoiffeuse, LocalTime debut,
            int dureeMin) {
        LocalTime fin = debut.plusMinutes(dureeMin);
        for (RendezVousRepository.PlageSansCoiffeuse e : sansCoiffeuse) {
            LocalTime eFin = e.heure().plusMinutes(e.dureeMin());
            if (e.heure().isBefore(fin) && debut.isBefore(eFin)) {
                return true;
            }
        }
        return false;
    }

    private Referentiels.PrestationInfo exigerPrestation(String perimetre, String code) {
        return referentiels.prestationActive(perimetre, code)
                .orElseThrow(() -> DomainException.invalide("Prestation indisponible dans ce salon."));
    }

    private void exigerJour(LocalDate jour) {
        LocalDate aujourdhui = LocalDate.now(clock.withZone(AuditingConfig.ZONE));
        if (jour == null || jour.isBefore(aujourdhui) || jour.isAfter(aujourdhui.plusDays(HORIZON_JOURS))) {
            throw DomainException.invalide("Choisissez un jour entre aujourd'hui et les " + HORIZON_JOURS + " prochains jours.");
        }
    }

    private boolean assezTot(LocalDate jour, LocalTime heure) {
        ZonedDateTime limite = ZonedDateTime.now(clock.withZone(AuditingConfig.ZONE)).plusMinutes(DELAI_MINIMAL_MIN);
        return !jour.atTime(heure).atZone(AuditingConfig.ZONE).isBefore(limite);
    }

    /** Numéro sénégalais à 9 chiffres (indicatif +221 accepté), stocké en chiffres seuls. */
    private static String telephoneSenegal(String brut) {
        String chiffres = brut == null ? "" : brut.replaceAll("\\D", "");
        if (chiffres.length() == 12 && chiffres.startsWith("221")) {
            chiffres = chiffres.substring(3);
        }
        if (!chiffres.matches("[37]\\d{8}")) {
            throw DomainException.invalide("Numéro de téléphone invalide (9 chiffres, ex. 77 123 45 67).");
        }
        return chiffres;
    }

    private void limiterIp(String ip) {
        int n = parIp.get(ip == null ? "?" : ip, k -> new AtomicInteger()).incrementAndGet();
        if (n > MAX_PAR_IP_PAR_HEURE) {
            throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, "TROP_DE_RESERVATIONS",
                    "Trop de réservations depuis cette connexion. Réessayez plus tard ou appelez le salon.");
        }
    }

    public record Demande(String perimetre, LocalDate jour, LocalTime heure, String prestation, String nom, String telephone,
            String note, String typeCliente) {
    }

    public record Disponibilite(LocalTime heure, LocalTime heureFin, long placesRestantes) {
    }

    /** Récapitulatif renvoyé à la cliente : aucune donnée d'un tiers. */
    public record Confirmation(String reference, String perimetre, LocalDate jour, LocalTime heure, String prestation, int dureeMin,
            long prix) {
    }
}
