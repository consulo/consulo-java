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
package com.intellij.java.impl.refactoring.move.moveInner;

import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.fileEditor.FileEditorManager;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.function.Function;

/**
 * created at Sep 24, 2001
 *
 * @author Jeka
 */
public class MoveInnerProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(MoveInnerProcessor.class);

  private MoveCallback myMoveCallback;

  private PsiClass myInnerClass;
  private PsiClass myOuterClass;
  private PsiElement myTargetContainer;
  private String myParameterNameOuterClass;
  private String myFieldNameOuterClass;
  private String myDescriptiveName = "";
  private String myNewClassName;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private NonCodeUsageInfo[] myNonCodeUsages;

  public MoveInnerProcessor(Project project, MoveCallback moveCallback) {
    super(project);
    myMoveCallback = moveCallback;
  }

  public MoveInnerProcessor(Project project,
                            PsiClass innerClass,
                            String name,
                            boolean passOuterClass,
                            String parameterName,
                            final PsiElement targetContainer) {
    super(project);
    setup(innerClass, name, passOuterClass, parameterName, true, true, targetContainer);
  }

  protected String getCommandName() {
    return RefactoringLocalize.moveInnerClassCommand(myDescriptiveName).get();
  }

  @Nonnull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveInnerViewDescriptor(myInnerClass);
  }

  @Nonnull
  protected UsageInfo[] findUsages() {
    LOG.assertTrue(myTargetContainer != null);

    Collection<PsiReference> innerClassRefs = ReferencesSearch.search(myInnerClass).findAll();
    ArrayList<UsageInfo> usageInfos = new ArrayList<UsageInfo>(innerClassRefs.size());
    for (PsiReference innerClassRef : innerClassRefs) {
      PsiElement ref = innerClassRef.getElement();
      if (!PsiTreeUtil.isAncestor(myInnerClass, ref, true)) { // do not show self-references
        usageInfos.add(new UsageInfo(ref));
      }
    }

    final String newQName;
    if (myTargetContainer instanceof PsiDirectory) {
      final PsiDirectory targetDirectory = (PsiDirectory) myTargetContainer;
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
      LOG.assertTrue(aPackage != null);
      newQName = aPackage.getQualifiedName() + "." + myNewClassName;
    } else if (myTargetContainer instanceof PsiClass) {
      final String qName = ((PsiClass) myTargetContainer).getQualifiedName();
      if (qName != null) {
        newQName = qName + "." + myNewClassName;
      } else {
        newQName = myNewClassName;
      }
    } else {
      newQName = myNewClassName;
    }
    MoveClassesOrPackagesUtil.findNonCodeUsages(mySearchInComments, mySearchInNonJavaFiles,
        myInnerClass, newQName, usageInfos);
    return usageInfos.toArray(new UsageInfo[usageInfos.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiClass;
    LOG.assertTrue(condition);
    myInnerClass = (PsiClass) elements[0];
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public void setSearchInComments(boolean searchInComments) {
    mySearchInComments = searchInComments;
  }

  public boolean isSearchInNonJavaFiles() {
    return mySearchInNonJavaFiles;
  }

  public void setSearchInNonJavaFiles(boolean searchInNonJavaFiles) {
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }

  protected void performRefactoring(final UsageInfo[] usages) {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    final RefactoringElementListener elementListener = getTransaction().getElementListener(myInnerClass);
    try {
      PsiField field = null;
      if (myParameterNameOuterClass != null) {
        // pass outer as a parameter
        field = factory.createField(myFieldNameOuterClass, factory.createType(myOuterClass));
        field = addOuterField(field);
        myInnerClass = field.getContainingClass();
        addFieldInitializationToConstructors(myInnerClass, field, myParameterNameOuterClass);
      }

      ChangeContextUtil.encodeContextInfo(myInnerClass, false);

      myInnerClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myInnerClass);

      final MoveInnerOptions moveInnerOptions = new MoveInnerOptions(myInnerClass, myOuterClass, myTargetContainer, myNewClassName);
      final MoveInnerHandler handler = MoveInnerHandler.forLanguage(myInnerClass.getLanguage());
      final PsiClass newClass;
      try {
        newClass = handler.copyClass(moveInnerOptions);
      } catch (IncorrectOperationException e) {
        RefactoringUIUtil.processIncorrectOperation(myProject, e);
        return;
      }

      // replace references in a new class to old inner class with references to itself
      for (PsiReference ref : ReferencesSearch.search(myInnerClass, new LocalSearchScope(newClass), true)) {
        PsiElement element = ref.getElement();
        if (element.getParent() instanceof PsiJavaCodeReferenceElement) {
          PsiJavaCodeReferenceElement parentRef = (PsiJavaCodeReferenceElement) element.getParent();
          PsiElement parentRefElement = parentRef.resolve();
          if (parentRefElement instanceof PsiClass) { // reference to inner class inside our inner
            parentRef.getQualifier().delete();
            continue;
          }
        }
        ref.bindToElement(newClass);
      }

      List<PsiReference> referencesToRebind = new ArrayList<PsiReference>();
      for (UsageInfo usage : usages) {
        if (usage.isNonCodeUsage) continue;
        PsiElement refElement = usage.getElement();
        PsiReference[] references = refElement.getReferences();
        for (PsiReference reference : references) {
          if (reference.isReferenceTo(myInnerClass)) {
            referencesToRebind.add(reference);
          }
        }
      }

      myInnerClass.delete();

      // correct references in usages
      for (UsageInfo usage : usages) {
        if (usage.isNonCodeUsage) continue;
        PsiElement refElement = usage.getElement();
        if (myParameterNameOuterClass != null) { // should pass outer as parameter
          PsiElement refParent = refElement.getParent();
          if (refParent instanceof PsiNewExpression || refParent instanceof PsiAnonymousClass) {
            PsiNewExpression newExpr = refParent instanceof PsiNewExpression
                ? (PsiNewExpression) refParent
                : (PsiNewExpression) refParent.getParent();

            PsiExpressionList argList = newExpr.getArgumentList();

            if (argList != null) { // can happen in incomplete code
              if (newExpr.getQualifier() == null) {
                PsiThisExpression thisExpr;
                PsiClass parentClass = RefactoringChangeUtil.getThisClass(newExpr);
                if (myOuterClass.equals(parentClass)) {
                  thisExpr = RefactoringChangeUtil.createThisExpression(manager, null);
                } else {
                  thisExpr = RefactoringChangeUtil.createThisExpression(manager, myOuterClass);
                }
                argList.addAfter(thisExpr, null);
              } else {
                argList.addAfter(newExpr.getQualifier(), null);
                newExpr.getQualifier().delete();
              }
            }
          }
        }
      }

      for (PsiReference reference : referencesToRebind) {
        reference.bindToElement(newClass);
      }

      if (field != null) {
        final PsiExpression paramAccessExpression = factory.createExpressionFromText(myParameterNameOuterClass, null);
        for (final PsiMethod constructor : newClass.getConstructors()) {
          final PsiStatement[] statements = constructor.getBody().getStatements();
          if (statements.length > 0) {
            if (statements[0] instanceof PsiExpressionStatement) {
              PsiExpression expression = ((PsiExpressionStatement) statements[0]).getExpression();
              if (expression instanceof PsiMethodCallExpression) {
                @NonNls String text = ((PsiMethodCallExpression) expression).getMethodExpression().getText();
                if ("this".equals(text) || "super".equals(text)) {
                  ChangeContextUtil.decodeContextInfo(expression, myOuterClass, paramAccessExpression);
                }
              }
            }
          }
        }

        PsiExpression accessExpression = factory.createExpressionFromText(myFieldNameOuterClass, null);
        ChangeContextUtil.decodeContextInfo(newClass, myOuterClass, accessExpression);
      } else {
        ChangeContextUtil.decodeContextInfo(newClass, null, null);
      }

      PsiFile targetFile = newClass.getContainingFile();
      OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(myProject).builder(targetFile.getVirtualFile()).offset(newClass.getTextOffset()).build();
      FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);

      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }
      elementListener.elementMoved(newClass);

      List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<NonCodeUsageInfo>();
      for (UsageInfo usage : usages) {
        if (usage instanceof NonCodeUsageInfo) {
          nonCodeUsages.add((NonCodeUsageInfo) usage);
        }
      }
      myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[nonCodeUsages.size()]);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiField addOuterField(PsiField field) {
    final PsiMember[] members = PsiTreeUtil.getChildrenOfType(myInnerClass, PsiMember.class);
    if (members != null) {
      for (PsiMember member : members) {
        if (!member.hasModifierProperty(PsiModifier.STATIC)) {
          return (PsiField) myInnerClass.addBefore(field, member);
        }
      }
    }

    return (PsiField) myInnerClass.add(field);
  }

  protected void performPsiSpoilingRefactoring() {
    if (myNonCodeUsages != null) {
      RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    }
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    final HashMap<PsiElement, HashSet<PsiElement>> reported = new HashMap<PsiElement, HashSet<PsiElement>>();
    class Visitor extends JavaRecursiveElementWalkingVisitor {


      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiMember &&
            PsiTreeUtil.isAncestor(myInnerClass, resolved, true) &&
            becomesInaccessible((PsiMember) resolved)) {
          registerConflict(reference, resolved, reported, conflicts);
        }
      }


      @Override
      public void visitClass(PsiClass aClass) {
        if (aClass == myInnerClass) return;
        super.visitClass(aClass);
      }
    }

