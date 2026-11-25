package sn.arlettedebo.platform.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.shared.config.StorageProperties;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Stockage des justificatifs dans un dossier du serveur.
 * <ul>
 *   <li>type réel détecté par signature binaire (PDF, PNG, JPEG) — le type annoncé par le client est ignoré ;</li>
 *   <li>chemin généré ({@code dossier/aaaa/mm/uuid.ext}) : le nom fourni par le client n'est jamais utilisé,
 *       aucune sortie du dossier racine possible ;</li>
 *   <li>écriture dans un fichier temporaire puis déplacement : jamais de fichier à moitié écrit ;</li>
 *   <li>empreinte SHA-256 calculée pendant l'écriture, pour le contrôle d'intégrité.</li>
 * </ul>
 */
@Slf4j
@Component
public class JustificatifStorage {

    private final Path racine;
    private final Clock clock;

    public JustificatifStorage(StorageProperties props, Clock clock) {
        this.racine = Path.of(props.root()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    /** Dépôt dans le dossier « justificatifs » (dépenses). */
    public FichierStocke stocker(MultipartFile fichier) {
        return stocker("justificatifs", fichier);
    }

    /** Photo uniquement (PNG / JPEG) — refuse PDF. */
    public FichierStocke stockerImage(String dossier, MultipartFile fichier) {
        FichierStocke f = stocker(dossier, fichier);
        if (!f.typeMime().startsWith("image/")) {
            supprimer(f.chemin());
            throw DomainException.invalide("Photo : PNG ou JPEG uniquement.");
        }
        return f;
    }

    /** Dépôt dans un dossier métier ({@code justificatifs}, {@code caisse}…), classé par année et mois. */
    public FichierStocke stocker(String dossier, MultipartFile fichier) {
        if (!dossier.matches("[a-z-]+")) {
            throw new IllegalArgumentException("Dossier de stockage invalide : " + dossier);
        }
        if (fichier == null || fichier.isEmpty()) {
            throw DomainException.invalide("Fichier vide.");
        }
        TypeAutorise type = detecter(fichier);
        LocalDate jour = LocalDate.now(clock);
        Path relatif = Path.of(dossier, String.valueOf(jour.getYear()), String.format("%02d", jour.getMonthValue()),
                UUID.randomUUID() + type.extension);
        Path cible = resoudre(relatif.toString());
        Path temporaire = null;
        try {
            Files.createDirectories(cible.getParent());
            temporaire = Files.createTempFile(cible.getParent(), ".depot-", ".tmp");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(fichier.getInputStream(), sha)) {
                Files.copy(in, temporaire, StandardCopyOption.REPLACE_EXISTING);
            }
            deplacer(temporaire, cible);
            return new FichierStocke(relatif.toString().replace('\\', '/'), type.mime, Files.size(cible), HexFormat.of().formatHex(sha.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            supprimerSilencieusement(temporaire);
            throw indisponible("dépôt", e);
        }
    }

    public Resource lire(String cheminRelatif) {
        Path p = resoudre(cheminRelatif);
        if (!Files.isRegularFile(p)) {
            throw DomainException.introuvable("Fichier");
        }
        return new FileSystemResource(p);
    }

    /** Suppression d'un fichier retiré (sans effet s'il n'existe plus). */
    public void supprimer(String cheminRelatif) {
        try {
            Files.deleteIfExists(resoudre(cheminRelatif));
        } catch (IOException e) {
            throw indisponible("suppression", e);
        }
    }

    /** Chemin absolu sous la racine ; refuse toute tentative d'en sortir. */
    private Path resoudre(String cheminRelatif) {
        Path p = racine.resolve(cheminRelatif).normalize();
        if (!p.startsWith(racine)) {
            throw DomainException.invalide("Chemin de stockage invalide.");
        }
        return p;
    }

    private static void deplacer(Path source, Path cible) throws IOException {
        try {
            Files.move(source, cible, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, cible, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void supprimerSilencieusement(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignore) {
                // fichier temporaire : sans conséquence
            }
        }
    }

    private static StockageIndisponibleException indisponible(String operation, Exception cause) {
        log.error("Stockage des justificatifs : échec de {}", operation, cause);
        return new StockageIndisponibleException();
    }

    private static TypeAutorise detecter(MultipartFile fichier) {
        byte[] tete = new byte[8];
        try (InputStream in = fichier.getInputStream()) {
            int n = in.readNBytes(tete, 0, tete.length);
            if (n >= 4 && tete[0] == '%' && tete[1] == 'P' && tete[2] == 'D' && tete[3] == 'F') {
                return TypeAutorise.PDF;
            }
            if (n >= 8 && (tete[0] & 0xFF) == 0x89 && tete[1] == 'P' && tete[2] == 'N' && tete[3] == 'G') {
                return TypeAutorise.PNG;
            }
            if (n >= 3 && (tete[0] & 0xFF) == 0xFF && (tete[1] & 0xFF) == 0xD8 && (tete[2] & 0xFF) == 0xFF) {
                return TypeAutorise.JPEG;
            }
        } catch (IOException e) {
            throw DomainException.invalide("Fichier illisible.");
        }
        throw DomainException.invalide("Format non accepté : PDF, PNG ou JPEG uniquement.");
    }

    private enum TypeAutorise {
        PDF("application/pdf", ".pdf"), PNG("image/png", ".png"), JPEG("image/jpeg", ".jpg");

        final String mime;
        final String extension;

        TypeAutorise(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }
    }

    /** @param chemin chemin relatif au dossier racine */
    public record FichierStocke(String chemin, String typeMime, long taille, String sha256) {
    }

    /** Dossier de stockage inaccessible (disque plein, droits) : réponse 503, détail dans les journaux uniquement. */
    public static class StockageIndisponibleException extends RuntimeException {
        public StockageIndisponibleException() {
            super("Stockage des justificatifs momentanément indisponible. Réessayez dans un instant.");
        }
    }
}
