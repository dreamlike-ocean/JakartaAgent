package io.github.dreamlike.transform;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.validation.ConstraintTarget;
import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;
import java.io.IOException;
import java.util.function.Function;

@Valid
@WebFilter
public class JavaBean<T extends Servlet> {
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


    public static <T extends Filter> void print(T t) throws ServletException, IOException {
        try {
            t.doFilter(null, null, null);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        Class s = Filter.class;
        String name = "java.sevrlet.Filter";
        Function<Filter, String> toString = Filter::toString;
        System.out.println(t);
    }
}
