package sn.arlettedebo.platform.audit.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Journal d'audit")
@Validated
@RestController
@RequestMapping("/api/v1/journal")
@RequiredArgsConstructor
class JournalController {

    private final JournalRepository journal;

    @Operation(summary = "Journal d'audit (DG, Manager, DO) — filtrable par jour (Africa/Dakar), module et utilisateur")
    @PreAuthorize("hasAnyAuthority('PROFIL_DG', 'PROFIL_MANAGER', 'PROFIL_DO')")
    @Transactional(readOnly = true)
    @GetMapping
    PageResponse<EntreeVue> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) UUID utilisateur,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int taille) {
        boolean filtrerJour = jour != null;
        Instant debut = filtrerJour ? jour.atStartOfDay(AuditingConfig.ZONE).toInstant() : Instant.EPOCH;
        Instant fin = filtrerJour ? jour.plusDays(1).atStartOfDay(AuditingConfig.ZONE).toInstant() : Instant.EPOCH;
        String mod = module == null || module.isBlank() ? "" : module.trim();
        return PageResponse.of(
                journal.rechercher(mod, utilisateur, filtrerJour, debut, fin, PageRequest.of(page, taille)),
                EntreeVue::of);
    }

    record EntreeVue(long id, Instant horodatage, UUID utilisateurId, String utilisateur, String profil, String module,
            String action, String detail) {

        static EntreeVue of(EntreeJournal j) {
            return new EntreeVue(j.getId(), j.getHorodatage(), j.getUtilisateurId(), j.getUtilisateurNom(), j.getProfilCode(),
                    j.getModuleCode(), j.getAction(), j.getDetail());
        }
    }
}
