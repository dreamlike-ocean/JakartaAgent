package io.github.dreamlike.transform;

public class SelfReferenceClass {
    static void self(boolean ignore) {
        //这里的控制流 需要合并frame
        //合并frame校验o的类型的时候 asm默认会通过反射api forname SelfReferenceClass
        // 此时就会出现
        // Linkage loader 'app' attempted duplicate class definition for io.github.dreamlike.transform.SelfReferenceClass. (io.github.dreamlike.transform.SelfReferenceClass is in unnamed module of loader 'app')
        Object o;
        if (ignore) {
            o = new SelfReferenceClass();
        } else {
            o = new Object();
        }
    }
}
