package io.github.asmeduardo.orderflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;

/**
 * Executable version of ADR 0003: modules talk only through public APIs, and the dependencies
 * inside each module point from the web layer towards the domain.
 */
@AnalyzeClasses(packages = "io.github.asmeduardo.orderflow", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static final String INVENTORY_INTERNALS = "..inventory.domain..";
    private static final String INVENTORY_APPLICATION = "..inventory.application..";
    private static final String INVENTORY_WEB = "..inventory.web..";

    @ArchTest
    static final ArchRule inventoryDoesNotKnowAboutOrders = noClasses()
            .that()
            .resideInAPackage("..inventory..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..orders..")
            .because("inventory will become a separate service that only reacts to order events");

    @ArchTest
    static final ArchRule otherModulesUseOnlyTheInventoryApi = noClasses()
            .that()
            .resideOutsideOfPackage("..inventory..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(INVENTORY_INTERNALS, INVENTORY_APPLICATION, INVENTORY_WEB)
            .because("inventory.api is the only contract of the inventory module");

    @ArchTest
    static final ArchRule inventoryApiDoesNotLeakInternals = noClasses()
            .that()
            .resideInAPackage("..inventory.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(INVENTORY_INTERNALS, INVENTORY_APPLICATION, INVENTORY_WEB);

    @ArchTest
    static final ArchRule sharedKernelHasNoBusinessDependencies = noClasses()
            .that()
            .resideInAPackage("..orderflow.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..orders..", "..inventory..");

    @ArchTest
    static final ArchRule domainDependsOnNoOuterLayer = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..web..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnWeb = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..web..");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("controllers go through application services, which own transactions");
}
