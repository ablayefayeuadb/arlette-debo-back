package sn.arlettedebo.platform.stock.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

    /** Plus récents d'abord (id ULID décroissant) — listes non filtrées (pilotage legacy). */
    List<Article> findByPerimetreCodeInAndActifTrueOrderByIdDesc(Collection<String> perimetres);

    List<Article> findByPerimetreCodeInAndActifFalseOrderByIdDesc(Collection<String> perimetres);

    /**
     * Filtres optionnels : passer {@code ""} (jamais null) pour désactiver catégorie / recherche —
     * Postgres type mal un paramètre null dans {@code lower(...)} / {@code LIKE}.
     */
    @Query("""
            select a from Article a
             where a.perimetreCode in :perimetres and a.actif = true
               and (:categorie = '' or a.categorieCode = :categorie)
             order by a.id desc
            """)
    List<Article> findActifs(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    /** Alertes (rupture ou sous seuil), catégorie optionnelle — index partiel ix_td_article_alertes. */
    @Query("""
            select a from Article a
             where a.actif = true and a.stockActuel <= a.seuil and a.perimetreCode in :perimetres
               and (:categorie = '' or a.categorieCode = :categorie)
             order by a.stockActuel asc
            """)
    List<Article> enAlerte(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    /**
     * Catalogue paginé en base : périmètre, actif, catégorie, alertes, recherche, vendables (prix vente &gt; 0).
     * {@code q} / {@code categorie} / {@code typeProduit} vides = pas de filtre.
     */
    @Query("""
            select a from Article a
             where a.perimetreCode in :perimetres
               and a.actif = :actif
               and (:categorie = '' or a.categorieCode = :categorie)
               and (:typeProduit = '' or a.typeProduit = :typeProduit)
               and (:alertes = false or a.stockActuel <= a.seuil)
               and (:vendables = false or (a.prixVente is not null and a.prixVente > 0))
               and (:q = '' or lower(a.nom) like lower(concat('%', :q, '%'))
                    or lower(a.reference) like lower(concat('%', :q, '%'))
                    or lower(coalesce(a.fournisseur, '')) like lower(concat('%', :q, '%')))
            """)
    Page<Article> chercher(@Param("perimetres") Collection<String> perimetres, @Param("actif") boolean actif,
            @Param("categorie") String categorie, @Param("alertes") boolean alertes, @Param("q") String q,
            @Param("vendables") boolean vendables, @Param("typeProduit") String typeProduit, Pageable page);

    /** Identifiants seuls (journal / comptages) — pas d'hydratation complète. */
    @Query("""
            select a.id from Article a
             where a.perimetreCode in :perimetres and a.actif = true
               and (:categorie = '' or a.categorieCode = :categorie)
            """)
    List<UUID> idsActifs(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @Query("""
            select count(a) from Article a
             where a.perimetreCode in :perimetres and a.actif = true
               and (:categorie = '' or a.categorieCode = :categorie)
            """)
    long compterActifs(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @Query("""
            select count(a) from Article a
             where a.perimetreCode in :perimetres and a.actif = true and a.stockActuel <= a.seuil
               and (:categorie = '' or a.categorieCode = :categorie)
            """)
    long compterAlertes(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @Query("""
            select count(a) from Article a
             where a.perimetreCode in :perimetres and a.actif = false
               and (:categorie = '' or a.categorieCode = :categorie)
            """)
    long compterInactifs(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @Query("""
            select coalesce(sum(a.stockActuel * coalesce(a.prixAchat, 0)), 0) from Article a
             where a.perimetreCode in :perimetres and a.actif = true
               and (:categorie = '' or a.categorieCode = :categorie)
            """)
    long valeurStock(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @Query("""
            select count(a) from Article a
             where a.perimetreCode in :perimetres and a.actif = true and a.stockActuel = 0
               and (:categorie = '' or a.categorieCode = :categorie)
            """)
    long compterRuptures(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @Query("""
            select count(a) from Article a
             where a.perimetreCode in :perimetres and a.actif = true
               and a.stockActuel > 0 and a.stockActuel <= a.seuil
               and (:categorie = '' or a.categorieCode = :categorie)
            """)
    long compterSousSeuil(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @org.springframework.data.jpa.repository.Query(value = "select nextval('sq_article_reference')", nativeQuery = true)
    long prochainNumero();
}
