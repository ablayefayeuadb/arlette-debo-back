package sn.arlettedebo.platform.iam.internal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DelegationRepository extends JpaRepository<Delegation, UUID> {

    /** Servie par l'index partiel ix_td_delegation_beneficiaire_active. */
    List<Delegation> findByBeneficiaireProfilCodeAndActiveTrue(String profilCode);

    Optional<Delegation> findByCode(String code);

    List<Delegation> findAllByOrderByTitreAsc();
}
