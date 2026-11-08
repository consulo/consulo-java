// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.LanguageCachedValueUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.lang.ObjectUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jspecify.annotations.Nullable;

public class FieldChecker {
  private final boolean myTrustDirectFieldInitializers;
  private final boolean myTrustFieldInitializersInConstructors;
  private final boolean myCanInstantiateItself;
  private final PsiClass myClass;
  private final FieldChecker myParent;

  private FieldChecker(PsiElement context) {
    PsiMethod method = ObjectUtil.tryCast(PsiTreeUtil.getNonStrictParentOfType(context, PsiMember.class), PsiMethod.class);
    PsiClass contextClass = method != null ? method.getContainingClass() : context instanceof PsiClass ? (PsiClass)context : null;
    myClass = contextClass;
    if (method == null || myClass == null) {
      myTrustDirectFieldInitializers = myTrustFieldInitializersInConstructors = myCanInstantiateItself = false;
      myParent = null;
      return;
    }
    myParent = PsiUtil.isLocalOrAnonymousClass(myClass) ? new FieldChecker(myClass.getParent()) : null;
    // Indirect instantiation via other class is still possible, but hopefully unlikely
    ClassInitializationInfo info = LanguageCachedValueUtil.getProjectPsiDependentCache(contextClass, ClassInitializationInfo::new);
    myCanInstantiateItself = info.myCanInstantiateItself;
    if (method.hasModifierProperty(PsiModifier.STATIC) || method.isConstructor()) {
      myTrustDirectFieldInitializers = true;
      myTrustFieldInitializersInConstructors = false;
      return;
    }
    myTrustFieldInitializersInConstructors = !info.mySuperCtorsCallMethods && !info.myCtorsCallMethods;
    myTrustDirectFieldInitializers = !info.mySuperCtorsCallMethods;
  }

  public boolean canTrustFieldInitializer(PsiField field) {
    if (myParent != null && !myParent.canTrustFieldInitializer(field)) return false;
    if (field.hasInitializer()) {
      boolean staticField = field.hasModifierProperty(PsiModifier.STATIC);
      if (staticField && myClass != null && field.getContainingClass() != myClass) return true;
      return myTrustDirectFieldInitializers && (!myCanInstantiateItself || !staticField);
    }
    return myTrustFieldInitializersInConstructors;
  }

  public static FieldChecker getChecker(@Nullable PsiElement context) {
    if (context == null) return new FieldChecker(null);
    return LanguageCachedValueUtil.getProjectPsiDependentCache(context, FieldChecker::new);
  }

  private static class ClassInitializationInfo {
    private static final CallMatcher SAFE_CALLS =
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "requireNonNull");

    final boolean myCanInstantiateItself;
    final boolean myCtorsCallMethods;
    final boolean mySuperCtorsCallMethods;

    ClassInitializationInfo(PsiClass psiClass) {
      // Indirect instantiation via other class is still possible, but hopefully unlikely
      boolean canInstantiateItself = false;
      for (PsiElement child = psiClass.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiMember && ((PsiMember)child).hasModifierProperty(PsiModifier.STATIC) &&
            SyntaxTraverser.psiTraverser(child).filter(PsiNewExpression.class)
              .filterMap(PsiNewExpression::getClassReference)
              .find(classRef -> classRef.isReferenceTo(psiClass)) != null) {
          canInstantiateItself = true;
          break;
        }
      }
      myCanInstantiateItself = canInstantiateItself;
      mySuperCtorsCallMethods =
        !InheritanceUtil.processSupers(psiClass, false, superClass -> !canCallMethodsInConstructors(superClass, true));
      myCtorsCallMethods = canCallMethodsInConstructors(psiClass, false);
    }

    private static boolean canCallMethodsInConstructors(PsiClass aClass, boolean virtual) {
      boolean inByteCode = false;
      if (aClass instanceof PsiCompiledElement) {
        inByteCode = true;
        PsiElement navigationElement = aClass.getNavigationElement();
        if (navigationElement instanceof PsiClass) {
          aClass = (PsiClass)navigationElement;
        }
      }
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (inByteCode && JavaMethodContractUtil.isPure(constructor) &&
            !JavaMethodContractUtil.hasExplicitContractAnnotation(constructor)) {
          // While pure constructor may call pure overridable method, our current implementation
          // of bytecode inference will not infer the constructor purity in this case.
          // So if we inferred a constructor purity from bytecode we can currently rely that
          // no overridable methods are called there.
          continue;
        }
        if (!constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return true;

        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;

        for (PsiMethodCallExpression call : SyntaxTraverser.psiTraverser().withRoot(body).filter(PsiMethodCallExpression.class)) {
          PsiReferenceExpression methodExpression = call.getMethodExpression();
          if (methodExpression.textMatches(PsiKeyword.THIS) || methodExpression.textMatches(PsiKeyword.SUPER)) continue;
          if (SAFE_CALLS.test(call)) continue;
          if (!virtual) return true;

          PsiMethod target = call.resolveMethod();
          if (target != null && PsiUtil.canBeOverridden(target)) return true;
        }
      }

      return false;
    }
  }
}
