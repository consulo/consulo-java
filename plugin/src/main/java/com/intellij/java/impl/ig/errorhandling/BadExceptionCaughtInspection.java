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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.List;

@ExtensionImpl
public class BadExceptionCaughtInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public String exceptionsString = "";

  /**
   * @noinspection PublicField
   */
  public final ExternalizableStringSet exceptions =
    new ExternalizableStringSet(
        CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION,
      "java.lang.IllegalMonitorStateException",
        CommonClassNames.JAVA_LANG_ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION
    );

  public BadExceptionCaughtInspection() {
    if (exceptionsString.length() != 0) {
      exceptions.clear();
      final List<String> strings = StringUtil.split(exceptionsString, ",");
      for (String string : strings) {
        exceptions.add(string);
      }
      exceptionsString = "";
    }
  }

  @Nonnull
  public String getID() {
    return "ProhibitedExceptionCaught";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.badExceptionCaughtDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.badExceptionCaughtProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table =
      new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsLocalize.exceptionClassColumnName().get()));
    return UiUtils.createAddRemoveTreeClassChooserPanel(
      table,
      InspectionGadgetsLocalize.chooseExceptionClass().get(),
        CommonClassNames.JAVA_LANG_THROWABLE
    );
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionCaughtVisitor();
  }

  private class BadExceptionCaughtVisitor extends BaseInspectionVisitor {

    @Override
    public void visitCatchSection(PsiCatchSection section) {
      super.visitCatchSection(section);
      final PsiParameter parameter = section.getParameter();
      if (parameter == null) {
        return;
      }
      final PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiTypeElement[] childTypeElements = PsiTreeUtil.getChildrenOfType(typeElement, PsiTypeElement.class);
      if (childTypeElements != null) {
        for (PsiTypeElement childTypeElement : childTypeElements) {
          checkTypeElement(childTypeElement);
        }
      }
      else {
        checkTypeElement(typeElement);
      }
    }

    private void checkTypeElement(PsiTypeElement typeElement) {
      final PsiType type = typeElement.getType();
      if (exceptions.contains(type.getCanonicalText())) {
        registerError(typeElement);
      }
    }
  }
}
