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
package com.intellij.java.impl.ipp.psiutils;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ImportUtils {

  private ImportUtils() {}

  public static void addImportIfNeeded(@Nonnull PsiClass aClass, @Nonnull PsiElement context) {
    final PsiFile file = context.getContainingFile();
    if (!(file instanceof PsiJavaFile)) {
      return;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    final PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) {
      if (PsiTreeUtil.isAncestor(javaFile, aClass, true)) {
        return;
      }
    }
    else {
      if (PsiTreeUtil.isAncestor(outerClass, context, true) &&
          !PsiTreeUtil.isAncestor(outerClass.getModifierList(), context, true)) {
        return;
      }
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return;
    }
    final String containingPackageName = javaFile.getPackageName();
    @NonNls final String packageName = ClassUtil.extractPackageName(qualifiedName);
    if (containingPackageName.equals(packageName) || importList.findSingleClassImportStatement(qualifiedName) != null) {
      return;
    }
    if (importList.findOnDemandImportStatement(packageName) != null &&
        !hasDefaultImportConflict(qualifiedName, javaFile) && !hasOnDemandImportConflict(qualifiedName, javaFile)) {
      return;
    }
    final Project project = importList.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory elementFactory = psiFacade.getElementFactory();
    final PsiImportStatement importStatement = elementFactory.createImportStatement(aClass);
    importList.add(importStatement);
  }

  private static boolean nameCanBeStaticallyImported(@Nonnull String fqName, @Nonnull String memberName, @Nonnull PsiElement context) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (containingClass == null) {
      return false;
    }
    if (InheritanceUtil.isInheritor(containingClass, fqName)) {
      return true;
    }
    final PsiField field = containingClass.findFieldByName(memberName, true);
    if (field != null) {
      return false;
    }
    final PsiMethod[] methods = containingClass.findMethodsByName(memberName, true);
    if (methods.length > 0) {
      return false;
    }
    return !hasOnDemandImportStaticConflict(fqName, memberName, context, true) &&
           !hasExactImportStaticConflict(fqName, memberName, context);
  }

  public static boolean nameCanBeImported(@Nonnull String fqName, @Nonnull PsiElement context) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (containingClass != null) {
      if (fqName.equals(containingClass.getQualifiedName())) {
        return true;
      }
      final String shortName = ClassUtil.extractClassName(fqName);
      final PsiClass[] innerClasses = containingClass.getAllInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (innerClass.hasModifierProperty(PsiModifier.PRIVATE)) {
          continue;
        }
        if (innerClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
          if (!inSamePackage(innerClass, containingClass)) {
            continue;
          }
        }
        final String className = innerClass.getName();
        if (shortName.equals(className)) {
          return false;
        }
      }
      PsiField field = containingClass.findFieldByName(shortName, false);
      if (field != null) {
        return false;
      }
      field = containingClass.findFieldByName(shortName, true);
      if (field != null && PsiUtil.isAccessible(field, containingClass, null)) {
        return false;
      }
    }
    final PsiJavaFile file = PsiTreeUtil.getParentOfType(context, PsiJavaFile.class);
    if (file == null) {
      return false;
    }
    if (hasExactImportConflict(fqName, file)) {
      return false;
    }
    if (hasOnDemandImportConflict(fqName, file, true)) {
      return false;
    }
    if (containsReferenceToConflictingClass(file, fqName)) {
      return false;
    }
    if (containsConflictingClass(fqName, file)) {
      return false;
    }
    return !containsConflictingClassName(fqName, file);
  }

  public static boolean inSamePackage(@Nullable PsiElement element1, @Nullable PsiElement element2) {
    if (element1 == null || element2 == null) {
      return false;
    }
    final PsiFile containingFile1 = element1.getContainingFile();
    if (!(containingFile1 instanceof PsiClassOwner)) {
      return false;
    }
    final PsiClassOwner containingJavaFile1 = (PsiClassOwner)containingFile1;
    final String packageName1 = containingJavaFile1.getPackageName();
    final PsiFile containingFile2 = element2.getContainingFile();
    if (!(containingFile2 instanceof PsiClassOwner)) {
      return false;
    }
    final PsiClassOwner containingJavaFile2 = (PsiClassOwner)containingFile2;
    final String packageName2 = containingJavaFile2.getPackageName();
    return packageName1.equals(packageName2);
  }

  private static boolean containsConflictingClassName(String fqName, PsiJavaFile file) {
    final int lastDotIndex = fqName.lastIndexOf((int)'.');
    final String shortName = fqName.substring(lastDotIndex + 1);
    final PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (shortName.equals(aClass.getName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasExactImportConflict(String fqName, PsiJavaFile file) {
    final PsiImportList imports = file.getImportList();
    if (imports == null) {
      return false;
    }
    final PsiImportStatement[] importStatements = imports.getImportStatements();
    final int lastDotIndex = fqName.lastIndexOf((int)'.');
    final String shortName = fqName.substring(lastDotIndex + 1);
    final String dottedShortName = '.' + shortName;
    for (final PsiImportStatement importStatement : importStatements) {
      if (importStatement.isOnDemand()) {
        continue;
      }
      final String importName = importStatement.getQualifiedName();
      if (importName == null) {
        return false;
      }
      if (!importName.equals(fqName) && importName.endsWith(dottedShortName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasExactImportStaticConflict(String qualifierClass, String memberName, PsiElement context) {
    final PsiFile file = context.getContainingFile();
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    for (PsiImportStaticStatement importStaticStatement :
      importStaticStatements) {
      if (importStaticStatement.isOnDemand()) {
        continue;
      }
      final String name = importStaticStatement.getReferenceName();
      if (!memberName.equals(name)) {
        continue;
      }
      final PsiJavaCodeReferenceElement importReference = importStaticStatement.getImportReference();
      if (importReference == null) {
        continue;
      }
      final PsiElement qualifier = importReference.getQualifier();
      if (qualifier == null) {
        continue;
      }
      final String qualifierText = qualifier.getText();
      if (!qualifierClass.equals(qualifierText)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasOnDemandImportConflict(@Nonnull String fqName, @Nonnull PsiJavaFile file) {
    return hasOnDemandImportConflict(fqName, file, false);
  }

  /**
   * @param strict if strict is true this method checks if the conflicting
   *               class which is imported is actually used in the file. If it isn't the
   *               on demand import can be overridden with an exact import for the fqName
   *               without breaking stuff.
   */
  private static boolean hasOnDemandImportConflict(@Nonnull String fqName, @Nonnull PsiJavaFile file, boolean strict) {
    final PsiImportList imports = file.getImportList();
    if (imports == null) {
      return false;
    }
    final PsiImportStatement[] importStatements = imports.getImportStatements();
    final String shortName = ClassUtil.extractClassName(fqName);
    final String packageName = ClassUtil.extractPackageName(fqName);
    for (final PsiImportStatement importStatement : importStatements) {
      if (!importStatement.isOnDemand()) {
        continue;
      }
      final PsiJavaCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null) {
        continue;
      }
      final String packageText = importReference.getText();
      if (packageText.equals(packageName)) {
        continue;
      }
      final PsiElement element = importReference.resolve();
      if (element == null || !(element instanceof PsiPackage)) {
        continue;
      }
      final PsiJavaPackage aPackage = (PsiJavaPackage)element;
      final PsiClass[] classes = aPackage.getClasses();
      for (final PsiClass aClass : classes) {
        final String className = aClass.getName();
        if (!shortName.equals(className)) {
          continue;
        }
        if (!strict) {
          return true;
        }
        final String qualifiedClassName = aClass.getQualifiedName();
        if (qualifiedClassName == null || fqName.equals(qualifiedClassName)) {
          continue;
        }
        return containsReferenceToConflictingClass(file, qualifiedClassName);
      }
    }
    return hasJavaLangImportConflict(fqName, file);
  }

  private static boolean hasOnDemandImportStaticConflict(String fqName, String memberName, PsiElement context) {
    return hasOnDemandImportStaticConflict(fqName, memberName, context, false);
  }

  private static boolean hasOnDemandImportStaticConflict(String fqName, String memberName, PsiElement context, boolean strict) {
    final PsiFile file = context.getContainingFile();
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
      if (!importStaticStatement.isOnDemand()) {
        continue;
      }
      final PsiClass targetClass = importStaticStatement.resolveTargetClass();
      if (targetClass == null) {
        continue;
      }
      final String name = targetClass.getQualifiedName();
      if (fqName.equals(name)) {
        continue;
      }
      final PsiField field = targetClass.findFieldByName(memberName, true);
      if (field != null && (!strict || memberReferenced(field, javaFile))) {
        return true;
      }
      final PsiMethod[] methods = targetClass.findMethodsByName(memberName, true);
      if (methods.length > 0 && (!strict || membersReferenced(methods, javaFile))) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasDefaultImportConflict(String fqName, PsiJavaFile file) {
    final String shortName = ClassUtil.extractClassName(fqName);
    final String packageName = ClassUtil.extractPackageName(fqName);
    final String filePackageName = file.getPackageName();
    if (filePackageName.equals(packageName)) {
      return false;
    }
    final Project project = file.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiJavaPackage filePackage = psiFacade.findPackage(filePackageName);
    if (filePackage == null) {
      return false;
    }
    final PsiClass[] classes = filePackage.getClasses();
    for (PsiClass aClass : classes) {
      final String className = aClass.getName();
      if (shortName.equals(className)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasJavaLangImportConflict(String fqName, PsiJavaFile file) {
    final String shortName = ClassUtil.extractClassName(fqName);
    @NonNls final String packageName = ClassUtil.extractPackageName(fqName);
    if ("java.lang".equals(packageName)) {
      return false;
    }
    final Project project = file.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiJavaPackage javaLangPackage = psiFacade.findPackage("java.lang");
    if (javaLangPackage == null) {
      return false;
    }
    final PsiClass[] classes = javaLangPackage.getClasses();
    for (final PsiClass aClass : classes) {
      final String className = aClass.getName();
      if (shortName.equals(className)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsConflictingClass(String fqName, PsiJavaFile file) {
    final PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (containsConflictingInnerClass(fqName, aClass)) {
        return true;
      }
    }
    return false;
  }

  /**
   * ImportUtils currently checks all inner classes, even those that are
   * contained in inner classes themselves, because it doesn't know the
   * location of the original fully qualified reference. It should really only
   * check if the containing class of the fully qualified reference has any
   * conflicting inner classes.
   */
  private static boolean containsConflictingInnerClass(String fqName, PsiClass aClass) {
    final String shortName = ClassUtil.extractClassName(fqName);
    if (shortName.equals(aClass.getName()) && !fqName.equals(aClass.getQualifiedName())) {
      return true;
    }
    final PsiClass[] classes = aClass.getInnerClasses();
    for (PsiClass innerClass : classes) {
      if (containsConflictingInnerClass(fqName, innerClass)) {
        return true;
      }
    }
    return false;
  }

  public static boolean addStaticImport(@Nonnull String qualifierClass, @NonNls @Nonnull String memberName, @Nonnull PsiElement context) {
    if (!nameCanBeStaticallyImported(qualifierClass, memberName, context)) {
      return false;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (InheritanceUtil.isInheritor(containingClass, qualifierClass)) {
      return true;
    }
    final PsiFile psiFile = context.getContainingFile();
    if (!(psiFile instanceof PsiJavaFile)) {
      return false;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)psiFile;
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final PsiImportStatementBase existingImportStatement = importList.findSingleImportStatement(memberName);
    if (existingImportStatement != null) {
      return false;
    }
    final PsiImportStaticStatement onDemandImportStatement = findOnDemandImportStaticStatement(importList, qualifierClass);
    if (onDemandImportStatement != null && !hasOnDemandImportStaticConflict(qualifierClass, memberName, context)) {
      return true;
    }
    final Project project = context.getProject();
    final GlobalSearchScope scope = context.getResolveScope();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass aClass = psiFacade.findClass(qualifierClass, scope);
    if (aClass == null) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }
    final List<PsiImportStaticStatement> imports = getMatchingImports(importList, qualifiedName);
    final JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
    final PsiElementFactory elementFactory = psiFacade.getElementFactory();
    if (imports.size() < codeStyleSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
      importList.add(elementFactory.createImportStaticStatement(aClass, memberName));
    }
    else {
      for (PsiImportStaticStatement importStatement : imports) {
        importStatement.delete();
      }
      importList.add(elementFactory.createImportStaticStatement(aClass, "*"));
    }
    return true;
  }

  @Nullable
  private static PsiImportStaticStatement findOnDemandImportStaticStatement(PsiImportList importList, String qualifierClass) {
    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
      if (!importStaticStatement.isOnDemand()) {
        continue;
      }
      final PsiJavaCodeReferenceElement importReference = importStaticStatement.getImportReference();
      if (importReference == null) {
        continue;
      }
      final String text = importReference.getText();
      if (qualifierClass.equals(text)) {
        return importStaticStatement;
      }
    }
    return null;
  }

  private static List<PsiImportStaticStatement> getMatchingImports(@Nonnull PsiImportList importList, @Nonnull String className) {
    final List<PsiImportStaticStatement> imports = new ArrayList();
    for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
      final PsiClass psiClass = staticStatement.resolveTargetClass();
      if (psiClass == null) {
        continue;
      }
      if (!className.equals(psiClass.getQualifiedName())) {
        continue;
      }
      imports.add(staticStatement);
    }
    return imports;
  }

  public static boolean isStaticallyImported(@Nonnull PsiMember member, @Nonnull PsiElement context) {
    final PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) {
      return false;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    if (InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      return false;
    }
    final PsiFile psiFile = context.getContainingFile();
    if (!(psiFile instanceof PsiJavaFile)) {
      return false;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)psiFile;
    final PsiImportList importList = javaFile.getImportList();
    if (importList == null) {
      return false;
    }
    final String memberName = member.getName();
    if (memberName == null) {
      return false;
    }
    final PsiImportStatementBase existingImportStatement = importList.findSingleImportStatement(memberName);
    if (existingImportStatement instanceof PsiImportStaticStatement) {
      final PsiClass importClass = ((PsiImportStaticStatement)existingImportStatement).resolveTargetClass();
      if (InheritanceUtil.isInheritorOrSelf(importClass, memberClass, true)) {
        return true;
      }
    }
    final String memberClassName = memberClass.getQualifiedName();
    if (memberClassName == null) {
      return false;
    }
    final PsiImportStaticStatement onDemandImportStatement = findOnDemandImportStaticStatement(importList, memberClassName);
    if (onDemandImportStatement != null) {
      if (!hasOnDemandImportStaticConflict(memberClassName, memberName, context)) {
        return true;
      }
    }
    return false;
  }

  private static boolean memberReferenced(PsiMember member, PsiElement context) {
    final MemberReferenceVisitor visitor = new MemberReferenceVisitor(member);
    context.accept(visitor);
    return visitor.isReferenceFound();
  }

  private static boolean membersReferenced(PsiMember[] members, PsiElement context) {
    final MemberReferenceVisitor visitor = new MemberReferenceVisitor(members);
    context.accept(visitor);
    return visitor.isReferenceFound();
  }

  private static class MemberReferenceVisitor extends JavaRecursiveElementVisitor {

    private final PsiMember[] members;
    private boolean referenceFound = false;

    public MemberReferenceVisitor(PsiMember member) {
      members = new PsiMember[]{member};
    }

    public MemberReferenceVisitor(PsiMember[] members) {
      this.members = members;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      if (referenceFound) {
        return;
      }
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      for (PsiMember member : members) {
        if (member.equals(target)) {
          referenceFound = true;
          return;
        }
      }
    }

    public boolean isReferenceFound() {
      return referenceFound;
    }
  }

  /**
   * @return true, if the element contains a reference to a different class than fullyQualifiedName but which has the same class name
   */
  public static boolean containsReferenceToConflictingClass(PsiElement element, String fullyQualifiedName) {
    final ConflictingClassReferenceVisitor visitor = new ConflictingClassReferenceVisitor(fullyQualifiedName);
    element.accept(visitor);
    return visitor.isReferenceFound();
  }

  private static class ConflictingClassReferenceVisitor extends JavaRecursiveElementVisitor {

    private final String name;
    private final String fullyQualifiedName;
    private boolean referenceFound = false;

    private ConflictingClassReferenceVisitor(String fullyQualifiedName) {
      name = ClassUtil.extractClassName(fullyQualifiedName);
      this.fullyQualifiedName = fullyQualifiedName;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (referenceFound) {
        return;
      }
      final String text = reference.getText();
      if (text.indexOf((int)'.') >= 0 || !name.equals(text)) {
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass) || element instanceof PsiTypeParameter) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final String testClassName = aClass.getName();
      final String testClassQualifiedName = aClass.getQualifiedName();
      if (testClassQualifiedName == null || testClassName == null ||
          testClassQualifiedName.equals(fullyQualifiedName) || !testClassName.equals(name)) {
        return;
      }
      referenceFound = true;
    }

    public boolean isReferenceFound() {
      return referenceFound;
    }
  }
}