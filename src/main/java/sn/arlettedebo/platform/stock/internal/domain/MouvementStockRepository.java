package sn.arlettedebo.platform.stock.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, UUID> {

    /** Historique d'un article — index ix_td_mouvement_article_date. */
    List<MouvementStock> findByArticleIdOrderByHorodatageDesc(UUID articleId, Pageable page);

    /** Journal récent des mouvements d'un ensemble d'articles (index ix_td_mouvement_article_date). */
    List<MouvementStock> findTop200ByArticleIdInOrderByHorodatageDesc(Collection<UUID> articles);

    Page<MouvementStock> findByArticleIdIn(Collection<UUID> articles, Pageable page);

    Page<MouvementStock> findByArticleIdInAndType(Collection<UUID> articles, MouvementStock.Type type, Pageable page);

    /** Journal du périmètre via sous-requête (pas de IN d'ids en mémoire). Historique inclus articles inactifs. */
    @Query("""
            select m from MouvementStock m
             where exists (
               select 1 from Article a where a.id = m.articleId
                 and a.perimetreCode in :perimetres
                 and (:categorie = '' or a.categorieCode = :categorie))
            """)
    Page<MouvementStock> parPerimetre(@Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie,
            Pageable page);

    @Query("""
            select m from MouvementStock m
             where m.type = :type
               and exists (
                 select 1 from Article a where a.id = m.articleId
                   and a.perimetreCode in :perimetres
                   and (:categorie = '' or a.categorieCode = :categorie))
            """)
    Page<MouvementStock> parTypeEtPerimetre(@Param("type") MouvementStock.Type type,
            @Param("perimetres") Collection<String> perimetres, @Param("categorie") String categorie, Pageable page);
}
