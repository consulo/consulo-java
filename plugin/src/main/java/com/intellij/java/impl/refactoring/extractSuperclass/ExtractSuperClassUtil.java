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
package com.intellij.java.impl.refactoring.extractSuperclass;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.MultiMap;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author dsl
 */
public class ExtractSuperClassUtil {
  private static final Logger LOG = Logger.getInstance(ExtractSuperClassUtil.class);
  private ExtractSuperClassUtil() {}

  public static PsiClass extractSuperClass(final Project project,
                                           final PsiDirectory targetDirectory,
                                           final String superclassName,
                                           final PsiClass subclass,
                                           final MemberInfo[] selectedMemberInfos,
                                           final DocCommentPolicy javaDocPolicy)
    throws IncorrectOperationException {
    PsiClass superclass = JavaDirectoryService.getInstance().createClass(targetDirectory, superclassName);
    final PsiModifierList superClassModifierList = superclass.getModifierList();
    assert superClassModifierList != null;
    superClassModifierList.setModifierProperty(PsiModifier.FINAL, false);
    final PsiReferenceList subClassExtends = subclass.getExtendsList();
    assert subClassExtends != null: subclass;
    copyPsiReferenceList(subClassExtends, superclass.getExtendsList());

    // create constructors if neccesary
    PsiMethod[] constructors = getCalledBaseConstructors(subclass);
    if (constructors.length > 0) {
      createConstructorsByPattern(project, superclass, constructors);
    }

    // clear original class' "extends" list
    clearPsiReferenceList(subclass.getExtendsList());

    // make original class extend extracted superclass
    PsiJavaCodeReferenceElement ref = createExtendingReference(superclass, subclass, selectedMemberInfos); 
    subclass.getExtendsList().add(ref);

    PullUpProcessor pullUpHelper = new PullUpProcessor(subclass, superclass, selectedMemberInfos,
                                                 javaDocPolicy
    );

    pullUpHelper.moveMembersToBase();
    pullUpHelper.moveFieldInitializations();

    Collection<MethodSignature> toImplement = OverrideImplementUtil.getMethodSignaturesToImplement(superclass);
    if (!toImplement.isEmpty()) {
      superClassModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
    }
    return superclass;
  }

