package com.bringholm.reflectutil.v1_1;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.ClassUtils;
import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A utility class for performing various reflection operations.
 * <p>
 * Partly inspired by I Al Istannen's ReflectionUtil:
 * https://github.com/PerceiveDev/PerceiveCore/blob/master/Reflection/src/main/java/com/perceivedev/perceivecore/reflection/ReflectionUtil.java
 *
 * All the return types for methods in this class are wrapped inside ReflectionResponse class. This is to handle any reflective exceptions
 * that may occur. If an exception occurs, it will be caught and put inside the ReflectionResponse to allow the caller to handle it properly
 * using the ReflectionResponse#hasResult(), ReflectionResponse#getValue() and ReflectionResponse#getException() methods. There is also
 * a getOrThrow() method which will return the value if there is one, otherwise rethrow the caught exception in a ReflectUtil.ReflectionException
 * exception.
 *
 * @author AlvinB
 */

@SuppressWarnings({"SameParameterValue", "WeakerAccess", "unused"})
public class ReflectUtil {
    /**
     * The net.minecraft.server.{version} package for the current version
     */
    public static final String NMS_PACKAGE = "net.minecraft.server" + Bukkit.getServer().getClass().getPackage().getName().substring(Bukkit.getServer().getClass().getPackage().getName().lastIndexOf("."));
    /**
     * The org.bukkit.craftbukkit.{version} package for the current version
     */
    public static final String CB_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();
    /**
     * The major version for the current version.
     * If the version was 1.9.4, this would be 1.
     */
    public static final int MAJOR_VERSION;
    /**
     * The minor version for the current version.
     * If the version was 1.9.4, this would be 9.
     */
    public static final int MINOR_VERSION;
    /**
     * The build for the current version.
     * If the version was 1.9.4, this would be 4.
     */
    public static final int BUILD;

    // The field that needs to be hacked to allow for modification of static final fields
    private static final Field MODIFIERS_FIELD = getDeclaredField(Field.class, "modifiers", true).getOrThrow();

    static {
        String replacedVersionString = Bukkit.getVersion().replaceAll("[()]", "");
        String[] split = replacedVersionString.substring(replacedVersionString.indexOf("MC: ") + 4).split("\\.");
        MAJOR_VERSION = Integer.parseInt(split[0]);
        MINOR_VERSION = Integer.parseInt(split[1]);
        if (split.length > 2) {
            BUILD = Integer.parseInt(split[2]);
        } else {
            BUILD = 0;
        }
    }

    /**
     * Checks whether the current version is greater than the specified version.
     *
     * @param majorVersion the major version to check against
     * @param minorVersion the minor version to check against
     * @param build the build to check against
     * @return true, only if the current version is greater than the one specified
     */
    public static boolean isVersionHigherThan(int majorVersion, int minorVersion, int build) {
        return majorVersion < MAJOR_VERSION || (majorVersion == MAJOR_VERSION && minorVersion < MINOR_VERSION) ||
                (majorVersion == MAJOR_VERSION && minorVersion == MINOR_VERSION && build < BUILD);
    }

    /**
     * Checks whether the current version is greater than the specified version.
     *
     * @param majorVersion the major version to check against
     * @param minorVersion the minor version to check against
     * @return true, only if the current version is greater than the one specified
     */
    public static boolean isVersionHigherThan(int majorVersion, int minorVersion) {
        return isVersionHigherThan(majorVersion, minorVersion, 0);
    }

    /**
     * Gets a class in the net.minecraft.server.{version} package.
     *
     * @param clazz the simple name of the class to get
     * @return the class instance
     */
    public static ReflectionResponse<Class<?>> getNMSClass(String clazz) {
        Validate.notNull(clazz, "clazz cannot be null");
        return getClass(NMS_PACKAGE + "." + clazz);
    }

    /**
     * Gets a class in the org.bukkit.craftbukkit.{version} package
     *
     * @param clazz the simple name of the class, prefixed by the
     *              subpackage to org.bukkit.craftbukkit.{version},
     *              if the class has one
     * @return the class instance
     */
    public static ReflectionResponse<Class<?>> getCBClass(String clazz) {
        Validate.notNull(clazz, "clazz cannot be null");
        return getClass(CB_PACKAGE + "." + clazz);
    }

