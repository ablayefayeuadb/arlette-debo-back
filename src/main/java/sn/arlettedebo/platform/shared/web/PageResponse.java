package sn.arlettedebo.platform.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Page de résultats stable (indépendante de la sérialisation interne de Spring Data).
 * Contrat commun à toutes les listes paginées : paramètres {@code page} (à partir de 0) et {@code taille}
 * (1 à {@value #TAILLE_MAX}, {@value #TAILLE_DEFAUT} par défaut).
 */
public record PageResponse<T>(List<T> contenu, int page, int taille, long total, int pages) {

    public static final int TAILLE_DEFAUT = 20;
    public static final int TAILLE_MAX = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Page déjà convertie (vues construites en lot pour éviter les requêtes N+1). */
    public static <E, T> PageResponse<T> of(Page<E> page, List<T> contenu) {
        return new PageResponse<>(contenu, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Requête de page bornée (page ≥ 0, taille entre 1 et {@value #TAILLE_MAX}). */
    public static Pageable requete(int page, int taille, Sort tri) {
        return PageRequest.of(Math.max(0, page), Math.clamp(taille, 1, TAILLE_MAX), tri);
    }

    /**
     * Découpe une liste déjà filtrée par les règles métier (droits, périmètre, visibilité calculée en Java).
     * Réservé aux listes de taille bornée (comptes, personnel…) ; les tables volumineuses sont paginées en base.
     */
    public static <E, T> PageResponse<T> decouper(List<E> tous, int page, int taille, Function<List<E>, List<T>> mapper) {
        int t = Math.clamp(taille, 1, TAILLE_MAX);
        int p = Math.max(0, page);
        int debut = (int) Math.min((long) p * t, tous.size());
        int fin = Math.min(debut + t, tous.size());
        int pages = (tous.size() + t - 1) / t;
        return new PageResponse<>(mapper.apply(tous.subList(debut, fin)), p, t, tous.size(), pages);
    }
}
