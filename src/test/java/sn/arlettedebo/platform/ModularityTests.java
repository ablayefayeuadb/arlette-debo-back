package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Garde-fou d'architecture : aucun module n'accède aux internes d'un autre, pas de cycle. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);

    @Test
    void lesFrontieresDesModulesSontRespectees() {
        modules.verify();
    }

    @Test
    void lesModulesAttendusExistent() {
        assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
                .contains("iam", "audit", "referentiel", "rh", "planning", "qualite", "caisse", "commercial", "stock",
                        "communication", "finance");
    }

    @Test
    void documentationDesModules() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml().writeModuleCanvases();
        assertThat(java.nio.file.Path.of("target", "spring-modulith-docs")).isDirectoryContaining("glob:**/components.puml");
    }
}
