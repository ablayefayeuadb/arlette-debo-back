package sn.arlettedebo.platform.stock.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComptageRepository extends JpaRepository<Comptage, UUID> {

    /** Écarts à traiter sur des articles donnés, comptés en base (index ix_td_comptage_ecarts / ix_td_comptage_article). */
    long countByStatutAndArticleIdIn(Comptage.Statut statut, Collection<UUID> articles);

    long countByArticleIdIn(Collection<UUID> articles);

    boolean existsByArticleIdAndStatut(UUID articleId, Comptage.Statut statut);

    /** Identifiants d'articles avec un écart encore ouvert (blocage d'un nouveau comptage). */
    @Query("select distinct c.articleId from Comptage c where c.statut = :statut and c.articleId in :articles")
    List<UUID> articleIdsEcartsOuverts(@Param("statut") Comptage.Statut statut, @Param("articles") Collection<UUID> articles);

    /**
     * Écarts ouverts sur le périmètre (sous-requête article) — sans charger tous les ids en mémoire.
     * Articles inactifs exclus : un nouvel inventaire ne porte que sur le catalogue actif.
     */
    @Query("""
            select distinct c.articleId from Comptage c
             where c.statut = :statut
               and exists (
                 select 1 from Article a where a.id = c.articleId and a.actif = true
                   and a.perimetreCode in :perimetres
                   and (:categorie = '' or a.categorieCode = :categorie))
            """)
    List<UUID> articleIdsEcartsOuvertsParPerimetre(@Param("statut") Comptage.Statut statut,
            @Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    java.util.List<Comptage> findByStatutAndArticleIdInOrderByHorodatageDesc(Comptage.Statut statut, Collection<UUID> articles);

    java.util.List<Comptage> findTop100ByArticleIdInOrderByHorodatageDesc(Collection<UUID> articles);

    org.springframework.data.domain.Page<Comptage> findByArticleIdIn(Collection<UUID> articles, org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<Comptage> findByStatutAndArticleIdIn(Comptage.Statut statut, Collection<UUID> articles, org.springframework.data.domain.Pageable page);

    /** Historique des comptages du périmètre (articles actifs ou non — historique conservé). */
    @Query("""
            select c from Comptage c
             where exists (
               select 1 from Article a where a.id = c.articleId
                 and a.perimetreCode in :perimetres
                 and (:categorie = '' or a.categorieCode = :categorie))
            """)
    org.springframework.data.domain.Page<Comptage> parPerimetre(@Param("perimetres") Collection<String> perimetres,
            @Param("categorie") String categorie, org.springframework.data.domain.Pageable page);

    @Query("""
            select c from Comptage c
             where c.statut = :statut
               and exists (
                 select 1 from Article a where a.id = c.articleId
                   and a.perimetreCode in :perimetres
                   and (:categorie = '' or a.categorieCode = :categorie))
            """)
    org.springframework.data.domain.Page<Comptage> parStatutEtPerimetre(@Param("statut") Comptage.Statut statut,
            @Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie,
            org.springframework.data.domain.Pageable page);

    @Query("""
            select count(c) from Comptage c
             where exists (
               select 1 from Article a where a.id = c.articleId
                 and a.perimetreCode in :perimetres
                 and (:categorie = '' or a.categorieCode = :categorie))
            """)
    long countParPerimetre(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);

    @Query("""
            select count(c) from Comptage c
             where c.statut = :statut
               and exists (
                 select 1 from Article a where a.id = c.articleId
                   and a.perimetreCode in :perimetres
                   and (:categorie = '' or a.categorieCode = :categorie))
            """)
    long countParStatutEtPerimetre(@Param("statut") Comptage.Statut statut, @Param("perimetres") Collection<String> perimetres,
            @Param("categorie") String categorie);

    /** Écarts signalés du périmètre (pilotage) — sans hydrater tous les articles d'abord. */
    @Query("""
            select c from Comptage c
             where c.statut = :statut
               and exists (
                 select 1 from Article a where a.id = c.articleId and a.actif = true
                   and a.perimetreCode in :perimetres
                   and (:categorie = '' or a.categorieCode = :categorie))
             order by c.horodatage desc
            """)
    List<Comptage> ecartsSignalesParPerimetre(@Param("statut") Comptage.Statut statut,
            @Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie);
}
