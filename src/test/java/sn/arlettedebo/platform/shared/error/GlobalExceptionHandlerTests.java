package sn.arlettedebo.platform.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.sql.SQLException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage;

/** Traduction des exceptions en Problem Details (codes métier). */
@DisplayName("GlobalExceptionHandler : Problem Details")
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DomainException, accès refusé, concurrence, stockage, erreur inattendue")
    void handlersSimples() {
        var domaine = handler.domaine(DomainException.invalide("X"));
        assertThat(domaine.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
        assertThat(domaine.getProperties()).containsEntry("code", DomainException.invalide("X").code());

        assertThat(handler.accesRefuse(new AccessDeniedException("x")).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(handler.concurrence(new OptimisticLockingFailureException("x")).getStatus())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(handler.stockage(new JustificatifStorage.StockageIndisponibleException()).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(handler.inattendue(new RuntimeException("boom")).getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    @DisplayName("ConstraintViolationException : détail des champs")
    void validationBean() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> v = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("email");
        when(v.getPropertyPath()).thenReturn(path);
        when(v.getMessage()).thenReturn("invalide");
        var pd = handler.validation(new ConstraintViolationException(Set.of(v)));
        assertThat(pd.getProperties()).containsKey("erreurs");
        @SuppressWarnings("unchecked")
        var erreurs = (java.util.Map<String, String>) pd.getProperties().get("erreurs");
        assertThat(erreurs).containsEntry("email", "invalide");
    }

    @Test
    @DisplayName("Intégrité SQL : séparation, créneau, doublon, contrainte générique, sans nom")
    void integrite() {
        assertThat(handler.integrite(avecContrainte("ck_td_depense_valideur")).getProperties())
                .containsEntry("code", "SEPARATION_DES_TACHES");
        assertThat(handler.integrite(avecContrainte("ex_td_rendez_vous_coiffeuse")).getProperties())
                .containsEntry("code", "CRENEAU_OCCUPE");
        assertThat(handler.integrite(avecContrainte("ux_td_utilisateur_email")).getProperties())
                .containsEntry("code", "DOUBLON");
        assertThat(handler.integrite(avecContrainte("uq_td_ligne")).getProperties())
                .containsEntry("code", "DOUBLON");
        assertThat(handler.integrite(avecContrainte("td_facture_pkey")).getProperties())
                .containsEntry("code", "DOUBLON");
        assertThat(handler.integrite(avecContrainte("ck_autre_regle")).getProperties())
                .containsEntry("code", "CONTRAINTE");
        assertThat(handler.integrite(new DataIntegrityViolationException("sans contrainte")).getProperties())
                .containsEntry("code", "CONTRAINTE");
        assertThat(handler.integrite(avecMessagePg("ERROR: conflicting key value violates exclusion constraint \"ex_td_rendez_vous\""))
                .getProperties()).containsEntry("code", "CRENEAU_OCCUPE");
        assertThat(handler.integrite(avecContrainte("ck_independant_controle")).getProperties())
                .containsEntry("code", "SEPARATION_DES_TACHES");
        assertThat(handler.integrite(avecContrainte("ck_td_depense_payeur")).getProperties())
                .containsEntry("code", "SEPARATION_DES_TACHES");
        assertThat(handler.integrite(avecContrainte("ck_td_depense_controleur")).getProperties())
                .containsEntry("code", "SEPARATION_DES_TACHES");
        assertThat(handler.integrite(avecContrainte("table_separation_x")).getProperties())
                .containsEntry("code", "SEPARATION_DES_TACHES");
        // Nom de contrainte non identifiable → branche « sans nom »
        assertThat(handler.integrite(avecContrainte("nom invalide!")).getProperties())
                .containsEntry("code", "CONTRAINTE");
        assertThat(handler.integrite(avecMessagePg("pas de contrainte nommee ici")).getProperties())
                .containsEntry("code", "CONTRAINTE");
    }

    private static DataIntegrityViolationException avecContrainte(String nom) {
        var hibernate = new org.hibernate.exception.ConstraintViolationException("x", null, nom);
        return new DataIntegrityViolationException("x", hibernate);
    }

    private static DataIntegrityViolationException avecMessagePg(String message) {
        return new DataIntegrityViolationException("x", new SQLException(message));
    }
}
