package sn.arlettedebo.platform.rh;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** API publique RH : lecture des effectifs pour les autres modules (planning, qualité, caisse). */
public interface Effectifs {

    /** Dossier du personnel lié à un compte utilisateur. */
    Optional<UUID> employeDuCompte(UUID utilisateurId);

    /** Noms des employés (résolution groupée). */
    Map<UUID, String> noms(Collection<UUID> employes);

    /** Coiffeuses actives d'un périmètre (compétences = codes prestations validés). */
    List<EmployeRef> coiffeuses(String perimetre);

    /**
     * Prestations maîtrisées d'une coiffeuse. Ensemble vide = compétences pas encore renseignées
     * (le planning ne bloque alors pas l'affectation).
     */
    Set<String> competences(UUID employeId);

    record EmployeRef(UUID id, String nom, String perimetre, UUID utilisateurId, Set<String> competences) {
    }
}
