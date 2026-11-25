package sn.arlettedebo.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import sn.arlettedebo.platform.support.ApiTest;

@DisplayName("Finance : circuit des dépenses, seuil DG, séparation des tâches, justificatifs")
class FinanceTests extends ApiTest {

    private static final byte[] PDF = "%PDF-1.4\n% justificatif de test\n".getBytes();

    private String demander(String objet, long montant) throws Exception {
        JsonNode d = lire(mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/finance/depenses")),
                Map.of("objet", objet, "categorie", "MAINTENANCE", "perimetre", "SKY", "montant", montant, "fournisseur", "Froid Service")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("DEMANDEE"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("DEP-")))
                .andReturn());
        return d.path("id").asString();
    }

    private void joindre(String id) throws Exception {
        mvc.perform(en(ADMINISTRATION, multipart("/api/v1/finance/depenses/" + id + "/justificatifs")
                        .file(new MockMultipartFile("fichier", "facture.pdf", "application/pdf", PDF))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeMime").value("application/pdf"))
                .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.hasLength(64)));
    }

    @Test
    @DisplayName("Au-delà du seuil : DO bloquée, DG valide, Administration paie, contrôle indépendant avec justificatif")
    void circuitCompletAuDelaDuSeuil() throws Exception {
        String id = demander("Fauteuils de coiffage", 150_000);
        String url = "/api/v1/finance/depenses/" + id;

        mvc.perform(en(ADMINISTRATION, post(url + "/validation")))
                .andExpect(status().isForbidden());
        mvc.perform(en(DO, post(url + "/validation")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SEUIL_DG"));
        mvc.perform(en(DO, get(url)))
                .andExpect(jsonPath("$.actions[0].action").value("valider"))
                .andExpect(jsonPath("$.actions[0].autorisee").value(false));

        mvc.perform(en(DG, post(url + "/validation")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.validation.nom").value("Direction Générale"));

        mvc.perform(jsonBody(en(DG, post(url + "/paiement")), Map.of("modePaiement", "VIREMENT")))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(ADMINISTRATION, post(url + "/paiement")), Map.of("modePaiement", "VIREMENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PAYEE"));

        mvc.perform(en(ADMINISTRATION, post(url + "/controle"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTROLE_DIRECTION"));
        mvc.perform(en(DG, post(url + "/controle"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("JUSTIFICATIF_MANQUANT"));

        joindre(id);
        mvc.perform(en(DG, post(url + "/controle"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEPARATION_DES_TACHES"));
        mvc.perform(en(DO, post(url + "/controle")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CONTROLEE"))
                .andExpect(jsonPath("$.controle.nom").value("Directeur des Opérations"));
    }

    @Test
    @DisplayName("Sous le seuil : la DO valide par délégation")
    void doValideSousLeSeuil() throws Exception {
        String id = demander("Ampoules zone shampoing", 45_000);
        mvc.perform(en(DO, post("/api/v1/finance/depenses/" + id + "/validation")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
    }

    @Test
    @DisplayName("Seuil modifiable par la DG uniquement, appliqué immédiatement")
    void seuilDg() throws Exception {
        mvc.perform(jsonBody(en(DO, put("/api/v1/finance/parametres/seuil-dg")), Map.of("montant", 500_000)))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(DG, put("/api/v1/finance/parametres/seuil-dg")), Map.of("montant", 200_000)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeur").value("200000"));
        try {
            String id = demander("Climatiseur", 180_000);
            mvc.perform(en(DO, post("/api/v1/finance/depenses/" + id + "/validation"))).andExpect(status().isOk());
        } finally {
            mvc.perform(jsonBody(en(DG, put("/api/v1/finance/parametres/seuil-dg")), Map.of("montant", 100_000)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Refus motivé ; un dossier refusé n'accepte plus de justificatif")
    void refus() throws Exception {
        String id = demander("Décoration", 20_000);
        mvc.perform(jsonBody(en(DG, post("/api/v1/finance/depenses/" + id + "/refus")), Map.of("motif", "Hors budget")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("REFUSEE"))
                .andExpect(jsonPath("$.motifRefus").value("Hors budget"));
        mvc.perform(en(ADMINISTRATION, multipart("/api/v1/finance/depenses/" + id + "/justificatifs")
                        .file(new MockMultipartFile("fichier", "f.pdf", "application/pdf", PDF))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Justificatif : type réel vérifié (un .pdf qui n'en est pas un est refusé) ; téléchargement intègre")
    void justificatifs() throws Exception {
        String id = demander("Produits d'entretien", 12_000);
        mvc.perform(en(ADMINISTRATION, multipart("/api/v1/finance/depenses/" + id + "/justificatifs")
                        .file(new MockMultipartFile("fichier", "facture.pdf", "application/pdf", "<script>alert(1)</script>".getBytes()))))
                .andExpect(status().isUnprocessableContent());
        JsonNode j = lire(mvc.perform(en(ADMINISTRATION, multipart("/api/v1/finance/depenses/" + id + "/justificatifs")
                        .file(new MockMultipartFile("fichier", "../../etc/passwd.pdf", "application/pdf", PDF))))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(en(DG, get("/api/v1/finance/justificatifs/" + j.path("id").asString())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-SHA256", j.path("sha256").asString()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Validation des entrées : montant négatif, catégorie inconnue")
    void validationDesEntrees() throws Exception {
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/finance/depenses")),
                Map.of("objet", "X", "categorie", "MAINTENANCE", "montant", -5, "fournisseur", "Y")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs.montant").exists());
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/finance/depenses")),
                Map.of("objet", "X", "categorie", "INCONNUE", "montant", 5, "fournisseur", "Y")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Synthèse par statut et seuil")
    void synthese() throws Exception {
        mvc.perform(en(DG, get("/api/v1/finance/synthese")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuilDg").value(100_000))
                .andExpect(jsonPath("$.parStatut.DEMANDEE.nombre").exists());
    }
}
