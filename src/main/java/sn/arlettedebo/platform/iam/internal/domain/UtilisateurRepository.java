package sn.arlettedebo.platform.iam.internal.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

    /** {@code email} est en CITEXT : comparaison insensible à la casse, servie par l'index unique. */
    Optional<Utilisateur> findByEmailRecherche(String emailRecherche);

    boolean existsByEmailRecherche(String emailRecherche);

    List<Utilisateur> findAllByActifTrueOrderByNomAsc();

    List<Utilisateur> findAllByOrderByNomAsc();

    /** UUID v7 : ordre décroissant ≈ plus récents d’abord. */
    List<Utilisateur> findAllByOrderByIdDesc();

    @Query("select u.id as id, u.nom as nom from Utilisateur u where u.id in :ids")
    List<IdNom> findNoms(Collection<UUID> ids);

    interface IdNom {
        UUID getId();

        String getNom();
    }
}
