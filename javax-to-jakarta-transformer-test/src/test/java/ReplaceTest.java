import io.github.dreamlike.transform.*;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.groups.Default;
import org.junit.Assert;
import org.junit.Test;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.NotBlank;

import jakarta.validation.ConstraintTarget;

import javax.validation.constraints.NotEmpty;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;
import java.util.Set;

public class ReplaceTest {

    @Test
    public void testClass() {
        Class<JavaBean> javaBeanClass = JavaBean.class;
        Valid[] annotationsByType = javaBeanClass.getAnnotationsByType(Valid.class);
        Assert.assertNotNull(annotationsByType);
        Assert.assertEquals(1, annotationsByType.length);
    }

    @Test
    public void testInterface() {
        Class<NotCheckValidator> notCheckValidatorClass = NotCheckValidator.class;
        Class<?> constraintValidatorClass = notCheckValidatorClass.getInterfaces()[0];
        Assert.assertEquals(ConstraintValidator.class, constraintValidatorClass);

        Type genericConstraintValidatorClass = CustomNotNullValidator.class.getGenericInterfaces()[0];
        Assert.assertTrue(genericConstraintValidatorClass instanceof ParameterizedType);
        Type[] actualTypeArguments = ((ParameterizedType) genericConstraintValidatorClass).getActualTypeArguments();
        Assert.assertEquals(2, actualTypeArguments.length);
        Assert.assertEquals(NotNull.class, actualTypeArguments[0]);
        Assert.assertEquals(String.class, actualTypeArguments[1]);
    }

    @Test
    public void testMethodParamType() throws NoSuchMethodException {
        Method isValidMethod = CustomNotNullValidator.class.getMethod("isValid", String.class, ConstraintValidatorContext.class);
        Assert.assertEquals(ConstraintValidatorContext.class, isValidMethod.getParameterTypes()[1]);
        Method genericMethod = CustomNotNullValidator.class.getMethod("genericMethod", List.class, Set.class);
        Type[] genericParameterTypes = genericMethod.getGenericParameterTypes();
        Assert.assertEquals(2, genericParameterTypes.length);
        Assert.assertTrue(genericParameterTypes[1] instanceof ParameterizedType);
        Assert.assertEquals(ConstraintValidatorContext.class, ((ParameterizedType) genericParameterTypes[1]).getActualTypeArguments()[0]);
        TypeVariable<Method>[] typeParameters = genericMethod.getTypeParameters();
        Assert.assertEquals(ConstraintValidatorContext.class, typeParameters[0].getBounds()[0]);
    }

    @Test
    public void testMethod() throws NoSuchMethodException {
        Class<JavaBean> javaBeanClass = JavaBean.class;
        Method testEnum = javaBeanClass.getMethod("assertBool", ConstraintTarget.class);
        AssertTrue annotation = testEnum.getAnnotation(AssertTrue.class);
        Assert.assertNotNull(annotation);
        Assert.assertEquals("jakarta.servlet", JavaBean.returnJakarta());
        Assert.assertEquals(Default.class, JavaBean.returnClass());
    }

    @Test
    public void testField() throws NoSuchFieldException {
        Class<JavaBean> javaBeanClass = JavaBean.class;
        Field name = javaBeanClass.getDeclaredField("name");
        NotNull annotation = name.getAnnotation(NotNull.class);
        Assert.assertNotNull(annotation);
        Class<?>[] groups = annotation.groups();
        Assert.assertEquals(ValidationGroup.ForceDefault.class, groups[0]);
        Assert.assertEquals(Default.class ,groups[0].getInterfaces()[0]);

        Assert.assertNotNull(name.getAnnotation(NotCheck.class));
        AnnotatedType annotatedType = name.getAnnotatedType();
        Assert.assertNull(annotatedType.getAnnotation(NotCheck.class));
        Assert.assertNotNull(annotatedType.getAnnotation(NotNull.class));

        Assert.assertNotNull(name.getAnnotation(NotEmpty.List.class));
        Assert.assertNotNull(annotatedType.getAnnotation(NotEmpty.List.class));

        Field doubleNotNullField = javaBeanClass.getDeclaredField("doubleNotNull");
        AnnotatedType doubleNotNullFieldAnnotatedType = doubleNotNullField.getAnnotatedType();
        Annotation[] doubleNotNullFieldAnnotations = doubleNotNullField.getAnnotations();
        Assert.assertEquals(1, doubleNotNullFieldAnnotations.length);
        Assert.assertEquals(1, doubleNotNullFieldAnnotatedType.getAnnotations().length);
        Assert.assertEquals(NotNull.class,doubleNotNullFieldAnnotations[0].annotationType());
        Assert.assertEquals(NotNull.class,doubleNotNullFieldAnnotatedType.getAnnotations()[0].annotationType());

        NotNull doubleNotNullAnnotation = doubleNotNullField.getAnnotation(NotNull.class);
        Assert.assertTrue(doubleNotNullAnnotation.message().contains("jakarta"));
        Assert.assertTrue(doubleNotNullFieldAnnotatedType.getAnnotation(NotNull.class).message().contains("jakarta"));
    }

    @Test
    public void testRecord() throws NoSuchFieldException {
        Class<JavaBeanRecord> javaBeanRecordClass = JavaBeanRecord.class;
        Assert.assertEquals(Default.class, javaBeanRecordClass.getInterfaces()[0]);
        RecordComponent recordComponent = javaBeanRecordClass.getRecordComponents()[0];
        Field field = javaBeanRecordClass.getDeclaredField(recordComponent.getName());
        NotBlank annotation = field.getAnnotation(NotBlank.class);
        Assert.assertNotNull(annotation);
        annotation = field.getAnnotatedType().getAnnotation(NotBlank.class);
        Assert.assertNotNull(annotation);
    }
}
