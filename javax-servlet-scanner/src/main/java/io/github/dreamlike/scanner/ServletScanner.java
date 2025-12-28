package io.github.dreamlike.scanner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class ServletScanner {
    private static final String TARGET_PACKAGE = "javax/servlet";
    private static final String TARGET_NAME = "javax.servlet";
    private static final String EXTEND_SERVLET_CLASS = "Current Class extends javax.servlet class";
    private static final String EXTEND_SERVLET_INTERFACE = "Current Class implements javax.servlet interface";
    private static final String CLASS_HAVE_SERVLET_GENERIC = "Current Class implements javax.servlet generic";
    private static final String CLASS_HAVE_SERVLET_ANNOTATION = "Current Class has javax.servlet annotation";

    private static final String RECORD_HAVE_SERVLET_COMPONENT = "Current record has javax.servlet component";

    private static final String METHOD_HAVE_SERVLET_GENERIC = "Current Method has javax.servlet generic";
    private static final String METHOD_HAVE_SERVLET_ANNOTATION = "Current Method has javax.servlet annotation";
    private static final String METHOD_HAVE_SERVLET_PARAMETER = "Current Method has javax.servlet parameter";
    private static final String METHOD_HAVE_SERVLET_EXCEPTION = "Current Method has javax.servlet exception";
    private static final String METHOD_HAVE_SERVLET_BODY = "Current Method has javax.servlet code";

    private static final String FIELD_HAVE_SERVLET_GENERIC = "Current Field has javax.servlet generic";
    private static final String FIELD_HAVE_SERVLET_ANNOTATION = "Current Field has javax.servlet annotation";
    private static final String FIELD_HAVE_SERVLET_TYPE = "Current Field has javax.servlet type";

    private final String targetPath;
    private final DetailType detailType;

    private static boolean shouldRecord(String name) {
        return name != null && (name.contains(TARGET_PACKAGE) || name.contains(TARGET_NAME));
    }

    public ServletScanner(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: JavaxScanner <jar path> [detail type (Jar/Class/Detail)]");
        }
        targetPath = args[0];
        detailType = args.length > 1 ? DetailType.valueOf(args[1]) : DetailType.ALL;
    }

    void scan() throws IOException {
        ArrayDeque<JarChunk> stack = new ArrayDeque<>();
        try {
            stack.push(new JarChunk(Files.readAllBytes(new File(targetPath).toPath()), targetPath));
        } catch (IOException e) {
            System.err.println("jar not found" + targetPath);
            return;
        }
        ArrayList<Stream<ScannedClass>> scannedClasses = new ArrayList<>();
        while (!stack.isEmpty()) {
            JarChunk currentJar = stack.pop();
            try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(currentJar.classBytes()))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = entry.getName();
                    if (entryName.endsWith(".jar")) {
                        stack.push(new JarChunk(zipInputStream.readAllBytes(), entryName));
                        continue;
                    }
                    if (entryName.endsWith(".class")) {
                        byte[] bytecode = zipInputStream.readAllBytes();
                        scannedClasses.add(parseClass(currentJar.jarName(), bytecode));
                    }
                }
            }
        }
        switch (detailType) {
            case Jar -> outputJar(scannedClasses);
            case Class -> outputClass(scannedClasses);
            case ALL -> outputAll(scannedClasses);
            default -> throw new IllegalArgumentException("Unknown detailType");
        }
    }
    private static void outputClass(ArrayList<Stream<ScannedClass>> scannedClasses) {
        System.out.println("=== Detail Type: CLASS ===");
        System.out.println("JAR Name                         | Class Name");
        scannedClasses.stream()
                .flatMap(Function.identity())
                .map(sc -> sc.jarName() + " | " + sc.className())
                .distinct()
                .forEach(System.out::println);
        System.out.println("--------------------------------|------------------------------");
    }

    private static void outputAll(ArrayList<Stream<ScannedClass>> scannedClasses) {
        System.out.println("=== Detail Type: ALL ===");
        System.out.println("JAR Name | Class Name | Location | Detail");
        System.out.println("--------------------------------------------------------------");

        scannedClasses.stream()
                .flatMap(Function.identity())
                .forEach(sc -> System.out.printf(
                        "%s | %s | %s | %s%n",
                        sc.jarName(),
                        sc.className(),
                        sc.location(),
                        sc.detail()
                ));
    }
    private static void outputJar(ArrayList<Stream<ScannedClass>> scannedClasses) {
        System.out.println("=== Detail Type: JAR ===");
        System.out.println("JAR Name:");
        scannedClasses.stream()
                .flatMap(Function.identity())
                .map(ScannedClass::jarName)
                .distinct()
                .forEach(System.out::println);
        System.out.println("---------");
    }

    private static Stream<ScannedClass> parseClass(String jarName, byte[] classBytes) {
        ClassModel classElement = ClassFile.of().parse(classBytes);
        Stream<ScannedClass> classStream = parseClass(jarName, classElement);
        Stream<ScannedClass> methodStream = parseMethod(jarName, classElement);
        Stream<ScannedClass> fieldStream = parseField(jarName, classElement);
        return Stream.of(classStream, methodStream, fieldStream)
                .flatMap(Function.identity());
    }

    private static Stream<ScannedClass> parseField(String jarName, ClassModel classElement) {
        String thisClass = classElement.thisClass().asInternalName().replace("/", ".");
        return classElement.fields()
                .stream()
                .flatMap(f -> parseField0(jarName, thisClass, f));
    }

    private static Stream<ScannedClass> parseField0(String jarName, String thisClassName, FieldModel fieldModel) {
        Stream<ScannedClass> typeStream;
        if (shouldRecord(fieldModel.fieldTypeSymbol().descriptorString())) {
            typeStream = Stream.of(new ScannedClass(jarName, thisClassName, Location.Field, FIELD_HAVE_SERVLET_TYPE));
        } else {
            typeStream = Stream.empty();
        }
        Stream<ScannedClass> genericStream = fieldModel.attributes()
                .stream()
                .filter(a -> a instanceof SignatureAttribute)
                .map(a -> (SignatureAttribute) a)
                .filter(a -> shouldRecord(a.asTypeSignature().signatureString()))
                .map(a -> new ScannedClass(jarName, thisClassName, Location.Field, FIELD_HAVE_SERVLET_GENERIC));
        Stream<ScannedClass> annotation = parseAnnotation(jarName, thisClassName, Location.Field, FIELD_HAVE_SERVLET_ANNOTATION, fieldModel);
        return Stream.of(typeStream, genericStream, annotation)
                .flatMap(Function.identity());
    }

    private static Stream<ScannedClass> parseClass(String jarName, ClassModel classElement) {
        String thisClass = classElement.thisClass().asInternalName().replace("/", ".");
        //泛型
        Stream<ScannedClass> genericStream = classElement.attributes()
                .stream()
                .filter(a -> a instanceof SignatureAttribute)
                .map(a -> (SignatureAttribute) a)
                .filter(a -> shouldRecord(a.asClassSignature().signatureString()))
                .map(a -> new ScannedClass(jarName, thisClass, Location.Class, CLASS_HAVE_SERVLET_GENERIC));
        // 继承
        Stream<ScannedClass> superClassStream = classElement.superclass()
                .map(ClassEntry::asInternalName)
                .filter(ServletScanner::shouldRecord)
                .map(_ -> new ScannedClass(jarName, thisClass, Location.Class, EXTEND_SERVLET_CLASS))
                .stream();
        //接口
        Stream<ScannedClass> interfaceStream = classElement.interfaces().stream()
                .map(ClassEntry::asInternalName)
                .filter(ServletScanner::shouldRecord)
                .map(_ -> new ScannedClass(jarName, thisClass, Location.Class, EXTEND_SERVLET_INTERFACE));

        // 注解
        Stream<ScannedClass> annotationStream = parseAnnotation(jarName, thisClass, Location.Class, CLASS_HAVE_SERVLET_ANNOTATION, classElement);

        //record
        Stream<ScannedClass> recordStream = classElement.attributes()
                .stream()
                .filter(a -> a instanceof RecordAttribute)
                .flatMap(a -> ((RecordAttribute) a).components().stream())
                .filter(ServletScanner::parseRecordComponent)
                .map(r -> new ScannedClass(jarName, thisClass, Location.Class, RECORD_HAVE_SERVLET_COMPONENT));
        return Stream.of(genericStream, superClassStream, interfaceStream, annotationStream, recordStream)
                .flatMap(Function.identity());
    }

    private static boolean parseRecordComponent(RecordComponentInfo recordComponentInfo) {
        Predicate<RecordComponentInfo> rawTypePredicate = (info) -> shouldRecord(info.descriptor().stringValue());
        Predicate<RecordComponentInfo> signaturePredicate = (info) -> info.findAttribute(Attributes.signature())
                .map(SignatureAttribute::asTypeSignature)
                .map(Signature::signatureString)
                .filter(ServletScanner::shouldRecord)
                .isPresent();
        Predicate<RecordComponentInfo> annotationPredicate = (info) -> filterAnnotation(info).findAny().isPresent();
        return rawTypePredicate
                .or(signaturePredicate)
                .or(annotationPredicate)
                .test(recordComponentInfo);
    }

    private static Stream<ScannedClass> parseMethod(String jarName, ClassModel classElement) {
        String thisClass = classElement.thisClass().asInternalName().replace("/", ".");
        return classElement.methods()
                .stream()
                .flatMap(methodModel -> parseMethod0(jarName, thisClass, methodModel));
    }

    private static Stream<ScannedClass> parseMethod0(String jarName, String thisClassName, MethodModel methodModel) {
        //泛型
        Stream<ScannedClass> genericStream = methodModel.attributes()
                .stream()
                .filter(a -> a instanceof SignatureAttribute)
                .map(a -> (SignatureAttribute) a)
                .filter(a -> shouldRecord(a.asMethodSignature().signatureString()))
                .map(a -> new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_GENERIC));
        //异常
        Stream<ScannedClass> execptionDeclareStream = methodModel.attributes()
                .stream()
                .filter(a -> a instanceof ExceptionsAttribute)
                .map(a -> (ExceptionsAttribute) a)
                .flatMap(a -> a.exceptions().stream())
                .map(ClassEntry::asInternalName)
                .filter(ServletScanner::shouldRecord)
                .map(_ -> new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_EXCEPTION));
        //注解
        Stream<ScannedClass> annotationStream = parseAnnotation(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_ANNOTATION, methodModel);

        //参数
        Stream<ScannedClass> parameterListStream = methodModel.methodTypeSymbol().parameterList()
                .stream()
                .map(ClassDesc::descriptorString)
                .filter(ServletScanner::shouldRecord)
                .map(_ -> new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_PARAMETER));
        // 方法体
        Stream<ScannedClass> bodyStream = methodModel.code()
                .flatMap(codeModel -> parseMethodBody(jarName, thisClassName, codeModel))
                .stream();
        return Stream.of(
                        genericStream,
                        execptionDeclareStream,
                        annotationStream,
                        parameterListStream,
                        bodyStream
                )
                .flatMap(Function.identity());
    }

    private static Optional<ScannedClass> parseMethodBody(String jarName, String thisClassName, CodeModel codeModel) {
        // load的来源只可能是parameters或者方法调用 或者LDC
        for (CodeElement codeElement : codeModel.elementList()) {
            var res = switch (codeElement) {
                case ExceptionCatch exceptionCatch ->
                        exceptionCatch.catchType().map(ClassEntry::asInternalName).filter(name -> shouldRecord(name)).map(_ -> new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                case ConstantInstruction.LoadConstantInstruction loadInstruction when checkConstantInstruction(loadInstruction.constantEntry()) ->
                        Optional.of(new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                case InvokeDynamicInstruction invokeDynamicInstruction when checkInvokeDynamicInstruction(invokeDynamicInstruction) ->
                        Optional.of(new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                case InvokeInstruction instruction when checkInvokeInstruction(instruction) ->
                        Optional.of(new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                case FieldInstruction fieldInstruction when shouldRecord(fieldInstruction.field().typeSymbol().descriptorString()) ->
                        Optional.of(new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                case NewObjectInstruction newObjectInstruction when shouldRecord(newObjectInstruction.className().asInternalName()) ->
                        Optional.of(new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                case NewReferenceArrayInstruction newReferenceArrayInstruction when shouldRecord(newReferenceArrayInstruction.componentType().asInternalName()) ->
                        Optional.of(new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                case TypeCheckInstruction typeCheckInstruction when shouldRecord(typeCheckInstruction.type().asInternalName()) ->
                        Optional.of(new ScannedClass(jarName, thisClassName, Location.Method, METHOD_HAVE_SERVLET_BODY));
                default -> Optional.<ScannedClass>empty();
            };
            if (res.isPresent()) {
                return res;
            }
        }
        return Optional.empty();
    }

    private static boolean checkInvokeInstruction(InvokeInstruction instruction) {
        ClassEntry owner = instruction.owner();
        if (shouldRecord(owner.asInternalName())) {
            return true;
        }
        return shouldRecord(instruction.typeSymbol().descriptorString());
    }

    private static boolean checkInvokeDynamicInstruction(InvokeDynamicInstruction invokeDynamicInstruction) {
        String descriptorString = invokeDynamicInstruction.bootstrapMethod().invocationType().descriptorString();
        if (shouldRecord(descriptorString)) {
            return true;
        }

        for (ConstantDesc bootstrapArg : invokeDynamicInstruction.bootstrapArgs()) {
            if (checkConstantDesc(bootstrapArg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkConstantDesc(ConstantDesc constantDesc) {
        return switch (constantDesc) {
            case ClassDesc classDesc -> shouldRecord(classDesc.descriptorString());
            case MethodHandleDesc methodHandleDesc ->
                    shouldRecord(methodHandleDesc.invocationType().descriptorString());
            case MethodTypeDesc methodTypeDesc -> shouldRecord(methodTypeDesc.descriptorString());
            case String constant -> shouldRecord(constant);
            case DynamicConstantDesc dynamicConstantDesc -> {
                if (shouldRecord(dynamicConstantDesc.constantType().descriptorString())) {
                    yield true;
                }
                if (shouldRecord(dynamicConstantDesc.bootstrapMethod().invocationType().descriptorString())) {
                    yield true;
                }
                for (ConstantDesc bootstrapArg : dynamicConstantDesc.bootstrapArgs()) {
                    if (checkConstantDesc(bootstrapArg)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean checkConstantInstruction(LoadableConstantEntry entry) {
        return switch (entry) {
            //就是xxx.class
            case ClassEntry classEntry -> shouldRecord(classEntry.asInternalName());
            // 就是字符串
            case ConstantValueEntry constantValueEntry when constantValueEntry instanceof StringEntry stringEntry ->
                    shouldRecord(stringEntry.stringValue());
            //一般来说没这种东西哈。。。javac不会生成这个字节码 就是condy
            case ConstantDynamicEntry constantDynamicEntry -> checkConstantDynamicEntry(constantDynamicEntry);
            // 一般来说没这种东西哈。。。javac不会生成这个字节码
            case MethodHandleEntry methodHandleEntry -> checkMethodHandleEntry(methodHandleEntry);
            // 一般来说没这种东西哈。。。javac不会生成这个字节码
            case MethodTypeEntry methodTypeEntry -> checkMethodTypeEntry(methodTypeEntry);
            default -> false;
        };
    }

    private static boolean checkConstantDynamicEntry(ConstantDynamicEntry constantDynamicEntry) {
        // 只检查bsm和额外的常量参数就行了
        // mh的类型足够推断condy了
        MethodHandleEntry methodHandleEntry = constantDynamicEntry.bootstrap().bootstrapMethod();
        if (checkMethodHandleEntry(methodHandleEntry)) {
            return true;
        }
        for (LoadableConstantEntry argument : constantDynamicEntry.bootstrap().arguments()) {
            if (checkConstantInstruction(argument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkMethodHandleEntry(MethodHandleEntry methodHandleEntry) {
        DirectMethodHandleDesc methodHandleEntrySymbol = methodHandleEntry.asSymbol();
        String ownerBinaryName = methodHandleEntrySymbol.owner().descriptorString();
        if (shouldRecord(ownerBinaryName)) {
            return true;
        }
        MethodTypeDesc methodTypeDesc = methodHandleEntrySymbol.invocationType();
        return shouldRecord(methodTypeDesc.descriptorString());
    }

    private static boolean checkMethodTypeEntry(MethodTypeEntry methodTypeEntry) {
        return shouldRecord(methodTypeEntry.asSymbol().descriptorString());
    }


    private static Stream<ScannedClass> parseAnnotation(String jarName, String thisClassName, Location location, String detail, AttributedElement attributedElement) {
        return filterAnnotation(attributedElement)
                .map(a -> new ScannedClass(jarName, thisClassName, location, detail));
    }

    private static Stream<Annotation> filterAnnotation(AttributedElement attributedElement) {
        return attributedElement.attributes()
                .stream()
                .filter(a -> a instanceof RuntimeVisibleAnnotationsAttribute || a instanceof RuntimeVisibleTypeAnnotationsAttribute)
                .flatMap(a -> {
                    if (a instanceof RuntimeVisibleAnnotationsAttribute runtimeVisibleAnnotationsAttribute) {
                        return runtimeVisibleAnnotationsAttribute.annotations().stream();
                    } else {
                        RuntimeVisibleTypeAnnotationsAttribute runtimeVisibleTypeAnnotationsAttribute = (RuntimeVisibleTypeAnnotationsAttribute) a;
                        return runtimeVisibleTypeAnnotationsAttribute.annotations().stream().map(TypeAnnotation::annotation);
                    }
                })
                .filter(a -> shouldRecord(a.className().stringValue()));
    }

    private record JarChunk(byte[] classBytes, String jarName) {
    }

    private record ScannedClass(String jarName, String className, Location location, String detail) {
    }

    private enum DetailType {
        Jar,
        Class,
        ALL
    }

    private enum Location {
        Class,
        Method,
        Field
    }
}
