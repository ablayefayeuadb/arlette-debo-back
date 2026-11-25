package sn.arlettedebo.platform.planning.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import sn.arlettedebo.platform.planning.internal.PlanningService;
import sn.arlettedebo.platform.planning.internal.domain.Creneau;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.commercial.Abonnees;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousSupplement;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.pilotage.Periode;

@Tag(name = "Planning & rendez-vous")
@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('planning:C')")
class PlanningController {

    private final PlanningService service;
    private final Effectifs effectifs;
    private final Abonnees abonnees;

    @Operation(summary = "Rendez-vous d'une période (coiffeuse : uniquement les siens)")
    @GetMapping("/rendez-vous")
    List<RdvVue> rendezVous(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String perimetre) {
        List<RendezVous> liste = service.rendezVous(new Periode(du, au), perimetre);
        Map<UUID, String> coiffeuses = effectifs.noms(liste.stream().map(RendezVous::getCoiffeuseId).toList());
        Set<UUID> dossiers = abonnees.clientIdsAvecDossier(liste.stream()
                .map(RendezVous::getClientId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<String, Boolean> cacheTel = new HashMap<>();
        List<UUID> ids = liste.stream().map(RendezVous::getId).toList();
        Map<UUID, Long> attentes = service.countAttentes(ids);
        Map<UUID, List<RendezVousSupplement>> supplements = service.supplementsDesRdvs(ids);
        return liste.stream().map(r -> RdvVue.of(r, coiffeuses, attentes.getOrDefault(r.getId(), 0L),
                supplements.getOrDefault(r.getId(), List.of()).stream()
                        .map(s -> new SupplementVue(s.getId(), s.getLibelle(), s.getMontant(), s.getAccepteLe()))
                        .toList(),
                dossierAbonnee(r, dossiers, cacheTel))).toList();
    }

    @Operation(summary = "Prendre un rendez-vous (durée et prix du catalogue ; pas de double réservation de la coiffeuse)")
    @PreAuthorize("hasAuthority('planning:G')")
    @PostMapping("/rendez-vous")
    @ResponseStatus(HttpStatus.CREATED)
    RdvVue creer(@Valid @RequestBody RdvRequest r) {
        return vue(service.creer(new PlanningService.NouveauRdv(r.perimetre(), r.jour(), r.heure(), r.prestation(), r.coiffeuseId(),
                r.clientId(), r.cliente(), r.telephone(), r.source(), r.note(), Boolean.TRUE.equals(r.horsCreneau()),
                Boolean.TRUE.equals(r.forcer()))));
    }

    @Operation(summary = "Changer le statut (accueil / Manager ; la coiffeuse démarre et termine ses prestations)")
    @PatchMapping("/rendez-vous/{id}/statut")
    RdvVue statut(@PathVariable UUID id, @Valid @RequestBody StatutRequest r) {
        return vue(service.changerStatut(id, r.statut(), r.motif(), Boolean.TRUE.equals(r.forcer())));
    }

    @Operation(summary = "Historique des modifications du rendez-vous")
    @GetMapping("/rendez-vous/{id}/historique")
    List<HistoriqueVue> historique(@PathVariable UUID id) {
        return service.historique(id).stream().map(HistoriqueVue::of).toList();
    }

    @Operation(summary = "Confirmer / ajuster la prestation d'intention à l'arrivée")
    @PatchMapping("/rendez-vous/{id}/intention")
    RdvVue intention(@PathVariable UUID id, @Valid @RequestBody IntentionRequest r) {
        return vue(service.definirIntention(id, r.prestation(), r.note()));
    }

    @Operation(summary = "Enregistrer une fiche d'attente (retard significatif)")
    @PreAuthorize("hasAuthority('planning:G')")
    @PostMapping("/rendez-vous/{id}/attente")
    @ResponseStatus(HttpStatus.CREATED)
    AttenteVue attente(@PathVariable UUID id, @Valid @RequestBody AttenteRequest r) {
        return AttenteVue.of(service.enregistrerAttente(id, r.motif(), Boolean.TRUE.equals(r.infoCliente()), r.decision()));
    }

    @Operation(summary = "Préparer la clôture : réalisé + suppléments")
    @PatchMapping("/rendez-vous/{id}/cloture")
    RdvVue cloture(@PathVariable UUID id, @Valid @RequestBody ClotureRequest r) {
        List<PlanningService.SupplementSaisi> lignes = r.supplements() == null ? List.of()
                : r.supplements().stream().map(s -> new PlanningService.SupplementSaisi(s.libelle(), s.montant())).toList();
        return vue(service.cloturerPreparation(id, r.prestationRealisee(), r.observations(), r.motifEcart(), lignes));
    }

    @Operation(summary = "Suppléments acceptés d'un rendez-vous")
    @GetMapping("/rendez-vous/{id}/supplements")
    List<SupplementVue> supplements(@PathVariable UUID id) {
        return service.supplementsDuRdv(id).stream()
                .map(s -> new SupplementVue(s.getId(), s.getLibelle(), s.getMontant(), s.getAccepteLe()))
                .toList();
    }

    @Operation(summary = "Réaffecter à une autre coiffeuse du salon")
    @PreAuthorize("hasAuthority('planning:G')")
    @PatchMapping("/rendez-vous/{id}/coiffeuse")
    RdvVue reaffecter(@PathVariable UUID id, @RequestBody CoiffeuseRequest r) {
        return vue(service.reaffecter(id, r.coiffeuseId(), Boolean.TRUE.equals(r.forcer())));
    }

    @Operation(summary = "Déplacer un rendez-vous pas encore commencé")
    @PreAuthorize("hasAuthority('planning:G')")
    @PatchMapping("/rendez-vous/{id}/horaire")
    RdvVue deplacer(@PathVariable UUID id, @Valid @RequestBody HoraireRequest r) {
        return vue(service.deplacer(id, r.jour(), r.heure()));
    }

    @Operation(summary = "Créneaux ouverts d'un jour avec places restantes (prise de RDV interne ; filtre coiffeuse optionnel)")
    @GetMapping("/disponibilites")
    List<DisponibiliteVue> disponibilites(@RequestParam String perimetre,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam String prestation,
            @RequestParam(required = false) UUID coiffeuseId) {
        return service.disponibilites(perimetre, jour, prestation, coiffeuseId).stream()
                .map(d -> new DisponibiliteVue(d.heure(), d.heureFin(), d.placesRestantes()))
                .toList();
    }

    @Operation(summary = "Créneaux de réservation (ouverts et fermés) avec occupation ; jour ou période du/au")
    @GetMapping("/creneaux")
    List<CreneauVue> creneaux(@RequestParam String perimetre,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        List<PlanningService.CreneauDetail> liste;
        if (du != null && au != null) {
            liste = service.creneaux(perimetre, du, au);
        } else if (jour != null) {
            liste = service.creneaux(perimetre, jour);
        } else {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Indiquez jour ou du/au.");
        }
        return liste.stream().map(CreneauVue::of).toList();
    }

    @Operation(summary = "Ouvrir un créneau de réservation")
    @PreAuthorize("hasAuthority('planning:G')")
    @PostMapping("/creneaux")
    @ResponseStatus(HttpStatus.CREATED)
    CreneauVue ouvrirCreneau(@Valid @RequestBody CreneauRequest r) {
        return CreneauVue.of(service.detailNouveau(
                service.ouvrirCreneau(r.perimetre(), r.jour(), r.heureDebut(), r.heureFin(), r.capacite() == null ? 1 : r.capacite())));
    }

    @Operation(summary = "Grille type horaire (1 h) : crée les créneaux absents entre de et a")
    @PreAuthorize("hasAuthority('planning:G')")
    @PostMapping("/creneaux/grille")
    Map<String, Integer> ouvrirGrille(@Valid @RequestBody GrilleRequest r) {
        int n = service.ouvrirGrille(r.perimetre(), r.jour(), r.de(), r.a(), r.capacite() == null ? 1 : r.capacite());
        return Map.of("crees", n);
    }

    @Operation(summary = "Grille métier Sky/Kids (créneaux fixes retours v1) sur une plage de jours")
    @PreAuthorize("hasAuthority('planning:G')")
    @PostMapping("/creneaux/grille-metier")
    Map<String, Integer> ouvrirGrilleMetier(@Valid @RequestBody GrilleMetierRequest r) {
        int n = service.ouvrirGrilleMetier(r.perimetre(), r.du(), r.au(), r.capacite() == null ? 1 : r.capacite());
        return Map.of("crees", n);
    }

    @Operation(summary = "Ouvrir ou fermer tous les créneaux d'un jour")
    @PreAuthorize("hasAuthority('planning:G')")
    @PatchMapping("/creneaux/jour")
    Map<String, Integer> basculerJour(@Valid @RequestBody JourOuvertureRequest r) {
        return Map.of("modifies", service.basculerJour(r.perimetre(), r.jour(), r.ouvert()));
    }

    @Operation(summary = "Recopier la grille de la veille sur un jour (sans écraser l'existant)")
    @PreAuthorize("hasAuthority('planning:G')")
    @PostMapping("/creneaux/recopie")
    Map<String, Integer> recopierVeille(@Valid @RequestBody RecopieRequest r) {
        return Map.of("crees", service.recopierVeille(r.perimetre(), r.jour()));
    }

    @Operation(summary = "Ouvrir / fermer un créneau à la réservation")
    @PreAuthorize("hasAuthority('planning:G')")
    @PatchMapping("/creneaux/{id}/ouverture")
    CreneauVue basculer(@PathVariable UUID id, @Valid @RequestBody OuvertureRequest r) {
        return CreneauVue.of(service.basculerCreneau(id, r.ouvert()));
    }

    @Operation(summary = "Modifier la capacité d'un créneau")
    @PreAuthorize("hasAuthority('planning:G')")
    @PatchMapping("/creneaux/{id}/capacite")
    CreneauVue capacite(@PathVariable UUID id, @Valid @RequestBody CapaciteRequest r) {
        return CreneauVue.of(service.modifierCapacite(id, r.capacite()));
    }

    @Operation(summary = "Retirer un créneau")
    @PreAuthorize("hasAuthority('planning:G')")
    @DeleteMapping("/creneaux/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimer(@PathVariable UUID id) {
        service.supprimerCreneau(id);
    }

    private RdvVue vue(RendezVous r) {
        Map<UUID, String> coiffeuses = effectifs.noms(java.util.Collections.singletonList(r.getCoiffeuseId()));
        boolean dossier = abonnees.aDossier(r.getClientId(), r.getTelephone(), r.getPerimetreCode());
        return RdvVue.of(r, coiffeuses, service.countAttentes(r.getId()),
                service.supplementsDuRdv(r.getId()).stream()
                        .map(s -> new SupplementVue(s.getId(), s.getLibelle(), s.getMontant(), s.getAccepteLe()))
                        .toList(),
                dossier);
    }

    private boolean dossierAbonnee(RendezVous r, Set<UUID> dossiersParClient, Map<String, Boolean> cacheTel) {
        if (r.getClientId() != null) {
            return dossiersParClient.contains(r.getClientId());
        }
        String tel = r.getTelephone();
        if (tel == null || tel.isBlank()) {
            return false;
        }
        String cle = r.getPerimetreCode() + "|" + tel;
        return cacheTel.computeIfAbsent(cle, k -> abonnees.aDossier(null, tel, r.getPerimetreCode()));
    }

    @Operation(summary = "Coiffeuses actives d'un salon (compétences = codes prestations validés)")
    @GetMapping("/coiffeuses")
    List<CoiffeuseVue> coiffeuses(@RequestParam String perimetre) {
        return service.coiffeuses(perimetre).stream()
                .map(c -> new CoiffeuseVue(c.id(), c.nom(), c.perimetre(), c.competences()))
                .toList();
    }

    @Operation(summary = "Paramètres de grille du salon (capacité et plage horaire type)")
    @GetMapping("/parametres-creneaux")
    ParametresCreneauxVue parametresCreneaux(@RequestParam String perimetre) {
        PlanningService.ParametresCreneaux p = service.parametresCreneaux(perimetre);
        return new ParametresCreneauxVue(p.capacite(), p.heureOuverture(), p.heureFermeture());
    }

    record CoiffeuseVue(UUID id, String nom, String perimetre, java.util.Set<String> competences) {
    }

    record RdvVue(UUID id, String perimetre, LocalDate jour, LocalTime heure, int dureeMin, String cliente, String prestation,
            String prestationIntention, String prestationRealisee, String coiffeuse, long prix, String statut, String source,
            Long attenteMinutes, boolean attenteSignificative, long fichesAttente, UUID coiffeuseId, UUID clientId,
            String telephone, String note, String noteArrivee, String observationsCloture, String motifEcart,
            Boolean verifieConforme, String reference, List<SupplementVue> supplements,
            boolean horsCreneau, String typeCliente, boolean dossierAbonnee) {

        static RdvVue of(RendezVous r, Map<UUID, String> coiffeuses) {
            return of(r, coiffeuses, 0, List.of(), false);
        }

        static RdvVue of(RendezVous r, Map<UUID, String> coiffeuses, long fichesAttente, List<SupplementVue> supplements,
                boolean dossierAbonnee) {
            Long attente = r.attenteMinutes();
            if (attente == null && r.getArriveeLe() != null && r.getDebutLe() == null) {
                attente = java.time.Duration.between(r.getArriveeLe(), java.time.Instant.now()).toMinutes();
            }
            boolean significative = attente != null && attente >= sn.arlettedebo.platform.planning.internal.domain.AttenteRdv.SEUIL_MINUTES;
            return new RdvVue(r.getId(), r.getPerimetreCode(), r.getJour(), r.getHeure(), r.getDureeMin(), r.getClienteNom(),
                    r.getPrestationCode(), r.getPrestationIntentionCode(), r.getPrestationRealiseeCode(),
                    coiffeuses.get(r.getCoiffeuseId()), r.getPrix(), r.getStatut().name(), r.getSource().name(),
                    attente, significative, fichesAttente, r.getCoiffeuseId(), r.getClientId(), r.getTelephone(), r.getNote(),
                    r.getNoteArrivee(), r.getObservationsCloture(), r.getMotifEcart(), r.getVerifieConforme(), r.reference(),
                    supplements, r.isHorsCreneau(), r.getTypeCliente(), dossierAbonnee);
        }
    }

    record RdvRequest(@NotBlank String perimetre, @NotNull @FutureOrPresent LocalDate jour, @NotNull LocalTime heure,
            @NotBlank String prestation, UUID coiffeuseId, UUID clientId, @Size(max = 120) String cliente,
            @Size(max = 25) String telephone, RendezVous.Source source, @Size(max = 255) String note,
            Boolean horsCreneau, Boolean forcer) {
    }

    record StatutRequest(@NotNull RendezVous.Statut statut, @Size(max = 500) String motif, Boolean forcer) {
    }

    record IntentionRequest(@NotBlank String prestation, @Size(max = 500) String note) {
    }

    record AttenteRequest(@NotBlank @Size(max = 500) String motif, Boolean infoCliente,
            @NotNull sn.arlettedebo.platform.planning.internal.domain.AttenteRdv.Decision decision) {
    }

    record ClotureRequest(@NotBlank String prestationRealisee, @Size(max = 500) String observations,
            @Size(max = 500) String motifEcart, List<SupplementSaisiVue> supplements) {
    }

    record SupplementSaisiVue(@NotBlank @Size(max = 120) String libelle, @NotNull Long montant) {
    }

    record HistoriqueVue(UUID id, String champ, String avant, String apres, String motif, UUID acteurId,
            java.time.Instant le) {
        static HistoriqueVue of(sn.arlettedebo.platform.planning.internal.domain.RendezVousHistorique h) {
            return new HistoriqueVue(h.getId(), h.getChamp().name(), h.getAvant(), h.getApres(), h.getMotif(),
                    h.getActeurId(), h.getLe());
        }
    }

    record AttenteVue(UUID id, UUID rendezVousId, int attenteMinutes, String motif, boolean infoCliente, String decision,
            java.time.Instant le) {
        static AttenteVue of(sn.arlettedebo.platform.planning.internal.domain.AttenteRdv a) {
            return new AttenteVue(a.getId(), a.getRendezVousId(), a.getAttenteMinutes(), a.getMotif(), a.isInfoCliente(),
                    a.getDecision().name(), a.getLe());
        }
    }

    record SupplementVue(UUID id, String libelle, long montant, java.time.Instant accepteLe) {
    }

    record CoiffeuseRequest(UUID coiffeuseId, Boolean forcer) {
    }

    record HoraireRequest(@NotNull LocalDate jour, @NotNull LocalTime heure) {
    }

    record CreneauRequest(@NotBlank String perimetre, @NotNull LocalDate jour, @NotNull LocalTime heureDebut,
            @NotNull LocalTime heureFin, Integer capacite) {
    }

    record GrilleRequest(@NotBlank String perimetre, @NotNull LocalDate jour, @NotNull Integer de, @NotNull Integer a, Integer capacite) {
    }

    record GrilleMetierRequest(@NotBlank String perimetre, @NotNull LocalDate du, @NotNull LocalDate au, Integer capacite) {
    }

    record JourOuvertureRequest(@NotBlank String perimetre, @NotNull LocalDate jour, @NotNull Boolean ouvert) {
    }

    record RecopieRequest(@NotBlank String perimetre, @NotNull LocalDate jour) {
    }

    record OuvertureRequest(@NotNull Boolean ouvert) {
    }

    record CapaciteRequest(@NotNull Integer capacite) {
    }

    record CreneauVue(UUID id, String perimetre, LocalDate jour, LocalTime heureDebut, LocalTime heureFin, int capacite, boolean ouvert,
            long placesPrises, long placesRestantes) {

        static CreneauVue of(PlanningService.CreneauDetail d) {
            Creneau c = d.creneau();
            long prises = d.placesPrises();
            return new CreneauVue(c.getId(), c.getPerimetreCode(), c.getJour(), c.getHeureDebut(), c.getHeureFin(), c.getCapacite(),
                    c.isOuvert(), prises, Math.max(0, c.getCapacite() - prises));
        }
    }

    record DisponibiliteVue(LocalTime heure, LocalTime heureFin, long placesRestantes) {
    }

    record ParametresCreneauxVue(int capacite, int heureOuverture, int heureFermeture) {
    }
}