    /**
     * Gets the specified class
     *
     * @param clazz the fully qualified name of the class
     * @return the class instance
     */
    public static ReflectionResponse<Class<?>> getClass(String clazz) {
        Validate.notNull(clazz, "clazz cannot be null");
        try {
            return new ReflectionResponse<>(Class.forName(clazz));
        } catch (ClassNotFoundException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the specified constructor
     *
     * @param clazz the class which has the constructor
     * @param params the types of the parameters the constructor takes
     * @param <T> the constructor type
     * @return the constructor
     */
    public static <T> ReflectionResponse<Constructor<T>> getConstructor(Class<T> clazz, Class<?>... params) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(clazz.getConstructor(params));
        } catch (NoSuchMethodException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the specified field.
     *
     * @param clazz the class which has the field
     * @param fieldName the name of the field
     * @return the field
     */
    public static ReflectionResponse<Field> getField(Class<?> clazz, String fieldName) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(fieldName, "fieldName cannot be null");
        try {
            return new ReflectionResponse<>(clazz.getField(fieldName));
        } catch (NoSuchFieldException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the specified declared field.
     *
     * @param clazz the class which has the field
     * @param fieldName the name of the field
     * @return the field
     */
    public static ReflectionResponse<Field> getDeclaredField(Class<?> clazz, String fieldName) {
        return getDeclaredField(clazz, fieldName, false);
    }

    /**
     * Gets the specified declared field.
     *
     * @param clazz the class which has the field
     * @param fieldName the name of the field
     * @param setAccessible whether to forcefully make this field accessible
     * @return the field
     */
    public static ReflectionResponse<Field> getDeclaredField(Class<?> clazz, String fieldName, boolean setAccessible) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(fieldName, "fieldName cannot be null");
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(setAccessible);
            return new ReflectionResponse<>(field);
        } catch (NoSuchFieldException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the specified field by its type.
     *
     * @param clazz the class which has this field
     * @param type the type of the field
     * @param index the index of the field in the class
     *              relative to all other fields with the
     *              same return type
     * @return the field
     */
    public static ReflectionResponse<Field> getFieldByType(Class<?> clazz, Class<?> type, int index) {
        return getFieldByPredicate(clazz, new FieldPredicate().withType(type), index);
    }

    /**
     * Gets a static final field and uses a hack to make it modifiable.
     *
     * @param clazz the class which has the field
     * @param fieldName the name of the field
     * @return the field
     */
    public static ReflectionResponse<Field> getModifiableFinalStaticField(Class<?> clazz, String fieldName) {
        ReflectionResponse<Field> response = getField(clazz, fieldName);
        if (!response.hasResult()) {
            return response;
        }
        Field field = response.getValue();
        ReflectionResponse<Void> voidResponse = makeFinalStaticFieldModifiable(field);
        if (!voidResponse.hasResult()) {
            return new ReflectionResponse<>(voidResponse.getException());
        }
        return new ReflectionResponse<>(field);
    }

    /**
     * Gets a declared static final field and uses a hack to make it modifiable.
     *
     * @param clazz the class which has the field
     * @param fieldName the name of the field
     * @param setAccessible whether or not to forcefully make the field accessible
     * @return the field
     */
    public static ReflectionResponse<Field> getModifiableDeclaredFinalStaticField(Class<?> clazz, String fieldName, boolean setAccessible) {
        ReflectionResponse<Field> response = getDeclaredField(clazz, fieldName, setAccessible);
        if (!response.hasResult()) {
            return response;
        }
        Field field = response.getValue();
        ReflectionResponse<Void> voidResponse = makeFinalStaticFieldModifiable(field);
        if (!voidResponse.hasResult()) {
            return new ReflectionResponse<>(voidResponse.getException());
        }
        return new ReflectionResponse<>(field);
    }

    /**
     * Makes a static final field modifiable using a hack
     *
     * @param field the field to make modifiable
     * @return void
     */
    public static ReflectionResponse<Void> makeFinalStaticFieldModifiable(Field field) {
        Validate.notNull(field, "field cannot be null");
        Validate.isTrue(Modifier.isStatic(field.getModifiers()), "field is not static");
        Validate.isTrue(Modifier.isFinal(field.getModifiers()), "field is not final");
        return setFieldValue(field, MODIFIERS_FIELD, field.getModifiers() & ~Modifier.FINAL);
    }

    /**
     * Gets a declared field by its type.
     *
     * @param clazz the class which has the field
     * @param type the type of the field
     * @param index the index of the field in the class
     *              relative to all the other fields in
     *              the class with the same type
     * @return the field
     */
    public static ReflectionResponse<Field> getDeclaredFieldByType(Class<?> clazz, Class<?> type, int index) {
        return getDeclaredFieldByType(clazz, type, index, false);
    }

    /**
     * Gets a declared field by its type.
     *
     * @param clazz the class which has the field
     * @param type the type of the field
     * @param index the index of the field in the class
     *              relative to all the other fields in
     *              the class with the same type
     * @param setAccessible whether or not to forcefully make the field accessible
     * @return the field
     */
    public static ReflectionResponse<Field> getDeclaredFieldByType(Class<?> clazz, Class<?> type, int index, boolean setAccessible) {
        return getDeclaredFieldByPredicate(clazz, new FieldPredicate().withType(type), setAccessible, index);
    }

    /**
     * Gets a field by the specified predicate.
     *
     * You can either write your own Predicate, or use the
     * convenient FieldPredicate class which contains
     * various methods for finding fields.
     *
     * @see FieldPredicate
     *
     * @param clazz the class which contains the field
     * @param predicate the predicate which matches the field
     * @param index the index of the field relative to
     *              all other fields which match the same
     *              predicate in the class
     * @return the field
     */
    public static ReflectionResponse<Field> getFieldByPredicate(Class<?> clazz, Predicate<Field> predicate, int index) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Field field : clazz.getFields()) {
            if (predicate == null || predicate.test(field)) {
                if (curIndex == index) {
                    return new ReflectionResponse<>(field);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchFieldException("No field matching " + (predicate instanceof FieldPredicate ? predicate : "specified predicate") + " in " + clazz));
    }

    /**
     * Gets a declared field by the specified predicate.
     *
     * You can either write your own Predicate, or use the
     * convenient FieldPredicate class which contains
     * various methods for finding fields.
     *
     * @see FieldPredicate
     *
     * @param clazz the class which contains the field
     * @param predicate the predicate which matches the field
     * @param index the index of the field relative to
     *              all other fields which match the same
     *              predicate in the class
     * @return the field
     */
    public static ReflectionResponse<Field> getDeclaredFieldByPredicate(Class<?> clazz, Predicate<Field> predicate, int index) {
        return getDeclaredFieldByPredicate(clazz, predicate, false, index);
    }

    /**
     * Gets a declared field by the specified predicate.
     *
     * You can either write your own Predicate, or use the
     * convenient FieldPredicate class which contains
     * various methods for finding fields.
     *
     * @see FieldPredicate
     *
     * @param clazz the class which contains the field
     * @param predicate the predicate which matches the field
     * @param setAccessible whether or not to forcefully make the field accessible
     * @param index the index of the field relative to
     *              all other fields which match the same
     *              predicate in the class
     * @return the field
     */
    public static ReflectionResponse<Field> getDeclaredFieldByPredicate(Class<?> clazz, Predicate<Field> predicate, boolean setAccessible, int index) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Field field : clazz.getDeclaredFields()) {
            //noinspection Duplicates
            if (predicate == null || predicate.test(field)) {
                if (curIndex == index) {
                    field.setAccessible(setAccessible);
                    return new ReflectionResponse<>(field);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchFieldException("No declared field matching " + (predicate instanceof FieldPredicate ? predicate : "specified predicate") + " in " + clazz));
    }

    /**
     * Gets the specified method.
     *
     * @param clazz the class which has the method
     * @param methodName the name of the method
     * @param params the parameters of the method
     * @return the method
     */
    public static ReflectionResponse<Method> getMethod(Class<?> clazz, String methodName, Class<?>... params) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(methodName, "methodName cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(clazz.getMethod(methodName, params));
        } catch (NoSuchMethodException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the specified method by its return type.
     *
     * @param clazz the class which has the method
     * @param type the return type
     * @param index the index of the method in the class
     *              relative to all the other methods in
     *              the class with the same return type.
     * @return the method
     */
    public static ReflectionResponse<Method> getMethodByType(Class<?> clazz, Class<?> type, int index) {
        return getMethodByPredicate(clazz, new MethodPredicate().withReturnType(type), index);
    }

    /**
     * Gets the specified method by its parameters.
     *
     * @param clazz the class which has the method
     * @param index the index of the method in the class
     *              relative to all the other methods in
     *              the class with the same parameters.
     * @param params the parameters
     * @return the method
     */
    public static ReflectionResponse<Method> getMethodByParams(Class<?> clazz, int index, Class<?>... params) {
        return getMethodByPredicate(clazz, new MethodPredicate().withParams(params), index);
    }

    /**
     * Gets the specified method by both its return type and parameters.
     *
     * @param clazz the class which has the method
     * @param type the return type
     * @param index the index of the method in the class
     *              relative to all the other methods in
     *              the class with the same return type
     *              and parameters.
     * @param params the parameters
     * @return the method
     */
    public static ReflectionResponse<Method> getMethodByTypeAndParams(Class<?> clazz, Class<?> type, int index, Class<?>... params) {
        return getMethodByPredicate(clazz, new MethodPredicate().withReturnType(type).withParams(params), index);
    }

    /**
     * Gets a method by the specified predicate.
     *
     * @param clazz the class which has the method
     * @param predicate the predicate to match the method
     * @param index the index of the method in the class
     *              relative to all the other methods in
     *              the class which match the same predicate.
     * @return the method
     */
    public static ReflectionResponse<Method> getMethodByPredicate(Class<?> clazz, Predicate<Method> predicate, int index) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Method method : clazz.getMethods()) {
            if (predicate == null || predicate.test(method)) {
                if (curIndex == index) {
                    return new ReflectionResponse<>(method);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchMethodException("No method matching " + (predicate instanceof MethodPredicate ? predicate : "specified predicate") + " in " + clazz));
    }

    /**
     * Gets the specified declared method.
     *
     * @param clazz the class
     * @param name the name of the method
     * @param params the parameters of the method
     * @return the method
     */
    public static ReflectionResponse<Method> getDeclaredMethod(Class<?> clazz, String name, Class<?>... params) {
        return getDeclaredMethod(clazz, name, false, params);
    }

    /**
     * Gets the specified declared method.
     *
     * @param clazz the class
     * @param name the name of the method
     * @param setAccessible whether to forcefully make the method accessible
     * @param params the parameters of the method
     * @return the method
     */
    public static ReflectionResponse<Method> getDeclaredMethod(Class<?> clazz, String name, boolean setAccessible, Class<?>... params) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.notNull(name, "name cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            Method method = clazz.getDeclaredMethod(name, params);
            method.setAccessible(setAccessible);
            return new ReflectionResponse<>(method);
        } catch (NoSuchMethodException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the specified declared method by its return type.
     *
     * @param clazz the class which has the method
     * @param type the return type
     * @param index the index of the method in the class
     *              relative to all the other methods in
     *              the class which have the same return type.
     * @return the method
     */
    public static ReflectionResponse<Method> getDeclaredMethodByType(Class<?> clazz, Class<?> type, int index) {
        return getDeclaredMethodByType(clazz, type, index, false);
    }

    /**
     * Gets the specified declared method by its return type.
     *
     * @param clazz the class which has the method
     * @param type the return type
     * @param index the index of the method in the class
     *              relative to all the other methods in
     *              the class which have the same return type.
     * @param setAccessible whether to forcefully make the method accessible
     * @return the method
     */
    public static ReflectionResponse<Method> getDeclaredMethodByType(Class<?> clazz, Class<?> type, int index, boolean setAccessible) {
        return getDeclaredMethodByPredicate(clazz, new MethodPredicate().withReturnType(type), 0, setAccessible);
    }

    /**
     * Gets a declared method by the specified predicate.
     *
     * @param clazz the class which has the method
     * @param predicate the predicate to match the field
     * @param index the index of the method in the class
     *              relative to all the other methods in
     *              the class which match the same predicate.
     * @param setAccessible whether to forcefully make the field accessible
     * @return the field
     */
    public static ReflectionResponse<Method> getDeclaredMethodByPredicate(Class<?> clazz, Predicate<Method> predicate, int index, boolean setAccessible) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(index >= 0, "index cannot be less than zero");
        int curIndex = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            //noinspection Duplicates
            if (predicate == null || predicate.test(method)) {
                if (curIndex == index) {
                    method.setAccessible(setAccessible);
                    return new ReflectionResponse<>(method);
                }
                curIndex++;
            }
        }
        return new ReflectionResponse<>(new NoSuchMethodException("No method matching " + (predicate instanceof MethodPredicate ? predicate : "specified predicate") + " in " + clazz));
    }

    /**
     * Gets a field value.
     *
     * @param object the object to get the value from, or null if the field is static
     * @param field the field
     * @return the value
     */
    public static ReflectionResponse<Object> getFieldValue(Object object, Field field) {
        Validate.notNull(field, "field cannot be null");
        Validate.isTrue(object != null || Modifier.isStatic(field.getModifiers()), "object cannot be null");
        try {
            return new ReflectionResponse<>(field.get(object));
        } catch (IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets an enum constant.
     *
     * @param clazz the enum class
     * @param constant the name of the constant to get
     * @return the constant
     */
    public static ReflectionResponse<Object> getEnumConstant(Class<?> clazz, String constant) {
        Validate.notNull(clazz, "clazz cannot be null");
        Validate.isTrue(clazz.isEnum(), "clazz is not an Enum");
        Validate.notNull(constant, "constant cannot be null");
        try {
            Field field = clazz.getField(constant);
            return new ReflectionResponse<>(field.get(null));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Sets a field value.
     *
     * @param object the object to set the field value to, or null if the field is static
     * @param field the field
     * @param newValue the value to set to the field
     * @return void
     */
    public static ReflectionResponse<Void> setFieldValue(Object object, Field field, Object newValue) {
        Validate.notNull(field, "field cannot be null");
        Validate.isTrue(object != null || Modifier.isStatic(field.getModifiers()), "object cannot be null");
        try {
            field.set(object, newValue);
            return new ReflectionResponse<>((Void) null);
        } catch (IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Invokes a method.
     *
     * @param object the object to invoke the method on, or null if the method is static
     * @param method the method
     * @param params the parameters to pass to the method
     * @return the result of the method
     */
    public static ReflectionResponse<Object> invokeMethod(Object object, Method method, Object... params) {
        Validate.notNull(method, "method cannot be null");
        Validate.isTrue(object != null || Modifier.isStatic(method.getModifiers()), "object cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(method.invoke(object, params));
        } catch (IllegalAccessException | InvocationTargetException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Invokes a constructor.
     *
     * @param constructor the constructor to invoke
     * @param params the params to pass to the constructor
     * @param <T> the constructor type
     * @return the newly instantiated object returned by the constructor
     */
    public static <T> ReflectionResponse<T> invokeConstructor(Constructor<T> constructor, Object... params) {
        Validate.notNull(constructor, "constructor cannot be null");
        Validate.notNull(params, "params cannot be null");
        try {
            return new ReflectionResponse<>(constructor.newInstance(params));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the printable fields of an object using reflection.
     * The method will attempt to use the toString() on objects
     * which have it overridden.
     *
     * This method recurses deeply, and doesn't stop until
     * the object has a toString() implementation not contained
     * in the toStringExceptions list.
     *
     * @param object the object
     * @param toStringExceptions exceptions on which to not use toString(), even if they have it overridden
     * @return a Map keyed by the field name, and the value is the string representation of the object the field contains
     */
    public static ReflectionResponse<Map<String, String>> getPrintableFields(Object object, Class<?>... toStringExceptions) {
        return getPrintableFields(object, true, toStringExceptions);
    }

    /**
     * Gets the printable fields of an object using reflection.
     * The method will attempt to use the toString() on objects
     * which have it overridden.
     *
     * This method recurses deeply, and doesn't stop until
     * the object has a toString() implementation which it
     * is configured to use.
     *
     * @param object the object
     * @param useToString whether or not to use toString() methods
     *                    if they are overridden from the standard
     *                    implementation contained in the Object class.
     * @param toStringExceptions exceptions on which to not use toString(), even if they have it overridden,
     *                           or the exact reverse if useToString is false.
     * @return a Map keyed by the field name, and the value is the string representation of the object the field contains
     */
    public static ReflectionResponse<Map<String, String>> getPrintableFields(Object object, boolean useToString, Class<?>... toStringExceptions) {
        Validate.notNull(object, "object cannot be null");
        return getPrintableFields(object, object.getClass(), useToString, toStringExceptions);
    }

    public static ReflectionResponse<Map<String, String>> getPrintableFields(Object object, Class<?> clazz, boolean useToString, Class<?>... toStringExceptions) {
        Validate.notNull(clazz, "clazz cannot be null");
        Map<String, String> fields = Maps.newHashMap();
        try {
            for (Field field : clazz.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    ReflectionResponse<String> response = getStringRepresentation(field.get(object), useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        return new ReflectionResponse<>(response.getException());
                    }
                    fields.put(field.getName(), response.getValue());
                }
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    if (clazz.getEnclosingClass() != null && field.getType() == clazz.getEnclosingClass()) {
                        /* Inner classes contain a reference to their outer class instance and not ignoring it would
                           cause the code to recurse infinitely and cause a StackOverflowError.
                           This field is normally named 'this$0' (or with added $'s if a field with that name already exists),
                           but Mojang's obfuscation tool obfuscates this field and renames it 'a'.

                            Later edit: Actually, the latter about Mojang's obfuscation tool is true, but misleading. Since
                            CraftBukkit releases are decompiled and recompiled, they get the correct names again.
                            */
                        if (field.getName().startsWith("this$0")) {
                            continue;
                        }
                    }
                    field.setAccessible(true);
                    ReflectionResponse<String> response = getStringRepresentation(field.get(object), useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        return new ReflectionResponse<>(response.getException());
                    }
                    fields.put(field.getName(), response.getValue());
                }
            }
        } catch (IllegalAccessException e) {
            return new ReflectionResponse<>(e);
        }
        return new ReflectionResponse<>(fields);
    }

    /**
     * Gets a string representation of an object using reflection.
     * The method will attempt to use the toString() on objects
     * which have it overridden.
     *
     * This method recurses deeply, and doesn't stop until
     * the object has a toString() implementation which it
     * is configured to use.
     *
     * @param object the object
     * @param useToString whether or not to use toString() methods
     *                    if they are overridden from the standard
     *                    implementation contained in the Object class.
     * @param toStringExceptions exceptions on which to not use toString(), even if they have it overridden,
     *                           or the exact reverse if useToString is false.
     * @return the string representation of the object
     */
    public static ReflectionResponse<String> getStringRepresentation(Object object, boolean useToString, Class<?>... toStringExceptions) {
        try {
            if (object == null) {
                return new ReflectionResponse<>("null");
            }
            // Multimaps don't extend Map apparently.
            if (object instanceof Multimap) {
                object = ((Multimap) object).asMap();
            }
            // Using toString() (or Arrays.toString) on Collections/Maps/arrays would use
            // toString() on the contained Objects, which is why we make our own implementation.
            if (object instanceof Map) {
                StringBuilder str = new StringBuilder("{");
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    ReflectionResponse<String> firstResponse = getStringRepresentation(entry.getKey(), useToString, toStringExceptions);
                    ReflectionResponse<String> secondResponse = getStringRepresentation(entry.getValue(), useToString, toStringExceptions);
                    if (!firstResponse.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return firstResponse;
                    }
                    if (!secondResponse.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return secondResponse;
                    }
                    str.append(firstResponse.getValue()).append("=").append(secondResponse.getValue()).append(",");
                }
                // Remove last comma
                str = new StringBuilder(str.substring(0, str.length() - 1) + "}");
                return new ReflectionResponse<>(str.toString());
            }
            if (object instanceof Collection) {
                StringBuilder str = new StringBuilder("[");
                for (Object listEntry : (Collection) object) {
                    ReflectionResponse<String> response = getStringRepresentation(listEntry, useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return response;
                    }
                    str.append(response.getValue()).append(",");
                }
                // Remove last comma
                str = new StringBuilder(str.substring(0, str.length() - 1) + "]");
                return new ReflectionResponse<>(str.toString());
            }
            if (object.getClass().isArray()) {
                StringBuilder str = new StringBuilder("[");
                for (int i = 0; i < Array.getLength(object); i++) {
                    ReflectionResponse<String> response = getStringRepresentation(Array.get(object, i), useToString, toStringExceptions);
                    if (!response.hasResult()) {
                        // getStringRepresentation caught an Exception, so we abort.
                        return response;
                    }
                    str.append(response.getValue()).append(",");
                }
                // Remove last comma
                str = new StringBuilder(str.substring(0, str.length() - 1) + "]");
                return new ReflectionResponse<>(str.toString());
            }
            if (useToString) {
                if (object.getClass().getMethod("toString").getDeclaringClass() != Object.class && !ArrayUtils.contains(toStringExceptions, object.getClass())) {
                    return new ReflectionResponse<>(object.toString());
                } else {
                    ReflectionResponse<Map<String, String>> response = getPrintableFields(object, true, toStringExceptions);
                    if (!response.hasResult()) {
                        // getPrintableFields caught an Exception, so we abort.
                        return new ReflectionResponse<>(response.getException());
                    }
                    return new ReflectionResponse<>(object.getClass().getSimpleName() + response.getValue());
                }
            } else {
                if (ClassUtils.isPrimitiveWrapper(object.getClass()) || object instanceof String || object instanceof Enum || ArrayUtils.contains(toStringExceptions, object.getClass())) {
                    // Even though useToString is false, we call toString on primitive wrappers, Strings, Enums and the specified exceptions.
                    return new ReflectionResponse<>(object.toString());
                } else {
                    ReflectionResponse<Map<String, String>> response = getPrintableFields(object, false, toStringExceptions);
                    if (!response.hasResult()) {
                        // getPrintableFields caught an Exception, so we abort.
                        return new ReflectionResponse<>(response.getException());
                    }
                    return new ReflectionResponse<>(object.getClass().getSimpleName() + response.getValue());
                }
            }
        } catch (NoSuchMethodException e) {
            return new ReflectionResponse<>(e);
        }
    }

    /**
     * Gets the pretty print string representation of an object.
     *
     * This method is functionally the same as #getStringRepresentation(),
     * but includes line breaks and indentation for easier readability.
     *
     * @param object the object
     * @param useToString whether or not to use toString() methods
     *                    if they are overridden from the standard
     *                    implementation contained in the Object class.
     * @param toStringExceptions exceptions on which to not use toString(), even if they have it overridden,
     *                           or the exact reverse if useToString is false.
     * @return the pretty print string representation
     */
    public static ReflectionResponse<String> getPrettyPrintStringRepresentation(Object object, boolean useToString, Class<?>... toStringExceptions) {
        ReflectionResponse<String> response = getStringRepresentation(object, useToString, toStringExceptions);
        if (!response.hasResult()) {
            return response;
        }
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = response.getValue().replace("\n", "").replaceAll("(?<=[,{\\[}\\]]) ", "").toCharArray();
        int depth = 0;
        for (char c : chars) {
            if (stringBuilder.length() > 0 && stringBuilder.charAt(stringBuilder.length() - 1) == '\n') {
                for (int i2 = 0; i2 < depth; i2++) {
                    stringBuilder.append("    ");
                }
            }
            if ((c == '}' || c == ']')) {
                stringBuilder.append('\n');
                depth--;
                for (int i2 = 0; i2 < depth; i2++) {
                    stringBuilder.append("    ");
                }
            }
            stringBuilder.append(c);
            if (c == '{' || c == '[') {
                stringBuilder.append('\n');
                depth++;
            }
            if (c == ',') {
                stringBuilder.append('\n');
            }
        }
        return new ReflectionResponse<>(stringBuilder.toString());
    }

    /**
     * This is the response from all reflection calls in this class.
     *
     * @param <T> The type of value this response contains
     */
    @SuppressWarnings("unused")
    public static class ReflectionResponse<T> {
        private final T value;
        private final Exception exception;
        private final boolean hasResult;

        private ReflectionResponse(T value, boolean hasResult, Exception exception) {
            this.value = value;
            this.hasResult = hasResult;
            this.exception = exception;
        }

        private ReflectionResponse(T value) {
            this(value, true, null);
        }

        private ReflectionResponse(Exception exception) {
            this(null, false, exception);
        }

        /**
         * Gets the value of this response.
         *
         * @return the value
         */
        public T getValue() {
            return value;
        }

        /**
         * Checks whether or not this reflection
         * response has a result or not.
         *
         * @return whether or not a result exists
         */
        public boolean hasResult() {
            return hasResult;
        }

        /**
         * Gets the exception of this response.
         *
         * @return the exception
         */
        public Exception getException() {
            return exception;
        }

        /**
         * Gets the value of this response, or throws
         * a ReflectionException with the exception
         * if no value exists.
         *
         * @return the value
         */
        public T getOrThrow() {
            if (hasResult) {
                return value;
            } else {
                throw new ReflectionException(exception);
            }
        }

        @Override
        public String toString() {
            return "ReflectionResponse{value=" + value + ",exception=" + exception + ",hasResult=" + hasResult + "}";
        }
    }

    /**
     * This is the class used by ReflectionResponse to
     * throw exceptions.
     */
    public static class ReflectionException extends RuntimeException {
        public ReflectionException(String message) {
            super(message);
        }

        public ReflectionException(Throwable cause) {
            super(cause);
        }

        public ReflectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * A Predicate for methods, with various helper methods to help
     * match the methods.
     */
    public static class MethodPredicate implements Predicate<Method> {
        private Class<?> returnType;
        private Class<?>[] params;
        private List<Integer> withModifiers;
        private List<Integer> withoutModifiers;
        private Predicate<Method> predicate;
        private String name;

        /**
         * Matches methods with the specified return type
         *
         * @param returnType the return type
         * @return this instance, for convenience
         */
        public MethodPredicate withReturnType(Class<?> returnType) {
            this.returnType = returnType;
            return this;
        }

        /**
         * Matches methods with the specified parameters.
         *
         * @param params the parameters
         * @return this instance, for convenience
         */
        public MethodPredicate withParams(Class<?>... params) {
            this.params = params;
            return this;
        }

        /**
         * Matches methods with the specified modifiers.
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public MethodPredicate withModifiers(int... modifiers) {
            this.withModifiers = Arrays.stream(modifiers).boxed().collect(Collectors.toList());
            return this;
        }

        /**
         * Matches methods with the specified modifiers.
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public MethodPredicate withModifiers(Collection<Integer> modifiers) {
            this.withModifiers = new ArrayList<>(modifiers);
            return this;
        }

        /**
         * Matches methods without the specified modifiers.
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public MethodPredicate withoutModifiers(int... modifiers) {
            this.withoutModifiers = Arrays.stream(modifiers).boxed().collect(Collectors.toList());
            return this;
        }

        /**
         * Matches methods without the specified modifiers.
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public MethodPredicate withoutModifiers(Collection<Integer> modifiers) {
            this.withoutModifiers = new ArrayList<>(modifiers);
            return this;
        }

        /**
         * Matches methods with the specified predicate.
         *
         * @param predicate the predicate
         * @return this instance, for convenience
         */
        public MethodPredicate withPredicate(Predicate<Method> predicate) {
            this.predicate = predicate;
            return this;
        }

        /**
         * Matches methods with the specified name.
         *
         * @param name the name
         * @return this instance, for convenience
         */
        public MethodPredicate withName(String name) {
            this.name = name;
            return this;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean test(Method method) {
            if (returnType != null && method.getReturnType() != returnType) {
                return false;
            }
            if (params != null && !Arrays.equals(method.getParameterTypes(), params)) {
                return false;
            }
            //noinspection Duplicates
            if (withModifiers != null) {
                int modifiers = method.getModifiers();
                for (int bitMask : withModifiers) {
                    if ((modifiers & bitMask) == 0) {
                        return false;
                    }
                }
            }
            //noinspection Duplicates
            if (withoutModifiers != null) {
                int modifiers = method.getModifiers();
                for (int bitMask : withoutModifiers) {
                    if ((modifiers & bitMask) != 0) {
                        return false;
                    }
                }
            }
            if (predicate != null && !predicate.test(method)) {
                return false;
            }
            if (name != null && !method.getName().equals(name)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            List<String> args = Lists.newArrayList();
            if (returnType != null) {
                args.add("return type " + returnType);
            }
            if (params != null) {
                args.add("params " + Arrays.toString(params));
            }
            if (withModifiers != null) {
                args.add("with modifiers (bitmasks) " + withModifiers);
            }
            if (withoutModifiers != null) {
                args.add("without modifiers (bitmasks) " + withoutModifiers);
            }
            if (predicate != null) {
                args.add("specified predicate");
            }
            if (name != null) {
                args.add("with name " + name);
            }
            return Joiner.on(", ").join(args.subList(0, args.size() - 1)) + ", and " + args.get(args.size() - 1);
        }
    }

    /**
     * A Predicate for fields, with various helper methods to help
     * match the fields.
     */
    public static class FieldPredicate implements Predicate<Field> {
        private Class<?> type;
        private List<Integer> withModifiers;
        private List<Integer> withoutModifiers;
        private Predicate<Field> predicate;
        private String name;

        /**
         * Matches fields with the specified type
         *
         * @param type the type
         * @return this instance, for convenience
         */
        public FieldPredicate withType(Class<?> type) {
            this.type = type;
            return this;
        }

        /**
         * Matches fields with the specified modifiers
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public FieldPredicate withModifiers(int... modifiers) {
            this.withModifiers = Arrays.stream(modifiers).boxed().collect(Collectors.toList());
            return this;
        }

        /**
         * Matches fields with the specified modifiers
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public FieldPredicate withModifiers(Collection<Integer> modifiers) {
            this.withModifiers = new ArrayList<>(modifiers);
            return this;
        }

        /**
         * Matches fields without the specified modifiers
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public FieldPredicate withoutModifiers(int... modifiers) {
            this.withoutModifiers = Arrays.stream(modifiers).boxed().collect(Collectors.toList());
            return this;
        }

        /**
         * Matches fields without the specified modifiers
         *
         * @see Modifier
         *
         * @param modifiers the modifiers
         * @return this instance, for convenience
         */
        public FieldPredicate withoutModifiers(Collection<Integer> modifiers) {
            this.withoutModifiers = new ArrayList<>(modifiers);
            return this;
        }

        /**
         * Matches fields with the specified predicate
         *
         * @param predicate the predicate
         * @return this instance, for convenience
         */
        public FieldPredicate withPredicate(Predicate<Field> predicate) {
            this.predicate = predicate;
            return this;
        }

        /**
         * Matches fields with the specified name.
         *
         * @param name the name
         * @return this instance, for convenience
         */
        public FieldPredicate withName(String name) {
            this.name = name;
            return this;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean test(Field field) {
            if (type != null && field.getType() != type) {
                return false;
            }
            //noinspection Duplicates
            if (withModifiers != null) {
                int modifiers = field.getModifiers();
                for (int bitMask : withModifiers) {
                    if ((modifiers & bitMask) == 0) {
                        return false;
                    }
                }
            }
            //noinspection Duplicates
            if (withoutModifiers != null) {
                int modifiers = field.getModifiers();
                for (int bitMask : withoutModifiers) {
                    if ((modifiers & bitMask) != 0) {
                        return false;
                    }
                }
            }
            if (predicate != null && !predicate.test(field)) {
                return false;
            }
            if (name != null && !field.getName().equals(name)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            List<String> args = Lists.newArrayList();
            if (type != null) {
                args.add("type " + type);
            }
            if (withModifiers != null) {
                args.add("with modifiers (bitmasks) " + withModifiers);
            }
            if (withoutModifiers != null) {
                args.add("without modifiers (bitmasks) " + withoutModifiers);
            }
            if (predicate != null) {
                args.add("specified predicate");
            }
            if (name != null) {
                args.add("with name " + name);
            }
            return Joiner.on(", ").join(args.subList(0, args.size() - 1)) + ", and " + args.get(args.size() - 1);
        }
    }
}
