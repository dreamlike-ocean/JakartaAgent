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
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

class JakartaRelocatingClassVisitor extends ClassVisitor {
    private static final Map<String, String> binaryMappings = Map.of("javax/servlet", "jakarta/servlet", "javax/validation", "jakarta/validation");
    private static final Map<String, String> classMappings = Map.of("javax.servlet", "jakarta.servlet", "javax.validation", "jakarta.validation");

    private final HashSet<String> classHandleProcessed = new HashSet<String>();
    boolean needTransform;

    public JakartaRelocatingClassVisitor(ClassWriter classWriter) {
        super(Opcodes.ASM9, classWriter);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }

    private String relocateBinary(String s) {
        if (s == null)
            return null;
        for (Map.Entry<String, String> binaryMapping : binaryMappings.entrySet()) {
            String oldName = binaryMapping.getKey();
            String newValue = binaryMapping.getValue();
            if (s.contains(oldName)) {
                needTransform = true;
                return s.replace(oldName, newValue);
            }
        }
        return s;
    }

    private String relocateClassName(String s) {
        if (s == null)
            return null;
        for (Map.Entry<String, String> classMapping : classMappings.entrySet()) {
            String oldName = classMapping.getKey();
            String newValue = classMapping.getValue();
            if (s.contains(oldName)) {
                needTransform = true;
                return s.replace(oldName, newValue);
            }
        }
        return s;
    }

    private String relocateSignature(String signature, boolean isField) {
        if (signature == null) {
            return null;
        }
        SignatureReader reader = new SignatureReader(signature);
        SignatureWriter writer = new SignatureWriter() {
            @Override
            public void visitClassType(String name) {
                super.visitClassType(relocateBinary(name));
            }

            @Override
            public void visitInnerClassType(String name) {
                super.visitInnerClassType(relocateBinary(name));
            }
        };
        // 字段是单一类型 只有一个
        // Ljava/util/List<Ljavax/validation/ConstraintValidator;>;
        // JavaTypeSignature
        if (isField) {
            reader.acceptType(writer);
        } else {
            // 类上的签名是一个复合类型 包含多个所以这里
            // <T:Ljava/lang/Object;>Ljava/lang/Object;Ljava/util/Map<TT;Ljava/lang/String;>;
            // ClassSignature
            reader.accept(writer);
        }
        return writer.toString();
    }

