package sn.arlettedebo.platform.shared.security;

/** Codes des profils ({@code tp_profil}), en majuscules. */
public final class Profils {

    public static final String DG = "DG";
    public static final String DO = "DO";
    public static final String MANAGER = "MANAGER";
    public static final String COORDINATION = "COORDINATION";
    public static final String CAISSIER = "CAISSIER";
    public static final String CHARGE_CLIENTELE = "CHARGE_CLIENTELE";
    public static final String COIFFEUR = "COIFFEUR";
    public static final String VENDEUR_BOUTIQUE = "VENDEUR_BOUTIQUE";
    public static final String INVENTAIRE = "INVENTAIRE";
    public static final String COMMUNITY_MANAGER = "COMMUNITY_MANAGER";
    public static final String ADMINISTRATION = "ADMINISTRATION";
    /** Portail cliente (hors matrice staff) : lecture seule de son dossier. */
    public static final String ABONNEE = "ABONNEE";

    /**
     * Profils qui encaissent : un salon n'a qu'un seul compte actif de ces profils.
     * Le vendeur boutique ne figure pas ici : il établit la facture, le client paie au caissier.
     */
    public static final java.util.Set<String> CAISSIERS = java.util.Set.of(CAISSIER, CHARGE_CLIENTELE);

    /** Profils de terrain évalués par la Manager (N+1 salon). */
    public static final java.util.Set<String> EQUIPE_SALON = java.util.Set.of(COIFFEUR, CAISSIER, CHARGE_CLIENTELE, VENDEUR_BOUTIQUE);

    private Profils() {
    }
}
