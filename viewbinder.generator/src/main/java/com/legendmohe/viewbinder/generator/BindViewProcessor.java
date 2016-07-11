package com.legendmohe.viewbinder.generator;

import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.legendmohe.viewbinder.annotation.BindWidget;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Created by legendmohe on 16/7/11.
 */
@AutoService(Processor.class)
public final class BindViewProcessor extends AbstractProcessor {

    private static final Map<String, Class> sTypeKindClassMap = new HashMap<>();

    static {
        sTypeKindClassMap.put("int", Integer.TYPE);
        sTypeKindClassMap.put("long", Long.TYPE);
        sTypeKindClassMap.put("double", Double.TYPE);
        sTypeKindClassMap.put("float", Float.TYPE);
        sTypeKindClassMap.put("bool", Boolean.TYPE);
        sTypeKindClassMap.put("boolean", Boolean.TYPE);
        sTypeKindClassMap.put("char", Character.TYPE);
        sTypeKindClassMap.put("byte", Byte.TYPE);
        sTypeKindClassMap.put("void", Void.TYPE);
        sTypeKindClassMap.put("short", Short.TYPE);
    }

    private Elements mElementUtils;
    private Types mTypeUtils;
    private Filer mFiler;

//    private static final ClassName VIEW_BINDER = ClassName.get("com.legendmohe.viewbinder", "ViewModel");

    private static final String BINDING_CLASS_SUFFIX = "$$ViewModelProxy";//生成类的后缀 以后会用反射去取

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);

        mElementUtils = env.getElementUtils();
        mTypeUtils = env.getTypeUtils();
        mFiler = env.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(BindWidget.class.getCanonicalName());
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, List<FieldViewBinding>> targetClassMap = new LinkedHashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(BindWidget.class)) {
            if (!SuperficialValidation.validateElement(element))
                continue;
            // Start by verifying common generated code restrictions.
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            boolean hasError = isInaccessibleViaGeneratedCode(BindWidget.class, "fields", element)
                    || isBindingInWrongPackage(BindWidget.class, element);

            if (hasError) {
                continue;
            }

            // Assemble information on the field.

            List<FieldViewBinding> fieldViewBindingList = targetClassMap.get(enclosingElement);

            if (fieldViewBindingList == null) {
                fieldViewBindingList = new ArrayList<>();
                targetClassMap.put(enclosingElement, fieldViewBindingList);
            }

            String packageName = getPackageName(enclosingElement);
            TypeName targetType = TypeName.get(enclosingElement.asType());
            int[] id = element.getAnnotation(BindWidget.class).value();
            String fieldName = element.getSimpleName().toString();
            TypeMirror fieldType = element.asType();

            FieldViewBinding fieldViewBinding = new FieldViewBinding(fieldName, fieldType, id);
            fieldViewBindingList.add(fieldViewBinding);
        }

        for (Map.Entry<TypeElement, List<FieldViewBinding>> item : targetClassMap.entrySet()) {
            List<FieldViewBinding> list = item.getValue();
            if (list == null || list.size() == 0) {
                continue;
            }

            TypeElement enclosingElement = item.getKey();
            String packageName = getPackageName(enclosingElement);
            ClassName typeClassName = ClassName.bestGuess(getClassName(enclosingElement, packageName));
            TypeSpec.Builder result = TypeSpec.classBuilder(getClassName(enclosingElement, packageName) + BINDING_CLASS_SUFFIX)
                    .addModifiers(Modifier.PUBLIC)
                    .superclass(typeClassName);
            createProxyMethod(list, typeClassName, result);
            try {
                JavaFile.builder(packageName, result.build())
                        .addFileComment(" This codes are generated automatically. Do not modify!")
                        .build().writeTo(mFiler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    private void createProxyMethod(List<FieldViewBinding> list, ClassName typeClassName, TypeSpec.Builder builder) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class, "target")
                .addStatement("super(target)");
        builder.addMethod(constructor.build());

        for (int i = 0; i < list.size(); i++) {
            FieldViewBinding fieldViewBinding = list.get(i);
            String setterName = getSetterNameFromFieldName(fieldViewBinding.getName());

            Class paramClass;
            if (fieldViewBinding.getType().getKind().isPrimitive()) {
                paramClass = sTypeKindClassMap.get(fieldViewBinding.getType().toString());
            } else {
                try {
                    paramClass = Class.forName(fieldViewBinding.getType().toString());
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    continue;
                }
            }

            MethodSpec.Builder result = MethodSpec.methodBuilder(setterName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.VOID)
                    .addAnnotation(Override.class)
                    .addParameter(paramClass, "value");

            StringBuilder stringBuilder = new StringBuilder();
            for (int resId :
                    fieldViewBinding.getResId()) {
                stringBuilder.append(String.valueOf(resId));
                stringBuilder.append(",");
            }
            if (stringBuilder.length() > 1) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }

            result.addStatement("super." + setterName + "(value)");
            result.addStatement("notifyFieldChanged(new int[]{" + stringBuilder.toString() + "}, value)");

            builder.addMethod(result.build());
        }
    }

    private String getSetterNameFromFieldName(String name) {
        String setterName = name.substring(1);
        if (name.startsWith("m") && Character.isUpperCase(name.charAt(1))) {
            return "set" + setterName;
        } else {
            return "set" + Character.toUpperCase(name.charAt(0)) + setterName;
        }
    }

    private boolean isInaccessibleViaGeneratedCode(Class<? extends Annotation> annotationClass,
                                                   String targetThing, Element element) {
        boolean hasError = false;
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        // Verify containing type.
        if (enclosingElement.getKind() != ElementKind.CLASS) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s may only be contained in classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName()));
            hasError = true;
        }

        // Verify containing class visibility is not private.
        if (enclosingElement.getModifiers().contains(Modifier.PRIVATE)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s may not be contained in private classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName()));
            hasError = true;
        }

        return hasError;
    }

    private boolean isBindingInWrongPackage(Class<? extends Annotation> annotationClass,
                                            Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith("android.")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName));
            return true;
        }
        if (qualifiedName.startsWith("java.")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName));
            return true;
        }

        return false;
    }

    private boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
        if (otherType.equals(typeMirror.toString())) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(',');
                }
                typeString.append('?');
            }
            typeString.append('>');
            if (typeString.toString().equals(otherType)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, otherType)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInterface(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType
                && ((DeclaredType) typeMirror).asElement().getKind() == ElementKind.INTERFACE;
    }

    private String getPackageName(TypeElement type) {
        return mElementUtils.getPackageOf(type).getQualifiedName().toString();
    }

    private static String getClassName(TypeElement type, String packageName) {
        int packageLen = packageName.length() + 1;
        return type.getQualifiedName().toString().substring(packageLen).replace('.', '$');
    }
}