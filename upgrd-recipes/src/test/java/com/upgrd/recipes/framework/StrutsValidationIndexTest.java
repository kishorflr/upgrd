package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsValidationIndexTest {

    @Test
    void parsesValidationFieldsWithDepends() {
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
        var userFields = fields.get("userForm");
        assertTrue(userFields.get(0).hasRule("required"));
        assertTrue(userFields.get(1).hasRule("email"));
        assertTrue(userFields.get(1).hasRule("required"));
    }
}
