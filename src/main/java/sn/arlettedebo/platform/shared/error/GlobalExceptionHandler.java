package sn.arlettedebo.platform.shared.error;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Toutes les erreurs sortent au format Problem Details (RFC 9457) avec une propriété {@code code}.
 * Aucune trace technique n'est exposée au client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI TYPE_BASE = URI.create("https://arlettedebo.sn/problemes/");

    @ExceptionHandler(DomainException.class)
    public ProblemDetail domaine(DomainException ex) {
        return probleme(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesRefuse(AccessDeniedException ex) {
        return probleme(HttpStatus.FORBIDDEN, "ACCES_REFUSE", "Votre profil n'est pas habilité pour cette action.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail concurrence(OptimisticLockingFailureException ex) {
        return probleme(HttpStatus.CONFLICT, "MODIFICATION_CONCURRENTE",
                "Le dossier a été modifié entre-temps par une autre personne. Rechargez puis recommencez.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail validation(ConstraintViolationException ex) {
        ProblemDetail pd = probleme(HttpStatus.BAD_REQUEST, "VALIDATION", "Requête invalide.");
        Map<String, String> erreurs = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> erreurs.put(v.getPropertyPath().toString(), v.getMessage()));
        pd.setProperty("erreurs", erreurs);
        return pd;
    }

    /** Les contraintes SQL portent aussi des règles de gestion : on les traduit en message métier. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrite(DataIntegrityViolationException ex) {
        String contrainte = nomContrainte(ex);
        log.warn("Violation de contrainte {}", contrainte);
        if (contrainte == null) {
            return probleme(HttpStatus.CONFLICT, "CONTRAINTE", "Opération incompatible avec les données existantes.");
        }
        if (contrainte.contains("separation") || contrainte.contains("independant")
                || contrainte.startsWith("ck_td_depense_valideur") || contrainte.startsWith("ck_td_depense_payeur")
                || contrainte.startsWith("ck_td_depense_controleur")) {
            return probleme(HttpStatus.FORBIDDEN, "SEPARATION_DES_TACHES",
                    "Séparation des tâches : une autre personne doit intervenir à cette étape.");
        }
        if (contrainte.contains("sans_coiffeuse")) {
            return probleme(HttpStatus.CONFLICT, "CRENEAU_OCCUPE",
                    "Un rendez-vous sans coiffeuse occupe déjà ce créneau : assignez une coiffeuse ou choisissez un autre horaire.");
        }
        if (contrainte.startsWith("ex_td_rendez_vous")) {
            return probleme(HttpStatus.CONFLICT, "CRENEAU_OCCUPE", "La coiffeuse a déjà un rendez-vous sur ce créneau.");
        }
        if (contrainte.startsWith("ux_") || contrainte.startsWith("uq_") || contrainte.endsWith("_pkey")) {
            return probleme(HttpStatus.CONFLICT, "DOUBLON", "Cet enregistrement existe déjà.");
        }
        return probleme(HttpStatus.CONFLICT, "CONTRAINTE", "Opération refusée par une règle de cohérence (" + contrainte + ").");
    }

    @ExceptionHandler(sn.arlettedebo.platform.shared.storage.JustificatifStorage.StockageIndisponibleException.class)
    public ProblemDetail stockage(RuntimeException ex) {
        return probleme(HttpStatus.SERVICE_UNAVAILABLE, "STOCKAGE_INDISPONIBLE", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inattendue(Exception ex) {
        log.error("Erreur inattendue", ex);
        return probleme(HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE", "Une erreur inattendue est survenue.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = probleme(HttpStatus.BAD_REQUEST, "VALIDATION", "Requête invalide.");
        Map<String, String> erreurs = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> erreurs.putIfAbsent(e.getField(), e.getDefaultMessage()));
        pd.setProperty("erreurs", erreurs);
        return ResponseEntity.badRequest().body(pd);
    }

    public static ProblemDetail probleme(HttpStatus status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setType(TYPE_BASE.resolve(code.toLowerCase().replace('_', '-')));
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        return pd;
    }

    /** Nom de la contrainte : Hibernate, sinon message PostgreSQL (cas des contraintes d'exclusion, SQLSTATE 23P01). */
    private static String nomContrainte(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve && identifiant(cve.getConstraintName())) {
                return cve.getConstraintName().toLowerCase();
            }
            if (t instanceof java.sql.SQLException sql && sql.getMessage() != null) {
                java.util.regex.Matcher m = CONTRAINTE_PG.matcher(sql.getMessage());
                if (m.find()) {
                    return m.group(1).toLowerCase();
                }
            }
        }
        return null;
    }

    /** Nom SQL valide uniquement : jamais de fragment de message (colonne, table) renvoyé au client. */
    private static boolean identifiant(String nom) {
        return nom != null && nom.matches("\\w{1,63}");
    }

    private static final java.util.regex.Pattern CONTRAINTE_PG = java.util.regex.Pattern.compile("constraint \"([a-z0-9_]{1,63})\"");
}
