package io.github.asmeduardo.orderflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;

/**
 * Executable version of ADRs 0003 and 0008: the modules share no code and talk only through
 * events, and the dependencies inside each module point from the adapters towards the domain.
 */
@AnalyzeClasses(packages = "io.github.asmeduardo.orderflow", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    @ArchTest
    static final ArchRule inventoryDoesNotKnowAboutOrders = noClasses()
            .that()
            .resideInAPackage("..inventory..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..orders..")
            .because("inventory is about to become a separate service that only reacts to order events");

    @ArchTest
    static final ArchRule ordersDoesNotKnowAboutInventory = noClasses()
            .that()
            .resideInAPackage("..orders..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..inventory..")
            .because("orders and inventory only exchange the versioned events of event-contracts");

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
            .resideInAnyPackage("..application..", "..web..", "..messaging..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..web..", "..orders.messaging..", "..inventory.messaging..")
            .because("HTTP and Kafka adapters call the application, never the other way round");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("controllers go through application services, which own transactions");
}
