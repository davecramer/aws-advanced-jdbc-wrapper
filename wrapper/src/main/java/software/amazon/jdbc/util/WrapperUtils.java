/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package software.amazon.jdbc.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLData;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.jdbc.ConnectionPluginManager;
import software.amazon.jdbc.JdbcCallable;
import software.amazon.jdbc.JdbcRunnable;
import software.amazon.jdbc.wrapper.ArrayWrapper;
import software.amazon.jdbc.wrapper.BlobWrapper;
import software.amazon.jdbc.wrapper.CallableStatementWrapper;
import software.amazon.jdbc.wrapper.ClobWrapper;
import software.amazon.jdbc.wrapper.ConnectionWrapper;
import software.amazon.jdbc.wrapper.DatabaseMetaDataWrapper;
import software.amazon.jdbc.wrapper.NClobWrapper;
import software.amazon.jdbc.wrapper.ParameterMetaDataWrapper;
import software.amazon.jdbc.wrapper.PreparedStatementWrapper;
import software.amazon.jdbc.wrapper.RefWrapper;
import software.amazon.jdbc.wrapper.ResultSetMetaDataWrapper;
import software.amazon.jdbc.wrapper.ResultSetWrapper;
import software.amazon.jdbc.wrapper.SQLDataWrapper;
import software.amazon.jdbc.wrapper.SQLInputWrapper;
import software.amazon.jdbc.wrapper.SQLOutputWrapper;
import software.amazon.jdbc.wrapper.SQLTypeWrapper;
import software.amazon.jdbc.wrapper.SavepointWrapper;
import software.amazon.jdbc.wrapper.StatementWrapper;
import software.amazon.jdbc.wrapper.StructWrapper;

public class WrapperUtils {

  private static final ConcurrentMap<Class<?>, Class<?>[]> getImplementedInterfacesCache =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<Class<?>, Boolean> isJdbcInterfaceCache =
      new ConcurrentHashMap<>();

  private static final Map<Class<?>, Class<?>> availableWrappers =
      new HashMap<Class<?>, Class<?>>() {
        {
          put(CallableStatement.class, CallableStatementWrapper.class);
          put(PreparedStatement.class, PreparedStatementWrapper.class);
          put(Statement.class, StatementWrapper.class);
          put(ResultSet.class, ResultSetWrapper.class);
          put(Array.class, ArrayWrapper.class);
          put(Blob.class, BlobWrapper.class);
          put(NClob.class, NClobWrapper.class);
          put(Clob.class, ClobWrapper.class);
          put(Ref.class, RefWrapper.class);
          put(Struct.class, StructWrapper.class);
          put(Savepoint.class, SavepointWrapper.class);
          put(DatabaseMetaData.class, DatabaseMetaDataWrapper.class);
          put(ParameterMetaData.class, ParameterMetaDataWrapper.class);
          put(ResultSetMetaData.class, ResultSetMetaDataWrapper.class);
          put(SQLData.class, SQLDataWrapper.class);
          put(SQLInput.class, SQLInputWrapper.class);
          put(SQLOutput.class, SQLOutputWrapper.class);
          put(SQLType.class, SQLTypeWrapper.class);
        }
      };

  private static Set<Class<?>> allWrapperClasses = new HashSet<Class<?>>() {
    {
      add(ArrayWrapper.class);
      add(BlobWrapper.class);
      add(CallableStatementWrapper.class);
      add(ClobWrapper.class);
      add(ConnectionWrapper.class);
      add(DatabaseMetaDataWrapper.class);
      add(NClobWrapper.class);
      add(ParameterMetaDataWrapper.class);
      add(PreparedStatementWrapper.class);
      add(RefWrapper.class);
      add(ResultSetMetaDataWrapper.class);
      add(ResultSetWrapper.class);
      add(SavepointWrapper.class);
      add(SQLDataWrapper.class);
      add(SQLInputWrapper.class);
      add(SQLOutputWrapper.class);
      add(SQLTypeWrapper.class);
      add(StatementWrapper.class);
      add(StructWrapper.class);
    }
  };

  public static void runWithPlugins(
      final ConnectionPluginManager pluginManager,
      final Object methodInvokeOn,
      final String methodName,
      final JdbcRunnable<RuntimeException> jdbcMethodFunc,
      Object... jdbcMethodArgs) {

    executeWithPlugins(
        Void.TYPE,
        RuntimeException.class,
        pluginManager,
        methodInvokeOn,
        methodName,
        () -> {
          jdbcMethodFunc.call();
          return null;
        },
        jdbcMethodArgs);
  }

  public static <E extends Exception> void runWithPlugins(
      final Class<E> exceptionClass,
      final ConnectionPluginManager pluginManager,
      final Object methodInvokeOn,
      final String methodName,
      final JdbcRunnable<E> jdbcMethodFunc,
      Object... jdbcMethodArgs)
      throws E {

    executeWithPlugins(
        Void.TYPE,
        exceptionClass,
        pluginManager,
        methodInvokeOn,
        methodName,
        () -> {
          jdbcMethodFunc.call();
          return null;
        },
        jdbcMethodArgs);
  }

