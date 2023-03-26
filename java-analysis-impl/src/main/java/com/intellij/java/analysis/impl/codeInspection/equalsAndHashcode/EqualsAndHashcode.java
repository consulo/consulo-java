/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.equalsAndHashcode;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.function.Computable;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author max
 */
@ExtensionImpl
public class EqualsAndHashcode extends BaseJavaBatchLocalInspectionTool {
  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    final Project project = holder.getProject();
    Pair<PsiMethod, PsiMethod> pair = CachedValuesManager.getManager(project).getCachedValue(project, new CachedValueProvider<Pair<PsiMethod, PsiMethod>>() {
      @Override
      public Result<Pair<PsiMethod, PsiMethod>> compute() {
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass psiObjectClass = ApplicationManager.getApplication().runReadAction(
            new Computable<PsiClass>() {
              @Override
              @Nullable
              public PsiClass compute() {
                return psiFacade.findClass(JavaClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project));
              }
            }
        );
        if (psiObjectClass == null) {
          return Result.create(null, ProjectRootManager.getInstance(project));
        }
        PsiMethod[] methods = psiObjectClass.getMethods();
        PsiMethod myEquals = null;
        PsiMethod myHashCode = null;
        for (PsiMethod method : methods) {
          @NonNls final String name = method.getName();
          if ("equals".equals(name)) {
            myEquals = method;
          }
          else if ("hashCode".equals(name)) {
            myHashCode = method;
          }
        }
        return Result.create(Pair.create(myEquals, myHashCode), psiObjectClass);
      }
    });

    if (pair == null) return new PsiElementVisitor() {};

    //jdk wasn't configured for the project
    final PsiMethod myEquals = pair.first;
    final PsiMethod myHashCode = pair.second;
    if (myEquals == null || myHashCode == null || !myEquals.isValid() || !myHashCode.isValid()) return new PsiElementVisitor() {};

    return new JavaElementVisitor() {
      @Override public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        boolean [] hasEquals = {false};
        boolean [] hasHashCode = {false};
        processClass(aClass, hasEquals, hasHashCode, myEquals, myHashCode);
        if (hasEquals[0] != hasHashCode[0]) {
          PsiIdentifier identifier = aClass.getNameIdentifier();
          holder.registerProblem(identifier != null ? identifier : aClass,
                                 hasEquals[0]
                                  ? InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>equals()</code>", "<code>hashCode()</code>")
                                  : InspectionsBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor","<code>hashCode()</code>", "<code>equals()</code>"),
                                 (LocalQuickFix[])null);
        }
      }
    };
  }

  private static void processClass(final PsiClass aClass,
                                   final boolean[] hasEquals,
                                   final boolean[] hasHashCode,
                                   PsiMethod equals, PsiMethod hashcode) {
    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (MethodSignatureUtil.areSignaturesEqual(method, equals)) {
        hasEquals[0] = true;
      }
      else if (MethodSignatureUtil.areSignaturesEqual(method, hashcode)) {
        hasHashCode[0] = true;
      }
    }
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.equals.hashcode.display.name");
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "EqualsAndHashcode";
  }
}
