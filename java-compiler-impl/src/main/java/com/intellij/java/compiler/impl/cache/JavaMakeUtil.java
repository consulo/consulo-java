/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

/**
 * created at Jan 17, 2002
 *
 * @author Jeka
 */
package com.intellij.java.compiler.impl.cache;

import com.intellij.java.compiler.impl.classParsing.*;
import com.intellij.java.compiler.impl.util.cls.ClsUtil;
import consulo.application.util.function.Computable;
import consulo.compiler.CacheCorruptedException;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.compiler.util.MakeUtil;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.Platform;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

public class JavaMakeUtil extends MakeUtil {
  private static final Logger LOGGER = Logger.getInstance(JavaMakeUtil.class);

  private JavaMakeUtil() {
  }

  /**
   * cuts inner or anonymous class' parts and translates package names to lower case
   */
  private static String normalizeClassName(String qName) {
    int index = qName.indexOf('$');
    if (index >= 0) {
      qName = qName.substring(0, index);
    }
    if (Platform.current().fs().isCaseSensitive()) {
      return qName;
    }
    // the name of a dir should be lowercased because javac seem to allow difference in case
    // between the physical directory and package name.
    final int dotIndex = qName.lastIndexOf('.');
    final StringBuilder builder = new StringBuilder();
    builder.append(qName);
    for (int idx = 0; idx < dotIndex; idx++) {
      builder.setCharAt(idx, Character.toLowerCase(builder.charAt(idx)));
    }
    return builder.toString();
  }

  public static boolean isAnonymous(String name) {
    int index = name.lastIndexOf('$');
    if (index >= 0) {
      index++;
      if (index < name.length()) {
        try {
          Integer.parseInt(name.substring(index));
          return true;
        }
        catch (NumberFormatException ignore) {
        }
      }
    }
    return false;
  }

  /*
   not needed currently
  public static String getEnclosingClassName(String anonymousQName) {
    return anonymousQName.substring(0, anonymousQName.lastIndexOf('$'));
  }
  */

  /*
   not needed currently
  public static boolean isNative(int flags) {
    return (ClsUtil.ACC_NATIVE & flags) != 0;
  }
  */

