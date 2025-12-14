package io.github.dreamlike.transform;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public class CustomNotNullValidator implements ConstraintValidator<NotNull, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return false;
    }

    public <T extends ConstraintValidatorContext> void genericMethod(List<T> constraintValidatorContexts, Set<ConstraintValidatorContext> constraintValidatorContexts2) {
    }
}
