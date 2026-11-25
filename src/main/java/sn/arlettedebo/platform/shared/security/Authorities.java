package sn.arlettedebo.platform.shared.security;

/**
 * Format des autorités Spring Security.
 * <ul>
 *   <li>Droit : {@code "<module>:<action>"} — ex. {@code "administration:V"} ;</li>
 *   <li>Profil : {@code "PROFIL_<code>"} — ex. {@code "PROFIL_DG"}.</li>
 * </ul>
 * Utilisable dans {@code @PreAuthorize("hasAuthority('administration:V')")}.
 */
public final class Authorities {

    public static final String PROFIL_PREFIX = "PROFIL_";

    private Authorities() {
    }

    public static String droit(ModuleCode module, ActionCode action) {
        return module.code() + ":" + action.name();
    }

    public static String profil(String profilCode) {
        return PROFIL_PREFIX + profilCode;
    }
}
