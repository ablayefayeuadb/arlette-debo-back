package sn.arlettedebo.platform.caisse.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.caisse.internal.CaisseService;
import sn.arlettedebo.platform.caisse.internal.FacturePdfService;
import sn.arlettedebo.platform.caisse.internal.FactureService;
import sn.arlettedebo.platform.caisse.internal.JustificatifCaisseService;
import sn.arlettedebo.platform.caisse.internal.JustificatifCaisseService.Compteurs;
import sn.arlettedebo.platform.caisse.internal.domain.Facture;
import sn.arlettedebo.platform.caisse.internal.domain.JustificatifCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.Encaissement;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisseRepository.TotalSession;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Caisse & ventes — journée de caisse, clôtures et contrôle")
@RestController
@Validated
@RequestMapping("/api/v1/caisse")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('caisse:C')")
class CaisseController {

    private final CaisseService service;
    private final FactureService factures;
    private final FacturePdfService pdfs;
    private final JustificatifCaisseService pieces;
    private final UserDirectory annuaire;

    @Operation(summary = "Sessions de caisse d'une période, paginées (jour décroissant) — filtre salon facultatif")
    @GetMapping("/sessions")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    PageResponse<SessionVue> lister(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String perimetre,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.lister(new Periode(du, au), perimetre, PageResponse.requete(page, taille,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "jour").and(
                        org.springframework.data.domain.Sort.by("perimetreCode"))));
        return PageResponse.of(p, vues(p.getContent()));
    }

    @Operation(summary = "Synthèse des clôtures d'une période, calculée sur toute la période : à contrôler, contrôlées, écarts cumulés")
    @GetMapping("/sessions/synthese")
    SyntheseClotures synthese(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String perimetre) {
        var s = service.synthese(new Periode(du, au), perimetre);
        return new SyntheseClotures(s.getNbAControler(), s.getControlees(), s.getEcarts());
    }

    record SyntheseClotures(long aControler, long controlees, long ecarts) {
    }

    @Operation(summary = "Clôtures que je peux contrôler (contrôle indépendant)")
    @GetMapping("/sessions/a-controler")
    List<SessionVue> aControler() {
        return vues(service.aControler());
    }

    @Operation(summary = "Détail d'une session avec ses encaissements")
    @GetMapping("/sessions/{id}")
    DetailVue lire(@PathVariable UUID id) {
        return detail(service.lire(id));
    }

    @Operation(summary = "Session du jour d'un périmètre (204 si la caisse n'est pas encore ouverte)")
    @GetMapping("/sessions/du-jour")
    ResponseEntity<DetailVue> duJour(@RequestParam @NotBlank String perimetre) {
        return service.duJour(perimetre).map(s -> ResponseEntity.ok(detail(s))).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Sessions encore ouvertes, y compris la veille non clôturée (clôture le lendemain) — salon facultatif")
    @GetMapping("/sessions/ouvertes")
    List<SessionVue> ouvertes(@RequestParam(required = false) String perimetre) {
        return vues(service.ouvertes(perimetre));
    }

    @Operation(summary = "Ouvrir la caisse du jour avec le fond compté")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    DetailVue ouvrir(@Valid @RequestBody OuvertureRequest r) {
        return detail(service.ouvrir(r.perimetre(), r.fondOuverture()));
    }

    @Operation(summary = "Encaisser (prestation, vente, abonnement) — cliente, coiffeuse et geste commercial facultatifs")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/sessions/{id}/encaissements")
    @ResponseStatus(HttpStatus.CREATED)
    EncaissementVue encaisser(@PathVariable UUID id, @Valid @RequestBody EncaissementRequest r) {
        Encaissement e = service.encaisser(id, new CaisseService.NouvelEncaissement(r.libelle(), r.montant(), r.modePaiement(),
                r.clientId(), r.coiffeuseId(), r.demandeAutorisationId(), r.note()));
        return vue(e, annuaire.noms(List.of(e.getEncaissePar())));
    }

    @Operation(summary = "Factures impayées à présenter au caissier (prestations du salon + ventes boutique), paginées")
    @GetMapping("/factures/impayees")
    PageResponse<FactureVue> facturesImpayees(
            @RequestParam(required = false) String perimetre,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = factures.impayees(perimetre, PageResponse.requete(page, taille,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id")));
        return PageResponse.of(p, CaisseController::factureVueListe);
    }

    @Operation(summary = "Détail d'une facture (réimpression caisse)")
    @GetMapping("/factures/{id}")
    FactureVue facture(@PathVariable UUID id) {
        var e = factures.lire(id);
        return new FactureVue(e.id(), e.numero(), e.perimetreOrigine(), e.jour(), e.origine(), e.statut(), e.montant(),
                e.libelle(), e.clienteNom(), e.telephone(), e.clientId(), null,
                e.lignes().stream().map(l -> new LigneFactureVue(l.articleId(), l.libelle(), l.quantite(), l.prixUnitaire(), l.montant()))
                        .toList());
    }

    @Operation(summary = "PDF facture (template carnet Jasper)")
    @GetMapping("/factures/{id}/pdf")
    ResponseEntity<byte[]> facturePdf(@PathVariable UUID id) {
        byte[] pdf = pdfs.pdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"facture-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Encaisser une facture sur la caisse ouverte du salon")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/sessions/{id}/factures/{factureId}/paiement")
    @ResponseStatus(HttpStatus.CREATED)
    EncaissementVue payerFacture(@PathVariable UUID id, @PathVariable UUID factureId, @Valid @RequestBody PaiementFactureRequest r) {
        Encaissement e = factures.encaisser(id, factureId, r.modePaiement(), r.note());
        return vue(e, annuaire.noms(List.of(e.getEncaissePar())));
    }

    @Operation(summary = "Annuler un encaissement (motif obligatoire, caisse encore ouverte)")
    @PreAuthorize("hasAuthority('caisse:G')")
    @PostMapping("/encaissements/{id}/annulation")
    EncaissementVue annuler(@PathVariable UUID id, @Valid @RequestBody AnnulationRequest r) {
        Encaissement e = service.annuler(id, r.motif());
        return vue(e, annuaire.noms(List.of(e.getEncaissePar())));
    }

    @Operation(summary = "Clôturer la caisse : espèces comptées, écart calculé, contrôle indépendant ensuite")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping("/sessions/{id}/cloture")
    DetailVue cloturer(@PathVariable UUID id, @Valid @RequestBody ClotureRequest r) {
        return detail(service.cloturer(id, r.montantCompte()));
    }

    // ------------------------------------------------------------------ Justificatifs

    @Operation(summary = "Justificatifs d'une journée de caisse")
    @GetMapping("/sessions/{id}/justificatifs")
    List<JustificatifVue> justificatifs(@PathVariable UUID id) {
        List<JustificatifCaisse> l = pieces.lister(id);
        Map<UUID, String> noms = annuaire.noms(l.stream().map(JustificatifCaisse::getDeposePar).toList());
        return l.stream().map(j -> JustificatifVue.of(j, noms)).toList();
    }

    @Operation(summary = "Déposer un justificatif (PDF, PNG, JPEG — 10 Mo max), pour la journée ou un encaissement")
    @PreAuthorize("hasAuthority('caisse:S')")
    @PostMapping(path = "/sessions/{id}/justificatifs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    JustificatifVue deposer(@PathVariable UUID id, @RequestPart("fichier") MultipartFile fichier,
            @RequestParam JustificatifCaisse.Nature nature, @RequestParam(required = false) UUID encaissementId,
            @RequestParam(required = false) @Size(max = 160) String commentaire) {
        JustificatifCaisse j = pieces.deposer(id, encaissementId, nature, commentaire, fichier);
        return JustificatifVue.of(j, annuaire.noms(List.of(j.getDeposePar())));
    }

    @Operation(summary = "Télécharger un justificatif de caisse")
    @GetMapping("/justificatifs/{id}")
    ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        JustificatifCaisseService.Fichier f = pieces.telecharger(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.meta().getTypeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.meta().getNomFichier(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", f.meta().getEmpreinteSha256())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.contenu());
    }

    @Operation(summary = "Retirer un justificatif (personne qui l'a déposé, caisse encore ouverte)")
    @PreAuthorize("hasAuthority('caisse:S')")
    @DeleteMapping("/justificatifs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirer(@PathVariable UUID id) {
        pieces.retirer(id);
    }

    private DetailVue detail(SessionCaisse s) {
        List<JustificatifCaisse> pj = pieces.deLaSession(s.getId());
        Map<UUID, String> noms = annuaire.noms(Stream.concat(s.getEncaissements().stream().map(Encaissement::getEncaissePar),
                pj.stream().map(JustificatifCaisse::getDeposePar)).toList());
        return new DetailVue(vues(List.of(s)).getFirst(), s.getEncaissements().stream().map(e -> vue(e, noms)).toList(),
                pj.stream().map(j -> JustificatifVue.of(j, noms)).toList());
    }

    private static EncaissementVue vue(Encaissement e, Map<UUID, String> noms) {
        return new EncaissementVue(e.getId(), e.getHorodatage(), e.getLibelle(), e.getMontant(), e.getModePaiementCode(),
                noms.get(e.getEncaissePar()), e.isAnnule(), e.getNote(), e.getClientId(), e.getCoiffeuseId(), e.getFactureId());
    }

    @Operation(summary = "Contrôler une clôture (personne indépendante ; anomalie commentée)")
    @PostMapping("/sessions/{id}/controle")
    SessionVue controler(@PathVariable UUID id, @Valid @RequestBody ControleRequest r) {
        return vues(List.of(service.controler(id, r.conforme(), r.commentaire()))).getFirst();
    }

    private List<SessionVue> vues(List<SessionCaisse> liste) {
        List<UUID> ids = liste.stream().map(SessionCaisse::getId).toList();
        Map<UUID, TotalSession> totaux = service.totaux(ids);
        Map<UUID, Compteurs> compteurs = pieces.compteurs(ids);
        Map<UUID, String> noms = annuaire.noms(liste.stream()
                .flatMap(s -> Stream.of(s.getOuvertePar(), s.getClotureePar(), s.getControlePar())).toList());
        return liste.stream().map(s -> {
            TotalSession t = totaux.get(s.getId());
            Compteurs c = compteurs.getOrDefault(s.getId(), new Compteurs(0, 0));
            return new SessionVue(s.getId(), s.getPerimetreCode(), s.getJour(), s.getStatut().name(), s.getFondOuverture(),
                    t == null ? 0 : t.getTotal(), t == null ? 0 : t.getNombre(), s.getMontantCompte(), s.getEcart(),
                    noms.get(s.getOuvertePar()), noms.get(s.getClotureePar()), noms.get(s.getControlePar()), s.getControleLe(),
                    s.getControleConforme(), s.getControleCommentaire(), service.raisonRefusControle(s) == null, service.raisonRefusControle(s),
                    c.pieces(), c.paiementsSansJustificatif());
        }).toList();
    }

    record ControleRequest(@NotNull Boolean conforme, @Size(max = 500) String commentaire) {
    }

    record SessionVue(UUID id, String perimetre, LocalDate jour, String statut, long fondOuverture, long total, long encaissements,
            Long montantCompte, Long ecart, String ouvertePar, String clotureePar, String controlePar, Instant controleLe,
            Boolean conforme, String commentaireControle, boolean peutControler, String raisonRefusControle,
            long justificatifs, long paiementsSansJustificatif) {
    }

    record EncaissementVue(UUID id, Instant horodatage, String libelle, long montant, String mode, String encaissePar, boolean annule,
            String note, UUID clientId, UUID coiffeuseId, UUID factureId) {
    }

    record LigneFactureVue(UUID articleId, String libelle, int quantite, long prixUnitaire, long montant) {
    }

    record FactureVue(UUID id, String numero, String perimetreOrigine, LocalDate jour, String origine, String statut, long montant,
            String libelle, String clienteNom, String telephone, UUID clientId, UUID rendezVousId, List<LigneFactureVue> lignes) {
    }

    record PaiementFactureRequest(@NotBlank String modePaiement, @Size(max = 120) String note) {
    }

    /** Liste caisse : sans charger les lignes (détail / PDF les rechargent). */
    private static FactureVue factureVueListe(Facture f) {
        return new FactureVue(f.getId(), f.getNumero(), f.getPerimetreOrigine(), f.getJour(), f.getOrigine().name(), f.getStatut().name(),
                f.getMontant(), f.getLibelle(), f.getClienteNom(), f.getTelephone(), f.getClientId(), f.getRendezVousId(), List.of());
    }

    record OuvertureRequest(@NotBlank String perimetre, @PositiveOrZero @Max(5_000_000) long fondOuverture) {
    }

    record EncaissementRequest(@NotBlank @Size(max = 100) String libelle, @Positive @Max(10_000_000) long montant,
            @NotBlank String modePaiement, UUID clientId, UUID coiffeuseId, UUID demandeAutorisationId, @Size(max = 120) String note) {
    }

    record AnnulationRequest(@NotBlank @Size(max = 120) String motif) {
    }

    record ClotureRequest(@PositiveOrZero @Max(50_000_000) long montantCompte) {
    }

    record DetailVue(SessionVue session, List<EncaissementVue> encaissements, List<JustificatifVue> justificatifs) {
    }

    record JustificatifVue(UUID id, UUID encaissementId, String nature, String commentaire, String nomFichier, String typeMime,
            long taille, String sha256, String deposePar, UUID deposeParId, Instant deposeLe) {

        static JustificatifVue of(JustificatifCaisse j, Map<UUID, String> noms) {
            return new JustificatifVue(j.getId(), j.getEncaissementId(), j.getNature().name(), j.getCommentaire(), j.getNomFichier(),
                    j.getTypeMime(), j.getTailleOctets(), j.getEmpreinteSha256(), noms.get(j.getDeposePar()), j.getDeposePar(), j.getDeposeLe());
        }
    }
}
