package sn.arlettedebo.platform.referentiel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.referentiel.internal.ChecklistItem;
import sn.arlettedebo.platform.referentiel.internal.Formule;
import sn.arlettedebo.platform.referentiel.internal.OptionSuiviSoin;
import sn.arlettedebo.platform.referentiel.internal.Perimetre;
import sn.arlettedebo.platform.referentiel.internal.Prestation;
import sn.arlettedebo.platform.referentiel.internal.Referentiel;
import sn.arlettedebo.platform.referentiel.internal.TypeAbsence;
import sn.arlettedebo.platform.referentiel.internal.TypeVariablePaie;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * API publique du module référentiel : contrôle d'existence et lecture des catalogues.
 * Les catalogues changent rarement : lecture mise en cache.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Referentiels {

    private final EntityManager em;

    public boolean existe(TypeReferentiel type, String code) {
        return code != null && em.find(type.entite(), code) != null;
    }

    /** Lève une erreur 422 si le code n'existe pas dans le catalogue. */
    public void exiger(TypeReferentiel type, String code) {
        if (!existe(type, code)) {
            throw DomainException.invalide("Valeur inconnue pour " + type.cle() + " : " + code);
        }
    }

    public Optional<String> libelle(TypeReferentiel type, String code) {
        return Optional.ofNullable(code).map(c -> em.find(type.entite(), c)).map(Referentiel::getLibelle);
    }

    /** Codes des salons actifs : un salon désactivé n'ouvre plus aucun accès. */
    public java.util.Set<String> perimetresActifs() {
        return em.createQuery("select p.code from Perimetre p where p.actif = true", String.class).getResultStream()
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Codes des salons actifs d'un type ({@code SALON}). */
    public java.util.Set<String> perimetresActifs(String type) {
        return em.createQuery("select p.code from Perimetre p where p.actif = true and p.type = :type", String.class)
                .setParameter("type", type).getResultStream().collect(Collectors.toUnmodifiableSet());
    }

    /** Congé : soumis à décision RH ; autres types enregistrés directement. */
    public boolean absenceSoumiseAValidation(String typeAbsence) {
        TypeAbsence t = em.find(TypeAbsence.class, typeAbsence);
        if (t == null) {
            throw DomainException.invalide("Type d'absence inconnu : " + typeAbsence);
        }
        return t.isSoumisValidation();
    }

    /** +1 (gain) ou -1 (retenue). */
    public int sensVariablePaie(String typeVariable) {
        TypeVariablePaie t = em.find(TypeVariablePaie.class, typeVariable);
        if (t == null) {
            throw DomainException.invalide("Type de variable de paie inconnu : " + typeVariable);
        }
        return t.getSens();
    }

    /** Prestation : périmètre, durée et prix catalogue. */
    public Optional<PrestationInfo> prestation(String code) {
        return Optional.ofNullable(em.find(Prestation.class, code))
                .map(p -> new PrestationInfo(p.getCode(), p.getLibelle(), p.getPerimetreCode(), p.getDureeMin(), p.getPrix(), p.getCategorieCode()));
    }

    /** Capacité et plage horaire type d'un salon (grille de créneaux). */
    public Optional<ParametresCreneaux> parametresCreneaux(String perimetre) {
        return Optional.ofNullable(em.find(Perimetre.class, perimetre))
                .map(p -> new ParametresCreneaux(p.getCapaciteCreneau(), p.getHeureOuverture(), p.getHeureFermeture()));
    }

    /** Prestations actives réservables, dans l'ordre du catalogue (réservation en ligne) — filtre et tri en SQL. */
    public List<PrestationInfo> prestationsActives() {
        return em.createQuery("""
                select new sn.arlettedebo.platform.referentiel.Referentiels$PrestationInfo(
                    p.code, p.libelle, p.perimetreCode, cast(p.dureeMin as integer), p.prix, p.categorieCode)
                from Prestation p where p.actif = true order by p.perimetreCode, p.ordre, p.code
                """, PrestationInfo.class).getResultList();
    }

    /** Prestations actives d'un seul salon, dans l'ordre du catalogue — filtre et tri en SQL. */
    public List<PrestationInfo> prestationsActives(String perimetre) {
        return em.createQuery("""
                select new sn.arlettedebo.platform.referentiel.Referentiels$PrestationInfo(
                    p.code, p.libelle, p.perimetreCode, cast(p.dureeMin as integer), p.prix, p.categorieCode)
                from Prestation p where p.actif = true and p.perimetreCode = :perimetre order by p.ordre, p.code
                """, PrestationInfo.class).setParameter("perimetre", perimetre).getResultList();
    }

    /** Une prestation active d'un salon (lecture ciblée, sans charger tout le catalogue). */
    public Optional<PrestationInfo> prestationActive(String perimetre, String code) {
        if (perimetre == null || code == null) {
            return Optional.empty();
        }
        return em.createQuery("""
                select new sn.arlettedebo.platform.referentiel.Referentiels$PrestationInfo(
                    p.code, p.libelle, p.perimetreCode, cast(p.dureeMin as integer), p.prix, p.categorieCode)
                from Prestation p where p.actif = true and p.perimetreCode = :perimetre and p.code = :code
                """, PrestationInfo.class).setParameter("perimetre", perimetre).setParameter("code", code)
                .getResultStream().findFirst();
    }

    /** Libellés d'un lot de prestations (une requête {@code IN}) : code → libellé. */
    public Map<String, String> libellesPrestations(java.util.Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        return em.createQuery("select p.code, p.libelle from Prestation p where p.code in :codes", Object[].class)
                .setParameter("codes", codes).getResultList().stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (String) r[1]));
    }

    /** Tous les catalogues, pour le front (un seul appel au démarrage). */
    @Cacheable("referentiels")
    public Map<String, List<Object>> tous() {
        return java.util.Arrays.stream(TypeReferentiel.values())
                .collect(Collectors.toMap(TypeReferentiel::cle, t -> List.copyOf(lister(t.entite())),
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /** Requête typée (Criteria API) : aucune concaténation de chaîne dans le JPQL. */
    private <T extends Referentiel> List<T> lister(Class<T> entite) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> q = cb.createQuery(entite);
        Root<T> r = q.from(entite);
        return em.createQuery(q.select(r).orderBy(cb.asc(r.get("code")))).getResultList();
    }

    /** Formule d'abonnement : périmètre, prix, nombre de séances (null = illimité) et durée. */
    public Optional<FormuleInfo> formule(String code) {
        return Optional.ofNullable(code).map(c -> em.find(Formule.class, c)).filter(Formule::isActif)
                .map(f -> new FormuleInfo(f.getCode(), f.getLibelle(), f.getPerimetreCode(), f.getPrix(), f.getNbSeances(), f.getDureeJours()));
    }

    /** Options actives du calendrier de soins abonnée, triées par catégorie puis ordre. */
    public List<OptionSuiviInfo> optionsSuiviSoinActives() {
        return lister(OptionSuiviSoin.class).stream().filter(OptionSuiviSoin::isActif)
                .sorted(java.util.Comparator.comparing(OptionSuiviSoin::getCategorie).thenComparingInt(OptionSuiviSoin::getOrdre))
                .map(o -> new OptionSuiviInfo(o.getCode(), o.getLibelle(), o.getCategorie(), o.getOrdre()))
                .toList();
    }

    /** Points de contrôle d'une check-list d'hygiène (ouverture, après prestation, fermeture), dans l'ordre. */
    @Cacheable("referentiels-checklist")
    public List<ItemChecklist> checklist(String moment) {
        return lister(ChecklistItem.class).stream().filter(i -> i.isActif() && i.getMoment().equals(moment))
                .sorted(java.util.Comparator.comparingInt(ChecklistItem::getOrdre))
                .map(i -> new ItemChecklist(i.getCode(), i.getLibelle())).toList();
    }

    public record ItemChecklist(String code, String libelle) {
    }

    public record OptionSuiviInfo(String code, String libelle, String categorie, int ordre) {
    }

    public record FormuleInfo(String code, String libelle, String perimetre, long prix, Short nbSeances, int dureeJours) {
    }

    public record PrestationInfo(String code, String libelle, String perimetre, int dureeMin, long prix,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String categorieCode) {
    }

    /** Grille type d'un salon : places par créneau et plage horaire (ouverture inclusive, fermeture exclusive). */
    public record ParametresCreneaux(int capacite, int heureOuverture, int heureFermeture) {
    }
}
