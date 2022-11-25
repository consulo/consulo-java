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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.lookup.TypedLookupItem;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.AllIcons;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import static consulo.java.language.module.util.JavaClassNames.*;

/**
 * @author peter
 */
class CollectConversion {

  static void addCollectConversion(PsiReferenceExpression ref,
                                   Collection<ExpectedTypeInfo> expectedTypes,
                                   Consumer<LookupElement> consumer) {
    final PsiExpression qualifier = ref.getQualifierExpression();
    PsiType component = qualifier == null ? null : PsiUtil.substituteTypeParameter(qualifier.getType(),
        JAVA_UTIL_STREAM_STREAM, 0, true);
    if (component == null) {
      return;
    }

    JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
    GlobalSearchScope scope = ref.getResolveScope();
    PsiClass list = facade.findClass(JAVA_UTIL_LIST, scope);
    PsiClass set = facade.findClass(JAVA_UTIL_SET, scope);
    if (facade.findClass(JAVA_UTIL_STREAM_COLLECTORS, scope) == null || list == null || set == null) {
      return;
    }

    boolean hasList = false;
    boolean hasSet = false;
    for (ExpectedTypeInfo info : expectedTypes) {
      PsiType type = info.getDefaultType();
      PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(type);
      PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(type, true);
      if (expectedClass == null || expectedComponent == null || !TypeConversionUtil.isAssignable(expectedComponent, component)) {
        continue;
      }

      if (!hasList && InheritanceUtil.isInheritorOrSelf(list, expectedClass, true)) {
        hasList = true;
        consumer.accept(new MyLookupElement("toList", type));
      }

      if (!hasSet && InheritanceUtil.isInheritorOrSelf(set, expectedClass, true)) {
        hasSet = true;
        consumer.accept(new MyLookupElement("toSet", type));
      }
    }
  }

  private static class MyLookupElement extends LookupElement implements TypedLookupItem {
    private final String myLookupString;
    private final String myTypeText;
    private final String myMethodName;
    @Nonnull
    private final PsiType myExpectedType;

    MyLookupElement(String methodName, @Nonnull PsiType expectedType) {
      myMethodName = methodName;
      myExpectedType = expectedType;
      myLookupString = "collect(Collectors." + myMethodName + "())";
      myTypeText = myExpectedType.getPresentableText();
    }

    @Nonnull
    @Override
    public String getLookupString() {
      return myLookupString;
    }

    @Override
    public Set<String> getAllLookupStrings() {
      return Set.of(myLookupString, myMethodName);
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setTypeText(myTypeText);
      presentation.setIcon(AllIcons.Nodes.Method);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), getInsertString());
      context.commitDocument();

      PsiMethodCallExpression call = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(),
          context.getStartOffset(), PsiMethodCallExpression.class, false);
      if (call == null) {
        return;
      }

      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1 || !(args[0] instanceof PsiMethodCallExpression)) {
        return;
      }

      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(args[0]);
    }

    @Nonnull
    private String getInsertString() {
      return "collect(" + JAVA_UTIL_STREAM_COLLECTORS + "." + myMethodName + "())";
    }

    @Override
    public PsiType getType() {
      return myExpectedType;
    }
  }
}
