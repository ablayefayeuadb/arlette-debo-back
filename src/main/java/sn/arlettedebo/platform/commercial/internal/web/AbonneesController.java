package sn.arlettedebo.platform.commercial.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.commercial.internal.AbonneesService;
import sn.arlettedebo.platform.commercial.internal.BilansService;
import sn.arlettedebo.platform.commercial.internal.EcheancesService;
import sn.arlettedebo.platform.commercial.internal.PortailAbonneeService;
import sn.arlettedebo.platform.commercial.internal.domain.BilanTrimestrielAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.EcheanceAbonnement;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonnee;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.Referentiels.OptionSuiviInfo;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.web.PageResponse;

@Tag(name = "Abonnées — dossiers et suivi de séance")
@RestController
@Validated
@RequestMapping("/api/v1/commercial/abonnes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('PROFIL_MANAGER', 'PROFIL_COORDINATION', 'PROFIL_CHARGE_CLIENTELE', 'PROFIL_DG', 'PROFIL_CAISSIER')")
class AbonneesController {

    private final AbonneesService service;
    private final PortailAbonneeService portail;
    private final BilansService bilans;
    private final EcheancesService echeances;
    private final Referentiels referentiels;
    private final Effectifs effectifs;

    @Operation(summary = "Catalogue des options cochables (calendrier de soins)")
    @GetMapping("/options-suivi")
    List<OptionSuiviInfo> optionsSuivi() {
        return referentiels.optionsSuiviSoinActives();
    }

    @Operation(summary = "Dossiers d'abonnées de mes périmètres (page SQL) — salon et recherche nom / prénom / téléphone")
    @GetMapping
    PageResponse<LigneVue> lister(@RequestParam(required = false) String perimetre,
            @RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.lister(perimetre, q, PageResponse.requete(page, taille, Sort.unsorted()));
        return PageResponse.of(p, LigneVue::of);
    }

    @Operation(summary = "Créer un dossier : cliente existante (clientId) ou nouvelle cliente ; elle devient abonnée")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DossierVue creer(@Valid @RequestBody CreationRequest r) {
        AbonneesService.Fiche f = service.creer(new AbonneesService.NouvelleAbonnee(r.clientId(), r.perimetre(), r.nom(), r.prenom(),
                r.telephone(), r.email(), r.dateNaissance(), new DossierAbonnee.Donnees(null, null, r.prenom(), r.adresse(), r.profession(),
                        r.dateInscription(), r.priseEnCharge(), r.typeAbonnement(), r.profilJson(), r.recommandations())));
        return DossierVue.of(f, new PortailAbonneeService.StatutAcces(false, null, null, false));
    }

    @Operation(summary = "Détail d'un dossier")
    @GetMapping("/{id}")
    DossierVue lire(@PathVariable UUID id) {
        AbonneesService.Fiche f = service.lire(id);
        return DossierVue.of(f, portail.statutAcces(id));
    }

    @Operation(summary = "Statut de l'accès portail abonnée")
    @GetMapping("/{id}/acces")
    AccesVue statutAcces(@PathVariable UUID id) {
        return AccesVue.of(portail.statutAcces(id));
    }

    @Operation(summary = "Ouvrir l'accès portail (compte ABONNEE + e-mail avec MDP provisoire) — e-mail enregistré sur la fiche")
    @PostMapping("/{id}/acces")
    @ResponseStatus(HttpStatus.CREATED)
    AccesCreeVue ouvrirAcces(@PathVariable UUID id, @Valid @RequestBody AccesRequest body) {
        return AccesCreeVue.of(portail.ouvrirAcces(id, body.email()));
    }

    @Operation(summary = "Modifier un dossier (champs absents inchangés, chaîne vide = effacer)")
    @PatchMapping("/{id}")
    DossierVue modifier(@PathVariable UUID id, @Valid @RequestBody ModificationRequest r) {
        AbonneesService.Fiche f = service.modifier(id, new DossierAbonnee.Donnees(null, null, r.prenom(), r.adresse(), r.profession(),
                r.dateInscription(), r.priseEnCharge(), r.typeAbonnement(), r.profilJson(), r.recommandations()),
                r.dateNaissance());
        return DossierVue.of(f, portail.statutAcces(id));
    }

    @Operation(summary = "Suivis de séance d'un dossier, paginés (plus récents d'abord)")
    @GetMapping("/{id}/suivis")
    PageResponse<SuiviVue> suivis(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        var p = service.suivis(id, PageResponse.requete(page, taille, Sort.unsorted()));
        Map<UUID, String> noms = effectifs.noms(p.getContent().stream().map(SuiviSeanceAbonnee::getCoiffeuseId).toList());
        return PageResponse.of(p, s -> SuiviVue.of(s, noms.get(s.getCoiffeuseId())));
    }

