package sn.arlettedebo.platform.planning.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService.Confirmation;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService.Disponibilite;
import sn.arlettedebo.platform.referentiel.Referentiels.PrestationInfo;

/** Réservation en ligne publique (sans compte) : catalogue, disponibilités, prise de rendez-vous. */
@Tag(name = "Réservation en ligne (public)")
@RestController
@RequestMapping("/api/v1/public/reservation")
@RequiredArgsConstructor
class ReservationController {

    private final ReservationEnLigneService service;

    @Operation(summary = "Prestations réservables par salon (durée, prix)")
    @GetMapping("/prestations")
    List<PrestationPubliqueVue> prestations() {
        return service.catalogue().stream().map(PrestationPubliqueVue::of).toList();
    }

    @Operation(summary = "Créneaux ouverts d'un jour et places restantes")
    @GetMapping("/disponibilites")
    List<DisponibiliteVue> disponibilites(@RequestParam @NotBlank String perimetre,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour, @RequestParam @NotBlank String prestation) {
        return service.disponibilites(perimetre, jour, prestation).stream().map(DisponibiliteVue::of).toList();
    }

    @Operation(summary = "Réserver un créneau — cliente ou abonnée (nom + téléphone) ; si abonnée déjà connue, le nom est repris de la fiche")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Confirmation reserver(@Valid @RequestBody DemandeRequest r, HttpServletRequest http) {
        return service.reserver(new ReservationEnLigneService.Demande(r.perimetre(), r.jour(), r.heure(), r.prestation(), r.nom(),
                r.telephone(), r.note(), r.typeCliente()), http.getRemoteAddr());
    }

    /** Prestation affichée au formulaire public : code, libellé, salon, durée, prix (sans catégorie interne). */
    record PrestationPubliqueVue(String code, String libelle, String perimetre, int dureeMin, long prix) {

        static PrestationPubliqueVue of(PrestationInfo p) {
            return new PrestationPubliqueVue(p.code(), p.libelle(), p.perimetre(), p.dureeMin(), p.prix());
        }
    }

    /** Créneau affiché au formulaire public : heure de début et places restantes. */
    record DisponibiliteVue(LocalTime heure, long placesRestantes) {

        static DisponibiliteVue of(Disponibilite d) {
            return new DisponibiliteVue(d.heure(), d.placesRestantes());
        }
    }

    /** Nom requis sauf si le téléphone correspond déjà à une abonnée (repris de la fiche). */
    record DemandeRequest(@NotBlank @Size(max = 20) String perimetre, @NotNull LocalDate jour, @NotNull LocalTime heure,
            @NotBlank @Size(max = 40) String prestation, @Size(max = 120) String nom, @NotBlank @Size(max = 25) String telephone,
            @Size(max = 255) String note, @NotBlank @Size(max = 20) String typeCliente) {
    }
}
