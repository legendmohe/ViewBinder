package com.legendmohe.viewbinder;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by legendmohe on 16/7/11.
 */
public class ViewModelBinder {

    /*
    public static <T> T create(Object target, Class<T> viewModelClass) {
        return (T) new TestViewModelProxy(target);
    }
     */

    public static <T> T create(Object target, Class<T> viewModelClass) {
        String clsName = viewModelClass.getName();
        try {
            Class<?> viewBindingClass = Class.forName(clsName + "$$ViewModelProxy");
            return (T) viewBindingClass.getDeclaredConstructor(Object.class).newInstance(target);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
}
