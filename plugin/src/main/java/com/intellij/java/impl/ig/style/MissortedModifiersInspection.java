/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.*;

@ExtensionImpl
public class MissortedModifiersInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_requireAnnotationsFirst = true;

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.missortedModifiersDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.missortedModifiersProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MissortedModifiersVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SortModifiersFix();
  }

  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.missortedModifiersRequireOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "m_requireAnnotationsFirst");
  }

  private static class SortModifiersFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.missortedModifiersSortQuickfix();
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {

      final PsiModifierList modifierList = (PsiModifierList)descriptor.getPsiElement();
      final List<String> modifiers = new ArrayList<>();
      final PsiElement[] children = modifierList.getChildren();
      for (final PsiElement child : children) {
        if (child instanceof PsiComment) {
          final PsiComment comment = (PsiComment)child;
          final IElementType tokenType = comment.getTokenType();
          if (JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType)) {
            @NonNls final String text = child.getText() + '\n';
            modifiers.add(text);
          }
          else {
            modifiers.add(child.getText());
          }
        }
        else if (child instanceof PsiJavaToken) {
          modifiers.add(child.getText());
        }
        else if (child instanceof PsiAnnotation) {
          modifiers.add(0, child.getText());
        }
      }
      Collections.sort(modifiers, new ModifierComparator());
      @NonNls final StringBuilder buffer = new StringBuilder();
      for (String modifier : modifiers) {
        buffer.append(modifier);
        buffer.append(' ');
      }
      final PsiManager manager = modifierList.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      buffer.append("void x() {}");
      final String text = buffer.toString();
      final PsiMethod method =
        factory.createMethodFromText(text, modifierList);
      final PsiModifierList newModifierList = method.getModifierList();
      modifierList.replace(newModifierList);
    }
  }

  private class MissortedModifiersVisitor extends BaseInspectionVisitor {

    private final Comparator<String> modifierComparator =
      new ModifierComparator();

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      super.visitClass(aClass);
      checkForMissortedModifiers(aClass);
    }

    @Override
    public void visitClassInitializer(
      @Nonnull PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      checkForMissortedModifiers(initializer);
    }

    @Override
    public void visitLocalVariable(@Nonnull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      checkForMissortedModifiers(variable);
    }

    @Override
    public void visitParameter(@Nonnull PsiParameter parameter) {
      super.visitParameter(parameter);
      checkForMissortedModifiers(parameter);
    }

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      checkForMissortedModifiers(method);
    }

    @Override
    public void visitField(@Nonnull PsiField field) {
      super.visitField(field);
      checkForMissortedModifiers(field);
    }

    private void checkForMissortedModifiers(
      PsiModifierListOwner listOwner) {
      final PsiModifierList modifierList = listOwner.getModifierList();
      if (modifierList == null) {
        return;
      }
      if (!isModifierListMissorted(modifierList)) {
        return;
      }
      registerError(modifierList);
    }

    private boolean isModifierListMissorted(PsiModifierList modifierList) {
      if (modifierList == null) {
        return false;
      }
      final PsiElement[] children = modifierList.getChildren();
      String currentModifier = null;
      for (final PsiElement child : children) {
        if (child instanceof PsiJavaToken) {
          final String text = child.getText();
          if (modifierComparator.compare(text, currentModifier) < 0) {
            return true;
          }
          currentModifier = text;
        }
        if (child instanceof PsiAnnotation) {
          if (m_requireAnnotationsFirst && currentModifier != null) {
            //things aren't in order, since annotations come first
            return true;
          }
        }
      }
      return false;
    }
  }

  private static class ModifierComparator implements Comparator<String> {

    /**
     * @noinspection StaticCollection
     */
    @NonNls private static final Map<String, Integer> s_modifierOrder =
      new HashMap<String, Integer>(11);

    static {
      s_modifierOrder.put(PsiModifier.PUBLIC, Integer.valueOf(0));
      s_modifierOrder.put(PsiModifier.PROTECTED, Integer.valueOf(1));
      s_modifierOrder.put(PsiModifier.PRIVATE, Integer.valueOf(2));
      s_modifierOrder.put(PsiModifier.ABSTRACT, Integer.valueOf(3));
      s_modifierOrder.put(PsiModifier.STATIC, Integer.valueOf(4));
      s_modifierOrder.put(PsiModifier.FINAL, Integer.valueOf(5));
      s_modifierOrder.put(PsiModifier.TRANSIENT, Integer.valueOf(6));
      s_modifierOrder.put(PsiModifier.VOLATILE, Integer.valueOf(7));
      s_modifierOrder.put(PsiModifier.SYNCHRONIZED, Integer.valueOf(8));
      s_modifierOrder.put(PsiModifier.NATIVE, Integer.valueOf(9));
      s_modifierOrder.put(PsiModifier.STRICTFP, Integer.valueOf(10));
    }

    public int compare(String modifier1, String modifier2) {
      final Integer ordinal1 = s_modifierOrder.get(modifier1);
      if (ordinal1 == null) {
        return 0;
      }
      final Integer ordinal2 = s_modifierOrder.get(modifier2);
      if (ordinal2 == null) {
        return 0;
      }
      return ordinal1.intValue() - ordinal2.intValue();
    }
  }
}