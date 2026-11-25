package sn.arlettedebo.platform.commercial.internal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByTelephoneRecherche(String telephoneRecherche);

    /** Recherche floue sur le nom — index trigramme ix_td_client_nom_trgm. */
    @Query(value = "select * from td_client where perimetre_code in (:perimetres) and nom % :texte order by similarity(nom, :texte) desc limit 20",
            nativeQuery = true)
    List<Client> rechercher(Collection<String> perimetres, String texte);

    /** « Clientes non revenues depuis 90 jours » — index ix_td_client_derniere_visite. */
    List<Client> findByPerimetreCodeInAndDerniereVisiteBefore(Collection<String> perimetres, LocalDate avant);

    long countByPerimetreCodeInAndCreeLeGreaterThanEqualAndCreeLeLessThan(Collection<String> perimetres, java.time.Instant debut,
            java.time.Instant fin);

    long countByPerimetreCodeInAndStatut(Collection<String> perimetres, Client.Statut statut);

    org.springframework.data.domain.Page<Client> findByPerimetreCodeInOrderByIdDesc(Collection<String> perimetres,
            org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<Client> findByPerimetreCodeInAndStatutOrderByIdDesc(Collection<String> perimetres,
            Client.Statut statut, org.springframework.data.domain.Pageable page);
}
