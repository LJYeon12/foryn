package com.forA.chatbot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.forA.chatbot", importOptions = DoNotIncludeTests.class)
class LayerArchitectureTest {

    // Controller가 Repository를 직접 의존하지 않고 Service를 거치도록 강제한다.
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_repositories =
            noClasses()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..repository..")
                    .because("controllers must call repositories through services");

    // Service 계층이 HttpServletRequest 같은 웹 계층 타입에 묶이지 않도록 막는다.
    @ArchTest
    static final ArchRule services_should_not_depend_on_servlet_types =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("jakarta.servlet..", "javax.servlet..")
                    .because("services must stay independent from the web layer");

    // Domain 객체가 Controller, Service, Repository 같은 애플리케이션 계층을 알지 못하게 한다.
    @ArchTest
    static final ArchRule domains_should_not_depend_on_application_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..controller..", "..service..", "..repository..")
                    .because("domain classes must not depend on application layers");

    // Controller가 하위 계층 구현을 상속해 레이어 경계를 우회하지 못하게 한다.
    @ArchTest
    static final ArchRule controllers_should_not_inherit_from_lower_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .beAssignableTo(resideInAnyPackage("..service..", "..repository..", "..domain.."))
                    .because("controllers must not inherit implementation details from lower layers");

    // Service가 Controller, Repository, Domain 타입을 상속하지 못하게 한다.
    @ArchTest
    static final ArchRule services_should_not_inherit_from_other_application_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .beAssignableTo(resideInAnyPackage("..controller..", "..repository..", "..domain.."))
                    .because("services must not inherit from controllers, repositories, or domain models");

    // Repository가 다른 애플리케이션 계층을 상속하지 않고 영속성 어댑터 역할에 머물게 한다.
    @ArchTest
    static final ArchRule repositories_should_not_inherit_from_application_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..repository..")
                    .should()
                    .beAssignableTo(resideInAnyPackage("..controller..", "..service..", "..domain.."))
                    .because("repositories must stay persistence adapters, not subclasses of application layers");

    // Domain 모델이 Controller, Service, Repository를 상속하지 못하게 한다.
    @ArchTest
    static final ArchRule domains_should_not_inherit_from_application_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .beAssignableTo(resideInAnyPackage("..controller..", "..service..", "..repository.."))
                    .because("domain models must not inherit from application layers");

    // 도메인 패키지끼리 순환 참조가 생기지 않도록 검사한다.
    @ArchTest
    static final ArchRule domain_packages_should_be_free_of_cycles =
            slices()
                    .matching("com.forA.chatbot.(*)..domain..")
                    .should()
                    .beFreeOfCycles()
                    .because("domain packages must not form cyclic dependencies");

    // 서비스 패키지끼리 순환 참조가 생기지 않도록 검사한다.
    @ArchTest
    static final ArchRule service_packages_should_be_free_of_cycles =
            slices()
                    .matching("com.forA.chatbot.(*)..service..")
                    .should()
                    .beFreeOfCycles()
                    .because("service packages must not form cyclic dependencies");

    // 레포지토리 패키지끼리 순환 참조가 생기지 않도록 검사한다.
    @ArchTest
    static final ArchRule repository_packages_should_be_free_of_cycles =
            slices()
                    .matching("com.forA.chatbot.(*)..repository..")
                    .should()
                    .beFreeOfCycles()
                    .because("repository packages must not form cyclic dependencies");

    // 컨트롤러 패키지끼리 순환 참조가 생기지 않도록 검사한다.
    @ArchTest
    static final ArchRule controller_packages_should_be_free_of_cycles =
            slices()
                    .matching("com.forA.chatbot.(*)..controller..")
                    .should()
                    .beFreeOfCycles()
                    .because("controller packages must not form cyclic dependencies");

    // Repository 패키지의 타입 이름이 Repository로 끝나는지 검사한다.
    @ArchTest
    static final ArchRule repositories_should_be_named_repository =
            classes()
                    .that()
                    .resideInAPackage("..repository..")
                    .should()
                    .haveSimpleNameEndingWith("Repository");

    // Controller 패키지의 타입 이름과 @RestController 선언을 함께 검사한다.
    @ArchTest
    static final ArchRule controllers_should_be_named_controller_and_be_rest_controllers =
            classes()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .haveSimpleNameEndingWith("Controller")
                    .andShould()
                    .beAnnotatedWith(RestController.class);
}
