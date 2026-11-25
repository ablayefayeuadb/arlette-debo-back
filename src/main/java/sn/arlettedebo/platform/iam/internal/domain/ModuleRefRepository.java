package sn.arlettedebo.platform.iam.internal.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRefRepository extends JpaRepository<ModuleRef, String> {

    List<ModuleRef> findAllByOrderByOrdreAsc();
}
