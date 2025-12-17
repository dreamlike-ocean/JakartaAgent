/*
 * Copyright 2025 Dreamlike Ocean
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.dreamlike.agent;

import org.objectweb.asm.*;

import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JakartaAgent {

    private static final String DUMP_PATH_KEY = "jakarta.dump.path";

    private static final String FAST_KEY = "jakarta.compute.frames.fast";

    public static void premain(String args, Instrumentation inst) {
        transform(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        transform(args, inst);
    }

    private static void transform(String args, Instrumentation inst) {
        inst.addTransformer(new RelocateTransformer(parseArgs(args)));
    }

    private static JakartaAgentArgs parseArgs(String args) {
        String[] split = Objects.requireNonNullElse(args, "").split(",");
        Map<String, String> argMap = Stream.of(split)
                .map(s -> s.split("="))
                .filter(s -> s.length == 2)
                .collect(Collectors.toMap(s -> s[0].toLowerCase(), s -> s[1], (a, b) -> a));
        return new JakartaAgentArgs(
                argMap.get(DUMP_PATH_KEY),
                Boolean.parseBoolean(argMap.getOrDefault(FAST_KEY, "false"))
        );
    }

    record JakartaAgentArgs(String dumpPath, boolean fast){};
}