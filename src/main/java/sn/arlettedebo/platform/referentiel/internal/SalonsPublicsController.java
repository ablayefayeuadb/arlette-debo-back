package sn.arlettedebo.platform.referentiel.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liste publique des salons ouverts : noms affichés par l'interface (y compris avant connexion). */
@Tag(name = "Public — salons")
@RestController
@RequestMapping("/api/v1/public/salons")
@RequiredArgsConstructor
class SalonsPublicsController {

    private final PerimetreService salons;

    @Operation(summary = "Lieux actifs (salons et boutiques) : code, nom, type — l’UI filtre selon le besoin")
    @GetMapping
    List<Salon> actifs() {
        return salons.lister().stream().filter(Perimetre::isActif)
                .map(p -> new Salon(p.getCode(), p.getLibelle(), p.getType())).toList();
    }

    record Salon(String code, String libelle, String type) {
    }
}
