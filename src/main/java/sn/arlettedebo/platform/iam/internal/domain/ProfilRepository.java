package sn.arlettedebo.platform.iam.internal.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfilRepository extends JpaRepository<Profil, String> {

    List<Profil> findAllByOrderByOrdreAsc();
}
