package sn.arlettedebo.platform.commercial.internal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DossierAbonneeRepository extends JpaRepository<DossierAbonnee, UUID> {

    boolean existsByClientId(UUID clientId);

    Optional<DossierAbonnee> findByClientId(UUID clientId);

    @Query("select d.clientId from DossierAbonnee d where d.clientId in :ids")
    java.util.Set<UUID> findClientIdsByClientIdIn(Collection<UUID> ids);

    Optional<DossierAbonnee> findByUtilisateurId(UUID utilisateurId);

    /** Repère du dossier lié à un compte portail : trois colonnes, sans charger le profil JSON ni les champs chiffrés. */
    record Repere(UUID id, UUID clientId, String perimetre) {
    }

    @Query("""
            select new sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository$Repere(
                d.id, d.clientId, d.perimetreCode)
            from DossierAbonnee d where d.utilisateurId = :utilisateurId
            """)
    Optional<Repere> repereParUtilisateur(UUID utilisateurId);

    boolean existsByUtilisateurId(UUID utilisateurId);

    /** Ligne de liste : colonnes utiles uniquement (pas d'entité complète, pas de profil JSON). */
    record Ligne(UUID id, UUID clientId, String perimetre, String nom, String prenom, String telephone,
            DossierAbonnee.TypeAbonnement typeAbonnement, LocalDate dateInscription) {
    }

    /**
     * Dossiers des périmètres : nom / prénom en LIKE ; téléphone = égalité sur index aveugle
     * ({@code telRecherche} nul = ignorer le filtre téléphone).
     */
    @Query(value = """
            select new sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository$Ligne(
                d.id, c.id, d.perimetreCode, c.nom, d.prenom, c.telephone, d.typeAbonnement, d.dateInscription)
            from DossierAbonnee d join Client c on c.id = d.clientId
            where d.perimetreCode in :perimetres
              and (lower(c.nom) like :motif or lower(coalesce(d.prenom, '')) like :motif
                   or (:telRecherche is not null and c.telephoneRecherche = :telRecherche))
            order by d.id desc
            """,
            countQuery = """
            select count(d) from DossierAbonnee d join Client c on c.id = d.clientId
            where d.perimetreCode in :perimetres
              and (lower(c.nom) like :motif or lower(coalesce(d.prenom, '')) like :motif
                   or (:telRecherche is not null and c.telephoneRecherche = :telRecherche))
            """)
    Page<Ligne> chercher(Collection<String> perimetres, String motif, String telRecherche, Pageable page);
}
