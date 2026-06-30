package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Spring4To6RecipeTest {

    @Test
    void migratesWebMvcConfigurerAdapter() {
        String before = """
                package com.example;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
                public class WebConfig extends WebMvcConfigurerAdapter {
                }
                """;

        var change = new Spring4To6Recipe().transform("WebConfig.java", before);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("implements WebMvcConfigurer"));
        assertTrue(!after.contains("WebMvcConfigurerAdapter"));
    }
}
