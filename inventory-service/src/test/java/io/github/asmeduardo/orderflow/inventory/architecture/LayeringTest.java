package io.github.asmeduardo.orderflow.inventory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;

/**
 * Dependencies inside the inventory service point from the HTTP and Kafka adapters towards the
 * domain. The order context is not on the classpath: the services share only event-contracts.
 */
@AnalyzeClasses(
        packages = "io.github.asmeduardo.orderflow.inventory",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringTest {

    @ArchTest
    static final ArchRule domainDependsOnNoOuterLayer = noClasses()
            .that()
            .resideInAPackage("..inventory.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..inventory.application..", "..inventory.web..", "..inventory.messaging..", "..shared..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage("..inventory.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..inventory.web..", "..inventory.messaging..")
            .because("HTTP and Kafka adapters call the application, never the other way round");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .resideInAPackage("..inventory.web..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("controllers go through application services, which own transactions");
}
