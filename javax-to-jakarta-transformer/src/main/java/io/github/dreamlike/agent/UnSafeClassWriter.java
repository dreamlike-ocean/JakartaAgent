package io.github.dreamlike.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

class UnSafeClassWriter extends ClassWriter {

    public UnSafeClassWriter(ClassReader classReader, int flags) {
        super(classReader, flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
//        return "java/lang/Object";
        return super.getCommonSuperClass(type1, type2);
    }
}
