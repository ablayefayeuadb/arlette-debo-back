package sn.arlettedebo.platform.iam.internal.admin;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.iam.internal.domain.Profil;
import sn.arlettedebo.platform.iam.internal.domain.ProfilRepository;
import sn.arlettedebo.platform.iam.internal.security.RightsService;
import sn.arlettedebo.platform.referentiel.PerimetreEvenement;
import sn.arlettedebo.platform.referentiel.Referentiels;

/**
 * Réagit aux changements de salons / boutiques, dans la même transaction :
 * <ul>
 *   <li>nouveau périmètre : rattaché aux profils transverses (ceux qui couvrent déjà <em>tous</em> les actifs
 *       du même type, ou tous les salons si c'est la première boutique) ;</li>
 *   <li>nouvelle boutique : le vendeur désigné à la création reçoit le périmètre sur son compte ;</li>
 *   <li>profils d'un seul lieu (Caissier, Coiffeur…) ne changent pas ;</li>
 *   <li>tout changement : droits recalculés à la requête suivante.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SalonsListener {

    private final ProfilRepository profils;
    private final Referentiels referentiels;
    private final RightsService rights;
    private final CompteService comptes;

    @EventListener
    void surCreation(PerimetreEvenement.Cree e) {
        Set<String> memeType = new HashSet<>(referentiels.perimetresActifs(e.type()));
        memeType.remove(e.code());
        // Première boutique : pas encore de « frère » — on s'aligne sur les profils qui couvrent tous les salons
        Set<String> reference = referencePourNouveauPerimetre(memeType, e.type());
        for (Profil p : profils.findAll()) {
            boolean transverse = p.getPerimetres().size() > 1 && !reference.isEmpty() && p.getPerimetres().containsAll(reference);
            if (transverse) {
                p.rattacher(e.code());
                log.info("Périmètre {} ({}) rattaché au profil {}", e.code(), e.type(), p.getCode());
            }
        }
        if ("BOUTIQUE".equals(e.type()) && e.responsableId() != null) {
            comptes.rattacherBoutique(e.responsableId(), e.code());
        }
        rights.invaliderTout();
    }

    @EventListener
    void surModification(PerimetreEvenement.Modifie e) {
        rights.invaliderTout();
    }

    private Set<String> referencePourNouveauPerimetre(Set<String> memeType, String type) {
        if (!memeType.isEmpty()) {
            return memeType;
        }
        if ("BOUTIQUE".equals(type)) {
            return new HashSet<>(referentiels.perimetresActifs("SALON"));
        }
        return Set.of();
    }
}
