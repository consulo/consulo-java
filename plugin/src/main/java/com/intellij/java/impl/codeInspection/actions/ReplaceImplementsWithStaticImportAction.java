/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.actions;

import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.*;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceImplementsWithStaticImportAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class ReplaceImplementsWithStaticImportAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(ReplaceImplementsWithStaticImportAction.class);
  private static final String FIND_CONSTANT_FIELD_USAGES = "Find constant field usages...";

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return LocalizeValue.localizeTODO("Replace Implements with Static Import");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiClass) {
        return isEmptyClass(project, (PsiClass)parent) && DirectClassInheritorsSearch.search((PsiClass)parent).findFirst() != null;
      }
    }
    PsiReference psiReference = TargetElementUtil.findReference(editor);
    if (psiReference == null) return false;

    PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
    if (referenceList == null) return false;

    PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return false;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return false;

    PsiElement target = psiReference.resolve();
    if (target == null || !(target instanceof PsiClass)) return false;

    return isEmptyClass(project, (PsiClass)target);
  }

  private static boolean isEmptyClass(Project project, PsiClass targetClass) {
    if (!targetClass.isInterface()) {
      return false;
    }
    PsiReferenceList extendsList = targetClass.getExtendsList();
    if (extendsList != null && extendsList.getReferencedTypes().length > 0) {
      List<PsiMethod> methods = new ArrayList<PsiMethod>(Arrays.asList(targetClass.getAllMethods()));
      PsiClass objectClass =
        JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project));
      if (objectClass == null) return false;
      methods.removeAll(Arrays.asList(objectClass.getMethods()));
      if (methods.size() > 0) return false;
    }
    else if (targetClass.getMethods().length > 0) {
      return false;
    }
    return targetClass.getAllFields().length > 0;
  }

  @Override
  public void invoke(@Nonnull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(file)) return;

    int offset = editor.getCaretModel().getOffset();
    PsiReference psiReference = file.findReferenceAt(offset);
    if (psiReference != null) {
      final PsiElement element = psiReference.getElement();

      final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      LOG.assertTrue(psiClass != null);

      PsiElement target = psiReference.resolve();
      LOG.assertTrue(target instanceof PsiClass);

      final PsiClass targetClass = (PsiClass)target;
      new WriteCommandAction(project, getText().get()) {
        @Override
        protected void run(Result result) throws Throwable {
          for (PsiField constField : targetClass.getAllFields()) {
            String fieldName = constField.getName();
            PsiClass containingClass = constField.getContainingClass();
            for (PsiReference ref : ReferencesSearch.search(constField)) {
              PsiElement psiElement = ref.getElement();
              if (ref instanceof PsiReferenceExpression) {
                PsiElement qualifier = ((PsiReferenceExpression)ref).getQualifier();
                if (qualifier != null) {
                  if (qualifier instanceof PsiReferenceExpression) {
                    PsiElement resolved = ((PsiReferenceExpression)qualifier).resolve();
                    if (resolved instanceof PsiClass && !InheritanceUtil.isInheritorOrSelf(psiClass, (PsiClass)resolved, true)) {
                      continue;
                    }
                  }
                  qualifier.putCopyableUserData(ChangeContextUtil.CAN_REMOVE_QUALIFIER_KEY,
                                                ChangeContextUtil.canRemoveQualifier((PsiReferenceExpression)ref));
                }
              }
              bindReference(psiElement.getContainingFile(), constField, containingClass, fieldName, ref, project);
            }
          }
          element.delete();
          JavaCodeStyleManager.getInstance(project).optimizeImports(file);
        }
      }.execute();
    }
    else {
      PsiElement identifier = file.findElementAt(offset);
      LOG.assertTrue(identifier instanceof PsiIdentifier);
      PsiElement element = identifier.getParent();
      LOG.assertTrue(element instanceof PsiClass);
      final PsiClass targetClass = (PsiClass)element;
      final Map<PsiFile, Map<PsiField, Set<PsiReference>>> refs = new HashMap<PsiFile, Map<PsiField, Set<PsiReference>>>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          for (PsiField field : targetClass.getAllFields()) {
            PsiClass containingClass = field.getContainingClass();
            for (PsiReference reference : ReferencesSearch.search(field)) {
              if (reference == null) {
                continue;
              }
              PsiElement refElement = reference.getElement();
              if (encodeQualifier(containingClass, reference, targetClass)) continue;
              PsiFile psiFile = refElement.getContainingFile();
              if (psiFile instanceof PsiJavaFile) {
                Map<PsiField, Set<PsiReference>> references = refs.get(psiFile);
                if (references == null) {
                  references = new HashMap<PsiField, Set<PsiReference>>();
                  refs.put(psiFile, references);
                }
                Set<PsiReference> fieldsRefs = references.get(field);
                if (fieldsRefs == null) {
                  fieldsRefs = new HashSet<PsiReference>();
                  references.put(field, fieldsRefs);
                }
                fieldsRefs.add(reference);
              }
            }
          }
        }
      }, FIND_CONSTANT_FIELD_USAGES, true, project)) {
        return;
      }

      final Set<PsiJavaCodeReferenceElement> refs2Unimplement = new HashSet<PsiJavaCodeReferenceElement>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          for (PsiClass psiClass : DirectClassInheritorsSearch.search(targetClass)) {
            PsiFile containingFile = psiClass.getContainingFile();
            if (!refs.containsKey(containingFile)) {
              refs.put(containingFile, new HashMap<PsiField, Set<PsiReference>>());
            }
            if (collectExtendsImplements(targetClass, psiClass.getExtendsList(), refs2Unimplement)) continue;
            collectExtendsImplements(targetClass, psiClass.getImplementsList(), refs2Unimplement);
          }
        }
      }, "Find references in implement/extends lists...", true, project)) {
        return;
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {

          for (PsiFile psiFile : refs.keySet()) {
            Map<PsiField, Set<PsiReference>> map = refs.get(psiFile);
            for (PsiField psiField : map.keySet()) {
              PsiClass containingClass = psiField.getContainingClass();
              String fieldName = psiField.getName();
              for (PsiReference reference : map.get(psiField)) {
                bindReference(psiFile, psiField, containingClass, fieldName, reference, project);
              }
            }
          }

          for (PsiJavaCodeReferenceElement referenceElement : refs2Unimplement) {
            referenceElement.delete();
          }
        }
      });

      final Set<SmartPsiElementPointer<PsiImportStatementBase>> redundant = new HashSet<SmartPsiElementPointer<PsiImportStatementBase>>();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable(){
        @Override
        public void run() {
          for (PsiFile psiFile : refs.keySet()) {
            Collection<PsiImportStatementBase> red = codeStyleManager.findRedundantImports((PsiJavaFile)psiFile);
            if (red != null) {
              for (PsiImportStatementBase statementBase : red) {
                redundant.add(pointerManager.createSmartPsiElementPointer(statementBase));
              }
            }
          }
        }
      }, "Collect redundant imports...", true, project)) return;
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          for (SmartPsiElementPointer<PsiImportStatementBase> pointer : redundant) {
            PsiImportStatementBase statementBase = pointer.getElement();
            if (statementBase != null) statementBase.delete();
          }
        }
      });
    }
  }

  private static boolean encodeQualifier(PsiClass containingClass, PsiReference reference, PsiClass targetClass) {
    if (reference instanceof PsiReferenceExpression) {
      PsiElement qualifier = ((PsiReferenceExpression)reference).getQualifier();
      if (qualifier != null) {
        if (qualifier instanceof PsiReferenceExpression) {
          PsiElement resolved = ((PsiReferenceExpression)qualifier).resolve();
          if (resolved == containingClass || resolved instanceof PsiClass && InheritanceUtil.isInheritorOrSelf(targetClass, (PsiClass)resolved, true)) {
            return true;
          }
        }
        qualifier.putCopyableUserData(ChangeContextUtil.CAN_REMOVE_QUALIFIER_KEY,
                                      ChangeContextUtil.canRemoveQualifier((PsiReferenceExpression)reference));
      }
    }
    return false;
  }

  private static void bindReference(PsiFile psiFile,
                                    PsiField psiField,
                                    PsiClass containingClass,
                                    String fieldName,
                                    PsiReference reference,
                                    Project project) {
    if (reference instanceof PsiReferenceExpression) {
      PsiReferenceExpressionImpl.bindToElementViaStaticImport(containingClass, fieldName, ((PsiJavaFile)psiFile).getImportList());
      PsiElement qualifier = ((PsiReferenceExpression)reference).getQualifier();
      if (qualifier != null) {
        Boolean canRemoveQualifier = qualifier.getCopyableUserData(ChangeContextUtil.CAN_REMOVE_QUALIFIER_KEY);
        if (canRemoveQualifier != null && canRemoveQualifier.booleanValue()) {
          qualifier.delete();
        } else {
          PsiJavaCodeReferenceElement classReferenceElement =
            JavaPsiFacade.getElementFactory(project).createReferenceExpression(containingClass);
          qualifier.replace(classReferenceElement);
        }
      }
    } else if (reference.getElement() instanceof PsiDocMethodOrFieldRef){
      reference.bindToElement(psiField);    //todo refs through inheritors
    }
  }

  private static boolean collectExtendsImplements(PsiClass targetClass,
                                                  PsiReferenceList referenceList,
                                                  Set<PsiJavaCodeReferenceElement> refs) {
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
        if (referenceElement.resolve() == targetClass) {
          refs.add(referenceElement);
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
