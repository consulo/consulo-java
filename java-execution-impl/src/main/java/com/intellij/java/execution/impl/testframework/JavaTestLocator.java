/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.testframework;

import com.intellij.java.execution.impl.junit2.PsiMemberParameterizedLocation;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import consulo.execution.test.sm.runner.SMTestLocator;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaTestLocator implements SMTestLocator {
  public static final String SUITE_PROTOCOL = "java:suite";
  public static final String TEST_PROTOCOL = "java:test";

  public static final JavaTestLocator INSTANCE = new JavaTestLocator();

  @jakarta.annotation.Nonnull
  @Override
  public List<Location> getLocation(@jakarta.annotation.Nonnull String protocol, @Nonnull String path, @Nonnull Project project, @jakarta.annotation.Nonnull GlobalSearchScope scope) {
    List<Location> results = Collections.emptyList();

    String paramName = null;
    int idx = path.indexOf('[');
    if (idx >= 0) {
      paramName = path.substring(idx);
      path = path.substring(0, idx);
    }

    if (SUITE_PROTOCOL.equals(protocol)) {
      path = StringUtil.trimEnd(path, ".");
      PsiClass aClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), path, null, true, scope);
      if (aClass != null) {
        results = new ArrayList<>();
        results.add(createClassNavigatable(paramName, aClass));
      } else {
        results = collectMethodNavigatables(path, project, scope, paramName);
      }
    } else if (TEST_PROTOCOL.equals(protocol)) {
      results = collectMethodNavigatables(path, project, scope, paramName);
    }

    return results;
  }

  private static List<Location> collectMethodNavigatables(@Nonnull String path, @Nonnull Project project, @jakarta.annotation.Nonnull GlobalSearchScope scope, String paramName) {
    List<Location> results = Collections.emptyList();
    String className = StringUtil.getPackageName(path);
    if (!StringUtil.isEmpty(className)) {
      String methodName = StringUtil.getShortName(path);
      PsiClass aClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), className, null, true, scope);
      if (aClass != null) {
        results = new ArrayList<>();
        if (methodName.trim().equals(aClass.getName())) {
          results.add(createClassNavigatable(paramName, aClass));
        } else {
          PsiMethod[] methods = aClass.findMethodsByName(methodName.trim(), true);
          if (methods.length > 0) {
            for (PsiMethod method : methods) {
              results.add(paramName != null ? new PsiMemberParameterizedLocation(project, method, aClass, paramName) : MethodLocation.elementInClass(method, aClass));
            }
          }
        }
      }
    }
    return results;
  }

  private static Location createClassNavigatable(String paramName, @Nonnull PsiClass aClass) {
    return paramName != null ? PsiMemberParameterizedLocation.getParameterizedLocation(aClass, paramName) : new PsiLocation<>(aClass.getProject(), aClass);
  }
}
