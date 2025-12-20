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
package com.intellij.java.impl.refactoring.extractMethod;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.RedundantCastUtil;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ven
 */
public class ExtractMethodUtil {
  private static final Key<PsiMethod> RESOLVE_TARGET_KEY = Key.create("RESOLVE_TARGET_KEY");
  private static final Logger LOG = Logger.getInstance(com.intellij.java.analysis.impl.refactoring.extractMethod.ExtractMethodUtil.class);

  private ExtractMethodUtil() { }

  static Map<PsiMethodCallExpression, PsiMethod> encodeOverloadTargets(PsiClass targetClass,
                                                                       SearchScope processConflictsScope,
                                                                       final String overloadName,
                                                                       final PsiElement extractedFragment) {
    final Map<PsiMethodCallExpression, PsiMethod> ret = new HashMap<PsiMethodCallExpression, PsiMethod>();
    encodeInClass(targetClass, overloadName, extractedFragment, ret);

    ClassInheritorsSearch.search(targetClass, processConflictsScope, true).forEach(new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        encodeInClass(inheritor, overloadName, extractedFragment, ret);
        return true;
      }
    });

    return ret;
  }

  private static void encodeInClass(PsiClass aClass,
                                    String overloadName,
                                    PsiElement extractedFragment,
                                    Map<PsiMethodCallExpression, PsiMethod> ret) {
    PsiMethod[] overloads = aClass.findMethodsByName(overloadName, false);
    for (PsiMethod overload : overloads) {
      for (PsiReference ref : ReferencesSearch.search(overload)) {
        PsiElement element = ref.getElement();
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)parent;
          if (PsiTreeUtil.isAncestor(extractedFragment, element, false)) {
            call.putCopyableUserData(RESOLVE_TARGET_KEY, overload);
          } else {
            //we assume element won't be invalidated as a result of extraction
            ret.put(call, overload);
          }
        }
      }
    }
  }

  public static void decodeOverloadTargets(Map<PsiMethodCallExpression, PsiMethod> oldResolves, final PsiMethod extracted,
                                           PsiElement oldFragment) {
    PsiCodeBlock body = extracted.getBody();
    assert body != null;
    JavaRecursiveElementVisitor visitor = new JavaRecursiveElementVisitor() {

      @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiMethod target = expression.getCopyableUserData(RESOLVE_TARGET_KEY);
        if (target != null) {
          expression.putCopyableUserData(RESOLVE_TARGET_KEY, null);
          try {
            assertSameResolveTarget(target, expression, extracted);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    };
    body.accept(visitor);
    oldFragment.accept(visitor);

    for (Map.Entry<PsiMethodCallExpression, PsiMethod> entry : oldResolves.entrySet()) {
      try {
        assertSameResolveTarget(entry.getValue(), entry.getKey(), extracted);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static void assertSameResolveTarget(PsiMethod oldTarget, PsiMethodCallExpression call, PsiMethod extracted)
    throws IncorrectOperationException {
    PsiMethod newTarget = call.resolveMethod();
    PsiManager manager = extracted.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (!manager.areElementsEquivalent(oldTarget, newTarget)) {
      PsiParameter[] oldParameters = oldTarget.getParameterList().getParameters();
      if (oldParameters.length > 0) {
        PsiMethodCallExpression copy = (PsiMethodCallExpression)call.copy();
        PsiExpression[] args = copy.getArgumentList().getExpressions();
        for (int i = 0; i < args.length; i++) {
          PsiExpression arg = args[i];
          PsiType paramType = i < oldParameters.length ? oldParameters[i].getType() : oldParameters[oldParameters.length - 1].getType();
          PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(a)b", null);
          PsiTypeElement typeElement = cast.getCastType();
          assert typeElement != null;
          typeElement.replace(factory.createTypeElement(paramType));
          PsiExpression operand = cast.getOperand();
          assert operand != null;
          operand.replace(arg);
          arg.replace(cast);
        }

        for (int i = 0; i < copy.getArgumentList().getExpressions().length; i++) {
          PsiExpression oldarg = call.getArgumentList().getExpressions()[i];
          PsiTypeCastExpression cast = (PsiTypeCastExpression)copy.getArgumentList().getExpressions()[i];
          if (!RedundantCastUtil.isCastRedundant(cast)) {
            oldarg.replace(cast);
          }
        }
      }
    }
  }
}
