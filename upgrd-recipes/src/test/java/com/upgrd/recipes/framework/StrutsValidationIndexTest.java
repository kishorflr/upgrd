package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsValidationIndexTest {

    @Test
    void parsesValidationFields() {
        var fields = StrutsValidationIndex.parse("""
                <form-validation>
                  <formset>
                    <form name="userForm">
                      <field property="username" depends="required"/>
                      <field property="email" depends="required,email"/>
                    </form>
                  </formset>
                </form-validation>
                """);
        assertTrue(fields.get("userForm").containsAll(java.util.List.of("username", "email")));
    }
}
