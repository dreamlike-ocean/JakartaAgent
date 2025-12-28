package io.github.dreamlike.scanner;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.List;
import java.util.stream.Collectors;

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
                        if (methodElement instanceof CodeModel codeModel) {
                            transformCodeModel(builder, codeModel);
                        } else {
                            builder.with(methodElement);
                        }
                    }
                }
        );
    }


    private void transformCodeModel(MethodBuilder methodBuilder, CodeModel codeModel) {
        //todo...
        for (CodeElement codeElement : codeModel.elementList()) {

        }
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
        if (!utf8Entry.stringValue().contains(SOURCE_BINARY_NAME)) {
            return utf8Entry;
        }
        String afterProcess = utf8Entry.stringValue().replaceAll(SOURCE_BINARY_NAME, TARGET_BINARY_NAME);
        return builder.constantPool().utf8Entry(afterProcess);
    }
}
