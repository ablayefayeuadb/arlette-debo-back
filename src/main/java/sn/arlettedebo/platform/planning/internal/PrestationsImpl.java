package sn.arlettedebo.platform.planning.internal;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.planning.Prestations;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.shared.error.DomainException;

@Component
@RequiredArgsConstructor
class PrestationsImpl implements Prestations {

    private final RendezVousRepository rdvs;
    private final AccessPolicy access;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PrestationRealisee verifier(UUID rendezVousId, boolean conforme) {
        RendezVous r = rdvs.findById(rendezVousId).orElseThrow(() -> DomainException.introuvable("Rendez-vous"));
        access.exigerPerimetre(r.getPerimetreCode());
        if (r.getVerifieLe() != null) {
            throw DomainException.etatInvalide("Cette prestation a déjà été contrôlée.");
        }
        r.verifier(access.utilisateur().id(), clock.instant(), conforme);
        return new PrestationRealisee(r.getId(), r.getPerimetreCode(), r.getPrestationCode(), r.getCoiffeuseId());
    }
}
