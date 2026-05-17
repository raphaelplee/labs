package de.raphaellee.transflow;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@Tag("unit")
class ArchUnitTest {

    private final JavaClasses classes = new ClassFileImporter()
        .importPackages("de.raphaellee.transflow");

    @Test
    void payment_doesNotImportOrderInternals() {
        // Note: PaymentController/PaymentService are allowed to call OrderService (public API).
        // The rule targets internal classes — Order entity and OrderRepository.
        ArchRule internalRule = noClasses()
            .that().resideInAPackage("de.raphaellee.transflow.payment..")
            .should().accessClassesThat()
            .haveFullyQualifiedName("de.raphaellee.transflow.order.Order")
            .orShould().accessClassesThat()
            .haveFullyQualifiedName("de.raphaellee.transflow.order.OrderRepository");

        internalRule.check(classes);
    }

    @Test
    void fulfillment_doesNotImportOrchestrationInternals() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("de.raphaellee.transflow.fulfillment..")
            .should().accessClassesThat()
            .resideInAPackage("de.raphaellee.transflow.orchestration..");

        rule.check(classes);
    }

    @Test
    void fulfillment_doesNotImportOrderInternals() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("de.raphaellee.transflow.fulfillment..")
            .should().accessClassesThat()
            .haveFullyQualifiedName("de.raphaellee.transflow.order.Order")
            .orShould().accessClassesThat()
            .haveFullyQualifiedName("de.raphaellee.transflow.order.OrderRepository");

        rule.check(classes);
    }

    @Test
    void order_doesNotImportAnyOtherModule() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("de.raphaellee.transflow.order..")
            .should().accessClassesThat()
            .resideInAnyPackage(
                "de.raphaellee.transflow.payment..",
                "de.raphaellee.transflow.fulfillment..",
                "de.raphaellee.transflow.orchestration.."
            );

        rule.check(classes);
    }
}
