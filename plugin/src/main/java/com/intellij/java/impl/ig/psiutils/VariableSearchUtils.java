/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public class VariableSearchUtils {

  private VariableSearchUtils() {
  }

  public static boolean variableNameResolvesToTarget(
    @Nonnull String variableName, @Nonnull PsiVariable target,
    @Nonnull PsiElement context) {

    Project project = context.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();
    PsiVariable variable =
      resolveHelper.resolveAccessibleReferencedVariable(
        variableName, context);
    return target.equals(variable);
  }

  public static boolean containsConflictingDeclarations(
      PsiCodeBlock block, PsiCodeBlock parentBlock) {
    List<PsiCodeBlock> followingBlocks = new ArrayList();
    collectFollowingBlocks(block.getParent().getNextSibling(),
                           followingBlocks);
    PsiStatement[] statements = block.getStatements();
    Project project = block.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiResolveHelper resolveHelper = facade.getResolveHelper();
    for (PsiStatement statement : statements) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        continue;
      }
      PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)statement;
      PsiElement[] variables =
        declaration.getDeclaredElements();
      for (PsiElement variable : variables) {
        if (!(variable instanceof PsiLocalVariable)) {
          continue;
        }
        PsiLocalVariable localVariable =
          (PsiLocalVariable)variable;
        String variableName = localVariable.getName();
        PsiVariable target =
          resolveHelper.resolveAccessibleReferencedVariable(
            variableName, parentBlock);
        if (target != null) {
          return true;
        }
        for (PsiCodeBlock codeBlock : followingBlocks) {
          PsiVariable target1 =
            resolveHelper.resolveAccessibleReferencedVariable(
              variableName, codeBlock);
          if (target1 != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Depth first traversal to find all PsiCodeBlock children.
   */
  private static void collectFollowingBlocks(PsiElement element,
                                             List<PsiCodeBlock> out) {
    while (element != null) {
      if (element instanceof PsiCodeBlock) {
        out.add((PsiCodeBlock)element);
      }
      collectFollowingBlocks(element.getFirstChild(), out);
      element = element.getNextSibling();
    }
  }
}