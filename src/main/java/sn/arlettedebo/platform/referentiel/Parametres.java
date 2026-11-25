package sn.arlettedebo.platform.referentiel;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.referentiel.internal.Parametre;
import sn.arlettedebo.platform.referentiel.internal.ParametreRepository;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/** API publique : lecture/écriture typée des paramètres applicatifs ({@code tp_parametre}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Parametres {

    /** Montant au-delà duquel la validation DG est obligatoire. */
    public static final String SEUIL_DG = "finance.seuil-dg";
    public static final String ROTATION_QR = "pointage.rotation-qr-s";
    public static final String TOLERANCE_RETARD = "pointage.tolerance-min";
    /** Heure de fin de poste proposée pour un nouveau dossier du personnel (HH:MM). */
    public static final String HEURE_FIN_DEFAUT = "pointage.heure-fin-defaut";
    /** Tolérance avant qu'un départ soit compté comme anticipé (minutes). */
    public static final String TOLERANCE_DEPART = "pointage.tolerance-depart-min";
    /** Heure à laquelle les départs non pointés de la journée sont signalés (HH:MM). */
    public static final String CONTROLE_DEPART = "pointage.controle-depart";

    private final ParametreRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public long montant(String cle) {
        return Long.parseLong(charger(cle).getValeur());
    }

    public int entier(String cle) {
        return Integer.parseInt(charger(cle).getValeur());
    }

    /** Paramètre de type HEURE (HH:MM). */
    public java.time.LocalTime heure(String cle) {
        return java.time.LocalTime.parse(charger(cle).getValeur());
    }

    public ParametreVue lire(String cle) {
        Parametre p = charger(cle);
        return new ParametreVue(p.getCle(), p.getValeur(), p.getType(), p.getDescription(), p.getModifiePar(), p.getModifieLe());
    }

    /** Tous les paramètres en une requête (le catalogue est court et borné ; pas de relecture par clé). */
    public java.util.List<ParametreVue> tous() {
        return repository.findAll(org.springframework.data.domain.Sort.by("cle")).stream()
                .map(p -> new ParametreVue(p.getCle(), p.getValeur(), p.getType(), p.getDescription(), p.getModifiePar(), p.getModifieLe()))
                .toList();
    }

    /** L'autorisation (DG) est vérifiée par l'appelant ; la valeur est validée selon le type déclaré. */
    @Transactional
    public ParametreVue modifier(String cle, String valeur, UUID par) {
        Parametre p = charger(cle);
        String normalisee = valeur == null ? "" : valeur.trim();
        valider(p.getType(), normalisee);
        if (ROTATION_QR.equals(cle) && (Long.parseLong(normalisee) < 30 || Long.parseLong(normalisee) > 300)) {
            throw DomainException.invalide("La rotation du QR doit être comprise entre 30 et 300 secondes.");
        }
        String ancienne = p.getValeur();
        p.modifier(normalisee, par, clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Paramètre modifié", cle + " : " + ancienne + " → " + normalisee));
        return lire(cle);
    }

    /** Contrôle de la valeur selon le type déclaré du paramètre. */
    private static void valider(String type, String valeur) {
        String erreur = switch (type) {
            case "ENTIER", "MONTANT" -> entierPositif(valeur) ? null : "La valeur doit être un nombre entier positif.";
            case "BOOLEEN" -> "true".equals(valeur) || "false".equals(valeur) ? null : "La valeur doit être true ou false.";
            case "HEURE" -> valeur.matches("^([01]\\d|2[0-3]):[0-5]\\d$") ? null : "L'heure doit être au format HH:MM (ex. 19:00).";
            default -> valeur.isEmpty() ? "La valeur est obligatoire." : null;
        };
        if (erreur != null) {
            throw DomainException.invalide(erreur);
        }
    }

    private static boolean entierPositif(String valeur) {
        try {
            return Long.parseLong(valeur) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Parametre charger(String cle) {
        return repository.findById(cle).orElseThrow(() -> DomainException.introuvable("Paramètre « " + cle + " »"));
    }

    public record ParametreVue(String cle, String valeur, String type, String description, UUID modifiePar, java.time.Instant modifieLe) {
    }
}