  public static <T> T executeWithPlugins(
      final Class<T> resultClass,
      final ConnectionPluginManager pluginManager,
      final Object methodInvokeOn,
      final String methodName,
      final JdbcCallable<T, RuntimeException> jdbcMethodFunc,
      Object... jdbcMethodArgs) {

    pluginManager.lock();

    try {
      Object[] argsCopy =
          jdbcMethodArgs == null ? null : Arrays.copyOf(jdbcMethodArgs, jdbcMethodArgs.length);

      T result =
          pluginManager.execute(
              resultClass,
              RuntimeException.class,
              methodInvokeOn,
              methodName,
              jdbcMethodFunc,
              argsCopy);

      try {
        return wrapWithProxyIfNeeded(resultClass, result, pluginManager);
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      }

    } finally {
      pluginManager.unlock();
    }
  }

  public static <T, E extends Exception> T executeWithPlugins(
      final Class<T> resultClass,
      final Class<E> exceptionClass,
      final ConnectionPluginManager pluginManager,
      final Object methodInvokeOn,
      final String methodName,
      final JdbcCallable<T, E> jdbcMethodFunc,
      Object... jdbcMethodArgs)
      throws E {

    pluginManager.lock();

    try {
      Object[] argsCopy =
          jdbcMethodArgs == null ? null : Arrays.copyOf(jdbcMethodArgs, jdbcMethodArgs.length);

      T result =
          pluginManager.execute(
              resultClass, exceptionClass, methodInvokeOn, methodName, jdbcMethodFunc, argsCopy);

      try {
        return wrapWithProxyIfNeeded(resultClass, result, pluginManager);
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      }

    } finally {
      pluginManager.unlock();
    }
  }

  protected static @Nullable <T> T wrapWithProxyIfNeeded(
      final Class<T> resultClass, @Nullable T toProxy, final ConnectionPluginManager pluginManager)
      throws InstantiationException {

    if (toProxy == null) {
      return null;
    }

    // Exceptional case
    if (toProxy instanceof RowId || toProxy instanceof SQLXML) {
      return toProxy;
    }

    if (allWrapperClasses.contains(toProxy.getClass())) {
      return toProxy;
    }

    Class<?> wrapperClass = availableWrappers.get(resultClass);

    if (wrapperClass != null) {
      return createInstance(
          wrapperClass,
          resultClass,
          new Class<?>[] {resultClass, ConnectionPluginManager.class},
          toProxy,
          pluginManager);
    }

    if (isJdbcInterface(toProxy.getClass())) {
      throw new RuntimeException(
          Messages.get(
              "WrapperUtils.noWrapperClassExists",
              new Object[] {toProxy.getClass().getName()}));
    }

    return toProxy;
  }

  /**
   * Check whether the given package is a JDBC package.
   *
   * @param packageName the name of the package to analyze
   * @return true if the given package is a JDBC package
   */
  public static boolean isJdbcPackage(@Nullable String packageName) {
    return packageName != null
        && (packageName.startsWith("java.sql")
            || packageName.startsWith("javax.sql")
            || packageName.startsWith("org.postgresql"));
  }

  /**
   * Check whether the given class implements a JDBC interface defined in a JDBC package. See {@link
   * #isJdbcPackage(String)} Calls to this function are cached for improved efficiency.
   *
   * @param clazz the class to analyze
   * @return true if the given class implements a JDBC interface
   */
  public static boolean isJdbcInterface(Class<?> clazz) {
    if (isJdbcInterfaceCache.containsKey(clazz)) {
      return (isJdbcInterfaceCache.get(clazz));
    }

    if (clazz.isInterface()) {
      try {
        Package classPackage = clazz.getPackage();
        if (classPackage != null && isJdbcPackage(classPackage.getName())) {
          isJdbcInterfaceCache.putIfAbsent(clazz, true);
          return true;
        }
      } catch (Exception ex) {
        // Ignore any exceptions since they're caused by runtime-generated classes, or due to class
        // load issues.
      }
    }

    for (Class<?> iface : clazz.getInterfaces()) {
      if (isJdbcInterface(iface)) {
        isJdbcInterfaceCache.putIfAbsent(clazz, true);
        return true;
      }
    }

    if (clazz.getSuperclass() != null && isJdbcInterface(clazz.getSuperclass())) {
      isJdbcInterfaceCache.putIfAbsent(clazz, true);
      return true;
    }

    isJdbcInterfaceCache.putIfAbsent(clazz, false);
    return false;
  }

