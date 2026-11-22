package de.raphaellee.transflow;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@Tag("unit")
class ModularityTest {

    @Test
    void moduleDependenciesMatchPackageInfo() {
        ApplicationModules.of(TransflowApplication.class).verify();
    }
}
