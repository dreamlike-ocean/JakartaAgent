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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;

class RelocateTransformer implements ClassFileTransformer {
    private final String DUMP_PATH;
    private final boolean fast;
    final ConcurrentHashMap<String, ClassMeta> classMetaMap;

    RelocateTransformer(JakartaAgent.JakartaAgentArgs args) {
        DUMP_PATH = args.dumpPath();
        this.fast = args.fast();
        classMetaMap = new ConcurrentHashMap<>();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) {
        if (protectionDomain == null || protectionDomain.getCodeSource() == null || JakartaRelocatingClassVisitor.isBinaryPrefix(className)) {
            return classFileBuffer;
        }
        ClassReader classReader = new ClassReader(classFileBuffer);
        int flags = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
        ClassWriter classWriter = fast ? new UnSafeClassWriter(classReader, flags) : new SafeClassWriter(this, loader, classReader, flags);
        // relocatingClassVisitor重定向后交给classWriter写出
        JakartaRelocatingClassVisitor relocatingClassVisitor = new JakartaRelocatingClassVisitor(classWriter);

        classReader.accept(relocatingClassVisitor, ClassReader.EXPAND_FRAMES);
        if (relocatingClassVisitor.needTransform && DUMP_PATH != null) {
            dump(className, classWriter.toByteArray());
        }
        return relocatingClassVisitor.needTransform ? classWriter.toByteArray() : classFileBuffer;
    }

    void dump(String className, byte[] classfileBuffer) {
        try {
            Path path = Paths.get(DUMP_PATH + "/" + className.replace('.', '/') + ".class");
            Files.createDirectories(path.getParent());
            Files.write(path, classfileBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    record ClassMeta(String className, String superName, String[] interfaces, boolean isInterface) {
    }
}