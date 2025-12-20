/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class SerializationUtils {

  private SerializationUtils() {
  }

  public static boolean isSerializable(@Nullable PsiClass aClass) {
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_IO_SERIALIZABLE);
  }

  public static boolean isExternalizable(@Nullable PsiClass aClass) {
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_IO_EXTERNALIZABLE);
  }

  public static boolean isDirectlySerializable(@Nonnull PsiClass aClass) {
    PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList == null) {
      return false;
    }
    PsiJavaCodeReferenceElement[] interfaces = implementsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement aInterfaces : interfaces) {
      PsiClass implemented = (PsiClass) aInterfaces.resolve();
      if (implemented == null) {
        continue;
      }
      String name = implemented.getQualifiedName();
      if (CommonClassNames.JAVA_IO_SERIALIZABLE.equals(name)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasReadObject(@Nonnull PsiClass aClass) {
    PsiMethod[] methods = aClass.findMethodsByName("readObject", false);
    for (PsiMethod method : methods) {
      if (isReadObject(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasReadResolve(@Nonnull PsiClass aClass) {
    PsiMethod[] methods = aClass.findMethodsByName("readResolve", true);
    for (PsiMethod method : methods) {
      if (isReadResolve(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasWriteObject(@Nonnull PsiClass aClass) {
    PsiMethod[] methods = aClass.findMethodsByName("writeObject", false);
    for (PsiMethod method : methods) {
      if (isWriteObject(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasWriteReplace(@Nonnull PsiClass aClass) {
    PsiMethod[] methods = aClass.findMethodsByName("writeReplace", true);
    for (PsiMethod method : methods) {
      if (isWriteReplace(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isReadObject(@Nonnull PsiMethod method) {
    PsiClassType type = TypeUtils.getType("java.io.ObjectInputStream", method);
    return MethodUtils.methodMatches(method, null, PsiType.VOID, "readObject", type);
  }

  public static boolean isWriteObject(@Nonnull PsiMethod method) {
    PsiClassType type = TypeUtils.getType("java.io.ObjectOutputStream", method);
    return MethodUtils.methodMatches(method, null, PsiType.VOID, "writeObject", type);
  }

  public static boolean isReadResolve(@Nonnull PsiMethod method) {
    return MethodUtils.simpleMethodMatches(method, null, CommonClassNames.JAVA_LANG_OBJECT, "readResolve");
  }

  public static boolean isWriteReplace(@Nonnull PsiMethod method) {
    return MethodUtils.simpleMethodMatches(method, null, CommonClassNames.JAVA_LANG_OBJECT, "writeReplace");
  }

  public static boolean isProbablySerializable(PsiType type) {
    if (type instanceof PsiWildcardType) {
      return true;
    }
    if (type instanceof PsiPrimitiveType) {
      return true;
    }
    if (type instanceof PsiArrayType) {
      PsiArrayType arrayType = (PsiArrayType) type;
      PsiType componentType = arrayType.getComponentType();
      return isProbablySerializable(componentType);
    }
    if (type instanceof PsiClassType) {
      PsiClassType classTYpe = (PsiClassType) type;
      PsiClass psiClass = classTYpe.resolve();
      if (isSerializable(psiClass)) {
        return true;
      }
      if (isExternalizable(psiClass)) {
        return true;
      }
      if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_COLLECTION) ||
          InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_MAP)) {
        PsiType[] parameters = classTYpe.getParameters();
        for (PsiType parameter : parameters) {
          if (!isProbablySerializable(parameter)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
    return false;
  }
}
