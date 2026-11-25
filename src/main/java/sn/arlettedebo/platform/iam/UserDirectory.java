package sn.arlettedebo.platform.iam;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Annuaire en lecture seule : résolution des noms d'utilisateurs pour les autres modules. */
public interface UserDirectory {

    Optional<UserRef> parId(UUID id);

    /** Résolution groupée (une seule requête) — évite le N+1 dans les listes. */
    Map<UUID, String> noms(Collection<UUID> ids);

    List<UserRef> actifs();

    /** Libellés longs des profils (code → libellé), pour nommer un poste par défaut. */
    Map<String, String> libellesProfils();

    /** Périmètres par défaut d'un profil (ex. salons couverts par {@code MANAGER}). */
    Set<String> perimetresDuProfil(String profilCode);

    record UserRef(UUID id, String nom, String email, String profil, Set<String> perimetres) {
    }
}
