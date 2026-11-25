package sn.arlettedebo.platform.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.shared.config.StorageProperties;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage.StockageIndisponibleException;

/** Dépôt local : types autorisés, garde-fous chemin, lecture / suppression. */
@DisplayName("JustificatifStorage : dépôt et contrôles")
class JustificatifStorageTests {

    @TempDir
    Path tmp;

    private JustificatifStorage storage;

    @BeforeEach
    void setUp() {
        storage = new JustificatifStorage(new StorageProperties(tmp.toString()),
                Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("PDF / PNG / JPEG acceptés ; format inconnu et fichier vide refusés")
    void depotEtDetection() {
        var pdf = storage.stocker(pdfFile());
        assertThat(pdf.typeMime()).isEqualTo("application/pdf");
        assertThat(pdf.chemin()).startsWith("justificatifs/2026/11/");
        assertThat(pdf.sha256()).hasSize(64);

        var pngBytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 1, 2};
        var png = storage.stocker("caisse", new MockMultipartFile("f", "a.png", "image/png", pngBytes));
        assertThat(png.typeMime()).isEqualTo("image/png");
        assertThat(png.chemin()).startsWith("caisse/2026/11/");

        var jpegBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};
        var jpeg = storage.stocker(new MockMultipartFile("f", "a.jpg", "image/jpeg", jpegBytes));
        assertThat(jpeg.typeMime()).isEqualTo("image/jpeg");

        MockMultipartFile texte = new MockMultipartFile("f", "x.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> storage.stocker(texte))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Format non accepté");
        MockMultipartFile vide = new MockMultipartFile("f", "vide.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> storage.stocker(vide))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("vide");
        MockMultipartFile ok = pdfFile();
        assertThatThrownBy(() -> storage.stocker("Dossier/Invalide", ok))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.stocker((MultipartFile) null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("vide");
    }

    @Test
    @DisplayName("Flux illisible ou disque en échec : erreurs métier / 503")
    void erreursIo() throws Exception {
        MultipartFile illisible = mock(MultipartFile.class);
        when(illisible.isEmpty()).thenReturn(false);
        when(illisible.getInputStream()).thenThrow(new IOException("coupé"));
        assertThatThrownBy(() -> storage.stocker(illisible))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("illisible");

        MultipartFile disque = mock(MultipartFile.class);
        when(disque.isEmpty()).thenReturn(false);
        when(disque.getInputStream())
                .thenReturn(new ByteArrayInputStream("%PDF-1.4 suite".getBytes(StandardCharsets.US_ASCII)))
                .thenThrow(new IOException("disque plein"));
        assertThatThrownBy(() -> storage.stocker(disque))
                .isInstanceOf(StockageIndisponibleException.class);

        // En-têtes trop courts : les branches n >= 4 / 8 / 3 restent à couvrir
        MockMultipartFile tropCourt = new MockMultipartFile("f", "a.bin", "x", new byte[] {1});
        assertThatThrownBy(() -> storage.stocker(tropCourt)).isInstanceOf(DomainException.class);
        MockMultipartFile pngCourt = new MockMultipartFile("f", "a.png", "x",
                new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        assertThatThrownBy(() -> storage.stocker(pngCourt)).isInstanceOf(DomainException.class);
        MockMultipartFile jpegCourt = new MockMultipartFile("f", "a.jpg", "x",
                new byte[] {(byte) 0xFF, (byte) 0xD8});
        assertThatThrownBy(() -> storage.stocker(jpegCourt)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Lecture et suppression ; chemin hors racine refusé")
    void lireEtSupprimer() {
        var stocke = storage.stocker(pdfFile());
        assertThat(storage.lire(stocke.chemin()).exists()).isTrue();
        assertThatThrownBy(() -> storage.lire("justificatifs/2026/11/absent.pdf"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Fichier");
        assertThatThrownBy(() -> storage.lire("../etc/passwd"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Chemin");
        storage.supprimer(stocke.chemin());
        assertThat(Files.exists(tmp.resolve(stocke.chemin()))).isFalse();
        storage.supprimer(stocke.chemin());
    }

    private static MockMultipartFile pdfFile() {
        return new MockMultipartFile("f", "a.pdf", "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.US_ASCII));
    }
}