  /**
   * Get the {@link Class} objects corresponding to the interfaces implemented by the given class.
   * Calls to this function are cached for improved efficiency.
   *
   * @param clazz the class to analyze
   * @return the interfaces implemented by the given class
   */
  public static Class<?>[] getImplementedInterfaces(Class<?> clazz) {
    Class<?>[] implementedInterfaces = getImplementedInterfacesCache.get(clazz);
    if (implementedInterfaces != null) {
      return implementedInterfaces;
    }

    Set<Class<?>> interfaces = new LinkedHashSet<>();
    Class<?> superClass = clazz;
    do {
      Collections.addAll(interfaces, superClass.getInterfaces());
    } while ((superClass = superClass.getSuperclass()) != null);

    implementedInterfaces = interfaces.toArray(new Class<?>[0]);
    Class<?>[] oldValue = getImplementedInterfacesCache.putIfAbsent(clazz, implementedInterfaces);
    if (oldValue != null) {
      implementedInterfaces = oldValue;
    }

    return implementedInterfaces;
  }

  public static <T> List<T> loadClasses(
      final String extensionClassNames, final Class<T> clazz, final String errorMessageResourceKey)
      throws InstantiationException {

    List<T> instances = new LinkedList<>();
    List<String> interceptorsToCreate = StringUtils.split(extensionClassNames, ",", true);
    String className = null;

    try {
      for (String value : interceptorsToCreate) {
        className = value;
        T instance = createInstance(className, clazz);
        instances.add(instance);
      }

    } catch (Throwable t) {
      throw new InstantiationException(Messages.get(errorMessageResourceKey, new Object[] {className}));
    }

    return instances;
  }

  public static <T> List<T> loadClasses(
      final List<Class<? extends T>> extensionClassList,
      final Class<T> resultClass,
      final String errorMessageResourceKey)
      throws InstantiationException {

    List<T> instances = new LinkedList<>();
    Class<? extends T> lastClass = null;

    try {
      for (Class<? extends T> extensionClass : extensionClassList) {
        lastClass = extensionClass;
        T instance = createInstance(lastClass, resultClass, null);
        instances.add(instance);
      }

    } catch (Throwable t) {
      throw new InstantiationException(Messages.get(errorMessageResourceKey, new Object[] {lastClass.getName()}));
    }

    return instances;
  }

  public static <T> T createInstance(
      final Class<?> classToInstantiate,
      final Class<T> resultClass,
      final Class<?>[] constructorArgClasses,
      final Object... constructorArgs)
      throws InstantiationException {

    if (classToInstantiate == null) {
      throw new IllegalArgumentException("classToInstantiate");
    }

    if (resultClass == null) {
      throw new IllegalArgumentException("resultClass");
    }

    try {
      if (constructorArgs.length == 0) {
        return resultClass.cast(classToInstantiate.newInstance());
      }

      Class<?>[] argClasses = constructorArgClasses;
      if (argClasses == null) {
        argClasses = new Class<?>[constructorArgs.length];
        for (int i = 0; i < constructorArgs.length; i++) {
          argClasses[i] = constructorArgs[i].getClass();
        }
      }
      Constructor<?> constructor = classToInstantiate.getConstructor(argClasses);
      return resultClass.cast(constructor.newInstance(constructorArgs));
    } catch (Exception e) {
      throw new InstantiationException(
          Messages.get(
              "WrapperUtils.failedToInitializeClass",
              new Object[] {classToInstantiate.getName()}));
    }
  }

  public static <T> T createInstance(
      final String className, final Class<T> resultClass, final Object... constructorArgs)
      throws InstantiationException {

    if (StringUtils.isNullOrEmpty(className)) {
      throw new IllegalArgumentException("className");
    }

    if (resultClass == null) {
      throw new IllegalArgumentException("resultClass");
    }

    Class<?> loaded;
    try {
      loaded = Class.forName(className);
    } catch (Exception e) {
      throw new InstantiationException(
          Messages.get(
              "WrapperUtils.failedToInitializeClass",
              new Object[] {className}));
    }

    return createInstance(loaded, resultClass, null, constructorArgs);
  }

  public static Object getFieldValue(Object target, String accessor) {
    if (target == null) {
      return null;
    }

    List<String> fieldNames = StringUtils.split(accessor, "\\.", true);
    Class<?> targetClass = target.getClass();

    for (String fieldName : fieldNames) {
      Field field = null;
      while (targetClass != null && field == null) {
        try {
          field = targetClass.getDeclaredField(fieldName);
        } catch (Exception ex) {
          // try parent class
          targetClass = targetClass.getSuperclass();
        }
      }

      if (field == null) {
        return null; // field not found
      }

      if (!field.isAccessible()) {
        field.setAccessible(true);
      }

      Object fieldValue;
      try {
        fieldValue = field.get(target);
      } catch (Exception ex) {
        return null;
      }

      if (fieldValue == null) {
        return null;
      }

      target = fieldValue;
      targetClass = target.getClass();
    }

    return target;
  }
}
