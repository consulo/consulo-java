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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 26, 2002
 * Time: 2:33:58 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperFix;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.codeInsight.generation.PsiMethodMember;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.Result;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.CreateSubclassAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class CreateSubclassAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(CreateSubclassAction.class);
  private LocalizeValue myText = CodeInsightLocalize.intentionImplementAbstractClassFamily();

  private static final String IMPL_SUFFIX = "Impl";

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return myText;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    PsiElement element = file.findElementAt(position);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass instanceof PsiAnonymousClass ||
        psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    if (!isSupportedLanguage(psiClass)) return false;
    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length > 0) {
      boolean hasNonPrivateConstructor = false;
      for (PsiMethod constructor : constructors) {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          hasNonPrivateConstructor = true;
          break;
        }
      }
      if (!hasNonPrivateConstructor) return false;
    }
    PsiElement lBrace = psiClass.getLBrace();
    if (lBrace == null) return false;
    if (element.getTextOffset() >= lBrace.getTextOffset()) return false;

    TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    final TextRange elementTextRange = element.getTextRange();
    if (!declarationRange.contains(elementTextRange)) {
      if (!(element instanceof PsiWhiteSpace) || (declarationRange.getStartOffset() != elementTextRange.getEndOffset() &&
          declarationRange.getEndOffset() != elementTextRange.getStartOffset())) {
        return false;
      }
    }

    myText = getTitle(psiClass);
    return true;
  }

  @RequiredReadAction
  protected boolean isSupportedLanguage(PsiClass aClass) {
    return aClass.getLanguage() == JavaLanguage.INSTANCE;
  }

  protected static LocalizeValue getTitle(PsiClass psiClass) {
    return psiClass.isInterface()
      ? CodeInsightLocalize.intentionImplementAbstractClassInterfaceText()
      : psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
      ? CodeInsightLocalize.intentionImplementAbstractClassDefaultText()
      : CodeInsightLocalize.intentionImplementAbstractClassSubclassText();
  }

  @Override
  public void invoke(@Nonnull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

    LOG.assertTrue(psiClass != null);
    if (psiClass.hasModifierProperty(PsiModifier.PRIVATE) && psiClass.getContainingClass() != null) {
      createInnerClass(psiClass);
      return;
    }
    createTopLevelClass(psiClass);
  }

  public static void createInnerClass(final PsiClass aClass) {
    new WriteCommandAction(aClass.getProject(), getTitle(aClass).get(), getTitle(aClass).get()) {
      @Override
      @RequiredReadAction
      protected void run(Result result) throws Throwable {
        final PsiClass containingClass = aClass.getContainingClass();
        LOG.assertTrue(containingClass != null);

        final PsiTypeParameterList oldTypeParameterList = aClass.getTypeParameterList();
        PsiClass classFromText = JavaPsiFacade.getElementFactory(aClass.getProject()).createClass(aClass.getName() + IMPL_SUFFIX);
        classFromText = (PsiClass) containingClass.addAfter(classFromText, aClass);
        startTemplate(oldTypeParameterList, aClass.getProject(), aClass, classFromText, true);
      }
    }.execute();
  }

  protected void createTopLevelClass(PsiClass psiClass) {
    final CreateClassDialog dlg = chooseSubclassToCreate(psiClass);
    if (dlg != null) {
      createSubclass(psiClass, dlg.getTargetDirectory(), dlg.getClassName());
    }
  }

  @Nullable
  @RequiredReadAction
  public static CreateClassDialog chooseSubclassToCreate(PsiClass psiClass) {
    final PsiDirectory sourceDir = psiClass.getContainingFile().getContainingDirectory();

    final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(sourceDir);
    final CreateClassDialog dialog = new CreateClassDialog(
      psiClass.getProject(),
      getTitle(psiClass),
      psiClass.getName() + IMPL_SUFFIX,
      aPackage != null ? aPackage.getQualifiedName() : "",
      CreateClassKind.CLASS,
      true,
      ModuleUtilCore.findModuleForPsiElement(psiClass)
    ) {
      @Override
      protected PsiDirectory getBaseDir(String packageName) {
        return sourceDir;
      }

      @Override
      protected boolean reportBaseInTestSelectionInSource() {
        return true;
      }
    };
    dialog.show();
    if (!dialog.isOK()) return null;
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    if (targetDirectory == null) return null;
    return dialog;
  }

  @RequiredReadAction
  public static PsiClass createSubclass(final PsiClass psiClass, final PsiDirectory targetDirectory, final String className) {
    final Project project = psiClass.getProject();
    final PsiClass[] targetClass = new PsiClass[1];
    new WriteCommandAction(project, getTitle(psiClass).get(), getTitle(psiClass).get()) {
      @Override
      protected void run(Result result) throws Throwable {
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

        final PsiTypeParameterList oldTypeParameterList = psiClass.getTypeParameterList();

        try {
          targetClass[0] = JavaDirectoryService.getInstance().createClass(targetDirectory, className);
        } catch (final IncorrectOperationException e) {
          Application.get().invokeLater(() -> Messages.showErrorDialog(
            project,
            CodeInsightLocalize.intentionErrorCannotCreateClassMessage(className) + "\n" + e.getLocalizedMessage(),
            CodeInsightLocalize.intentionErrorCannotCreateClassTitle().get()
          ));
          return;
        }
        startTemplate(oldTypeParameterList, project, psiClass, targetClass[0], false);
      }
    }.execute();
    if (targetClass[0] == null) return null;
    if (!Application.get().isUnitTestMode() && !psiClass.hasTypeParameters()) {

      final Editor editor = CodeInsightUtil.positionCursor(project, targetClass[0].getContainingFile(), targetClass[0].getLBrace());
      if (editor == null) return targetClass[0];

      chooseAndImplement(psiClass, project, targetClass[0], editor);
    }
    return targetClass[0];
  }

  @RequiredReadAction
  private static void startTemplate(
    PsiTypeParameterList oldTypeParameterList,
    final Project project,
    final PsiClass psiClass,
    final PsiClass targetClass,
    final boolean includeClassName
  ) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiJavaCodeReferenceElement ref = elementFactory.createClassReferenceElement(psiClass);
    try {
      if (psiClass.isInterface()) {
        ref = (PsiJavaCodeReferenceElement) targetClass.getImplementsList().add(ref);
      } else {
        ref = (PsiJavaCodeReferenceElement) targetClass.getExtendsList().add(ref);
      }
      if (psiClass.hasTypeParameters() || includeClassName) {
        final Editor editor = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
        final TemplateBuilder templateBuilder = editor != null
            ? TemplateBuilderFactory.getInstance().createTemplateBuilder(targetClass) : null;

        if (includeClassName && templateBuilder != null) {
          templateBuilder.replaceElement(targetClass.getNameIdentifier(), targetClass.getName());
        }

        if (oldTypeParameterList != null) {
          for (PsiTypeParameter parameter : oldTypeParameterList.getTypeParameters()) {
            final PsiElement param = ref.getParameterList().add(elementFactory.createTypeElement(elementFactory.createType(parameter)));
            if (templateBuilder != null) {
              templateBuilder.replaceElement(param, param.getText());
            }
          }
        }

        replaceTypeParamsList(targetClass, oldTypeParameterList);
        if (templateBuilder != null) {
          templateBuilder.setEndVariableBefore(ref);
          final Template template = templateBuilder.buildTemplate();
          template.addEndVariable();

          final PsiFile containingFile = targetClass.getContainingFile();

          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

          final TextRange textRange = targetClass.getTextRange();
          final RangeMarker startClassOffset = editor.getDocument().createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
          startClassOffset.setGreedyToLeft(true);
          startClassOffset.setGreedyToRight(true);
          editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
          CreateFromUsageBaseFix.startTemplate(editor, template, project, new TemplateEditingAdapter() {
            @Override
            public void templateFinished(Template template, boolean brokenOff) {
              try {
                LOG.assertTrue(startClassOffset.isValid(), startClassOffset);
                final PsiElement psiElement = containingFile.findElementAt(startClassOffset.getStartOffset());
                final PsiClass aTargetClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
                LOG.assertTrue(aTargetClass != null, psiElement);
                chooseAndImplement(psiClass, project, aTargetClass, editor);
              } finally {
                startClassOffset.dispose();
              }
            }
          }, getTitle(psiClass));
        }
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiElement replaceTypeParamsList(PsiClass psiClass, PsiTypeParameterList oldTypeParameterList) {
    final PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
    assert typeParameterList != null;
    return typeParameterList.replace(oldTypeParameterList);
  }

  @RequiredReadAction
  protected static void chooseAndImplement(PsiClass psiClass, Project project, @Nonnull PsiClass targetClass, Editor editor) {
    boolean hasNonTrivialConstructor = false;
    final PsiMethod[] constructors = psiClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParametersCount() > 0) {
        hasNonTrivialConstructor = true;
        break;
      }
    }
    if (hasNonTrivialConstructor) {
      final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(psiClass, targetClass, PsiSubstitutor.EMPTY);
      final List<PsiMethodMember> baseConstructors = new ArrayList<>();
      for (PsiMethod baseConstr : constructors) {
        if (PsiUtil.isAccessible(baseConstr, targetClass, targetClass)) {
          baseConstructors.add(new PsiMethodMember(baseConstr, substitutor));
        }
      }
      final int offset = editor.getCaretModel().getOffset();
      CreateConstructorMatchingSuperFix.chooseConstructor2Delegate(project, editor,
          substitutor,
          baseConstructors, constructors, targetClass);
      editor.getCaretModel().moveToOffset(offset);
    }

    OverrideImplementUtil.chooseAndImplementMethods(project, editor, targetClass);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
