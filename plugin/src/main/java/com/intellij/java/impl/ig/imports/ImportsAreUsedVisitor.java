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
package com.intellij.java.impl.ig.imports;

import com.intellij.java.language.psi.*;
import consulo.util.lang.StringUtil;
import consulo.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ImportsAreUsedVisitor extends JavaRecursiveElementVisitor {

  private final List<PsiImportStatementBase> importStatements;
  private final List<PsiImportStatementBase> usedImportStatements = new ArrayList();

  ImportsAreUsedVisitor(PsiImportStatementBase[] importStatements) {
    this.importStatements = new ArrayList(Arrays.asList(importStatements));
    Collections.reverse(this.importStatements);
  }

  @Override
  public void visitElement(PsiElement element) {
    if (importStatements.isEmpty()) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
    followReferenceToImport(reference);
    super.visitReferenceElement(reference);
  }

  private void followReferenceToImport(PsiJavaCodeReferenceElement reference) {
    if (reference.getQualifier() != null) {
      // it's already fully qualified, so the import statement wasn't
      // responsible
      return;
    }
    // during typing there can be incomplete code
    JavaResolveResult resolveResult = reference.advancedResolve(true);
    PsiElement element = resolveResult.getElement();
    if (element == null) {
      return;
    }
    if (findImport(element, usedImportStatements) != null) {
      return;
    }
    PsiImportStatementBase foundImport = findImport(element, importStatements);
    if (foundImport != null) {
      removeAll(foundImport);
      usedImportStatements.add(foundImport);
    }
  }

  private static PsiImportStatementBase findImport(PsiElement element, List<PsiImportStatementBase> importStatements) {
    String qualifiedName;
    String packageName;
    if (element instanceof PsiClass) {
      PsiClass referencedClass = (PsiClass)element;
      qualifiedName = referencedClass.getQualifiedName();
      packageName = qualifiedName != null ? StringUtil.getPackageName(qualifiedName) : null;
    }
    else {
      qualifiedName = null;
      packageName = null;
    }
    PsiClass referenceClass;
    String referenceName;
    if (element instanceof PsiMember) {
      PsiMember member = (PsiMember)element;
      if (member instanceof PsiClass && !member.hasModifierProperty(PsiModifier.STATIC)) {
        referenceClass = null;
        referenceName = null;
      }
      else {
        referenceClass = member.getContainingClass();
        referenceName = member.getName();
      }
    }
    else {
      referenceClass = null;
      referenceName = null;
    }
    for (PsiImportStatementBase importStatementBase : importStatements) {
      if (importStatementBase instanceof PsiImportStatement && qualifiedName != null && packageName != null) {
        PsiImportStatement importStatement = (PsiImportStatement)importStatementBase;
        String importName = importStatement.getQualifiedName();
        if (importName != null) {
          if (importStatement.isOnDemand()) {
            if (importName.equals(packageName)) {
              return importStatement;
            }
          }
          else if (importName.equals(qualifiedName)) {
            return importStatement;
          }
        }
      }
      if (importStatementBase instanceof PsiImportStaticStatement && referenceClass != null && referenceName != null) {
        PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)importStatementBase;
        if (importStaticStatement.isOnDemand()) {
          PsiClass targetClass = importStaticStatement.resolveTargetClass();
          if (InheritanceUtil.isInheritorOrSelf(targetClass, referenceClass, true)) {
            return importStaticStatement;
          }
        }
        else {
          String importReferenceName = importStaticStatement.getReferenceName();
          if (importReferenceName != null) {
            if (importReferenceName.equals(referenceName)) {
              return importStaticStatement;
            }
          }
        }
      }
    }
    return null;
  }

  private void removeAll(@Nonnull PsiImportStatementBase importStatement) {
    for (int i = importStatements.size() - 1; i >= 0; i--) {
      PsiImportStatementBase statement = importStatements.get(i);
      String statementText = statement.getText();
      String importText = importStatement.getText();
      if (importText.equals(statementText)) {
        importStatements.remove(i);
      }
    }
  }

  public PsiImportStatementBase[] getUnusedImportStatements() {
    if (importStatements.isEmpty()) {
      return PsiImportStatementBase.EMPTY_ARRAY;
    }
    return importStatements.toArray(new PsiImportStatementBase[importStatements.size()]);
  }
}