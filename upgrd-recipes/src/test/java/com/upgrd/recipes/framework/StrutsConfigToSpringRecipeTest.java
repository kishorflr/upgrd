package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsConfigToSpringRecipeTest {

    @Test
    void generatesSpringHintsFromStrutsConfig() {
        String strutsConfig = """
                <?xml version="1.0" encoding="UTF-8"?>
                <struts-config>
                  <action-mappings>
                    <action path="/user" type="com.example.UserAction">
                      <forward name="success" path="/pages/success.jsp"/>
                    </action>
                  </action-mappings>
                </struts-config>
                """;

        var change = new StrutsConfigToSpringRecipe().transform("WEB-INF/struts-config.xml", strutsConfig);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("spring-struts-migration") || after.contains("/user"));
        assertTrue(after.contains("com.example.UserAction"));
        assertTrue(after.contains("mvc:annotation-driven"));
    }
}
