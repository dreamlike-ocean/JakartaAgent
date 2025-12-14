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

import java.util.*;

/**
 * 1. 注解转换
 * 注解转换后会只保留一个 当javax被转换为jakarta后 如果其同时存在两种注解 那么只会保留顺序第一个的
 * 对于javac来讲 同一种注解即使打了repeatable也最终只有一个保存
 * repeatable是一个特殊的语法糖会将其收集为一个注解
 * 2 attribute转换
 * 本实现只处理java源码编译的产物 不对kotlin/scala等产物进行处理 所以不处理attribute的内容
 */
class JakartaRelocatingClassVisitor extends ClassVisitor {
    private static final Map<String, String> binaryMappings = Map.of("javax/servlet", "jakarta/servlet", "javax/validation", "jakarta/validation");
    private static final List<String> binaryPrefixes = List.copyOf(binaryMappings.keySet());
    private static final Map<String, String> classMappings = Map.of("javax.servlet", "jakarta.servlet", "javax.validation", "jakarta.validation");

    private final HashSet<String> classHandleAnnotationHandleProcessed = new HashSet<String>();
    private final HashSet<String> classHandleTypeAnnotationHandleProcessed = new HashSet<String>();
    boolean needTransform;

    public JakartaRelocatingClassVisitor(ClassWriter classWriter) {
        super(Opcodes.ASM9, classWriter);
    }

    public static boolean isBinaryPrefix(String s) {
        for (String binaryPrefix : binaryPrefixes) {
            if (s.startsWith(binaryPrefix)) {
                return true;
            }
        }
        return false;
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
        //signature一定要改！必须跟interface保持一致
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
        if (classHandleAnnotationHandleProcessed.add(afterProcess)) {
            return new RelocatingAnnotationVisitor(api, super.visitAnnotation(afterProcess, visible));
        }
        return null;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        String afterProcess = relocateBinary(descriptor);
        if (classHandleTypeAnnotationHandleProcessed.add(afterProcess)) {
            return new RelocatingAnnotationVisitor(api, super.visitTypeAnnotation(typeRef, typePath, afterProcess, visible));
        }
        return null;
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        String rawType = relocateBinary(descriptor);
        String rawSignature = relocateSignature(signature, false);
        return new RelocatingRecordComponentVisitor(api, super.visitRecordComponent(name, rawType, rawSignature));
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
            if (value instanceof Type asmType) {
                super.visit(name,  Type.getType(relocateBinary(asmType.getDescriptor())));
                return;
            }

            if (value instanceof String literal) {
                super.visit(name, relocateClassName(literal));
                return;
            }
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
        private final HashSet<String> methodHandleAnnotationProcessed = new HashSet<String>();
        private final HashSet<String> methodTypeAnnotationHandleProcessed = new HashSet<String>();
        private final HashMap<Integer, HashSet<String>> parameterAnnotationHandleProcessed = new HashMap<>();

        public RelocatingMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String afterProcess = relocateBinary(descriptor);
            if (methodHandleAnnotationProcessed.add(afterProcess)) {
                return new RelocatingAnnotationVisitor(api, super.visitAnnotation(afterProcess, visible));
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
                AnnotationVisitor proxy = super.visitParameterAnnotation(parameter, afterProcess, visible);
                return new RelocatingAnnotationVisitor(api, proxy);
            }
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            String afterProcess = relocateBinary(descriptor);
            if (methodTypeAnnotationHandleProcessed.add(afterProcess)) {
                AnnotationVisitor proxy = super.visitTypeAnnotation(typeRef, typePath, afterProcess, visible);
                return new RelocatingAnnotationVisitor(api, proxy);
            }
            return null;
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
            super.visitLocalVariable(name, relocateBinary(desc), relocateSignature(signature, true), start, end, index);
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

    private class RelocatingRecordComponentVisitor extends RecordComponentVisitor {
        private final HashSet<String> recordComponentAnnotationProcessed = new HashSet<>();
        private final HashSet<String> recordComponentTypeAnnotationProcessed = new HashSet<>();
        protected RelocatingRecordComponentVisitor(int api, RecordComponentVisitor recordComponentVisitor) {
            super(api, recordComponentVisitor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String afterProcess = relocateBinary(descriptor);
            if (recordComponentAnnotationProcessed.add(afterProcess)) {
                return new RelocatingAnnotationVisitor(api, super.visitAnnotation(afterProcess, visible));
            }
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            String afterProcess = relocateBinary(descriptor);
            if (recordComponentTypeAnnotationProcessed.add(afterProcess)) {
                return new RelocatingAnnotationVisitor(api, super.visitTypeAnnotation(typeRef, typePath, afterProcess, visible));
            }
            return null;
        }
    }
}
