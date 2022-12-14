/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.PriorityIntentionActionWrapper;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.intention.UnresolvedReferenceQuickFixProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

@ExtensionImpl
public class DefaultQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@Nonnull PsiJavaCodeReferenceElement ref, @Nonnull QuickFixActionRegistrar registrar) {
    if (PsiUtil.isModuleFile(ref.getContainingFile())) {
      OrderEntryFix.registerFixes(registrar, ref);
      return;
    }

    registrar.register(new ImportClassFix(ref));
    registrar.register(new StaticImportConstantFix(ref));
    registrar.register(QuickFixFactory.getInstance().createSetupJDKFix());

    OrderEntryFix.registerFixes(registrar, ref);

    MoveClassToModuleFix.registerFixes(registrar, ref);

    if (ref instanceof PsiReferenceExpression) {
      TextRange fixRange = HighlightMethodUtil.getFixRange(ref);
      PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;

      registrar.register(fixRange, new CreateEnumConstantFromUsageFix(refExpr), null);
      registrar.register(new RenameWrongRefFix(refExpr));

      if (!ref.isQualified()) {
        registrar.register(fixRange, new BringVariableIntoScopeFix(refExpr), null);
      }

      registerPriorityActions(registrar, fixRange, refExpr);
    }

    registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.INTERFACE));
    if (PsiUtil.isLanguageLevel5OrHigher(ref)) {
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.ENUM));
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.ANNOTATION));
    }

    PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(ref, PsiExpressionList.class);
    if (parent instanceof PsiNewExpression && !(ref.getParent() instanceof PsiTypeElement) && (expressionList == null || !PsiTreeUtil.isAncestor(
      parent,
      expressionList,
      false))) {
      registrar.register(new CreateClassFromNewFix((PsiNewExpression)parent));
      registrar.register(new CreateInnerClassFromNewFix((PsiNewExpression)parent));
    }
    else {
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.CLASS));
      registrar.register(new CreateInnerClassFromUsageFix(ref, CreateClassKind.CLASS));
    }
  }

  private static void registerPriorityActions(@Nonnull QuickFixActionRegistrar registrar,
                                              @Nonnull TextRange fixRange,
                                              @Nonnull PsiReferenceExpression refExpr) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(refExpr.getProject());

    final Map<VariableKind, IntentionAction> map = new EnumMap<>(VariableKind.class);
    map.put(VariableKind.FIELD, new CreateFieldFromUsageFix(refExpr));
    map.put(VariableKind.STATIC_FINAL_FIELD, new CreateConstantFieldFromUsageFix(refExpr));
    if (!refExpr.isQualified()) {
      map.put(VariableKind.LOCAL_VARIABLE, new CreateLocalFromUsageFix(refExpr));
      map.put(VariableKind.PARAMETER, new CreateParameterFromUsageFix(refExpr));
    }

    final VariableKind kind = getKind(styleManager, refExpr);
    if (map.containsKey(kind)) {
      map.put(kind, PriorityIntentionActionWrapper.highPriority(map.get(kind)));
    }

    for (IntentionAction action : map.values()) {
      registrar.register(fixRange, action, null);
    }
  }

  @Nullable
  private static VariableKind getKind(@Nonnull JavaCodeStyleManager styleManager, @Nonnull PsiReferenceExpression refExpr) {
    final String reference = refExpr.getText();

    boolean upperCase = true;
    for (int i = 0; i < reference.length(); i++) {
      if (!Character.isUpperCase(reference.charAt(i))) {
        upperCase = false;
        break;
      }
    }
    if (upperCase) {
      return VariableKind.STATIC_FINAL_FIELD;
    }

    for (VariableKind kind : VariableKind.values()) {
      final String prefix = styleManager.getPrefixByVariableKind(kind);
      final String suffix = styleManager.getSuffixByVariableKind(kind);

      if (prefix.isEmpty() && suffix.isEmpty()) {
        continue;
      }

      if (reference.startsWith(prefix) && reference.endsWith(suffix)) {
        return kind;
      }
    }

    if (StringUtil.isCapitalized(reference)) {
      return null;
    }

    return VariableKind.LOCAL_VARIABLE;
  }

  @Override
  @Nonnull
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}