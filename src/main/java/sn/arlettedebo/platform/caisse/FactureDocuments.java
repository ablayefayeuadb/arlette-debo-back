package sn.arlettedebo.platform.caisse;

import java.util.UUID;

/** Génération documentaire des factures (PDF carnet Jasper). */
public interface FactureDocuments {

    /** PDF de la facture (contrôle d'accès inclus). */
    byte[] pdf(UUID factureId);
}