    @Operation(summary = "Prochain N° de semaine suggéré pour une nouvelle séance")
    @GetMapping("/{id}/suivis/prochaine-semaine")
    Map<String, Integer> prochaineSemaine(@PathVariable UUID id) {
        return Map.of("numeroSemaine", service.prochainNumeroSemaine(id));
    }

    @Operation(summary = "Ajouter le suivi d'une séance (cases cochées du calendrier de soins)")
    @PostMapping("/{id}/suivis")
    @ResponseStatus(HttpStatus.CREATED)
    SuiviVue ajouterSuivi(@PathVariable UUID id, @Valid @RequestBody SuiviRequest r) {
        SuiviSeanceAbonnee s = service.ajouterSuivi(id, r.toSaisie());
        String nom = s.getCoiffeuseId() == null ? null : effectifs.noms(List.of(s.getCoiffeuseId())).get(s.getCoiffeuseId());
        return SuiviVue.of(s, nom);
    }

    @Operation(summary = "Compléter / modifier un suivi existant")
    @PatchMapping("/{id}/suivis/{suiviId}")
    SuiviVue modifierSuivi(@PathVariable UUID id, @PathVariable UUID suiviId, @Valid @RequestBody SuiviRequest r) {
        SuiviSeanceAbonnee s = service.modifierSuivi(id, suiviId, r.toSaisie());
        String nom = s.getCoiffeuseId() == null ? null : effectifs.noms(List.of(s.getCoiffeuseId())).get(s.getCoiffeuseId());
        return SuiviVue.of(s, nom);
    }

    @Operation(summary = "Photos des séances demandées (suiviIds = séances de la page affichée, 100 au plus)")
    @GetMapping("/{id}/photos")
    List<PhotoVue> photos(@PathVariable UUID id, @RequestParam @Size(min = 1, max = PageResponse.TAILLE_MAX) List<UUID> suiviIds) {
        return service.photosDesSuivis(id, suiviIds).stream().map(PhotoVue::of).toList();
    }

    @Operation(summary = "Déposer une photo de séance (PNG/JPEG) — obligatoirement liée à un suivi")
    @PostMapping(path = "/{id}/suivis/{suiviId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PhotoVue deposerPhotoSeance(@PathVariable UUID id, @PathVariable UUID suiviId, @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(defaultValue = "EVOLUTION") PhotoEvolutionAbonnee.Type type,
            @RequestParam(required = false) LocalDate jour, @RequestParam(required = false) @Size(max = 255) String legende) {
        return PhotoVue.of(service.ajouterPhoto(id, fichier, type, jour, suiviId, legende));
    }

    /** @deprecated préférer {@code POST .../suivis/{suiviId}/photos} */
    @Operation(summary = "Déposer une photo (PNG/JPEG) — suiviId obligatoire")
    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PhotoVue deposerPhoto(@PathVariable UUID id, @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(defaultValue = "EVOLUTION") PhotoEvolutionAbonnee.Type type,
            @RequestParam(required = false) LocalDate jour, @RequestParam UUID suiviId,
            @RequestParam(required = false) @Size(max = 255) String legende) {
        return PhotoVue.of(service.ajouterPhoto(id, fichier, type, jour, suiviId, legende));
    }

    @Operation(summary = "Afficher / télécharger une photo")
    @GetMapping("/{id}/photos/{photoId}/fichier")
    ResponseEntity<Resource> telechargerPhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        AbonneesService.PhotoFichier f = service.telechargerPhoto(id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.meta().getTypeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.meta().getNomFichier(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.contenu());
    }

    @Operation(summary = "Retirer une photo")
    @DeleteMapping("/{id}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimerPhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        service.supprimerPhoto(id, photoId);
    }

    // ------------------------------------------------------------ Bilans trimestriels

    @Operation(summary = "Bilans de soins trimestriels d'un dossier, paginés (plus récents d'abord)")
    @GetMapping("/{id}/bilans")
    PageResponse<BilanVue> bilans(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.of(bilans.lister(id, PageResponse.requete(page, taille, Sort.unsorted())), BilanVue::of);
    }

    @Operation(summary = "Créer un bilan trimestriel")
    @PostMapping("/{id}/bilans")
    @ResponseStatus(HttpStatus.CREATED)
    BilanVue creerBilan(@PathVariable UUID id, @Valid @RequestBody BilanRequest r) {
        return BilanVue.of(bilans.creer(id, r.toSaisie()));
    }

