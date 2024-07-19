/*
 * Copyright 2006-2008 Bas Leijdekkers
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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RenameFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class AnonymousClassVariableHidesContainingMethodVariableInspection
  extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.anonymousClassVariableHidesContainingMethodVariableDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiParameter) {
      return InspectionGadgetsLocalize.anonymousClassParameterHidesContainingMethodVariableProblemDescriptor().get();
    }
    else if (info instanceof PsiField) {
      return InspectionGadgetsLocalize.anonymousClassFieldHidesContainingMethodVariableProblemDescriptor().get();
    }
    return InspectionGadgetsLocalize.anonymousClassVariableHidesContainingMethodVariableProblemDescriptor().get();
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AnonymousClassVariableHidesOuterClassVariableVisitor();
  }
}