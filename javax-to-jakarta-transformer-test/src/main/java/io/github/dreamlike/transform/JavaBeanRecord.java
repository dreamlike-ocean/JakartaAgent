package io.github.dreamlike.transform;

import javax.validation.constraints.NotBlank;
import javax.validation.groups.Default;

public record JavaBeanRecord(@NotBlank String name) implements Default {
}
