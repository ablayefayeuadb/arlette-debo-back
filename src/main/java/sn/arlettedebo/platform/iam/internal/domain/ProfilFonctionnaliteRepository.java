package sn.arlettedebo.platform.iam.internal.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfilFonctionnaliteRepository extends JpaRepository<ProfilFonctionnalite, Short> {

    List<ProfilFonctionnalite> findAllByOrderByProfilCodeAscOrdreAsc();
}
