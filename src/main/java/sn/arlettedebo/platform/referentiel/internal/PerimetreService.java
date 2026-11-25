package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.EntityManager;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.referentiel.PerimetreEvenement;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Salons et boutiques autonomes gérés par la DG : création, renommage, désactivation.
 * <ul>
 *   <li>{@code SALON} : coiffure (créneaux, prestations) — peut aussi avoir un coin boutique ;</li>
 *   <li>{@code BOUTIQUE} : magasin autonome (stocks / ventes), distinct du coin boutique d'un salon ;</li>
 *   <li>le code est dérivé du nom à la création, puis ne change plus ;</li>
 *   <li>jamais supprimé : désactivé, il disparaît des accès.</li>
 * </ul>
 */
@Service
@Transactional
public class PerimetreService {

    static final String TYPE_SALON = "SALON";
    static final String TYPE_BOUTIQUE = "BOUTIQUE";
    private static final Set<String> TYPES = Set.of(TYPE_SALON, TYPE_BOUTIQUE);

    private final EntityManager em;
    private final ApplicationEventPublisher events;
    private final PerimetreService self;

    public PerimetreService(EntityManager em, ApplicationEventPublisher events, @Lazy PerimetreService self) {
        this.em = em;
        this.events = events;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public List<Perimetre> lister() {
        return em.createQuery("select p from Perimetre p order by p.ordre, p.code", Perimetre.class).getResultList();
    }

    @CacheEvict(value = "referentiels", allEntries = true)
    public Perimetre creer(String libelle, String type, UUID responsableId) {
        String nom = nom(libelle);
        if (!TYPES.contains(type)) {
            throw DomainException.invalide("Type inconnu : salon ou boutique.");
        }
        if (TYPE_BOUTIQUE.equals(type) && responsableId == null) {
            throw DomainException.invalide("Choisissez le vendeur boutique qui gérera ce magasin.");
        }
        if (TYPE_SALON.equals(type)) {
            responsableId = null;
        }
        exigerNomLibre(nom, null);
        List<Perimetre> existants = self.lister();
        String base = code(nom);
        String code = base;
        for (int i = 2; em.find(Perimetre.class, code) != null; i++) {
            code = base.substring(0, Math.min(base.length(), 27)) + "_" + i;
        }
        short ordre = (short) (existants.stream().mapToInt(Perimetre::getOrdre).max().orElse(0) + 1);
        Perimetre p = new Perimetre(code, nom, type, ordre);
        em.persist(p);
        events.publishEvent(new PerimetreEvenement.Cree(code, type, responsableId));
        String libelleType = TYPE_BOUTIQUE.equals(type) ? "Boutique créée" : "Salon créé";
        events.publishEvent(AuditEvent.of(ModuleCode.RH, libelleType, nom + " (" + code + ")"));
        return p;
    }

    @Transactional(readOnly = true)
    public Perimetre exiger(String code) {
        Perimetre p = em.find(Perimetre.class, code);
        if (p == null) {
            throw DomainException.introuvable("Salon");
        }
        return p;
    }

    @CacheEvict(value = "referentiels", allEntries = true)
    public Perimetre modifier(String code, String libelle, boolean actif, Integer capaciteCreneau, Integer heureOuverture,
            Integer heureFermeture) {
        Perimetre p = self.exiger(code);
        String nom = nom(libelle);
        exigerNomLibre(nom, code);
        exigerPasDernierSalon(p, actif);
        boolean etatChange = p.isActif() != actif;
        String ancien = p.getLibelle();
        p.modifier(nom, actif);
        appliquerParametresCreneaux(p, capaciteCreneau, heureOuverture, heureFermeture);
        events.publishEvent(new PerimetreEvenement.Modifie(code, actif));
        auditerModification(p, ancien, nom, etatChange, actif);
        return p;
    }

    private void exigerPasDernierSalon(Perimetre p, boolean actif) {
        if (!p.isActif() || actif || !TYPE_SALON.equals(p.getType())) {
            return;
        }
        long salonsActifs = self.lister().stream().filter(x -> x.isActif() && TYPE_SALON.equals(x.getType())).count();
        if (salonsActifs == 1) {
            throw DomainException.regle("DERNIER_SALON", "Impossible de désactiver le dernier salon actif.");
        }
    }

    private static void appliquerParametresCreneaux(Perimetre p, Integer capacite, Integer ouverture, Integer fermeture) {
        if (TYPE_SALON.equals(p.getType()) && capacite != null && ouverture != null && fermeture != null) {
            p.definirParametresCreneaux(capacite, ouverture, fermeture);
        }
    }

    private void auditerModification(Perimetre p, String ancien, String nom, boolean etatChange, boolean actif) {
        boolean boutique = TYPE_BOUTIQUE.equals(p.getType());
        if (!ancien.equals(nom)) {
            events.publishEvent(AuditEvent.of(ModuleCode.RH, boutique ? "Boutique renommée" : "Salon renommé",
                    ancien + " → " + nom));
        }
        if (etatChange) {
            events.publishEvent(AuditEvent.of(ModuleCode.RH, libelleActivation(boutique, actif), nom));
        }
    }

    private static String libelleActivation(boolean boutique, boolean actif) {
        if (actif) {
            return boutique ? "Boutique réactivée" : "Salon réactivé";
        }
        return boutique ? "Boutique désactivée" : "Salon désactivé";
    }

    private void exigerNomLibre(String nom, String saufCode) {
        boolean pris = self.lister().stream().anyMatch(p -> p.getLibelle().equalsIgnoreCase(nom) && !p.getCode().equals(saufCode));
        if (pris) {
            throw new DomainException(org.springframework.http.HttpStatus.CONFLICT, "SALON_EXISTANT",
                    "Un salon ou une boutique porte déjà ce nom.");
        }
    }

    private static String nom(String libelle) {
        String nom = libelle == null ? "" : libelle.trim().replaceAll("\\s+", " ");
        if (nom.length() < 2 || nom.length() > 60) {
            throw DomainException.invalide("Le nom doit faire entre 2 et 60 caractères.");
        }
        return nom;
    }

    /** « Sky Club Almadies » → SKY_CLUB_ALMADIES (sans accents, 30 caractères au plus). */
    static String code(String nom) {
        String ascii = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String code = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        code = trimUnderscores(code);
        if (code.isEmpty()) {
            code = TYPE_SALON;
        }
        if (code.length() > 30) {
            code = trimUnderscores(code.substring(0, 30));
        }
        return code;
    }

    private static String trimUnderscores(String s) {
        int debut = 0;
        int fin = s.length();
        while (debut < fin && s.charAt(debut) == '_') {
            debut++;
        }
        while (fin > debut && s.charAt(fin - 1) == '_') {
            fin--;
        }
        return s.substring(debut, fin);
    }
}
