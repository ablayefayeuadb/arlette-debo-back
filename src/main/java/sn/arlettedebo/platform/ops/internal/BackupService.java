package sn.arlettedebo.platform.ops.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

@Service
@RequiredArgsConstructor
public class BackupService {

    private static final String PREFIX = "arlette-pg-";
    private static final String SUFFIX = ".dump";

    private final BackupProperties props;
    private final ApplicationEventPublisher events;

    public Optional<DumpInfo> dernier() {
        return dernierFichier().map(this::info);
    }

    /** Transactionnelle : l'écriture journal d'audit exige une TX (propagation MANDATORY). */
    @Transactional
    public Resource telechargerDernier() {
        Path f = dernierFichier().orElseThrow(() -> DomainException.introuvable("Sauvegarde Postgres"));
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Téléchargement dump Postgres", f.getFileName().toString()));
        return new FileSystemResource(f);
    }

    private Optional<Path> dernierFichier() {
        Path dir = Path.of(props.dir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(PREFIX) && n.endsWith(SUFFIX) && !n.contains("..");
                    })
                    .filter(p -> p.normalize().startsWith(dir))
                    .max(Comparator.comparing(this::mtime));
        } catch (IOException e) {
            throw DomainException.etatInvalide("Impossible de lire le dossier de sauvegarde.");
        }
    }

    private DumpInfo info(Path p) {
        try {
            return new DumpInfo(p.getFileName().toString(), Files.size(p), Files.getLastModifiedTime(p).toInstant());
        } catch (IOException e) {
            throw DomainException.etatInvalide("Impossible de lire le dump.");
        }
    }

    private Instant mtime(Path p) {
        try {
            FileTime t = Files.getLastModifiedTime(p);
            return t.toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    public record DumpInfo(String nom, long tailleOctets, Instant modifieLe) {
    }
}
