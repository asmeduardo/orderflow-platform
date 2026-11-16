package io.github.asmeduardo.orderflow.orders.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;

/**
 * Dependencies inside the order service point from the HTTP and Kafka adapters towards the domain.
 * The inventory context is not even on the classpath: the services share only event-contracts.
 */
@AnalyzeClasses(
        packages = "io.github.asmeduardo.orderflow.orders",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringTest {

    @ArchTest
    static final ArchRule domainDependsOnNoOuterLayer = noClasses()
            .that()
            .resideInAPackage("..orders.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..orders.application..", "..orders.web..", "..orders.messaging..", "..shared..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage("..orders.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..orders.web..", "..orders.messaging..")
            .because("HTTP and Kafka adapters call the application, never the other way round");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .resideInAPackage("..orders.web..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("controllers go through application services, which own transactions");
}
