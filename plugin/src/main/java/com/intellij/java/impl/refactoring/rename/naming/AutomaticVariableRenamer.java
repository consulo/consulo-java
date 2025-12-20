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
package com.intellij.java.impl.refactoring.rename.naming;

import com.intellij.java.impl.psi.impl.source.tree.StdTokenSets;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.ast.ASTNode;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import consulo.util.lang.StringUtil;

import java.util.*;

/**
 * @author dsl
 */
public class AutomaticVariableRenamer extends AutomaticRenamer {
  private final Set<PsiNamedElement> myToUnpluralize = new HashSet<PsiNamedElement>();

  public AutomaticVariableRenamer(PsiClass aClass, String newClassName, Collection<UsageInfo> usages) {
    String oldClassName = aClass.getName();
    for (UsageInfo info : usages) {
      PsiElement element = info.getElement();
      if (!(element instanceof PsiJavaCodeReferenceElement)) continue;
      PsiDeclarationStatement statement = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);
      if (statement != null) {
        for (PsiElement declaredElement : statement.getDeclaredElements()) {
          if (declaredElement instanceof PsiVariable) {
            checkRenameVariable(element, (PsiVariable) declaredElement, oldClassName);
          }
        }
      } else {
        PsiVariable variable = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
        if (variable != null) {
          checkRenameVariable(element, variable, oldClassName);
          if (variable instanceof PsiField) {
            for (PsiField field : getFieldsInSameDeclaration((PsiField) variable)) {
              checkRenameVariable(element, field, oldClassName);
            }
          }
        }
      }
    }
    suggestAllNames(oldClassName, newClassName);
  }

  private static List<PsiField> getFieldsInSameDeclaration(PsiField variable) {
    List<PsiField> result = new ArrayList<PsiField>();
    ASTNode node = variable.getNode();
    if (node != null) {
      while (true) {
        ASTNode comma = TreeUtil.skipElements(node.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (comma == null || comma.getElementType() != JavaTokenType.COMMA) break;
        ASTNode nextField = TreeUtil.skipElements(comma.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (nextField == null || nextField.getElementType() != JavaElementType.FIELD) break;
        result.add((PsiField) nextField.getPsi());
        node = nextField;
      }
    }
    return result;
  }

  private void checkRenameVariable(PsiElement element, PsiVariable variable, String oldClassName) {
    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) return;
    PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
    if (ref == null) return;
    String variableName = variable.getName();
    if (variableName != null && !StringUtil.containsIgnoreCase(variableName, oldClassName)) return;
    if (ref.equals(element)) {
      myElements.add(variable);
      if (variable.getType() instanceof PsiArrayType) {
        myToUnpluralize.add(variable);
      }
    } else {
      PsiType collectionType = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory()
          .createTypeByFQClassName(CommonClassNames.JAVA_UTIL_COLLECTION, variable.getResolveScope());
      if (!collectionType.isAssignableFrom(variable.getType())) return;
      PsiTypeElement[] typeParameterElements = ref.getParameterList().getTypeParameterElements();
      for (PsiTypeElement typeParameterElement : typeParameterElements) {
        PsiJavaCodeReferenceElement parameterRef = typeParameterElement.getInnermostComponentReferenceElement();
        if (parameterRef != null && parameterRef.equals(element)) {
          myElements.add(variable);
          myToUnpluralize.add(variable);
          break;
        }
      }
    }
  }

  public String getDialogTitle() {
    return RefactoringLocalize.renameVariablesTitle().get();
  }

  public String getDialogDescription() {
    return RefactoringLocalize.renameVariablesWithTheFollowingNamesTo().get();
  }

  public String entityName() {
    return RefactoringLocalize.entityNameVariable().get();
  }

  public String nameToCanonicalName(String name, PsiNamedElement psiVariable) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiVariable.getProject());
    String propertyName = codeStyleManager.variableNameToPropertyName(name, codeStyleManager.getVariableKind((PsiVariable) psiVariable));
    if (myToUnpluralize.contains(psiVariable)) {
      String singular = StringUtil.unpluralize(propertyName);
      if (singular != null) return singular;
      myToUnpluralize.remove(psiVariable); // no need to pluralize since it was initially in singular form
    }
    return propertyName;
  }

  public String canonicalNameToName(String canonicalName, PsiNamedElement psiVariable) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiVariable.getProject());
    String variableName =
        codeStyleManager.propertyNameToVariableName(canonicalName, codeStyleManager.getVariableKind((PsiVariable) psiVariable));
    if (myToUnpluralize.contains(psiVariable)) return StringUtil.pluralize(variableName);
    return variableName;
  }
}