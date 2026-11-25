package sn.arlettedebo.platform.ops.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.ops.internal.BackupService;
import sn.arlettedebo.platform.ops.internal.BackupService.DumpInfo;
import sn.arlettedebo.platform.shared.error.DomainException;

@Tag(name = "Ops — sauvegarde")
@RestController
@RequestMapping("/api/v1/ops/sauvegarde")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER')")
class BackupController {

    private final BackupService backups;

    @Operation(summary = "Métadonnées du dernier dump Postgres (DG / Manager)")
    @GetMapping
    DumpVue info() {
        DumpInfo d = backups.dernier().orElseThrow(() -> DomainException.introuvable("Sauvegarde Postgres"));
        return DumpVue.of(d);
    }

    @Operation(summary = "Télécharger le dernier dump Postgres (copie locale Manager / DG)")
    @GetMapping("/dernier")
    ResponseEntity<Resource> telecharger() {
        DumpInfo d = backups.dernier().orElseThrow(() -> DomainException.introuvable("Sauvegarde Postgres"));
        Resource res = backups.telechargerDernier();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.nom() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(d.tailleOctets())
                .body(res);
    }

    record DumpVue(String nom, long tailleOctets, Instant modifieLe) {
        static DumpVue of(DumpInfo d) {
            return new DumpVue(d.nom(), d.tailleOctets(), d.modifieLe());
        }
    }
}
