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

class RelocateTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) {
        if (protectionDomain == null || protectionDomain.getCodeSource() == null || JakartaRelocatingClassVisitor.isBinaryPrefix(className)) {
            return classFileBuffer;
        }
        ClassReader classReader = new ClassReader(classFileBuffer);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        // relocatingClassVisitor重定向后交给classWriter写出
        JakartaRelocatingClassVisitor relocatingClassVisitor = new JakartaRelocatingClassVisitor(classWriter);

        classReader.accept(relocatingClassVisitor, ClassReader.EXPAND_FRAMES);
        if (relocatingClassVisitor.needTransform) {
            //todo 条件dump
            dump(className, classWriter.toByteArray());
        }
        return relocatingClassVisitor.needTransform ? classWriter.toByteArray() : classFileBuffer;
    }

    static void dump(String className, byte[] classfileBuffer) {
        try {
            Path path = Paths.get("jakarta-transformed/" + className.replace('.', '/') + ".class");
            Files.createDirectories(path.getParent());
            Files.write(path, classfileBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}