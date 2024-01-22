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
package com.intellij.java.impl.codeInsight;

import com.intellij.java.impl.codeInspection.inferNullity.InferNullityAnnotationsAction;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.InferredAnnotationsManager;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.util.Collections;

/**
 * @author peter
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MakeInferredAnnotationExplicit", fileExtensions = "java", categories = {"Java", "Annotations"})
public class MakeInferredAnnotationExplicit extends BaseIntentionAction {

  public MakeInferredAnnotationExplicit() {
    setText("Make Inferred Annotations Explicit");
  }

  @Override
  public boolean isAvailable(@jakarta.annotation.Nonnull final Project project, Editor editor, PsiFile file) {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiModifierListOwner owner = ExternalAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    if (owner != null && owner.getLanguage().isKindOf(JavaLanguage.INSTANCE) && isWritable(owner) && ModuleUtilCore.findModuleForPsiElement(
      file) != null && PsiUtil.getLanguageLevel(file)
                              .isAtLeast(LanguageLevel.JDK_1_5)) {
      final PsiAnnotation[] annotations = InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner);
      if (annotations.length > 0) {
        final String annos = StringUtil.join(annotations, annotation ->
        {
          final PsiJavaCodeReferenceElement nameRef = correctAnnotation(annotation).getNameReferenceElement();
          final String name = nameRef != null ? nameRef.getReferenceName() : annotation.getQualifiedName();
          return "@" + name + annotation.getParameterList().getText();
        }, " ");
        setText("Insert '" + annos + "'");
        return true;
      }
    }

    return false;
  }

  private static boolean isWritable(PsiModifierListOwner owner) {
    if (owner instanceof PsiCompiledElement) {
      return false;
    }

    VirtualFile vFile = PsiUtilCore.getVirtualFile(owner);
    return vFile != null && vFile.isInLocalFileSystem();
  }

  @Override
  public void invoke(@Nonnull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiModifierListOwner owner = ExternalAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    assert owner != null;
    final PsiModifierList modifierList = owner.getModifierList();
    assert modifierList != null;
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;

    if (!FileModificationService.getInstance().preparePsiElementForWrite(owner)) {
      return;
    }

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (PsiAnnotation inferred : InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner)) {
      final PsiAnnotation toInsert = correctAnnotation(inferred);
      final String qname = toInsert.getQualifiedName();
      assert qname != null;
      if (facade.findClass(qname, file.getResolveScope()) == null && !InferNullityAnnotationsAction.addAnnotationsDependency(project,
                                                                                                                             Collections.singleton(
                                                                                                                               module),
                                                                                                                             qname,
                                                                                                                             getText())) {
        return;
      }

      WriteCommandAction.runWriteCommandAction(project,
                                               () -> DumbService.getInstance(project)
                                                                .withAlternativeResolveEnabled(() -> JavaCodeStyleManager.getInstance(
                                                                  project)
                                                                                                                         .shortenClassReferences(
                                                                                                                           modifierList.addAfter(
                                                                                                                             toInsert,
                                                                                                                             null))));
    }


  }

  @Nonnull
  private static PsiAnnotation correctAnnotation(@jakarta.annotation.Nonnull PsiAnnotation annotation) {
    Project project = annotation.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    NullableNotNullManager nnnm = NullableNotNullManager.getInstance(project);
    if (AnnotationUtil.NULLABLE.equals(annotation.getQualifiedName()) && facade.findClass(nnnm.getDefaultNullable(), allScope) != null) {
      return facade.getElementFactory().createAnnotationFromText("@" + nnnm.getDefaultNullable(), null);
    }

    if (AnnotationUtil.NOT_NULL.equals(annotation.getQualifiedName()) && facade.findClass(nnnm.getDefaultNotNull(), allScope) != null) {
      return facade.getElementFactory().createAnnotationFromText("@" + nnnm.getDefaultNotNull(), null);
    }
    return annotation;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