//    if (myInnerClass.hasModifierProperty(PsiModifier.)) {
    myOuterClass.accept(new Visitor());
    myInnerClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiMember) {
          if (PsiTreeUtil.isAncestor(myOuterClass, resolve, true) && !PsiTreeUtil.isAncestor(myInnerClass, resolve, false)) {
            if (becomesInaccessible((PsiMember) resolve)) {
              registerConflict(reference, resolve, reported, conflicts);
            }
          }
        }
      }
    });

    return showConflicts(conflicts, refUsages.get());
  }

  private static void registerConflict(PsiJavaCodeReferenceElement reference,
                                       PsiElement resolved,
                                       HashMap<PsiElement, HashSet<PsiElement>> reported, MultiMap<PsiElement, String> conflicts) {
    final PsiElement container = ConflictsUtil.getContainer(reference);
    HashSet<PsiElement> containerSet = reported.get(container);
    if (containerSet == null) {
      containerSet = new HashSet<PsiElement>();
      reported.put(container, containerSet);
    }
    if (!containerSet.contains(resolved)) {
      containerSet.add(resolved);
      String placesDescription;
      if (containerSet.size() == 1) {
        placesDescription = RefactoringUIUtil.getDescription(resolved, true);
      } else {
        placesDescription = "<ol><li>" + StringUtil.join(containerSet, new Function<PsiElement, String>() {
          @Override
          public String apply(PsiElement element) {
            return RefactoringUIUtil.getDescription(element, true);
          }
        }, "</li><li>") + "</li></ol>";
      }
      LocalizeValue message =
        RefactoringLocalize.zeroWillBecomeInaccessibleFrom1(placesDescription, RefactoringUIUtil.getDescription(container, true));
      conflicts.put(container, Collections.singletonList(message.get()));
    }
  }

  private boolean becomesInaccessible(PsiMember element) {
    final String visibilityModifier = VisibilityUtil.getVisibilityModifier(element.getModifierList());
    if (PsiModifier.PRIVATE.equals(visibilityModifier)) return true;
    if (PsiModifier.PUBLIC.equals(visibilityModifier)) return false;
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    if (myTargetContainer instanceof PsiDirectory) {
      final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory) myTargetContainer);
      assert aPackage != null : myTargetContainer;
      return !psiFacade.isInPackage(myOuterClass, aPackage);
    }
    // target container is a class
    PsiFile targetFile = myTargetContainer.getContainingFile();
    if (targetFile != null) {
      final PsiDirectory containingDirectory = targetFile.getContainingDirectory();
      if (containingDirectory != null) {
        final PsiJavaPackage targetPackage = JavaDirectoryService.getInstance().getPackage(containingDirectory);
        assert targetPackage != null : myTargetContainer;
        return psiFacade.isInPackage(myOuterClass, targetPackage);
      }
    }
    return false;
  }

  public void setup(final PsiClass innerClass,
                    final String className,
                    final boolean passOuterClass,
                    final String parameterName,
                    boolean searchInComments,
                    boolean searchInNonJava,
                    @Nonnull final PsiElement targetContainer) {
    myNewClassName = className;
    myInnerClass = innerClass;
    myDescriptiveName = DescriptiveNameUtil.getDescriptiveName(myInnerClass);
    myOuterClass = myInnerClass.getContainingClass();
    myTargetContainer = targetContainer;
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myParameterNameOuterClass = passOuterClass ? parameterName : null;
    if (myParameterNameOuterClass != null) {
      myFieldNameOuterClass =
          codeStyleManager.variableNameToPropertyName(myParameterNameOuterClass, VariableKind.PARAMETER);
      myFieldNameOuterClass = codeStyleManager.propertyNameToVariableName(myFieldNameOuterClass, VariableKind.FIELD);
    }
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJava;
  }

  private void addFieldInitializationToConstructors(PsiClass aClass, PsiField field, String parameterName)
      throws IncorrectOperationException {

    PsiMethod[] constructors = aClass.getConstructors();
    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    if (constructors.length > 0) {
      for (PsiMethod constructor : constructors) {
        if (parameterName != null) {
          PsiParameterList parameterList = constructor.getParameterList();
          PsiParameter parameter = factory.createParameter(parameterName, field.getType());
          parameterList.addAfter(parameter, null);
        }
        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;
        PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          PsiStatement first = statements[0];
          if (first instanceof PsiExpressionStatement) {
            PsiExpression expression = ((PsiExpressionStatement) first).getExpression();
            if (expression instanceof PsiMethodCallExpression) {
              @NonNls String text = ((PsiMethodCallExpression) expression).getMethodExpression().getText();
              if ("this".equals(text)) {
                continue;
              }
            }
          }
        }
        createAssignmentStatement(constructor, field.getName(), parameterName);
      }
    } else {
      PsiMethod constructor = factory.createConstructor();
      if (parameterName != null) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter parameter = factory.createParameter(parameterName, field.getType());
        parameterList.add(parameter);
      }
      createAssignmentStatement(constructor, field.getName(), parameterName);
      aClass.add(constructor);
    }
  }

  private PsiStatement createAssignmentStatement(PsiMethod constructor, String fieldName, String parameterName)
      throws IncorrectOperationException {

    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    @NonNls String pattern = fieldName + "=a;";
    if (fieldName.equals(parameterName)) {
      pattern = "this." + pattern;
    }

    PsiExpressionStatement statement = (PsiExpressionStatement) factory.createStatementFromText(pattern, null);
    statement = (PsiExpressionStatement) CodeStyleManager.getInstance(myProject).reformat(statement);

    PsiCodeBlock body = constructor.getBody();
    assert body != null : constructor;
    statement = (PsiExpressionStatement) body.addAfter(statement, getAnchorElement(body));

    PsiAssignmentExpression assignment = (PsiAssignmentExpression) statement.getExpression();
    PsiReferenceExpression rExpr = (PsiReferenceExpression) assignment.getRExpression();
    assert rExpr != null : assignment;
    PsiIdentifier identifier = (PsiIdentifier) rExpr.getReferenceNameElement();
    assert identifier != null : assignment;
    identifier.replace(factory.createIdentifier(parameterName));
    return statement;
  }

  @Nullable
  private static PsiElement getAnchorElement(PsiCodeBlock body) {
    PsiStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      PsiStatement first = statements[0];
      if (first instanceof PsiExpressionStatement) {

        PsiExpression expression = ((PsiExpressionStatement) first).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodCall = ((PsiMethodCallExpression) expression).getMethodExpression();
          @NonNls String text = methodCall.getText();
          if ("super".equals(text)) {
            return first;
          }
        }
      }
    }
    return null;
  }

  public PsiClass getInnerClass() {
    return myInnerClass;
  }

  public String getNewClassName() {
    return myNewClassName;
  }

  public boolean shouldPassParameter() {
    return myParameterNameOuterClass != null;
  }


  public String getParameterName() {
    return myParameterNameOuterClass;
  }
}
