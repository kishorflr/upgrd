package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void parsesMinlengthAndMaxlengthVars() {
        var fields = StrutsValidationIndex.parse("""
                <form-validation>
                  <formset>
                    <form name="userForm">
                      <field property="username" depends="required,minlength">
                        <var><var-name>minlength</var-name><var-value>3</var-value></var>
                      </field>
                      <field property="password" depends="required,maxlength">
                        <var><var-name>maxlength</var-name><var-value>20</var-value></var>
                      </field>
                    </form>
                  </formset>
                </form-validation>
                """);
        var userFields = fields.get("userForm");
        assertEquals(3, userFields.get(0).minLength());
        assertEquals(20, userFields.get(1).maxLength());
    }
}
