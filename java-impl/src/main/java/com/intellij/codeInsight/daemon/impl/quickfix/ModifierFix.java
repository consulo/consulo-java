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
package com.intellij.codeInsight.daemon.impl.quickfix;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import consulo.java.JavaQuickFixBundle;

public class ModifierFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(ModifierFix.class);

  @PsiModifier.ModifierConstant private final String myModifier;
  private final boolean myShouldHave;
  private final boolean myShowContainingClass;
  private final String myName;
  private final SmartPsiElementPointer<PsiVariable> myVariable;

  public ModifierFix(PsiModifierList modifierList, @PsiModifier.ModifierConstant @Nonnull String modifier, boolean shouldHave, boolean showContainingClass) {
    super(modifierList);
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    myName = format(null, modifierList);
    myVariable = null;
  }

  public ModifierFix(@Nonnull PsiModifierListOwner owner, @PsiModifier.ModifierConstant @Nonnull String modifier, boolean shouldHave, boolean showContainingClass) {
    super(owner.getModifierList());
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    PsiVariable variable = owner instanceof PsiVariable ? (PsiVariable)owner : null;
    myName = format(variable, owner.getModifierList());

    myVariable = variable == null ? null : SmartPointerManager.getInstance(owner.getProject()).createSmartPsiElementPointer(variable);
  }

  @Nonnull
  @Override
  public String getText() {
    return myName;
  }

  private String format(PsiVariable variable, PsiModifierList modifierList) {
    String name = null;
    PsiElement parent = variable == null ? modifierList == null ? null : modifierList.getParent() : variable;
    if (parent instanceof PsiClass) {
      name = ((PsiClass)parent).getName();
    }
    else {
      int options = PsiFormatUtilBase.SHOW_NAME | (myShowContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
      if (parent instanceof PsiMethod) {
        name = PsiFormatUtil.formatMethod((PsiMethod)parent, PsiSubstitutor.EMPTY, options, 0);
      }
      else if (parent instanceof PsiVariable) {
        name = PsiFormatUtil.formatVariable((PsiVariable)parent, options, PsiSubstitutor.EMPTY);
      }
      else if (parent instanceof PsiClassInitializer) {
        PsiClass containingClass = ((PsiClassInitializer)parent).getContainingClass();
        String className = containingClass instanceof PsiAnonymousClass
                           ? JavaQuickFixBundle.message("anonymous.class.presentation",
                                                    ((PsiAnonymousClass)containingClass).getBaseClassType().getPresentableText())
                           : containingClass != null ? containingClass.getName() : "unknown";
        name = JavaQuickFixBundle.message("class.initializer.presentation", className);
      }
    }

    String modifierText = VisibilityUtil.toPresentableText(myModifier);

    return JavaQuickFixBundle.message(myShouldHave ? "add.modifier.fix" : "remove.modifier.fix", name, modifierText);
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("fix.modifiers.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project,
                             @Nonnull PsiFile file,
                             @Nonnull PsiElement startElement,
                             @Nonnull PsiElement endElement) {
    final PsiModifierList myModifierList = (PsiModifierList)startElement;
    PsiVariable variable = myVariable == null ? null : myVariable.getElement();
    return myModifierList.isValid() &&
           myModifierList.getManager().isInProject(myModifierList) &&
           myModifierList.hasExplicitModifier(myModifier) != myShouldHave &&
           (variable == null || variable.isValid());
  }

  private void changeModifierList (PsiModifierList modifierList) {
    try {
      modifierList.setModifierProperty(myModifier, myShouldHave);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public void invoke(@Nonnull Project project,
                     @Nonnull PsiFile file,
                     @javax.annotation.Nullable Editor editor,
                     @Nonnull PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    final PsiModifierList myModifierList = (PsiModifierList)startElement;
    final PsiVariable variable = myVariable == null ? null : myVariable.getElement();
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myModifierList)) return;
    final List<PsiModifierList> modifierLists = new ArrayList<PsiModifierList>();
    final PsiFile containingFile = myModifierList.getContainingFile();
    final PsiModifierList modifierList;
    if (variable != null && variable.isValid()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            variable.normalizeDeclaration();
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });
      modifierList = variable.getModifierList();
      assert modifierList != null;
    }
    else {
      modifierList = myModifierList;
    }
    PsiElement owner = modifierList.getParent();
    if (owner instanceof PsiMethod) {
      PsiModifierList copy = (PsiModifierList)myModifierList.copy();
      changeModifierList(copy);
      final int accessLevel = PsiUtil.getAccessLevel(copy);

      OverridingMethodsSearch.search((PsiMethod)owner, owner.getResolveScope(), true).forEach(new PsiElementProcessorAdapter<PsiMethod>(new PsiElementProcessor<PsiMethod>() {
          @Override
          public boolean execute(@Nonnull PsiMethod inheritor) {
            PsiModifierList list = inheritor.getModifierList();
            if (inheritor.getManager().isInProject(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
              modifierLists.add(list);
            }
            return true;
          }
        }));
    }

    if (!FileModificationService.getInstance().prepareFileForWrite(containingFile)) return;

    if (!modifierLists.isEmpty()) {
      if (Messages.showYesNoDialog(project,
                                   JavaQuickFixBundle.message("change.inheritors.visibility.warning.text"),
                                   JavaQuickFixBundle.message("change.inheritors.visibility.warning.title"),
                                   Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (!FileModificationService.getInstance().preparePsiElementsForWrite(modifierLists)) {
              return;
            }

            for (final PsiModifierList modifierList : modifierLists) {
              changeModifierList(modifierList);
            }
          }
        });
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        changeModifierList(modifierList);
        UndoUtil.markPsiFileForUndo(containingFile);
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
