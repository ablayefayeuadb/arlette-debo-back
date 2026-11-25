package sn.arlettedebo.platform.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier normalisée, convertie en {@code application/problem+json} (RFC 9457)
 * avec un {@code code} stable exploitable par le front.
 */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Ressource introuvable (404). */
    public static DomainException introuvable(String ressource) {
        return new DomainException(HttpStatus.NOT_FOUND, "INTROUVABLE", ressource + " introuvable.");
    }

    /** Transition d'état impossible (409). */
    public static DomainException etatInvalide(String message) {
        return new DomainException(HttpStatus.CONFLICT, "ETAT_INVALIDE", message);
    }

    /** Règle de gestion violée — ex. séparation des tâches (403). */
    public static DomainException regle(String code, String message) {
        return new DomainException(HttpStatus.FORBIDDEN, code, message);
    }

    /** Donnée métier invalide (422). */
    public static DomainException invalide(String message) {
        return new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "DONNEE_INVALIDE", message);
    }
}
