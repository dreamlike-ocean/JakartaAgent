package io.github.dreamlike.transform;

import javax.servlet.Servlet;
import javax.validation.constraints.NotBlank;
import javax.validation.groups.Default;

public record JavaBeanRecord<T extends Servlet>(@NotBlank String name, T servlet) implements Default {
}
