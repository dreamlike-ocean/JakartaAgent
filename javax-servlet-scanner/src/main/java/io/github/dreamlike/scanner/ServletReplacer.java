package io.github.dreamlike.scanner;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

class ServletReplacer implements ClassTransform {
    private static final String SOURCE_BINARY_NAME = "javax/servlet";
    private static final String TARGET_BINARY_NAME = "jakarta/servlet";
    private static final String SOURCE_CLASS_NAME = "javax.servlet";
    private static final String TARGET_CLASS_NAME = "jakarta.servlet";
    private final String targetPath;

    public ServletReplacer(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: ServletReplace <jar path>");
        }
        targetPath = args[0];
    }

    private byte[] replace(byte[] classBytecode) {
        return ClassFile.of().transformClass(
                ClassFile.of().parse(classBytecode),
                this
        );
    }

    @Override
    public void accept(ClassBuilder builder, ClassElement element) {
        switch (element) {
            case Superclass superclass ->
                    builder.with(Superclass.of(transformClassEntry(builder, superclass.superclassEntry())));
            case Interfaces interfaces ->
                    Interfaces.of(interfaces.interfaces().stream().map(ce -> transformClassEntry(builder, ce)).toList());
            case FieldModel fieldModel -> transformFieldModel(builder, fieldModel);
            case MethodModel methodModel -> transformMethodModel(builder, methodModel);
            default -> builder.with(element);
        }
    }

    private void transformMethodModel(ClassBuilder classBuilder, MethodModel methodModel) {
        classBuilder.withMethod(
                methodModel.methodName(),
                transformUtf8Entry(classBuilder, methodModel.methodType()),
                methodModel.flags().flagsMask(),
                builder -> {
                    for (MethodElement element : methodModel.elementList()) {
                        if (element instanceof CodeModel codeModel) {
                            transformCodeModel(builder, codeModel);
                            continue;
                        }
                        MethodElement methodElement = switch (element) {
                            case AnnotationDefaultAttribute annotationDefaultAttribute ->
                                    AnnotationDefaultAttribute.of(transformAnnotationValue(builder, annotationDefaultAttribute.defaultValue()));
                            case ExceptionsAttribute exceptionsAttribute -> ExceptionsAttribute.of(
                                    exceptionsAttribute.exceptions()
                                            .stream()
                                            .map(c -> transformClassEntry(builder, c))
                                            .toList()
                            );
                            case RuntimeVisibleAnnotationsAttribute runtimeVisibleAnnotationsAttribute ->
                                    RuntimeVisibleAnnotationsAttribute.of(runtimeVisibleAnnotationsAttribute.annotations().stream().map(a -> transformAnnotation(builder, a)).toList());
                            case RuntimeVisibleTypeAnnotationsAttribute runtimeVisibleTypeAnnotationsAttribute ->
                                    RuntimeVisibleTypeAnnotationsAttribute.of(runtimeVisibleTypeAnnotationsAttribute.annotations().stream().map(a -> transformTypeAnnotation(builder, a)).toList());
                            case RuntimeVisibleParameterAnnotationsAttribute runtimeVisibleParameterAnnotationsAttribute ->
                                    RuntimeVisibleParameterAnnotationsAttribute.of(
                                            runtimeVisibleParameterAnnotationsAttribute.parameterAnnotations()
                                                    .stream()
                                                    .map(annotations -> annotations.stream().map(a -> transformAnnotation(builder, a)).toList())
                                                    .toList()
                                    );
                            case SignatureAttribute signatureAttribute ->
                                    SignatureAttribute.of(transformUtf8Entry(builder, signatureAttribute.signature()));
                            default -> element;
                        };
                        builder.with(methodElement);
                    }
                }
        );
    }


    private void transformCodeModel(MethodBuilder methodBuilder, CodeModel codeModel) {
        methodBuilder.withCode(codeBuilder -> {
            for (CodeElement codeElement : codeModel.elementList()) {
                switch (codeElement) {
                    case Instruction instruction -> transformInstruction(codeBuilder, instruction);
                    case ExceptionCatch exceptionCatch -> codeBuilder.with(ExceptionCatch.of(
                            exceptionCatch.tryStart(),
                            exceptionCatch.tryEnd(),
                            exceptionCatch.handler(),
                            exceptionCatch.catchType().map(c -> transformClassEntry(codeBuilder, c))
                    ));
                    case RuntimeVisibleTypeAnnotationsAttribute runtimeVisibleTypeAnnotationsAttribute -> codeBuilder.with(RuntimeVisibleTypeAnnotationsAttribute.of(
                            runtimeVisibleTypeAnnotationsAttribute.annotations().stream().map(a -> transformTypeAnnotation(codeBuilder, a)).toList()
                    ));

                    default -> codeBuilder.with(codeElement);
                }
            }
        });
    }

    private void transformInstruction(CodeBuilder codeBuilder, Instruction instruction) {
        Instruction afterProcess = switch (instruction) {
            case ConstantInstruction.LoadConstantInstruction loadConstantInstruction -> {
                LoadableConstantEntry loadableConstantEntry = transformLoadableConstantEntry(codeBuilder, loadConstantInstruction.constantEntry());
                if (loadableConstantEntry == loadConstantInstruction.constantEntry()) {
                    yield instruction;
                }
                yield ConstantInstruction.ofLoad(loadConstantInstruction.opcode(), loadableConstantEntry);
            }
            case InvokeDynamicInstruction invokeDynamicInstruction -> {
                InvokeDynamicEntry invokeDynamicEntry = transformInvokeDynamicEntry(codeBuilder, invokeDynamicInstruction.invokedynamic());
                if (invokeDynamicEntry == invokeDynamicInstruction.invokedynamic()) {
                    yield instruction;
                }
                yield InvokeDynamicInstruction.of(invokeDynamicEntry);
            }
            case InvokeInstruction invokeInstruction -> {
                MemberRefEntry memberRefEntry = transformMemberRefEntry(codeBuilder, invokeInstruction.method());
                if (memberRefEntry == invokeInstruction.method()) {
                    yield instruction;
                }
                yield InvokeInstruction.of(invokeInstruction.opcode(), memberRefEntry);
            }
            case FieldInstruction fieldInstruction -> {
                FieldRefEntry fieldRefEntry = transformFieldRefEntry(codeBuilder, fieldInstruction.field());
                if (fieldRefEntry == fieldInstruction.field()) {
                    yield instruction;
                }
                yield FieldInstruction.of(fieldInstruction.opcode(), fieldRefEntry);
            }
            case NewObjectInstruction newObjectInstruction -> {
                ClassEntry classEntry = transformClassEntry(codeBuilder, newObjectInstruction.className());
                if (classEntry == newObjectInstruction.className()) {
                    yield instruction;
                }
                yield NewObjectInstruction.of(classEntry);
            }
            case NewReferenceArrayInstruction newReferenceArrayInstruction -> {
                ClassEntry classEntry = transformClassEntry(codeBuilder, newReferenceArrayInstruction.componentType());
                if (classEntry == newReferenceArrayInstruction.componentType()) {
                    yield instruction;
                }
                yield NewReferenceArrayInstruction.of(classEntry);
            }
            case TypeCheckInstruction typeCheckInstruction -> {
                ClassEntry classEntry = transformClassEntry(codeBuilder, typeCheckInstruction.type());
                if (classEntry == typeCheckInstruction.type()) {
                    yield instruction;
                }
                yield TypeCheckInstruction.of(typeCheckInstruction.opcode(), classEntry);
            }
            default -> instruction;
        };

        codeBuilder.with(afterProcess);
    }

    private InvokeDynamicEntry transformInvokeDynamicEntry(ClassFileBuilder builder, InvokeDynamicEntry invokeDynamicEntry) {
        BootstrapMethodEntry bootstrapMethodEntry = transformBootstrapMethodEntry(builder, invokeDynamicEntry.bootstrap());
        NameAndTypeEntry nameAndTypeEntry = transformNameAndTypeEntry(builder, invokeDynamicEntry.nameAndType());
        if (bootstrapMethodEntry == invokeDynamicEntry.bootstrap() && nameAndTypeEntry == invokeDynamicEntry.nameAndType()) {
            return invokeDynamicEntry;
        }
        return builder.constantPool().invokeDynamicEntry(bootstrapMethodEntry, nameAndTypeEntry);
    }

    private BootstrapMethodEntry transformBootstrapMethodEntry(ClassFileBuilder builder, BootstrapMethodEntry bootstrapMethodEntry) {
        MethodHandleEntry methodHandleEntry = transformMethodHandleEntry(builder, bootstrapMethodEntry.bootstrapMethod());
        List<LoadableConstantEntry> arguments = bootstrapMethodEntry.arguments()
                .stream()
                .map(a -> transformLoadableConstantEntry(builder, a))
                .toList();
        if (methodHandleEntry == bootstrapMethodEntry.bootstrapMethod() && arguments.equals(bootstrapMethodEntry.arguments())) {
            return bootstrapMethodEntry;
        }
        return builder.constantPool().bsmEntry(methodHandleEntry, arguments);
    }

    private MethodHandleEntry transformMethodHandleEntry(ClassFileBuilder builder, MethodHandleEntry methodHandleEntry) {
        MemberRefEntry memberRefEntry = transformMemberRefEntry(builder, methodHandleEntry.reference());
        if (memberRefEntry == methodHandleEntry.reference()) {
            return methodHandleEntry;
        }
        return builder.constantPool().methodHandleEntry(methodHandleEntry.kind(), memberRefEntry);
    }

    private MemberRefEntry transformMemberRefEntry(ClassFileBuilder builder, MemberRefEntry memberRefEntry) {
        ClassEntry owner = transformClassEntry(builder, memberRefEntry.owner());
        NameAndTypeEntry nameAndTypeEntry = transformNameAndTypeEntry(builder, memberRefEntry.nameAndType());
        ConstantPoolBuilder constantPool = builder.constantPool();
        if (memberRefEntry instanceof MethodRefEntry) {
            return constantPool.methodRefEntry(owner, nameAndTypeEntry);
        }
        if (memberRefEntry instanceof InterfaceMethodRefEntry) {
            return constantPool.interfaceMethodRefEntry(owner, nameAndTypeEntry);
        }
        if (memberRefEntry instanceof FieldRefEntry) {
            return constantPool.fieldRefEntry(owner, nameAndTypeEntry);
        }
        return memberRefEntry;
    }

    private FieldRefEntry transformFieldRefEntry(ClassFileBuilder builder, FieldRefEntry fieldRefEntry) {
        ClassEntry owner = transformClassEntry(builder, fieldRefEntry.owner());
        NameAndTypeEntry nameAndTypeEntry = transformNameAndTypeEntry(builder, fieldRefEntry.nameAndType());
        if (owner == fieldRefEntry.owner() && nameAndTypeEntry == fieldRefEntry.nameAndType()) {
            return fieldRefEntry;
        }
        return builder.constantPool().fieldRefEntry(owner, nameAndTypeEntry);
    }

    private NameAndTypeEntry transformNameAndTypeEntry(ClassFileBuilder builder, NameAndTypeEntry nameAndTypeEntry) {
        Utf8Entry type = transformUtf8Entry(builder, nameAndTypeEntry.type());
        if (type == nameAndTypeEntry.type()) {
            return nameAndTypeEntry;
        }
        return builder.constantPool().nameAndTypeEntry(nameAndTypeEntry.name(), type);
    }

    private LoadableConstantEntry transformLoadableConstantEntry(ClassFileBuilder builder, LoadableConstantEntry loadableConstantEntry) {
        return switch (loadableConstantEntry) {
            case ClassEntry classEntry -> transformClassEntry(builder, classEntry);
            case StringEntry stringEntry -> {
                String afterProcess = stringEntry.stringValue()
                        .replace(SOURCE_BINARY_NAME, TARGET_BINARY_NAME)
                        .replace(SOURCE_CLASS_NAME, TARGET_CLASS_NAME);
                if (afterProcess.equals(stringEntry.stringValue())) {
                    yield stringEntry;
                }
                yield builder.constantPool().stringEntry(builder.constantPool().utf8Entry(afterProcess));
            }
            case MethodTypeEntry methodTypeEntry -> transformMethodTypeEntry(builder, methodTypeEntry);
            case MethodHandleEntry methodHandleEntry -> transformMethodHandleEntry(builder, methodHandleEntry);
            case ConstantDynamicEntry constantDynamicEntry -> transformConstantDynamicEntry(builder, constantDynamicEntry);
            default -> loadableConstantEntry;
        };
    }

    private MethodTypeEntry transformMethodTypeEntry(ClassFileBuilder builder, MethodTypeEntry methodTypeEntry) {
        MethodTypeDesc methodTypeDesc = methodTypeEntry.asSymbol();
        String descriptorString = methodTypeDesc.descriptorString();
        if (!descriptorString.contains(SOURCE_BINARY_NAME)) {
            return methodTypeEntry;
        }
        MethodTypeDesc afterProcess = MethodTypeDesc.ofDescriptor(descriptorString.replace(SOURCE_BINARY_NAME, TARGET_BINARY_NAME));
        return builder.constantPool().methodTypeEntry(afterProcess);
    }

    private ConstantDynamicEntry transformConstantDynamicEntry(ClassFileBuilder builder, ConstantDynamicEntry constantDynamicEntry) {
        BootstrapMethodEntry bootstrapMethodEntry = transformBootstrapMethodEntry(builder, constantDynamicEntry.bootstrap());
        NameAndTypeEntry nameAndTypeEntry = transformNameAndTypeEntry(builder, constantDynamicEntry.nameAndType());
        if (bootstrapMethodEntry == constantDynamicEntry.bootstrap() && nameAndTypeEntry == constantDynamicEntry.nameAndType()) {
            return constantDynamicEntry;
        }
        return builder.constantPool().constantDynamicEntry(bootstrapMethodEntry, nameAndTypeEntry);
    }

    private void transformFieldModel(ClassBuilder classBuilder, FieldModel fieldModel) {
        classBuilder.withField(
                fieldModel.fieldName(),
                transformUtf8Entry(classBuilder, fieldModel.fieldType()),
                builder -> {
                    builder.withFlags(fieldModel.flags().flagsMask());
                    for (FieldElement element : fieldModel.elementList()) {
                        FieldElement fieldElement = switch (element) {
                            case ConstantValueAttribute constantValueAttribute when constantValueAttribute.constant() instanceof StringEntry string ->
                                    ConstantValueAttribute.of(builder.constantPool().stringEntry(transformUtf8Entry(classBuilder, string.utf8())));
                            case RuntimeVisibleAnnotationsAttribute runtimeVisibleAnnotationsAttribute ->
                                    RuntimeVisibleAnnotationsAttribute.of(runtimeVisibleAnnotationsAttribute.annotations().stream().map(a -> transformAnnotation(builder, a)).toList());
                            case RuntimeVisibleTypeAnnotationsAttribute runtimeVisibleTypeAnnotationsAttribute ->
                                    RuntimeVisibleTypeAnnotationsAttribute.of(runtimeVisibleTypeAnnotationsAttribute.annotations().stream().map(a -> transformTypeAnnotation(builder, a)).toList());
                            case SignatureAttribute signatureAttribute ->
                                    SignatureAttribute.of(transformUtf8Entry(builder, signatureAttribute.signature()));
                            default -> element;
                        };
                        builder.with(fieldElement);
                    }
                }
        );
    }


    private Annotation transformAnnotation(ClassFileBuilder builder, Annotation annotation) {
        if (!annotation.className().stringValue().contains(SOURCE_BINARY_NAME)) {
            return annotation;
        }
        Utf8Entry afterProcess = transformUtf8Entry(builder, annotation.className());
        List<AnnotationElement> annotationElements = annotation.elements()
                .stream()
                .map(ae -> transformAnnotationElement(builder, ae))
                .toList();

        return Annotation.of(
                afterProcess,
                annotationElements
        );
    }

    private AnnotationElement transformAnnotationElement(ClassFileBuilder builder, AnnotationElement annotationElement) {
        AnnotationValue annotationValue = transformAnnotationValue(builder, annotationElement.value());
        return AnnotationElement.of(
                annotationElement.name(),
                annotationValue
        );
    }

    private TypeAnnotation transformTypeAnnotation(ClassFileBuilder builder, TypeAnnotation typeAnnotation) {
        Annotation afterProcess = transformAnnotation(builder, typeAnnotation.annotation());
        return TypeAnnotation.of(
                typeAnnotation.targetInfo(),
                typeAnnotation.targetPath(),
                afterProcess
        );
    }

    private AnnotationValue transformAnnotationValue(ClassFileBuilder builder, AnnotationValue annotationValue) {
        return switch (annotationValue) {
            case AnnotationValue.OfAnnotation ofAnnotation ->
                    AnnotationValue.ofAnnotation(transformAnnotation(builder, ofAnnotation.annotation()));
            case AnnotationValue.OfArray ofArray ->
                    AnnotationValue.ofArray(ofArray.values().stream().map(av -> transformAnnotationValue(builder, av)).toList());
            case AnnotationValue.OfString ofString ->
                    AnnotationValue.ofString(transformUtf8Entry(builder, ofString.constant()));
            case AnnotationValue.OfClass ofClass ->
                    AnnotationValue.ofClass(builder.constantPool().utf8Entry(ofClass.className().stringValue().replace(SOURCE_BINARY_NAME, TARGET_BINARY_NAME)));
            case AnnotationValue.OfEnum ofEnum -> AnnotationValue.ofEnum(
                    builder.constantPool().utf8Entry(ofEnum.className().stringValue().replace(SOURCE_BINARY_NAME, TARGET_BINARY_NAME)),
                    ofEnum.constantName()
            );
            default -> annotationValue;
        };
    }


    private static ClassEntry transformClassEntry(ClassFileBuilder builder, ClassEntry classEntry) {
        if (!classEntry.asInternalName().contains(SOURCE_BINARY_NAME)) {
            return classEntry;
        }
        String afterProcess = classEntry.asInternalName().replaceAll(SOURCE_BINARY_NAME, TARGET_BINARY_NAME);
        ConstantPoolBuilder constantPool = builder.constantPool();
        return constantPool
                .classEntry(constantPool.utf8Entry(afterProcess));
    }

    private static Utf8Entry transformUtf8Entry(ClassFileBuilder builder, Utf8Entry utf8Entry) {
        if (!utf8Entry.stringValue().contains(SOURCE_BINARY_NAME) && !utf8Entry.stringValue().contains(SOURCE_CLASS_NAME)) {
            return utf8Entry;
        }
        String afterProcess = utf8Entry.stringValue()
                .replace(SOURCE_BINARY_NAME, TARGET_BINARY_NAME)
                .replace(SOURCE_CLASS_NAME, TARGET_CLASS_NAME);
        return builder.constantPool().utf8Entry(afterProcess);
    }
}