    private String[] renameArray(String[] arr) {
        if (arr == null)
            return null;
        HashSet<String> interfacesSet = new HashSet<String>();
        for (String s : arr) {
            interfacesSet.add(relocateBinary(s));
        }
        return interfacesSet.toArray(String[]::new);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, relocateSignature(signature, false), relocateBinary(superName), renameArray(interfaces));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
        return new RelocatingFieldVisitor(super.visitField(access, name, relocateBinary(desc), relocateSignature(sig, true), value));
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, relocateBinary(desc), relocateSignature(sig, false), renameArray(exceptions));
        return new RelocatingMethodVisitor(mv);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        String afterProcess = relocateBinary(descriptor);
        if (classHandleProcessed.add(afterProcess)) {
            return super.visitAnnotation(afterProcess, visible);
        }
        return null;
    }

    private class RelocatingFieldVisitor extends FieldVisitor {
        private final HashSet<String> fieldAnnotationHandleProcessed = new HashSet<String>();
        private final HashSet<String> fieldTypeAnnotationHandleProcessed = new HashSet<String>();

        public RelocatingFieldVisitor(FieldVisitor fv) {
            super(Opcodes.ASM9, fv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String afterProcess = relocateBinary(descriptor);
            if (fieldAnnotationHandleProcessed.add(afterProcess)) {
                AnnotationVisitor av = super.visitAnnotation(afterProcess, visible);
                return new RelocatingAnnotationVisitor(api, av);
            }
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            String afterProcess = relocateBinary(descriptor);
            if (fieldTypeAnnotationHandleProcessed.add(afterProcess)) {
                AnnotationVisitor av = super.visitTypeAnnotation(typeRef, typePath, afterProcess, visible);
                return new RelocatingAnnotationVisitor(api, av);
            }
            return null;
        }

    }

    private class RelocatingAnnotationVisitor extends AnnotationVisitor {
        public RelocatingAnnotationVisitor(int api, AnnotationVisitor av) {
            super(api, av);
        }

        @Override
        public void visit(String name, Object value) {
            super.visit(name, value);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av = super.visitArray(name);
            return av == null ? null : new RelocatingAnnotationVisitor(api, av);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            // 这里会命中 repeatable 容器的 value 数组元素：visitAnnotation(null, elementDesc)
            String newDesc = relocateBinary(descriptor);
            AnnotationVisitor av = super.visitAnnotation(name, newDesc);
            return av == null ? null : new RelocatingAnnotationVisitor(api, av);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            super.visitEnum(name, relocateBinary(descriptor), value);
        }
    }

    private class RelocatingMethodVisitor extends MethodVisitor {
        private final HashSet<String> methodHandleProcessed = new HashSet<String>();
        private final HashMap<Integer, HashSet<String>> parameterAnnotationHandleProcessed = new HashMap<>();

        public RelocatingMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String afterProcess = relocateBinary(descriptor);
            if (methodHandleProcessed.add(afterProcess)) {
                return super.visitAnnotation(afterProcess, visible);
            }
            return null;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, relocateBinary(type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            super.visitFieldInsn(opcode, relocateBinary(owner), name, relocateBinary(desc));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, relocateBinary(owner), name, relocateBinary(desc), itf);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            HashSet<String> currentParameterHandleProcessed = parameterAnnotationHandleProcessed.computeIfAbsent(parameter, k -> new HashSet<>());
            String afterProcess = relocateBinary(descriptor);
            if (currentParameterHandleProcessed.add(afterProcess)) {
                return super.visitParameterAnnotation(parameter, afterProcess, visible);
            }
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            Handle relocatedBsm = relocateHandle(bsm);
            Object[] relocatedArgs = new Object[bsmArgs.length];
            for (int i = 0; i < bsmArgs.length; i++) {
                Object arg = bsmArgs[i];
                if (arg instanceof Type) {
                    relocatedArgs[i] = Type.getType(relocateBinary(((Type) arg).getDescriptor()));
                } else if (arg instanceof Handle) {
                    relocatedArgs[i] = relocateHandle((Handle) arg);
                } else {
                    relocatedArgs[i] = arg;
                }
            }
            super.visitInvokeDynamicInsn(name, relocateBinary(desc), relocatedBsm, relocatedArgs);
        }

        @Override
        public void visitLdcInsn(Object v) {
            if (v instanceof Type) {
                v = Type.getType(relocateBinary(((Type) v).getDescriptor()));
            } else if (v instanceof String) {
                v = relocateClassName((String) v);
            }
            super.visitLdcInsn(v);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            super.visitMultiANewArrayInsn(relocateBinary(desc), dims);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler,
                                       String type) {
            super.visitTryCatchBlock(start, end, handler, relocateBinary(type));
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
            super.visitLocalVariable(name, relocateBinary(desc), relocateBinary(signature), start, end, index);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            super.visitFrame(type, nLocal, relocateFrameTypes(local), nStack, relocateFrameTypes(stack));
        }

        private Object[] relocateFrameTypes(Object[] types) {
            if (types == null)
                return null;
            Object[] relocated = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                Object type = types[i];
                if (type instanceof String) {
                    relocated[i] = relocateBinary((String) type);
                } else {
                    relocated[i] = type;
                }
            }
            return relocated;
        }

        private Handle relocateHandle(Handle handle) {
            return new Handle(handle.getTag(), relocateBinary(handle.getOwner()), handle.getName(), relocateBinary(handle.getDesc()),
                    handle.isInterface());
        }
    }
}
