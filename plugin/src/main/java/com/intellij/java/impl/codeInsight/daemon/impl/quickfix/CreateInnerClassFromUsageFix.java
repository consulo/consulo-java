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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.popup.JBPopup;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateClassKind.CLASS;
import static com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateClassKind.INTERFACE;

/**
 * @author ven
 */
public class CreateInnerClassFromUsageFix extends CreateClassFromUsageBaseFix implements SyntheticIntentionAction {

  public CreateInnerClassFromUsageFix(final PsiJavaCodeReferenceElement refElement, final CreateClassKind kind) {
    super(kind, refElement);
  }

  @Override
  public String getText(String varName) {
    return JavaQuickFixBundle.message("create.inner.class.from.usage.text", StringUtil.capitalize(myKind.getDescription()), varName);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement element = getRefElement();
    assert element != null;
    final String superClassName = getSuperClassName(element);
    PsiClass[] targets = getPossibleTargets(element);
    LOG.assertTrue(targets.length > 0);
    if (targets.length == 1) {
      doInvoke(targets[0], superClassName);
    }
    else {
      chooseTargetClass(targets, editor, superClassName);
    }
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
    return super.isAvailable(project, editor, file) && getPossibleTargets(getRefElement()).length > 0;
  }

  @Nonnull
  private static PsiClass[] getPossibleTargets(final PsiJavaCodeReferenceElement element) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    PsiElement run = element;
    PsiMember contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);

    while (contextMember != null) {
      if (contextMember instanceof PsiClass && !(contextMember instanceof PsiTypeParameter)) {
        if (!isUsedInExtends(run, (PsiClass)contextMember)) {
          result.add((PsiClass)contextMember);
        }
      }
      run = contextMember;
      contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);
    }

    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  private static boolean isUsedInExtends(PsiElement element, PsiClass psiClass) {
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    final PsiReferenceList implementsList = psiClass.getImplementsList();
    if (extendsList != null && PsiTreeUtil.isAncestor(extendsList, element, false)) {
      return true;
    }
      
    if (implementsList != null && PsiTreeUtil.isAncestor(implementsList, element, false)) {
      return true;
    }
    return false;
  }

  private void chooseTargetClass(PsiClass[] classes, final Editor editor, final String superClassName) {
    final Project project = classes[0].getProject();

    final JList list = new JBList(classes);
    PsiElementListCellRenderer renderer = new PsiClassListCellRenderer();
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    renderer.installSpeedSearch(builder);

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        final PsiClass aClass = (PsiClass)list.getSelectedValue();
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                try {
                  doInvoke(aClass, superClassName);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            });

          }
        }, getText(), null);
      }
    };

    JBPopup popup = builder.
        setTitle(JavaQuickFixBundle.message("target.class.chooser.title")).
        setItemChoosenCallback(runnable).
        createPopup();

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
  }

  private void doInvoke(final PsiClass aClass, final String superClassName) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement ref = getRefElement();
    assert ref != null;
    String refName = ref.getReferenceName();
    LOG.assertTrue(refName != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    PsiClass created = myKind == INTERFACE
                      ? elementFactory.createInterface(refName)
                      : myKind == CLASS ? elementFactory.createClass(refName) : elementFactory.createEnum(refName);
    final PsiModifierList modifierList = created.getModifierList();
    LOG.assertTrue(modifierList != null);
    if (aClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    } else {
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    }
    if (RefactoringUtil.isInStaticContext(ref, aClass)) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }
    if (superClassName != null) {
      PsiJavaCodeReferenceElement superClass =
        elementFactory.createReferenceElementByFQClassName(superClassName, created.getResolveScope());
      final PsiReferenceList extendsList = created.getExtendsList();
      LOG.assertTrue(extendsList != null);
      extendsList.add(superClass);
    }
    CreateFromUsageBaseFix.setupGenericParameters(created, ref);

    created = (PsiClass)aClass.add(created);
    ref.bindToElement(created);
  }
}