  private static void createConstructorsByPattern(Project project, final PsiClass superclass, PsiMethod[] patternConstructors) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    for (PsiMethod baseConstructor : patternConstructors) {
      PsiMethod constructor = (PsiMethod)superclass.add(factory.createConstructor());
      PsiParameterList paramList = constructor.getParameterList();
      PsiParameter[] baseParams = baseConstructor.getParameterList().getParameters();
      @NonNls StringBuilder superCallText = new StringBuilder();
      superCallText.append("super(");
      final PsiClass baseClass = baseConstructor.getContainingClass();
      LOG.assertTrue(baseClass != null);
      final PsiSubstitutor classSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, superclass, PsiSubstitutor.EMPTY);
      for (int i = 0; i < baseParams.length; i++) {
        final PsiParameter baseParam = baseParams[i];
        final PsiParameter newParam = (PsiParameter)paramList.add(factory.createParameter(baseParam.getName(), classSubstitutor.substitute(baseParam.getType())));
        if (i > 0) {
          superCallText.append(",");
        }
        superCallText.append(newParam.getName());
      }
      superCallText.append(");");
      PsiStatement statement = factory.createStatementFromText(superCallText.toString(), null);
      statement = (PsiStatement)styleManager.reformat(statement);
      final PsiCodeBlock body = constructor.getBody();
      assert body != null;
      body.add(statement);
      constructor.getThrowsList().replace(baseConstructor.getThrowsList());
    }
  }

  private static PsiMethod[] getCalledBaseConstructors(final PsiClass subclass) {
    Set<PsiMethod> baseConstructors = new HashSet<PsiMethod>();
    PsiMethod[] constructors = subclass.getConstructors();
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            PsiReferenceExpression calledMethod = ((PsiMethodCallExpression)expression).getMethodExpression();
            @NonNls String text = calledMethod.getText();
            if ("super".equals(text)) {
              PsiMethod baseConstructor = (PsiMethod)calledMethod.resolve();
              if (baseConstructor != null) {
                baseConstructors.add(baseConstructor);
              }
            }
          }
        }
      }
    }
    return baseConstructors.toArray(new PsiMethod[baseConstructors.size()]);
  }

  private static void clearPsiReferenceList(PsiReferenceList refList) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      ref.delete();
    }
  }

  private static void copyPsiReferenceList(PsiReferenceList sourceList, PsiReferenceList destinationList) throws IncorrectOperationException {
    clearPsiReferenceList(destinationList);
    PsiJavaCodeReferenceElement[] refs = sourceList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      destinationList.add(ref);
    }
  }

  public static PsiJavaCodeReferenceElement createExtendingReference(final PsiClass superClass,
                                                                      final PsiClass derivedClass,
                                                                      final MemberInfo[] selectedMembers) throws IncorrectOperationException {
    final PsiManager manager = derivedClass.getManager();
    Set<PsiElement> movedElements = new HashSet<PsiElement>();
    for (final MemberInfo info : selectedMembers) {
      movedElements.add(info.getMember());
    }
    final PsiTypeParameterList typeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(null,
                                                                                                                 new Condition<PsiTypeParameter>() {
                                                                                                                   @Override
                                                                                                                   public boolean value(
                                                                                                                     PsiTypeParameter parameter) {
                                                                                                                     return
                                                                                                                       findTypeParameterInDerived(
                                                                                                                         derivedClass,
                                                                                                                         parameter
                                                                                                                           .getName()) !=
                                                                                                                       null;
                                                                                                                   }
                                                                                                                 }, PsiUtilBase
      .toPsiElementArray(movedElements));
    final PsiTypeParameterList originalTypeParameterList = superClass.getTypeParameterList();
    assert originalTypeParameterList != null;
    final PsiTypeParameterList newList = typeParameterList != null ? (PsiTypeParameterList)originalTypeParameterList.replace(typeParameterList) : originalTypeParameterList;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
    for (final PsiTypeParameter parameter : newList.getTypeParameters()) {
      final PsiTypeParameter parameterInDerived = findTypeParameterInDerived(derivedClass, parameter.getName());
      if (parameterInDerived != null) {
        substitutionMap.put(parameter, factory.createType(parameterInDerived));
      }
    }

    final PsiClassType type = factory.createType(superClass, factory.createSubstitutor(substitutionMap));
    return factory.createReferenceElementByType(type);
  }

  @Nullable
  public static PsiTypeParameter findTypeParameterInDerived(final PsiClass aClass, final String name) {
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
      if (name.equals(typeParameter.getName())) return typeParameter;
    }

    return null;
  }

  public static void checkSuperAccessible(PsiDirectory targetDirectory, MultiMap<PsiElement, String> conflicts, final PsiClass subclass) {
    final VirtualFile virtualFile = subclass.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      final boolean inTestSourceContent = ProjectRootManager.getInstance(subclass.getProject()).getFileIndex().isInTestSourceContent(virtualFile);
      final Module module = ModuleUtil.findModuleForFile(virtualFile, subclass.getProject());
      if (targetDirectory != null &&
          module != null &&
          !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(targetDirectory.getVirtualFile())) {
        conflicts.putValue(subclass, "Superclass won't be accessible in subclass");
      }
    }
  }

  public static boolean showConflicts(DialogWrapper dialog, MultiMap<PsiElement, String> conflicts, final Project project) {
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
      conflictsDialog.show();
      final boolean ok = conflictsDialog.isOK();
      if (!ok && conflictsDialog.isShowConflicts()) dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      return ok;
    }
    return true;
  }
}
