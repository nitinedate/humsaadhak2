package org.humsaadhak;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Fails the build when a module reaches into another module's internals or modules form a cycle. */
class ModularityTests {
    static final ApplicationModules MODULES = ApplicationModules.of(HumSaadhakApplication.class);

    @Test void verifiesModuleStructure() { MODULES.verify(); }

    @Test void writesModuleDiagrams() { new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml(); }
}
