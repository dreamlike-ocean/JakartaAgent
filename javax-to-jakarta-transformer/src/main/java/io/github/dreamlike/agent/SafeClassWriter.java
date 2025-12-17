package io.github.dreamlike.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;

class SafeClassWriter extends ClassWriter {
    private static final String[] defaultInterfaces = new String[]{};
    private static final String OBJECT_CLASS_NAME = "java/lang/Object";
    private final ClassLoader classLoader;
    private final RelocateTransformer transformer;

    public SafeClassWriter(RelocateTransformer transformer, ClassLoader classLoader, ClassReader classReader, int flags) {
        super(classReader, flags);
        this.classLoader = classLoader;
        this.transformer = transformer;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        RelocateTransformer.ClassMeta type1Meta = parseClassMeta(type1);
        RelocateTransformer.ClassMeta type2Meta = parseClassMeta(type2);
        if (type1Meta.isInterface() && type2Meta.isInterface()) {
            return findCommonInterfaceSuper(type1Meta, type2Meta);
        }
        if (type1Meta.isInterface() || type2Meta.isInterface()) {
            return OBJECT_CLASS_NAME;
        }
        return findCommonClassSuper(type1, type2);
    }

    private String findCommonInterfaceSuper(RelocateTransformer.ClassMeta m1,
                                            RelocateTransformer.ClassMeta m2) {

        Set<String> ancestors1 = collectInterfaceHierarchy(m1.className());
        Set<String> ancestors2 = collectInterfaceHierarchy(m2.className());

        // 找交集，按层级最浅者返回
        for (String a : ancestors1) {
            if (ancestors2.contains(a)) {
                return a;
            }
        }

        // 没有共同父接口 → Object
        return OBJECT_CLASS_NAME;
    }

    private String findCommonClassSuper(String type1, String type2) {

        Set<String> ancestors = new LinkedHashSet<>();
        String c = type1;

        while (c != null) {
            ancestors.add(c);
            c = parseClassMeta(c).superName();
        }

        c = type2;
        while (c != null) {
            if (ancestors.contains(c)) {
                return c;
            }
            c = parseClassMeta(c).superName();
        }

        return OBJECT_CLASS_NAME;
    }

    private Set<String> collectInterfaceHierarchy(String type) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(type);

        while (!stack.isEmpty()) {
            String c = stack.pop();
            if (!result.add(c)) continue;
            RelocateTransformer.ClassMeta meta = parseClassMeta(c);
            for (String intf : meta.interfaces()) {
                stack.push(intf);
            }
        }

        return result;
    }

    private RelocateTransformer.ClassMeta parseClassMeta(String className) {
        RelocateTransformer.ClassMeta classMeta = transformer.classMetaMap.get(className);
        if (classMeta != null) {
            return classMeta;
        }

        try (InputStream resourceAsStream = classLoader.getResourceAsStream(className + ".class")) {
            if (resourceAsStream == null) {
                classMeta = defaultClassMeta(className);
            } else {
                byte[] classFile = resourceAsStream.readAllBytes();
                ClassReader classReader = new ClassReader(classFile);
                int access = classReader.getAccess();
                classMeta = new RelocateTransformer.ClassMeta(
                        classReader.getClassName(), classReader.getSuperName(), classReader.getInterfaces(), (access & ACC_INTERFACE) != 0
                );
            }
        } catch (IOException e) {
            classMeta = defaultClassMeta(className);
        }
        transformer.classMetaMap.put(className, classMeta);
        return classMeta;
    }

    private RelocateTransformer.ClassMeta defaultClassMeta(String className) {
        return new RelocateTransformer.ClassMeta(className, OBJECT_CLASS_NAME, defaultInterfaces, false);
    }
}
