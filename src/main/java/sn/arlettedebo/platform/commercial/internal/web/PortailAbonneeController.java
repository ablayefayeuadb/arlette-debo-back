package sn.arlettedebo.platform.commercial.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.commercial.internal.AbonneesService;
import sn.arlettedebo.platform.commercial.internal.PortailAbonneeService;
import sn.arlettedebo.platform.commercial.internal.domain.BilanTrimestrielAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.EcheanceAbonnementRepository;
import sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonneeRepository;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService.Confirmation;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService.Disponibilite;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.referentiel.Referentiels.PrestationInfo;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Portail abonnée")
@RestController
@Validated
@RequestMapping("/api/v1/portail/abonnee")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PROFIL_ABONNEE')")
class PortailAbonneeController {

    private final PortailAbonneeService portail;
    private final Effectifs effectifs;

    @Operation(summary = "Ma fiche (profil capillaire, abonnement)")
    @GetMapping("/moi")
    FicheVue moi() {
        return FicheVue.of(portail.maFiche());
    }

    @Operation(summary = "Mes suivis de séance avec photos liées (page SQL, colonnes affichées uniquement)")
    @GetMapping("/suivis")
    PageResponse<SuiviVue> suivis(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = portail.mesSuivis(PageResponse.requete(page, taille, Sort.unsorted()));
        Map<UUID, String> noms = effectifs.noms(p.getContent().stream().map(SuiviSeanceAbonneeRepository.LignePortail::coiffeuseId)
                .filter(java.util.Objects::nonNull).distinct().toList());
        Map<UUID, List<PhotoEvolutionAbonneeRepository.Ligne>> photos = portail.photosParSuivis(
                p.getContent().stream().map(SuiviSeanceAbonneeRepository.LignePortail::id).toList());
        return PageResponse.of(p, s -> SuiviVue.of(s, s.coiffeuseId() == null ? null : noms.get(s.coiffeuseId()),
                photos.getOrDefault(s.id(), List.of())));
    }

    @Operation(summary = "Mes échéances de paiement d'abonnement (lecture seule, page SQL)")
    @GetMapping("/echeances")
    PageResponse<EcheanceVue> echeances(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.of(portail.mesEcheances(PageResponse.requete(page, taille, Sort.unsorted())), EcheanceVue::of);
    }

    @Operation(summary = "Mes bilans de soins trimestriels (lecture seule, page SQL)")
    @GetMapping("/bilans")
    PageResponse<BilanVue> bilans(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.of(portail.mesBilans(PageResponse.requete(page, taille, Sort.unsorted())), BilanVue::of);
    }

    @Operation(summary = "Mes photos d'évolution (page SQL, plus récentes d'abord)")
    @GetMapping("/photos")
    PageResponse<PhotoVue> photos(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.of(portail.mesPhotos(PageResponse.requete(page, taille, Sort.unsorted())), PhotoVue::of);
    }

