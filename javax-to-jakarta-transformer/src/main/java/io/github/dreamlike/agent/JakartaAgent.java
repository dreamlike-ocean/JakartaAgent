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

public class JakartaAgent {

    public static void premain(String args, Instrumentation inst) {
        transform(inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        transform(inst);
    }

    private static void transform(Instrumentation inst) {
        inst.addTransformer(new RelocateTransformer());
    }
}