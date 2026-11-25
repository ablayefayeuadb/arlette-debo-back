package sn.arlettedebo.platform.iam.internal.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfilModuleRepository extends JpaRepository<ProfilModule, ProfilModule.Cle> {

    /** Cellules d'un profil — utilise la clé primaire (profil_code, module_code). */
    List<ProfilModule> findByIdProfilCode(String profilCode);
}
