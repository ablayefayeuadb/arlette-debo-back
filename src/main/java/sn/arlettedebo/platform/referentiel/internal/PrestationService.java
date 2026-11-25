package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.EntityManager;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Catalogue des prestations d'un salon : création et mise à jour par le Manager.
 * Jamais supprimée — désactivée, elle disparaît des choix de RDV / caisse.
 */
@Service
@Transactional
public class PrestationService {

    private final EntityManager em;
    private final ApplicationEventPublisher events;
    private final PrestationService self;
    private final PerimetreService salons;

    public PrestationService(EntityManager em, ApplicationEventPublisher events, @Lazy PrestationService self,
            PerimetreService salons) {
        this.em = em;
        this.events = events;
        this.self = self;
        this.salons = salons;
    }

    @Transactional(readOnly = true)
    public List<Prestation> lister(String perimetreCode) {
        if (perimetreCode == null || perimetreCode.isBlank()) {
            return em.createQuery("select p from Prestation p order by p.perimetreCode, p.ordre, p.code", Prestation.class)
                    .getResultList();
        }
        return em.createQuery(
                "select p from Prestation p where p.perimetreCode = :p order by p.ordre, p.code", Prestation.class)
                .setParameter("p", perimetreCode)
                .getResultList();
    }

    /**
     * Catalogue paginé en base : salon, texte (libellé ou code) et catégorie facultatifs.
     * Paramètres vides = pas de filtre (jamais null dans la requête : typage Postgres).
     */
    @Transactional(readOnly = true)
    public Page<Prestation> rechercher(String perimetreCode, String q, String categorieCode, Pageable page) {
        String perimetre = perimetreCode == null ? "" : perimetreCode.trim();
        String recherche = q == null ? "" : q.trim();
        String categorie = Prestation.categorie(categorieCode);
        categorie = categorie == null ? "" : categorie;
        String filtre = """
                 where (:perimetre = '' or p.perimetreCode = :perimetre)
                   and (:categorie = '' or p.categorieCode = :categorie)
                   and (:q = '' or lower(p.libelle) like lower(concat('%', :q, '%'))
                        or lower(p.code) like lower(concat('%', :q, '%')))
                """;
        Long total = em.createQuery("select count(p) from Prestation p" + filtre, Long.class)
                .setParameter("perimetre", perimetre).setParameter("categorie", categorie).setParameter("q", recherche)
                .getSingleResult();
        List<Prestation> contenu = total == 0 ? List.of()
                : em.createQuery("select p from Prestation p" + filtre + " order by p.perimetreCode, p.ordre, p.code", Prestation.class)
                        .setParameter("perimetre", perimetre).setParameter("categorie", categorie).setParameter("q", recherche)
                        .setFirstResult((int) page.getOffset()).setMaxResults(page.getPageSize())
                        .getResultList();
        return new PageImpl<>(contenu, page, total);
    }

    @Transactional(readOnly = true)
    public Prestation exiger(String code) {
        Prestation p = em.find(Prestation.class, code);
        if (p == null) {
            throw DomainException.introuvable("Prestation");
        }
        return p;
    }

    @CacheEvict(value = "referentiels", allEntries = true)
    public Prestation creer(String libelle, String perimetreCode, int dureeMin, long prix, String categorieCode) {
        Perimetre salon = salons.exiger(perimetreCode);
        if (!PerimetreService.TYPE_SALON.equals(salon.getType())) {
            throw DomainException.invalide("Les prestations ne concernent que les salons de coiffure.");
        }
        if (!salon.isActif()) {
            throw DomainException.regle("SALON_INACTIF", "Ce salon est désactivé.");
        }
        String nom = nom(libelle);
        Prestation.exigerDuree(dureeMin);
        Prestation.exigerPrix(prix);
        String categorie = Prestation.categorie(categorieCode);
        exigerNomLibre(nom, perimetreCode, null);
        String code = codeUnique(nom);
        short ordre = (short) (self.lister(perimetreCode).stream().mapToInt(Prestation::getOrdre).max().orElse(0) + 1);
        Prestation p = new Prestation(code, nom, perimetreCode, (short) dureeMin, prix, ordre, categorie);
        em.persist(p);
        events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Prestation créée", nom + " (" + code + ") — " + perimetreCode));
        return p;
    }

    @CacheEvict(value = "referentiels", allEntries = true)
    public Prestation modifier(String code, String libelle, int dureeMin, long prix, boolean actif, Integer ordre,
            String categorieCode) {
        Prestation p = self.exiger(code);
        String nom = nom(libelle);
        exigerNomLibre(nom, p.getPerimetreCode(), code);
        short o = ordre != null ? ordre.shortValue() : p.getOrdre();
        if (o < 0 || o > 999) {
            throw DomainException.invalide("Ordre entre 0 et 999.");
        }
        String ancien = p.getLibelle();
        boolean etatChange = p.isActif() != actif;
        p.modifier(nom, (short) dureeMin, prix, actif, o, Prestation.categorie(categorieCode));
        if (!ancien.equals(nom)) {
            events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, "Prestation renommée", ancien + " → " + nom));
        }
        if (etatChange) {
            events.publishEvent(AuditEvent.of(ModuleCode.PLANNING, actif ? "Prestation réactivée" : "Prestation désactivée", nom));
        }
        return p;
    }

    private void exigerNomLibre(String nom, String perimetreCode, String saufCode) {
        boolean pris = self.lister(perimetreCode).stream()
                .anyMatch(p -> p.getLibelle().equalsIgnoreCase(nom) && !p.getCode().equals(saufCode));
        if (pris) {
            throw new DomainException(HttpStatus.CONFLICT, "PRESTATION_EXISTANTE",
                    "Une prestation porte déjà ce nom dans ce salon.");
        }
    }

    private String codeUnique(String nom) {
        String base = code(nom);
        String code = base;
        for (int i = 2; em.find(Prestation.class, code) != null; i++) {
            code = base.substring(0, Math.min(base.length(), 36)) + "_" + i;
            if (code.length() > 40) {
                code = code.substring(0, 40);
            }
        }
        return code;
    }

    private static String nom(String libelle) {
        String nom = libelle == null ? "" : libelle.trim().replaceAll("\\s+", " ");
        if (nom.length() < 2 || nom.length() > 120) {
            throw DomainException.invalide("Le nom doit faire entre 2 et 120 caractères.");
        }
        return nom;
    }

    /** « Brushing express » → BRUSHING_EXPRESS (sans accents, 40 caractères au plus). */
    static String code(String nom) {
        String ascii = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String code = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        code = trimUnderscores(code);
        if (code.isEmpty()) {
            code = "PRESTATION";
        }
        if (code.length() > 40) {
            code = trimUnderscores(code.substring(0, 40));
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
