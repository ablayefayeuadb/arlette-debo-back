package sn.arlettedebo.platform.shared.security;

import java.util.Arrays;

/** Colonnes de la matrice des profils & fonctionnalités (codes de {@code tp_module}). */
public enum ModuleCode {
    DASHBOARD("dashboard"),
    RH("rh"),
    PLANNING("planning"),
    QUALITE("qualite"),
    CAISSE("caisse"),
    STOCKS("stocks"),
    COMMUNICATION("communication"),
    ADMINISTRATION("administration");

    private final String code;

    ModuleCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ModuleCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(m -> m.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Module inconnu : " + code));
    }
}