    @Operation(summary = "Détail d'un bilan trimestriel")
    @GetMapping("/{id}/bilans/{bilanId}")
    BilanVue bilan(@PathVariable UUID id, @PathVariable UUID bilanId) {
        return BilanVue.of(bilans.lire(id, bilanId));
    }

    @Operation(summary = "Modifier un bilan (champs absents inchangés, chaîne vide = effacer)")
    @PatchMapping("/{id}/bilans/{bilanId}")
    BilanVue modifierBilan(@PathVariable UUID id, @PathVariable UUID bilanId, @Valid @RequestBody BilanRequest r) {
        return BilanVue.of(bilans.modifier(id, bilanId, r.toSaisie()));
    }

    // ------------------------------------------------------------ Paiements de l'abonnement

    @Operation(summary = "Échéances de paiement d'un dossier, paginées (plus récentes d'abord)")
    @GetMapping("/{id}/echeances")
    PageResponse<EcheanceVue> echeances(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.TAILLE_MAX) int taille) {
        return PageResponse.of(echeances.lister(id, PageResponse.requete(page, taille, Sort.unsorted())), EcheanceVue::of);
    }

    @Operation(summary = "Ajouter une échéance à la main (montant par défaut : prix de la formule)")
    @PostMapping("/{id}/echeances")
    @ResponseStatus(HttpStatus.CREATED)
    EcheanceVue creerEcheance(@PathVariable UUID id, @Valid @RequestBody EcheanceRequest r) {
        return EcheanceVue.of(echeances.creer(id, r.echeanceLe(), r.montant()));
    }

    @Operation(summary = "Marquer une échéance payée / annulée, ou ajuster son montant")
    @PatchMapping("/{id}/echeances/{echeanceId}")
    EcheanceVue modifierEcheance(@PathVariable UUID id, @PathVariable UUID echeanceId, @Valid @RequestBody EcheanceModification r) {
        return EcheanceVue.of(echeances.modifier(id, echeanceId, new EcheancesService.Modification(r.montant(), r.statut())));
    }

    // ------------------------------------------------------------ DTO

    record CreationRequest(UUID clientId, @Size(max = 30) String perimetre, @Size(max = 120) String nom, @Size(max = 80) String prenom,
            @Size(max = 25) String telephone, @Email @Size(max = 160) String email, @Size(max = 255) String adresse,
            @Size(max = 120) String profession, LocalDate dateInscription, @Size(max = 255) String priseEnCharge,
            DossierAbonnee.TypeAbonnement typeAbonnement, Map<String, Object> profilJson, @Size(max = 2000) String recommandations,
            @Past LocalDate dateNaissance) {
    }

    record ModificationRequest(@Size(max = 80) String prenom, @Size(max = 255) String adresse, @Size(max = 120) String profession,
            LocalDate dateInscription, @Size(max = 255) String priseEnCharge, DossierAbonnee.TypeAbonnement typeAbonnement,
            Map<String, Object> profilJson, @Size(max = 2000) String recommandations, @Past LocalDate dateNaissance) {
    }

    record SuiviRequest(LocalDate jour, @Min(1) @Max(520) Short numeroSemaine, UUID coiffeuseId,
            List<@Size(max = 40) String> optionCodes, @Size(max = 255) String coiffureProtectrice,
            @Size(max = 1000) String observation) {

        AbonneesService.SaisieSuivi toSaisie() {
            return new AbonneesService.SaisieSuivi(jour, numeroSemaine, coiffeuseId, optionCodes, coiffureProtectrice, observation);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LigneVue(UUID id, UUID clientId, String perimetre, String nom, String prenom, String telephone, String typeAbonnement,
            LocalDate dateInscription) {

        static LigneVue of(DossierAbonneeRepository.Ligne l) {
            return new LigneVue(l.id(), l.clientId(), l.perimetre(), l.nom(), l.prenom(), l.telephone(),
                    l.typeAbonnement() == null ? null : l.typeAbonnement().name(), l.dateInscription());
        }
    }

    record BilanRequest(LocalDate trimestreDebut, @Size(max = 4000) String analyseObjectifs, @Size(max = 4000) String progres,
            @Size(max = 4000) String difficultes, @Size(max = 4000) String habitudesACorriger,
            @Size(max = 4000) String recommandations, BilanTrimestrielAbonnee.Conclusion conclusion) {

        BilansService.Saisie toSaisie() {
            return new BilansService.Saisie(trimestreDebut, analyseObjectifs, progres, difficultes, habitudesACorriger,
                    recommandations, conclusion);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BilanVue(UUID id, LocalDate trimestreDebut, String analyseObjectifs, String progres, String difficultes,
            String habitudesACorriger, String recommandations, String conclusion, Instant creeLe) {

        static BilanVue of(BilanTrimestrielAbonnee b) {
            return new BilanVue(b.getId(), b.getTrimestreDebut(), b.getAnalyseObjectifs(), b.getProgres(), b.getDifficultes(),
                    b.getHabitudesACorriger(), b.getRecommandations(), b.getConclusion().name(), b.getCreeLe());
        }
    }

    record EcheanceRequest(@jakarta.validation.constraints.NotNull LocalDate echeanceLe, @Min(0) Long montant) {
    }

    record EcheanceModification(@Min(0) Long montant, EcheanceAbonnement.Statut statut) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EcheanceVue(UUID id, String periode, LocalDate echeanceLe, long montant, String statut, UUID factureId,
            UUID seanceSuiviId, Instant payeeLe) {

        static EcheanceVue of(EcheanceAbonnement e) {
            return new EcheanceVue(e.getId(), e.getPeriode(), e.getEcheanceLe(), e.getMontant(), e.getStatut().name(),
                    e.getFactureId(), e.getSeanceSuiviId(), e.getPayeeLe());
        }
    }

    record AccesRequest(@NotBlank @Email @Size(max = 160) String email) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccesVue(boolean ouvert, UUID utilisateurId, String email, boolean actif) {
        static AccesVue of(PortailAbonneeService.StatutAcces s) {
            return new AccesVue(s.ouvert(), s.utilisateurId(), s.email(), s.actif());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccesCreeVue(UUID utilisateurId, String email, String motDePasseProvisoire) {
        static AccesCreeVue of(PortailAbonneeService.AccesOuvert a) {
            return new AccesCreeVue(a.utilisateurId(), a.email(), a.motDePasseProvisoire());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DossierVue(UUID id, UUID clientId, String perimetre, String nom, String prenom, String telephone, String email,
            String adresse, String profession, LocalDate dateNaissance, LocalDate dateInscription, String priseEnCharge,
            String typeAbonnement, Map<String, Object> profilJson, String recommandations, Boolean accesPortail, String accesEmail) {

        static DossierVue of(AbonneesService.Fiche f) {
            return of(f, null);
        }

        static DossierVue of(AbonneesService.Fiche f, PortailAbonneeService.StatutAcces acces) {
            DossierAbonnee d = f.dossier();
            return new DossierVue(d.getId(), d.getClientId(), d.getPerimetreCode(), f.client().getNom(), d.getPrenom(),
                    f.client().getTelephone(), f.client().getEmail(), d.getAdresse(), d.getProfession(), f.client().getDateNaissance(), d.getDateInscription(),
                    d.getPriseEnCharge(), d.getTypeAbonnement() == null ? null : d.getTypeAbonnement().name(), d.getProfilJson(),
                    d.getRecommandations(), acces == null ? d.getUtilisateurId() != null : acces.ouvert(),
                    acces == null ? null : acces.email());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuiviVue(UUID id, LocalDate jour, Short numeroSemaine, UUID coiffeuseId, String coiffeuse, List<String> optionCodes,
            String prepoo, String shampoo, String masque, String soinPersonnalise, String leaveIn, String modeSechage,
            String coiffureProtectrice, String observation) {

        static SuiviVue of(SuiviSeanceAbonnee s, String coiffeuseNom) {
            return new SuiviVue(s.getId(), s.getJour(), s.getNumeroSemaine(), s.getCoiffeuseId(), coiffeuseNom,
                    s.optionsOrdonnees(), s.getPrepoo(), s.getShampoo(), s.getMasque(), s.getSoinPersonnalise(), s.getLeaveIn(),
                    s.getModeSechage(), s.getCoiffureProtectrice(), s.getObservation());
        }
    }

    /** Photo d'une séance (écran staff) : de quoi l'afficher, la légender et la retirer. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PhotoVue(UUID id, String type, UUID suiviId, String legende) {

        static PhotoVue of(PhotoEvolutionAbonnee p) {
            return new PhotoVue(p.getId(), p.getTypePhoto().name(), p.getSuiviId(), p.getLegende());
        }

        static PhotoVue of(PhotoEvolutionAbonneeRepository.Ligne p) {
            return new PhotoVue(p.id(), p.type().name(), p.suiviId(), p.legende());
        }
    }
}
