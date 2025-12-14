package io.github.dreamlike.transform;

import javax.validation.ConstraintTarget;
import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;

@Valid
public class JavaBean {
    @NotNull(groups = ValidationGroup.ForceDefault.class)
    @NotEmpty
    @NotEmpty
    @NotCheck
    private String name;

    @NotNull
    @jakarta.validation.constraints.NotNull
    private String doubleNotNull;

    public void setName(@NotBlank String name) {
        this.name = name;
    }

    @AssertTrue
    public boolean assertBool(ConstraintTarget constraintTarget) {
        return true;
    }

    public static String returnJakarta() {
        return "javax.servlet";
    }

    public static Class<?> returnClass() {
        return Default.class;
    }
}
