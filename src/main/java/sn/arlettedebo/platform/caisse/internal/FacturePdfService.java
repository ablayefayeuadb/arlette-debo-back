package sn.arlettedebo.platform.caisse.internal;

import java.io.InputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.sf.jasperreports.engine.JRException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.caisse.FactureDocuments;
import sn.arlettedebo.platform.caisse.Facturation.FactureEmise;
import sn.arlettedebo.platform.caisse.Facturation.LigneEmise;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Génère le PDF facture à partir du template Jasper {@code reports/facture-carnet.jrxml}
 * (format carnet papier Arlette Debo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacturePdfService implements FactureDocuments {

    private static final String TEMPLATE = "reports/facture-carnet.jrxml";
    private static final String LOGO = "reports/logo-arlette-debo.jpg";
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat FCFA = NumberFormat.getInstance(Locale.FRANCE);

    private final FactureService factures;

    private volatile JasperReport compile;

    /** PDF d'une facture (contrôle d'accès via {@link FactureService#lire}). */
    @Override
    @Transactional(readOnly = true)
    public byte[] pdf(UUID factureId) {
        return rendre(factures.lire(factureId), null);
    }

    /** PDF à partir d'une facture déjà émise (même transaction que la vente). */
    public byte[] generer(FactureEmise f, String modePaiement) {
        return rendre(f, modePaiement);
    }

    private byte[] rendre(FactureEmise f, String modePaiement) {
        try {
            Map<String, Object> p = new HashMap<>();
            p.put("LOGO", logo());
            p.put("MARQUE", marque(f.perimetreOrigine()));
            p.put("NUMERO", numeroAffiche(f.numero()));
            p.put("CLIENTE_NOM", f.clienteNom());
            p.put("TELEPHONE", telephoneAffiche(f.telephone()));
            p.put("DATE", f.jour().format(DATE_FR));
            p.put("SOUS_TOTAL", montant(f.montant()));
            p.put("BON_ACHAT", "");
            p.put("MODE_PAIEMENT", modePaiement == null ? modeParStatut(f.statut()) : modePaiement);
            p.put("GRAND_TOTAL", montant(f.montant()) + " FCFA");
            p.put("ADRESSE", "Sacré coeur 1");
            p.put("TEL_SALON", "77 430 39 39");

            List<LignePdf> lignes = f.lignes().stream().map(FacturePdfService::ligne).toList();
            JasperPrint print = JasperFillManager.fillReport(rapport(), p,
                    lignes.isEmpty() ? new JREmptyDataSource() : new JRBeanCollectionDataSource(lignes));
            return JasperExportManager.exportReportToPdf(print);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Génération PDF facture {} : {}", f.id(), e.getMessage(), e);
            throw DomainException.invalide("Impossible de générer la facture PDF.");
        }
    }

    private JasperReport rapport() throws Exception {
        JasperReport c = compile;
        if (c == null) {
            synchronized (this) {
                c = compile;
                if (c == null) {
                    try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream()) {
                        c = JasperCompileManager.compileReport(in);
                        compile = c;
                    }
                }
            }
        }
        return c;
    }

    private static InputStream logo() throws Exception {
        return new ClassPathResource(LOGO).getInputStream();
    }

    private static String marque(String perimetre) {
        return "Arlette Debo — " + (perimetre == null || perimetre.isBlank() ? "Salon" : perimetre) + " —";
    }

    /** FAC-2026-0001 → 0001 (style carnet Nº 00361). */
    private static String numeroAffiche(String numero) {
        if (numero == null) {
            return "";
        }
        int i = numero.lastIndexOf('-');
        return i >= 0 && i < numero.length() - 1 ? numero.substring(i + 1) : numero;
    }

    private static String telephoneAffiche(String chiffres) {
        if (chiffres == null || chiffres.length() != 9) {
            return chiffres;
        }
        return chiffres.substring(0, 2) + " " + chiffres.substring(2, 5) + " " + chiffres.substring(5, 7) + " "
                + chiffres.substring(7, 9);
    }

    private static String modeParStatut(String statut) {
        return "PAYEE".equals(statut) ? "" : "À régler en caisse";
    }

    private static String montant(long n) {
        return FCFA.format(n);
    }

    private static LignePdf ligne(LigneEmise l) {
        return new LignePdf(l.libelle(), montant(l.prixUnitaire()), String.valueOf(l.quantite()), montant(l.montant()));
    }

    /** Ligne pour le datasource Jasper (getters JavaBeans — Jasper ne lit pas les records). */
    public static final class LignePdf {
        private final String description;
        private final String prix;
        private final String quantite;
        private final String total;

        public LignePdf(String description, String prix, String quantite, String total) {
            this.description = description;
            this.prix = prix;
            this.quantite = quantite;
            this.total = total;
        }

        public String getDescription() {
            return description;
        }

        public String getPrix() {
            return prix;
        }

        public String getQuantite() {
            return quantite;
        }

        public String getTotal() {
            return total;
        }
    }
}