  /**
   * tests if the accessibility, denoted by flags1 is less restricted than the accessibility denoted by flags2
   *
   * @return true means flags1 is less restricted than flags2 <br>
   * false means flags1 define more restricted access than flags2 or they have equal accessibility
   */
  public static boolean isMoreAccessible(int flags1, int flags2) {
    if (ClsUtil.isPrivate(flags2)) {
      return ClsUtil.isPackageLocal(flags1) || ClsUtil.isProtected(flags1) || ClsUtil.isPublic(flags1);
    }
    if (ClsUtil.isPackageLocal(flags2)) {
      return ClsUtil.isProtected(flags1) || ClsUtil.isPublic(flags1);
    }
    return ClsUtil.isProtected(flags2) && ClsUtil.isPublic(flags1);
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String relativeClassPathToQName(String relativePath, char separator) {
    if (!relativePath.endsWith(".class")) {
      return null;
    }
    int start = 0;
    int end = relativePath.length() - ".class".length();
    if (relativePath.startsWith(String.valueOf(separator))) {
      start += 1;
    }
    return (start <= end) ? relativePath.substring(start, end).replace(separator, '.') : null;
  }

  public static
  @NonNls
  String parseObjectType(final String descriptor, int fromIndex) {
    int semicolonIndex = descriptor.indexOf(';', fromIndex);
    if (descriptor.charAt(fromIndex) == 'L' && semicolonIndex > fromIndex) { // isObjectType
      return descriptor.substring(fromIndex + 1, semicolonIndex).replace('/', '.');
    }
    if (descriptor.charAt(fromIndex) == '[' && (descriptor.length() - fromIndex) > 0) { // isArrayType
      return parseObjectType(descriptor, fromIndex + 1);
    }
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isPrimitiveType(String descriptor) {
    return "V".equals(descriptor) || "B".equals(descriptor) || "C".equals(descriptor) || "D".equals(descriptor) || "F".equals(descriptor)
      || "I".equals(descriptor) || "J".equals(descriptor) || "S".equals(descriptor) || "Z".equals(descriptor);
  }

  public static boolean isArrayType(String descriptor) {
    return StringUtil.startsWithChar(descriptor, '[');
  }

  public static String getComponentType(String descriptor) {
    if (!isArrayType(descriptor)) {
      return null;
    }
    return descriptor.substring(1);
  }


  /**
   * @return a normalized path to source relative to a source root by class qualified name and sourcefile short name.
   * The path uses forward slashes "/".
   */
  public static String createRelativePathToSource(String qualifiedName, String srcName) {
    qualifiedName = normalizeClassName(qualifiedName);
    int index = qualifiedName.lastIndexOf('.');
    if (index >= 0) {
      srcName = qualifiedName.substring(0, index).replace('.', '/') + "/" + srcName;
    }
    return srcName;
  }

  public static boolean isInterface(int flags) {
    return (Opcodes.ACC_INTERFACE & flags) != 0;
  }

  public static int getAnnotationTargets(final Cache cache,
                                         final int annotationQName,
                                         final SymbolTable symbolTable) throws CacheCorruptedException {
    final AnnotationConstantValue
      targetAnnotation = findAnnotation("java.lang.annotation.Target", cache.getRuntimeVisibleAnnotations(annotationQName), symbolTable);
    if (targetAnnotation == null) {
      return AnnotationTargets.ALL; // all program elements are annotation targets by default
    }
    final AnnotationNameValuePair[] memberValues = targetAnnotation.getMemberValues();
    ConstantValueArray value = (ConstantValueArray)memberValues[0].getValue();
    final ConstantValue[] targets = value.getValue();
    int annotationTargets = 0;
    for (final ConstantValue target : targets) {
      if (target instanceof EnumConstantValue enumConstantValue) {
        final String constantName = symbolTable.getSymbol(enumConstantValue.getConstantName());
        if (AnnotationTargets.TYPE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.TYPE;
        }
        if (AnnotationTargets.FIELD_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.FIELD;
        }
        if (AnnotationTargets.METHOD_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.METHOD;
        }
        if (AnnotationTargets.PARAMETER_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.PARAMETER;
        }
        if (AnnotationTargets.CONSTRUCTOR_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.CONSTRUCTOR;
        }
        if (AnnotationTargets.LOCAL_VARIABLE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.LOCAL_VARIABLE;
        }
        if (AnnotationTargets.ANNOTATION_TYPE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.ANNOTATION_TYPE;
        }
        if (AnnotationTargets.PACKAGE_STR.equals(constantName)) {
          annotationTargets |= AnnotationTargets.PACKAGE;
        }
      }
    }
    return annotationTargets;
  }

  public static int getAnnotationRetentionPolicy(
    final int annotationQName,
    final Cache cache,
    final SymbolTable symbolTable
  ) throws CacheCorruptedException {
    final AnnotationConstantValue retentionPolicyAnnotation =
      findAnnotation("java.lang.annotation.Retention", cache.getRuntimeVisibleAnnotations(annotationQName), symbolTable);
    if (retentionPolicyAnnotation == null) {
      return RetentionPolicies.CLASS; // default retention policy
    }
    final AnnotationNameValuePair[] memberValues = retentionPolicyAnnotation.getMemberValues();
    final EnumConstantValue value = (EnumConstantValue)memberValues[0].getValue();
    final String constantName = symbolTable.getSymbol(value.getConstantName());
    if (RetentionPolicies.SOURCE_STR.equals(constantName)) {
      return RetentionPolicies.SOURCE;
    }
    if (RetentionPolicies.CLASS_STR.equals(constantName)) {
      return RetentionPolicies.CLASS;
    }
    if (RetentionPolicies.RUNTIME_STR.equals(constantName)) {
      return RetentionPolicies.RUNTIME;
    }
    LOGGER.error("Unknown retention policy: " + constantName);
    return -1;
  }

  public static AnnotationConstantValue findAnnotation(
    @NonNls final String annotationQName,
    AnnotationConstantValue[] annotations,
    final SymbolTable symbolTable
  ) throws CacheCorruptedException {
    for (final AnnotationConstantValue annotation : annotations) {
      if (annotationQName.equals(symbolTable.getSymbol(annotation.getAnnotationQName()))) {
        return annotation;
      }
    }
    return null;
  }

  public static String getModuleOutputDirPath(final Module module) {
    return module.getApplication().runReadAction((Computable<String>)() -> {
      final String url =
        ModuleCompilerPathsManager.getInstance(module).getCompilerOutputUrl(ProductionContentFolderTypeProvider.getInstance());
      if (url == null) {
        return null;
      }
      return VirtualFileUtil.urlToPath(url);
    });
  }
}
