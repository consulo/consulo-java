
/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.encapsulateFields;

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

import java.util.*;

public class EncapsulateFieldsProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(EncapsulateFieldsProcessor.class);

  private PsiClass myClass;
  @Nonnull
  private final EncapsulateFieldsDescriptor myDescriptor;
  private final FieldDescriptor[] myFieldDescriptors;

  private HashMap<String,PsiMethod> myNameToGetter;
  private HashMap<String,PsiMethod> myNameToSetter;

  public EncapsulateFieldsProcessor(Project project, @Nonnull EncapsulateFieldsDescriptor descriptor) {
    super(project);
    myDescriptor = descriptor;
    myFieldDescriptors = descriptor.getSelectedFields();
    myClass = descriptor.getTargetClass();
  }

  public static void setNewFieldVisibility(PsiField field, EncapsulateFieldsDescriptor descriptor) {
    try {
      if (descriptor.getFieldsVisibility() != null) {
        field.normalizeDeclaration();
        PsiUtil.setModifierProperty(field, descriptor.getFieldsVisibility(), true);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nonnull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    FieldDescriptor[] fields = new FieldDescriptor[myFieldDescriptors.length];
    System.arraycopy(myFieldDescriptors, 0, fields, 0, myFieldDescriptors.length);
    return new EncapsulateFieldsViewDescriptor(fields);
  }

  protected String getCommandName() {
    return RefactoringLocalize.encapsulateFieldsCommandName(DescriptiveNameUtil.getDescriptiveName(myClass)).get();
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    checkExistingMethods(conflicts, true);
    checkExistingMethods(conflicts, false);
    final Collection<PsiClass> classes = ClassInheritorsSearch.search(myClass).findAll();
    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      final Set<PsiMethod> setters = new HashSet<PsiMethod>();
      final Set<PsiMethod> getters = new HashSet<PsiMethod>();

      for (PsiClass aClass : classes) {
        final PsiMethod getterOverrider =
          myDescriptor.isToEncapsulateGet() ? aClass.findMethodBySignature(fieldDescriptor.getGetterPrototype(), false) : null;
        if (getterOverrider != null) {
          getters.add(getterOverrider);
        }
        final PsiMethod setterOverrider =
          myDescriptor.isToEncapsulateSet() ? aClass.findMethodBySignature(fieldDescriptor.getSetterPrototype(), false) : null;
        if (setterOverrider != null) {
          setters.add(setterOverrider);
        }
      }
      if (!getters.isEmpty() || !setters.isEmpty()) {
        final PsiField field = fieldDescriptor.getField();
        for (PsiReference reference : ReferencesSearch.search(field)) {
          final PsiElement place = reference.getElement();
          LOG.assertTrue(place instanceof PsiReferenceExpression);
          final PsiExpression qualifierExpression = ((PsiReferenceExpression)place).getQualifierExpression();
          final PsiClass ancestor;
          if (qualifierExpression == null) {
            ancestor = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
          }
          else {
            ancestor = PsiUtil.resolveClassInType(qualifierExpression.getType());
          }

          final boolean isGetter = !PsiUtil.isAccessedForWriting((PsiExpression)place);
          for (PsiMethod overridden : isGetter ? getters : setters) {
            if (InheritanceUtil.isInheritorOrSelf(myClass, ancestor, true)) {
              conflicts.putValue(overridden, "There is already a " +
                                             RefactoringUIUtil.getDescription(overridden, true) +
                                             " which would hide generated " +
                                             (isGetter ? "getter" : "setter") + " for " + place.getText());
              break;
            }
          }
        }
      }
    }
    return showConflicts(conflicts, refUsages.get());
  }

  private void checkExistingMethods(MultiMap<PsiElement, String> conflicts, boolean isGetter) {
    if (isGetter) {
      if (!myDescriptor.isToEncapsulateGet()) return;
    }
    else {
      if (!myDescriptor.isToEncapsulateSet()) return;
    }

    for (FieldDescriptor descriptor : myFieldDescriptors) {
      PsiMethod prototype = isGetter
                            ? descriptor.getGetterPrototype()
                            : descriptor.getSetterPrototype();

      final PsiType prototypeReturnType = prototype.getReturnType();
      PsiMethod existing = myClass.findMethodBySignature(prototype, true);
      if (existing != null) {
        final PsiType returnType = existing.getReturnType();
        if (!RefactoringUtil.equivalentTypes(prototypeReturnType, returnType, myClass.getManager())) {
          final String descr = PsiFormatUtil.formatMethod(existing,
                                                          PsiSubstitutor.EMPTY,
                                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE,
                                                          PsiFormatUtilBase.SHOW_TYPE
          );
          LocalizeValue message = isGetter
            ? RefactoringLocalize.encapsulateFieldsGetterExists(
              CommonRefactoringUtil.htmlEmphasize(descr),
              CommonRefactoringUtil.htmlEmphasize(prototype.getName())
            )
            : RefactoringLocalize.encapsulateFieldsSetterExists(
              CommonRefactoringUtil.htmlEmphasize(descr),
              CommonRefactoringUtil.htmlEmphasize(prototype.getName())
            );
          conflicts.putValue(existing, message.get());
        }
      } else {
        PsiClass containingClass = myClass.getContainingClass();
        while (containingClass != null && existing == null) {
          existing = containingClass.findMethodBySignature(prototype, true);
          if (existing != null) {
            for (PsiReference reference : ReferencesSearch.search(existing)) {
              final PsiElement place = reference.getElement();
              LOG.assertTrue(place instanceof PsiReferenceExpression);
              final PsiExpression qualifierExpression = ((PsiReferenceExpression)place).getQualifierExpression();
              final PsiClass inheritor;
              if (qualifierExpression == null) {
                inheritor = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
              } else {
                inheritor = PsiUtil.resolveClassInType(qualifierExpression.getType());
              }

              if (InheritanceUtil.isInheritorOrSelf(inheritor, myClass, true)) {
                conflicts.putValue(existing, "There is already a " + RefactoringUIUtil.getDescription(existing, true) + " which would be hidden by generated " + (isGetter ? "getter" : "setter"));
                break;
              }
            }
          }
          containingClass = containingClass.getContainingClass();
        }
      }
    }
  }

  @Nonnull
  protected UsageInfo[] findUsages() {
    ArrayList<EncapsulateFieldUsageInfo> array = ContainerUtil.newArrayList();
    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      for (final PsiReference reference : ReferencesSearch.search(fieldDescriptor.getField())) {
        final PsiElement element = reference.getElement();
        if (element == null) continue;

        final EncapsulateFieldHelper helper = EncapsulateFieldHelper.getHelper(element.getLanguage());
        EncapsulateFieldUsageInfo usageInfo = helper.createUsage(myDescriptor, fieldDescriptor, reference);
        if (usageInfo != null) {
          array.add(usageInfo);
        }
      }
    }
    EncapsulateFieldUsageInfo[] usageInfos = array.toArray(new EncapsulateFieldUsageInfo[array.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myFieldDescriptors.length);

    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];

      LOG.assertTrue(element instanceof PsiField);

      myFieldDescriptors[idx].refreshField((PsiField)element);
    }

    myClass = myFieldDescriptors[0].getField().getContainingClass();
  }

  protected void performRefactoring(UsageInfo[] usages) {
    updateFieldVisibility();
    generateAccessors();
    processUsagesPerFile(usages);
  }

  private void updateFieldVisibility() {
    if (myDescriptor.getFieldsVisibility() == null) return;

    for (FieldDescriptor descriptor : myFieldDescriptors) {
      setNewFieldVisibility(descriptor.getField(), myDescriptor);
    }
  }

  private void generateAccessors() {
    // generate accessors
    myNameToGetter = new HashMap<String, PsiMethod>();
    myNameToSetter = new HashMap<String, PsiMethod>();

    for (FieldDescriptor fieldDescriptor : myFieldDescriptors) {
      final DocCommentPolicy<PsiDocComment> commentPolicy = new DocCommentPolicy<PsiDocComment>(myDescriptor.getJavadocPolicy());

      PsiField field = fieldDescriptor.getField();
      final PsiDocComment docComment = field.getDocComment();
      if (myDescriptor.isToEncapsulateGet()) {
        final PsiMethod prototype = fieldDescriptor.getGetterPrototype();
        assert prototype != null;
        final PsiMethod getter = addOrChangeAccessor(prototype, myNameToGetter);
        if (docComment != null) {
          final PsiDocComment getterJavadoc = (PsiDocComment)getter.addBefore(docComment, getter.getFirstChild());
          commentPolicy.processNewJavaDoc(getterJavadoc);
        }
      }
      if (myDescriptor.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL)) {
        PsiMethod prototype = fieldDescriptor.getSetterPrototype();
        assert prototype != null;
        addOrChangeAccessor(prototype, myNameToSetter);
      }

      if (docComment != null) {
        commentPolicy.processOldJavaDoc(docComment);
      }
    }
  }

  private void processUsagesPerFile(UsageInfo[] usages) {
    Map<PsiFile, List<EncapsulateFieldUsageInfo>> usagesInFiles = new HashMap<PsiFile, List<EncapsulateFieldUsageInfo>>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      final PsiFile file = element.getContainingFile();
      List<EncapsulateFieldUsageInfo> usagesInFile = usagesInFiles.get(file);
      if (usagesInFile == null) {
        usagesInFile = new ArrayList<EncapsulateFieldUsageInfo>();
        usagesInFiles.put(file, usagesInFile);
      }
      usagesInFile.add(((EncapsulateFieldUsageInfo)usage));
    }

    for (List<EncapsulateFieldUsageInfo> usageInfos : usagesInFiles.values()) {
      //this is to avoid elements to become invalid as a result of processUsage
      final EncapsulateFieldUsageInfo[] infos = usageInfos.toArray(new EncapsulateFieldUsageInfo[usageInfos.size()]);
      CommonRefactoringUtil.sortDepthFirstRightLeftOrder(infos);

      for (EncapsulateFieldUsageInfo info : infos) {
        EncapsulateFieldHelper helper = EncapsulateFieldHelper.getHelper(info.getElement().getLanguage());
        helper.processUsage(info,
                            myDescriptor,
                            myNameToSetter.get(info.getFieldDescriptor().getSetterName()),
                            myNameToGetter.get(info.getFieldDescriptor().getGetterName())
        );
      }
    }
  }

  private PsiMethod addOrChangeAccessor(PsiMethod prototype, HashMap<String,PsiMethod> nameToAncestor) {
    PsiMethod existing = myClass.findMethodBySignature(prototype, false);
    PsiMethod result = existing;
    try{
      if (existing == null){
        PsiUtil.setModifierProperty(prototype, myDescriptor.getAccessorsVisibility(), true);
        result = (PsiMethod) myClass.add(prototype);
      }
      else{
        //TODO : change visibility
      }
      nameToAncestor.put(prototype.getName(), result);
      return result;
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    return null;
  }
}
