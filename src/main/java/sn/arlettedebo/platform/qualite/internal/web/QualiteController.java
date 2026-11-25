package sn.arlettedebo.platform.qualite.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import sn.arlettedebo.platform.shared.web.PageResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.qualite.internal.QualiteService;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.pilotage.Periode;

@Tag(name = "Prestations & qualité")
@RestController
@RequestMapping("/api/v1/qualite")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('qualite:C')")
class QualiteController {

    private final QualiteService service;
    private final UserDirectory annuaire;
    private final Effectifs effectifs;

    @Operation(summary = "Non-conformités et plans d'actions correctives, paginées (plus récentes d'abord)")
    @GetMapping("/non-conformites")
    @Transactional(readOnly = true)
    PageResponse<NcVue> nonConformites(@RequestParam(defaultValue = "true") boolean ouvertes, @RequestParam(required = false) String perimetre,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.nonConformites(ouvertes, perimetre, PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "dateConstat").and(Sort.by("id"))));
        var liste = p.getContent();
        Map<UUID, String> noms = annuaire.noms(liste.stream().flatMap(n -> Stream.concat(Stream.of(n.getCreeParId()),
                n.getActions().stream().map(a -> a.getResponsableId()))).toList());
        return PageResponse.of(p, liste.stream().map(n -> new NcVue(n.getId(), n.getPerimetreCode(), n.getDateConstat(), n.getOrigine().name(),
                n.getDescription(), n.getGravite().name(), n.getStatut().name(), noms.get(n.getCreeParId()),
                n.getActions().stream().map(a -> new ActionVue(a.getId(), a.getLibelle(), noms.get(a.getResponsableId()), a.getEcheance(),
                        a.getStatut().name())).toList())).toList());
    }

    @Operation(summary = "Réclamations d'une période, paginées (plus récentes d'abord)")
    @GetMapping("/reclamations")
    PageResponse<ReclamationVue> reclamations(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au, @RequestParam(required = false) String perimetre,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.of(service.reclamations(new Periode(du, au), perimetre,
                PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "dateReclamation").and(Sort.by("id")))),
                r -> new ReclamationVue(r.getId(), r.getPerimetreCode(), r.getDateReclamation(), r.getClienteNom(), r.getCanal().name(),
                        r.getMotif(), r.getGravite().name(), r.getStatut().name(), r.getReponse()));
    }

    @Operation(summary = "Avis clients d'une période, paginés (plus récents d'abord)")
    @GetMapping("/avis")
    PageResponse<AvisVue> avis(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au, @RequestParam(required = false) String perimetre,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.of(service.avis(new Periode(du, au), perimetre,
                PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "dateAvis").and(Sort.by("id")))),
                a -> new AvisVue(a.getId(), a.getPerimetreCode(), a.getDateAvis(), a.getClienteNom(), a.getNote(), a.getCommentaire()));
    }

    @Operation(summary = "Contrôles de prestations d'une période, paginés (plus récents d'abord)")
    @GetMapping("/controles")
    PageResponse<ControleVue> controles(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au, @RequestParam(required = false) String perimetre,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.controles(new Periode(du, au), perimetre,
                PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "dateControle").and(Sort.by("id"))));
        var liste = p.getContent();
        Map<UUID, String> coiffeuses = effectifs.noms(liste.stream().map(c -> c.getCoiffeuseId()).toList());
        Map<UUID, String> controleurs = annuaire.noms(liste.stream().map(c -> c.getControleurId()).toList());
        return PageResponse.of(p, liste.stream().map(c -> new ControleVue(c.getId(), c.getPerimetreCode(), c.getDateControle(), c.getPrestationCode(),
                coiffeuses.get(c.getCoiffeuseId()), controleurs.get(c.getControleurId()), c.getNoteAccueil(), c.getNoteTechnique(),
                c.getNoteHygiene(), c.getNoteDelai(), c.isConforme(), c.isParDelegation())).toList());
    }

    @Operation(summary = "Synthèse qualité d'une période, calculée sur toute la période : réclamations (ouvertes), avis "
            + "(satisfaction moyenne), contrôles (taux de conformité)")
    @GetMapping("/synthese")
    SyntheseQualite synthese(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au, @RequestParam(required = false) String perimetre) {
        Periode p = new Periode(du, au);
        var reclamations = service.reclamations(p, perimetre);
        var avis = service.avis(p, perimetre);
        var controles = service.controles(p, perimetre);
        Double satisfaction = avis.isEmpty() ? null
                : Math.round(avis.stream().mapToInt(a -> a.getNote()).average().orElse(0) * 10) / 10.0;
        Integer taux = controles.isEmpty() ? null
                : (int) Math.round(100.0 * controles.stream().filter(c -> c.isConforme()).count() / controles.size());
        return new SyntheseQualite(reclamations.size(),
                reclamations.stream().filter(r -> !"RESOLUE".equals(r.getStatut().name())).count(),
                avis.size(), satisfaction, controles.size(), taux);
    }

    record SyntheseQualite(long reclamations, long reclamationsOuvertes, long avis, Double satisfaction, long controles,
            Integer tauxConformite) {
    }

    @Operation(summary = "Signalements, paginés (coiffeuse : uniquement les siens) — avec historique des traitements")
    @GetMapping("/signalements")
    @Transactional(readOnly = true)
    PageResponse<SignalementVue> signalements(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.signalements(PageResponse.requete(page, taille, Sort.by(Sort.Direction.DESC, "dateSignalement").and(Sort.by("id"))));
        var liste = p.getContent();
        Map<UUID, String> noms = annuaire.noms(liste.stream()
                .flatMap(s -> Stream.concat(Stream.of(s.getAuteurId(), s.getTraitePar()),
                        s.getTraitements().stream().map(t -> t.getTraitePar())))
                .filter(java.util.Objects::nonNull)
                .toList());
        return PageResponse.of(p, liste.stream().map(s -> new SignalementVue(
                s.getId(), s.getPerimetreCode(), s.getDateSignalement(), noms.get(s.getAuteurId()),
                s.getTypeCode(), s.getDescription(), s.getStatut().name(),
                noms.get(s.getTraitePar()), s.getTraiteLe(),
                s.getTraitements().stream()
                        .map(t -> new TraitementVue(t.getId(), t.getStatutAvant().name(), t.getStatutApres().name(),
                                noms.get(t.getTraitePar()), t.getTraiteLe(), t.getCommentaire()))
                        .toList())).toList());
    }

    record ActionVue(UUID id, String libelle, String responsable, LocalDate echeance, String statut) {
    }

    record NcVue(UUID id, String perimetre, LocalDate date, String origine, String description, String gravite, String statut,
            String creePar, List<ActionVue> actions) {
    }

    record ReclamationVue(UUID id, String perimetre, Instant date, String cliente, String canal, String motif, String gravite,
            String statut, String reponse) {
    }

    record AvisVue(UUID id, String perimetre, Instant date, String cliente, short note, String commentaire) {
    }

    record ControleVue(UUID id, String perimetre, Instant date, String prestation, String coiffeuse, String controleur, short accueil,
            short technique, short hygiene, short delai, boolean conforme, boolean parDelegation) {
    }

    record SignalementVue(UUID id, String perimetre, Instant date, String auteur, String type, String description, String statut,
            String traitePar, Instant traiteLe, List<TraitementVue> traitements) {
    }

    record TraitementVue(UUID id, String statutAvant, String statutApres, String traitePar, Instant traiteLe, String commentaire) {
    }
}
