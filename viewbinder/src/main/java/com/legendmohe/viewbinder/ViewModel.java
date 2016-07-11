package com.legendmohe.viewbinder;

import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import com.legendmohe.viewbinder.annotation.BindWidget;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Created by legendmohe on 16/7/11.
 */
public class ViewModel {

    SparseArray<Object> mCacheArray = new SparseArray<>();

    public ViewModel(Object target) {
        SparseArray<View> viewSparseArray = findTargetViews(target);
        findAnnotatedFields(viewSparseArray);
    }

    protected void notifyFieldChanged(int[] resIds, Object value) {
        for (int resId : resIds) {
            Object widget = widgetForResId(resId);
            if (widget != null && widget instanceof TextView) {
                if (value == null) {
                    value = "";
                }
                if (value instanceof String) {
                    ((TextView) widget).setText((String) value);
                } else if (value instanceof Boolean) {
                    ((TextView) widget).setEnabled((Boolean) value);
                }
            }
        }
    }

    protected Object widgetForResId(int resId) {
        return mCacheArray.get(resId);
    }

    ///////////////////////////////////functions///////////////////////////////////

    protected SparseArray<View> findTargetViews(Object target) {
        Class<?> clazz = target.getClass();
        SparseArray<View> sparseArray = new SparseArray<>();
        while (!shouldSkipClass(clazz)) {
            final Field[] allFields = clazz.getDeclaredFields();
            for (final Field field : allFields) {
                if (filterWidgetField(field)) {
                    try {
                        View widget = (View) field.get(target);
                        if (widget != null) {
                            sparseArray.put(widget.getId(), widget);
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return sparseArray;
    }

    protected void findAnnotatedFields(SparseArray<View> viewMap) {
        Class<?> clazz = this.getClass();
        while (!shouldSkipClass(clazz)) {
            final Field[] allFields = clazz.getDeclaredFields();
            for (final Field field : allFields) {
                if (filterField(field, BindWidget.class)) {
                    BindWidget bindWidget = field.getAnnotation(BindWidget.class);
                    if (bindWidget.value() != null) {
                        for (int resId :
                                bindWidget.value()) {
                            View widget = viewMap.get(resId);
                            if (widget != null) {
                                mCacheArray.put(resId, widget);
                            }
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static boolean filterWidgetField(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        if (Modifier.isVolatile(field.getModifiers())) {
            return false;
        }
        if (!(View.class.isAssignableFrom(field.getType()))) {
            return false;
        }
        return true;
    }

    private static boolean filterField(Field field, Class<? extends Annotation> annotation) {
        if (!field.isAnnotationPresent(annotation)) {
            return false;
        }
        if (Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        if (Modifier.isVolatile(field.getModifiers())) {
            return false;
        }
        return true;
    }

    private static boolean shouldSkipClass(final Class<?> clazz) {
        final String clsName = clazz.getName();
        return Object.class.equals(clazz)
                || clsName.startsWith("java.")
                || clsName.startsWith("javax.")
                || clsName.startsWith("android.")
                || clsName.startsWith("com.android.");
    }
}
