package sn.arlettedebo.platform.commercial.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.commercial.internal.domain.Abonnement;
import sn.arlettedebo.platform.commercial.internal.domain.AbonnementRepository;
import sn.arlettedebo.platform.commercial.internal.domain.Client;
import sn.arlettedebo.platform.commercial.internal.domain.ClientRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonneeRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.Referentiels.OptionSuiviInfo;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage.FichierStocke;

/**
 * Gestion des abonnées : dossier riche (profil capillaire) et suivi après chaque séance.
 * L'accès est réservé par profil au niveau du contrôleur ; le périmètre est contrôlé ici.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AbonneesService {

    private final DossierAbonneeRepository dossiers;
    private final SuiviSeanceAbonneeRepository suivis;
    private final PhotoEvolutionAbonneeRepository photos;
    private final AbonnementRepository abonnements;
    private final ClientRepository clients;
    private final Referentiels referentiels;
    private final Effectifs effectifs;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final FieldCrypto crypto;
    private final JustificatifStorage storage;
    private final EcheancesService echeances;

    /** Dossier avec la fiche cliente associée (vue détail). */
    public record Fiche(DossierAbonnee dossier, Client client) {
    }

    /** Création d'un dossier : à partir d'une fiche existante ({@code clientId}) ou d'une nouvelle cliente. */
    public record NouvelleAbonnee(UUID clientId, String perimetre, String nom, String prenom, String telephone, String email,
            LocalDate dateNaissance, DossierAbonnee.Donnees dossier) {
    }

    // ------------------------------------------------------------------ Lecture

    @Transactional(readOnly = true)
    public Page<DossierAbonneeRepository.Ligne> lister(String perimetre, String q, Pageable page) {
        Set<String> p = perimetres(perimetre);
        if (p.isEmpty()) {
            return Page.empty(page);
        }
        String texte = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String motif = texte.isEmpty() ? "%" : "%" + echapper(texte) + "%";
        String chiffres = texte.replaceAll("\\D", "");
        // Téléphone chiffré : égalité exacte via HMAC (9 chiffres), pas de LIKE partiel.
        String telRecherche = chiffres.length() == 9 ? crypto.rechercheTelephone(chiffres) : null;
        return dossiers.chercher(p, motif, telRecherche, page);
    }

    @Transactional(readOnly = true)
    public Fiche lire(UUID id) {
        return fiche(dossier(id));
    }

    @Transactional(readOnly = true)
    public Page<SuiviSeanceAbonnee> suivis(UUID dossierId, Pageable page) {
        dossier(dossierId);
        return suivis.findByDossierIdOrderByJourDescIdDesc(dossierId, page);
    }

    // ------------------------------------------------------------------ Écriture

    public Fiche creer(NouvelleAbonnee n) {
        Client client;
        if (n.clientId() != null) {
            client = clients.findById(n.clientId()).orElseThrow(() -> DomainException.introuvable("Fiche cliente"));
            access.exigerPerimetre(client.getPerimetreCode());
            if (dossiers.existsByClientId(client.getId())) {
                throw new DomainException(HttpStatus.CONFLICT, "DOSSIER_EXISTANT", "Cette cliente a déjà un dossier d'abonnée.");
            }
            if (client.getTelephone() == null || client.getTelephone().isBlank()) {
                throw DomainException.invalide("Le téléphone est obligatoire pour une abonnée : complétez la fiche cliente.");
            }
        } else {
            if (n.perimetre() == null || n.perimetre().isBlank()) {
                throw DomainException.invalide("Le salon est obligatoire.");
            }
            if (n.nom() == null || n.nom().isBlank()) {
                throw DomainException.invalide("Le nom est obligatoire.");
            }
            access.exigerPerimetre(n.perimetre());
            String tel = ClienteleService.normaliserTelephone(n.telephone());
            if (tel == null || !tel.matches("^[37]\\d{8}$")) {
                throw DomainException.invalide("Le téléphone est obligatoire (9 chiffres, ex. 77 123 45 67).");
            }
            String cleTel = crypto.rechercheTelephone(tel);
            if (cleTel != null && clients.findByTelephoneRecherche(cleTel).isPresent()) {
                throw new DomainException(HttpStatus.CONFLICT, "DOUBLON",
                        "Une fiche existe déjà avec ce numéro : sélectionnez la cliente existante.");
            }
            // Fiche CRM : prénom + nom si prénom saisi (évite le doublon à l'affichage dossier.prenom).
            String libelle = n.prenom() == null || n.prenom().isBlank()
                    ? n.nom().trim()
                    : n.prenom().trim() + " " + n.nom().trim();
            client = clients.save(new Client(new Client.Donnees(n.perimetre(), libelle, tel, vide(n.email()), validerNaissance(n.dateNaissance()),
                    Client.Statut.CLIENTE, "Dossier abonnée", false)));
        }
        client.definirDateNaissance(validerNaissance(n.dateNaissance()));
        client.devenirAbonnee();
        DossierAbonnee.Donnees d = n.dossier();
        DossierAbonnee dossier = dossiers.save(new DossierAbonnee(new DossierAbonnee.Donnees(client.getId(), client.getPerimetreCode(),
                vide(n.prenom()), vide(d.adresse()), vide(d.profession()),
                d.dateInscription() == null ? LocalDate.now(clock) : d.dateInscription(), vide(d.priseEnCharge()),
                d.typeAbonnement(), d.profilJson(), vide(d.recommandations()))));
        assurerAbonnementFormule(client, dossier);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Dossier abonnée créé", client.getNom() + " · " + client.getPerimetreCode()));
        return new Fiche(dossier, client);
    }

    /** Garantit une ligne {@code td_abonnement} (formule du salon) quand le dossier n'en a pas encore. */
    private void assurerAbonnementFormule(Client client, DossierAbonnee dossier) {
        if (abonnements.existsByClientId(client.getId())) {
            return;
        }
        String pref = switch (dossier.getTypeAbonnement() == null ? DossierAbonnee.TypeAbonnement.MENSUEL
                : dossier.getTypeAbonnement()) {
            case LOCKS -> "SKY_BRUSHING_4";
            case BIMENSUEL, MENSUEL -> "KIDS".equals(client.getPerimetreCode()) ? "KIDS_MENSUEL" : "SKY_PREMIUM";
        };
        Referentiels.FormuleInfo f = referentiels.formule(pref)
                .filter(x -> x.perimetre().equals(client.getPerimetreCode()))
                .or(() -> referentiels.formule("KIDS".equals(client.getPerimetreCode()) ? "KIDS_MENSUEL" : "SKY_PREMIUM"))
                .orElse(null);
        if (f == null) {
            return;
        }
        LocalDate debut = dossier.getDateInscription() == null ? LocalDate.now(clock) : dossier.getDateInscription();
        abonnements.save(new Abonnement(client.getId(), f.code(), debut, f.dureeJours(), f.nbSeances(),
                access.utilisateur().id()));
    }

    /** Modification partielle du dossier ; {@code dateNaissance} (fiche cliente) {@code null} = inchangée. */
    public Fiche modifier(UUID id, DossierAbonnee.Donnees d, LocalDate dateNaissance) {
        DossierAbonnee dossier = dossier(id);
        dossier.mettreAJour(d);
        Fiche f = fiche(dossier);
        f.client().definirDateNaissance(validerNaissance(dateNaissance));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Dossier abonnée modifié", f.client().getNom()));
        return f;
    }

    /** Prochain N° de semaine suggéré (max existant + 1). */
    @Transactional(readOnly = true)
    public int prochainNumeroSemaine(UUID dossierId) {
        dossier(dossierId);
        return suivis.maxNumeroSemaine(dossierId) + 1;
    }

    /** Suivi d'une séance (jour du jour par défaut, jamais dans le futur ; un seul suivi par jour). */
    public SuiviSeanceAbonnee ajouterSuivi(UUID dossierId, SaisieSuivi saisie) {
        DossierAbonnee dossier = dossier(dossierId);
        LocalDate jour = validerJour(saisie.jour());
        if (suivis.existsByDossierIdAndJour(dossierId, jour)) {
            throw new DomainException(HttpStatus.CONFLICT, "SUIVI_EXISTANT", "Un suivi existe déjà pour ce jour.");
        }
        SuiviSeanceAbonnee.Donnees donnees = construireDonnees(dossierId, jour, saisie, null);
        SuiviSeanceAbonnee s = suivis.save(new SuiviSeanceAbonnee(donnees));
        echeances.assurerEcheanceDuMois(dossier, jour, s.getId());
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Suivi de séance abonnée", dossier.getId() + " · Week "
                + donnees.numeroSemaine() + " · " + jour));
        return s;
    }

    /** Complète / corrige un suivi existant (ex. séance créée à la clôture RDV). */
    public SuiviSeanceAbonnee modifierSuivi(UUID dossierId, UUID suiviId, SaisieSuivi saisie) {
        DossierAbonnee dossier = dossier(dossierId);
        SuiviSeanceAbonnee s = suivis.findByIdAndDossierId(suiviId, dossierId)
                .orElseThrow(() -> DomainException.introuvable("Suivi de séance"));
        LocalDate jour = saisie.jour() == null ? s.getJour() : validerJour(saisie.jour());
        if (!jour.equals(s.getJour()) && suivis.existsByDossierIdAndJour(dossierId, jour)) {
            throw new DomainException(HttpStatus.CONFLICT, "SUIVI_EXISTANT", "Un suivi existe déjà pour ce jour.");
        }
        s.modifier(construireDonnees(dossierId, jour, saisie, s));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Suivi de séance modifié", dossier.getId() + " · " + jour));
        return s;
    }

    /** Saisie front : options multi-coche + métadonnées. */
    public record SaisieSuivi(LocalDate jour, Short numeroSemaine, UUID coiffeuseId, List<String> optionCodes,
            String coiffureProtectrice, String observation) {
    }

    /** Noms des clientes pour un lot de dossiers (évite le N+1 si besoin d'enrichir une liste). */
    @Transactional(readOnly = true)
    public Map<UUID, Client> clients(java.util.Collection<UUID> ids) {
        return clients.findAllById(ids).stream().collect(java.util.stream.Collectors.toMap(Client::getId, c -> c));
    }

    // ------------------------------------------------------------------ Photos d'évolution

    /** Photos des séances demandées (celles de la page affichée) : une requête {@code IN}, colonnes utiles. */
    @Transactional(readOnly = true)
    public List<PhotoEvolutionAbonneeRepository.Ligne> photosDesSuivis(UUID dossierId, java.util.Collection<UUID> suiviIds) {
        dossier(dossierId);
        if (suiviIds == null || suiviIds.isEmpty()) {
            return List.of();
        }
        return photos.lignesDuDossierParSuivis(dossierId, suiviIds);
    }

    @Transactional(readOnly = true)
    public long compterPhotos(UUID dossierId) {
        dossier(dossierId);
        return photos.countByDossierId(dossierId);
    }

    public static final int MAX_PHOTOS_PAR_SEANCE = 5;

    public PhotoEvolutionAbonnee ajouterPhoto(UUID dossierId, MultipartFile fichier, PhotoEvolutionAbonnee.Type type,
            LocalDate jour, UUID suiviId, String legende) {
        DossierAbonnee dossier = dossier(dossierId);
        if (suiviId == null) {
            throw DomainException.invalide("La photo doit être rattachée à une séance.");
        }
        SuiviSeanceAbonnee seance = suivis.findByIdAndDossierId(suiviId, dossierId)
                .orElseThrow(() -> DomainException.introuvable("Suivi de séance"));
        if (photos.countBySuiviId(suiviId) >= MAX_PHOTOS_PAR_SEANCE) {
            throw DomainException.invalide("Une séance peut contenir au plus " + MAX_PHOTOS_PAR_SEANCE + " photos.");
        }
        LocalDate aujourdhui = LocalDate.now(clock);
        LocalDate j = jour == null ? seance.getJour() : jour;
        if (j.isAfter(aujourdhui)) {
            throw DomainException.invalide("La date de la photo ne peut pas être dans le futur.");
        }
        PhotoEvolutionAbonnee.Type t = type == null ? PhotoEvolutionAbonnee.Type.EVOLUTION : type;
        FichierStocke f = storage.stockerImage("abonnee-photos", fichier);
        String nom = fichier.getOriginalFilename();
        if (nom == null || nom.isBlank()) {
            nom = "photo.jpg";
        }
        nom = nom.replaceAll("[\\\\/]+", "_").trim();
        if (nom.length() > 200) {
            nom = nom.substring(nom.length() - 200);
        }
        PhotoEvolutionAbonnee p = photos.save(new PhotoEvolutionAbonnee(new PhotoEvolutionAbonnee.Donnees(dossierId, suiviId, j, t,
                vide(legende), nom, f.typeMime(), f.taille(), f.sha256(), f.chemin(), access.utilisateur().id(), Instant.now(clock))));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Photo évolution abonnée",
                dossier.getId() + " · " + t.name() + " · " + j));
        return p;
    }

    @Transactional(readOnly = true)
    public record PhotoFichier(PhotoEvolutionAbonnee meta, Resource contenu) {
    }

    @Transactional(readOnly = true)
    public PhotoFichier telechargerPhoto(UUID dossierId, UUID photoId) {
        dossier(dossierId);
        PhotoEvolutionAbonnee p = photos.findByIdAndDossierId(photoId, dossierId)
                .orElseThrow(() -> DomainException.introuvable("Photo"));
        return new PhotoFichier(p, storage.lire(p.getCheminStockage()));
    }

    public void supprimerPhoto(UUID dossierId, UUID photoId) {
        DossierAbonnee dossier = dossier(dossierId);
        PhotoEvolutionAbonnee p = photos.findByIdAndDossierId(photoId, dossierId)
                .orElseThrow(() -> DomainException.introuvable("Photo"));
        String chemin = p.getCheminStockage();
        photos.delete(p);
        storage.supprimer(chemin);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Photo évolution abonnée retirée", dossier.getId() + " · " + photoId));
    }

    // ------------------------------------------------------------------ Outils

    private Fiche fiche(DossierAbonnee d) {
        Client c = clients.findById(d.getClientId()).orElseThrow(() -> DomainException.introuvable("Fiche cliente"));
        return new Fiche(d, c);
    }

    private DossierAbonnee dossier(UUID id) {
        DossierAbonnee d = dossiers.findById(id).orElseThrow(() -> DomainException.introuvable("Dossier abonnée"));
        access.exigerPerimetre(d.getPerimetreCode());
        return d;
    }

    private Set<String> perimetres(String perimetre) {
        if (perimetre == null || perimetre.isBlank()) {
            return access.utilisateur().perimetres();
        }
        access.exigerPerimetre(perimetre);
        return Set.of(perimetre);
    }

    private LocalDate validerNaissance(LocalDate naissance) {
        if (naissance != null && !naissance.isBefore(LocalDate.now(clock))) {
            throw DomainException.invalide("La date de naissance doit être dans le passé.");
        }
        return naissance;
    }

    private LocalDate validerJour(LocalDate jour) {
        LocalDate aujourdhui = LocalDate.now(clock);
        LocalDate j = jour == null ? aujourdhui : jour;
        if (j.isAfter(aujourdhui)) {
            throw DomainException.invalide("Le suivi ne peut pas être daté dans le futur.");
        }
        return j;
    }

    private SuiviSeanceAbonnee.Donnees construireDonnees(UUID dossierId, LocalDate jour, SaisieSuivi saisie,
            SuiviSeanceAbonnee existant) {
        short semaine = resoudreSemaine(dossierId, saisie.numeroSemaine(), existant);
        UUID coiffeuseId = saisie.coiffeuseId();
        if (coiffeuseId != null && effectifs.noms(List.of(coiffeuseId)).isEmpty()) {
            throw DomainException.invalide("Coiffeuse inconnue.");
        }
        List<String> codes = saisie.optionCodes() == null ? List.of()
                : saisie.optionCodes().stream().filter(Objects::nonNull).map(String::trim).filter(c -> !c.isEmpty()).distinct().toList();
        Map<String, OptionSuiviInfo> catalogue = referentiels.optionsSuiviSoinActives().stream()
                .collect(Collectors.toMap(OptionSuiviInfo::code, o -> o, (a, b) -> a, LinkedHashMap::new));
        List<OptionSuiviInfo> choisies = new ArrayList<>();
        for (String code : codes) {
            OptionSuiviInfo o = catalogue.get(code);
            if (o == null) {
                throw DomainException.invalide("Option de soin inconnue : " + code);
            }
            choisies.add(o);
        }
        Map<String, String> parCat = new LinkedHashMap<>();
        for (OptionSuiviInfo o : choisies) {
            parCat.merge(o.categorie(), o.libelle(), (a, b) -> a + ", " + b);
        }
        String coiffure = vide(saisie.coiffureProtectrice());
        if (coiffure == null) {
            coiffure = parCat.get("COIFFURE");
        }
        return new SuiviSeanceAbonnee.Donnees(dossierId, jour, semaine, coiffeuseId, codes, coiffure, vide(saisie.observation()),
                parCat.get("PREPOO"), parCat.get("SHAMPOO"), parCat.get("MASQUE"), parCat.get("SOIN"), parCat.get("LEAVE_IN"),
                parCat.get("SECHAGE"));
    }

    private short resoudreSemaine(UUID dossierId, Short demande, SuiviSeanceAbonnee existant) {
        if (demande != null) {
            if (demande < 1 || demande > 520) {
                throw DomainException.invalide("Le numéro de semaine doit être entre 1 et 520.");
            }
            return demande;
        }
        if (existant != null && existant.getNumeroSemaine() != null) {
            return existant.getNumeroSemaine();
        }
        int prochain = suivis.maxNumeroSemaine(dossierId) + 1;
        return (short) Math.max(1, prochain);
    }

    private static String echapper(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
