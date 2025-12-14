package io.github.dreamlike.transform;

import javax.validation.Constraint;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotCheckValidator.class)
public @interface NotCheck {
}
