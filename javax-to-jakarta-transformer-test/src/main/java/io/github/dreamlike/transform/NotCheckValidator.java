package io.github.dreamlike.transform;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class NotCheckValidator implements ConstraintValidator<NotCheck, String> {
    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        return false;
    }
}
