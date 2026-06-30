package com.upgrd.core.compat;

import com.upgrd.core.model.ApiRemediationType;

import java.util.List;

final class ApiCompatibilityCatalog {

    private ApiCompatibilityCatalog() {
    }

    static List<ApiCatalogEntry> entries() {
        return List.of(
                entry("javax-servlet", "javax.servlet", "servlet",
                        "Java EE javax.servlet removed in Jakarta EE 10",
                        "jakarta.servlet.*", ApiRemediationType.REPLACEMENT,
                        "upgrd:JavaxToJakarta", "portable-jakarta"),
                entry("javax-persistence", "javax.persistence", "persistence",
                        "JPA javax.persistence namespace removed in Jakarta",
                        "jakarta.persistence.*", ApiRemediationType.REPLACEMENT,
                        "upgrd:JavaxToJakarta", "portable-jakarta"),
                entry("javax-validation", "javax.validation", "validation",
                        "Bean Validation javax namespace removed in Jakarta",
                        "jakarta.validation.*", ApiRemediationType.REPLACEMENT,
                        "upgrd:JavaxToJakarta", "portable-jakarta"),
                entry("log4j1", "org.apache.log4j", "logging",
                        "Log4j 1.x is EOL with known CVEs",
                        "org.slf4j.Logger + LoggerFactory", ApiRemediationType.AUTOMATED,
                        "upgrd:Log4j1ToSlf4j", "migrate-log4j1"),
                entry("struts-action", "org.apache.struts.action", "framework",
                        "Struts 1.x Action/ActionForm API is unmaintained",
                        "Spring MVC @Controller + @ModelAttribute", ApiRemediationType.MANUAL,
                        null, "struts-to-spring-mvc"),
                entry("struts-config", "org.apache.struts.config", "framework",
                        "Struts configuration API",
                        "Spring MVC @RequestMapping / Java config", ApiRemediationType.MANUAL,
                        null, "struts-config-to-spring"),
                entry("struts-util", "org.apache.struts.util", "framework",
                        "Struts utility classes",
                        "Spring equivalents or plain Java", ApiRemediationType.MANUAL,
                        null, "struts-to-spring-mvc"),
                entry("weblogic-api", "weblogic.", "server",
                        "WebLogic proprietary API in portable code",
                        "Thin adapter in deploy/weblogic or portable JDBC/JNDI", ApiRemediationType.MANUAL,
                        null, "weblogic-adapters"),
                entry("spring4-web", "org.springframework.web.servlet", "framework",
                        "Spring MVC 4.x incompatible with Jakarta EE / Java 21",
                        "Spring Framework 6 / Boot 3", ApiRemediationType.REPLACEMENT,
                        "upgrd:Spring4To6", "spring-4-to-6"),
                entry("java-util-date", "java.util.Date", "language",
                        "Legacy mutable date/time API",
                        "java.time.Instant, LocalDate, ZonedDateTime", ApiRemediationType.MANUAL,
                        null, "upgrade-java"),
                entry("vector-hashtable", "java.util.Vector", "collections",
                        "Legacy synchronized collections",
                        "java.util.ArrayList or concurrent collections", ApiRemediationType.MANUAL,
                        null, "replace-raw-collections"),
                entry("unsafe-deserialization", "ObjectInputStream", "security",
                        "Unsafe Java deserialization",
                        "JSON/DTO mapping or validated deserialization filter", ApiRemediationType.UNSUPPORTED,
                        null, "remediate-deserialization"));
    }

    private static ApiCatalogEntry entry(
            String id,
            String apiPattern,
            String category,
            String description,
            String replacement,
            ApiRemediationType type,
            String recipeId,
            String planStepId) {
        return new ApiCatalogEntry(id, apiPattern, category, description, replacement, type, recipeId, planStepId);
    }
}