    @Operation(summary = "Télécharger une de mes photos")
    @GetMapping("/photos/{photoId}/fichier")
    ResponseEntity<Resource> photo(@PathVariable UUID photoId) {
        AbonneesService.PhotoFichier f = portail.telechargerPhoto(photoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.meta().getNomFichier(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(f.meta().getTypeMime()))
                .body(f.contenu());
    }

    @Operation(summary = "Mes rendez-vous à venir (page SQL, libellés de prestation en une requête)")
    @GetMapping("/rdvs")
    PageResponse<RdvVue> rdvs(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = portail.mesRdvsAVenir(PageResponse.requete(page, taille, Sort.unsorted()));
        Map<String, String> libelles = portail.libellesPrestations(
                p.getContent().stream().map(RendezVousRepository.LigneAVenir::prestationCode).distinct().toList());
        return PageResponse.of(p, r -> RdvVue.of(r, libelles.getOrDefault(r.prestationCode(), r.prestationCode())));
    }

    @Operation(summary = "Prestations réservables de mon salon")
    @GetMapping("/rdvs/prestations")
    List<PrestationVue> prestations() {
        return portail.mesPrestations().stream().map(PrestationVue::of).toList();
    }

    @Operation(summary = "Créneaux ouverts pour un jour / une prestation")
    @GetMapping("/rdvs/disponibilites")
    List<DisponibiliteVue> disponibilites(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam @NotBlank String prestation) {
        return portail.mesDisponibilites(jour, prestation).stream().map(DisponibiliteVue::of).toList();
    }

    @Operation(summary = "Prendre rendez-vous (identité reprise de ma fiche)")
    @PostMapping("/rdvs")
    @ResponseStatus(HttpStatus.CREATED)
    ConfirmationVue reserver(@Valid @RequestBody ReservationRequest r, HttpServletRequest http) {
        return ConfirmationVue.of(portail.reserver(r.jour(), r.heure(), r.prestation(), r.note(), http.getRemoteAddr()));
    }

    // ------------------------------------------------------------ Vues

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FicheVue(String nom, String prenom, String telephone, String email, String adresse, String profession,
            LocalDate dateInscription, String priseEnCharge, String typeAbonnement, Map<String, Object> profilJson,
            String recommandations) {

        static FicheVue of(PortailAbonneeService.FichePortail f) {
            DossierAbonnee d = f.dossier();
            return new FicheVue(f.client().getNom(), d.getPrenom(), f.client().getTelephone(), f.client().getEmail(),
                    d.getAdresse(), d.getProfession(), d.getDateInscription(), d.getPriseEnCharge(),
                    d.getTypeAbonnement() == null ? null : d.getTypeAbonnement().name(), d.getProfilJson(),
                    d.getRecommandations());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuiviVue(UUID id, LocalDate jour, Short numeroSemaine, String coiffeuse, String prepoo, String shampoo,
            String masque, String soinPersonnalise, String leaveIn, String modeSechage, String coiffureProtectrice,
            String observation, List<PhotoSeanceVue> photos) {

        static SuiviVue of(SuiviSeanceAbonneeRepository.LignePortail s, String coiffeuseNom,
                List<PhotoEvolutionAbonneeRepository.Ligne> photos) {
            return new SuiviVue(s.id(), s.jour(), s.numeroSemaine(), coiffeuseNom, s.prepoo(), s.shampoo(), s.masque(),
                    s.soinPersonnalise(), s.leaveIn(), s.modeSechage(), s.coiffureProtectrice(), s.observation(),
                    photos.isEmpty() ? null : photos.stream().map(PhotoSeanceVue::of).toList());
        }
    }

    record ReservationRequest(@NotNull LocalDate jour, @NotNull LocalTime heure,
            @NotBlank @Size(max = 40) String prestation, @Size(max = 255) String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EcheanceVue(UUID id, LocalDate echeanceLe, long montant, String statut, Instant payeeLe) {

        static EcheanceVue of(EcheanceAbonnementRepository.LignePortail e) {
            return new EcheanceVue(e.id(), e.echeanceLe(), e.montant(), e.statut().name(), e.payeeLe());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BilanVue(UUID id, LocalDate trimestreDebut, String analyseObjectifs, String progres, String difficultes,
            String habitudesACorriger, String recommandations, String conclusion, Instant creeLe) {

        static BilanVue of(BilanTrimestrielAbonneeRepository.LignePortail b) {
            return new BilanVue(b.id(), b.trimestreDebut(), b.analyseObjectifs(), b.progres(), b.difficultes(),
                    b.habitudesACorriger(), b.recommandations(), b.conclusion().name(), b.creeLe());
        }
    }

    /** Photo affichée dans une séance : de quoi la charger et la légender. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PhotoSeanceVue(UUID id, String type, String legende, String nomFichier) {

        static PhotoSeanceVue of(PhotoEvolutionAbonneeRepository.Ligne p) {
            return new PhotoSeanceVue(p.id(), p.type().name(), p.legende(), p.nomFichier());
        }
    }

    /** Photo de la galerie : date, légende, nom (le fichier se charge à part). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PhotoVue(UUID id, LocalDate jour, String legende, String nomFichier) {

        static PhotoVue of(PhotoEvolutionAbonneeRepository.Ligne p) {
            return new PhotoVue(p.id(), p.jour(), p.legende(), p.nomFichier());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RdvVue(UUID id, LocalDate jour, LocalTime heure, int dureeMin, String prestation, String statut) {

        static RdvVue of(RendezVousRepository.LigneAVenir r, String prestationLibelle) {
            return new RdvVue(r.id(), r.jour(), r.heure(), r.dureeMin(), prestationLibelle, r.statut().name());
        }
    }

    record PrestationVue(String code, String libelle, long prix) {

        static PrestationVue of(PrestationInfo p) {
            return new PrestationVue(p.code(), p.libelle(), p.prix());
        }
    }

    record DisponibiliteVue(LocalTime heure, long placesRestantes) {

        static DisponibiliteVue of(Disponibilite d) {
            return new DisponibiliteVue(d.heure(), d.placesRestantes());
        }
    }

    record ConfirmationVue(String reference, LocalDate jour, LocalTime heure, String prestation, int dureeMin, long prix) {

        static ConfirmationVue of(Confirmation c) {
            return new ConfirmationVue(c.reference(), c.jour(), c.heure(), c.prestation(), c.dureeMin(), c.prix());
        }
    }
}
