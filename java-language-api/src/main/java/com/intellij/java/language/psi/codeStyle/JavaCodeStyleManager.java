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
package com.intellij.java.language.psi.codeStyle;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.util.IncorrectOperationException;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * @author max
 */
public abstract class JavaCodeStyleManager {
  public static JavaCodeStyleManager getInstance(Project project) {
    return ServiceManager.getService(project, JavaCodeStyleManager.class);
  }

  public static final int DO_NOT_ADD_IMPORTS = 0x1000;
  public static final int INCOMPLETE_CODE = 0x2000;

  /**
   * @deprecated use {@link #INCOMPLETE_CODE} (to be removed in IDEA 17)
   */
  @SuppressWarnings({
      "unused",
      "SpellCheckingInspection"
  })
  public static final int UNCOMPLETE_CODE = INCOMPLETE_CODE;

  public abstract boolean addImport(@Nonnull PsiJavaFile file, @Nonnull PsiClass refClass);

  @Nonnull
  public abstract PsiElement shortenClassReferences(@Nonnull PsiElement element, @MagicConstant(flags = {
      DO_NOT_ADD_IMPORTS,
      INCOMPLETE_CODE
  }) int flags) throws IncorrectOperationException;

  @Nonnull
  public abstract String getPrefixByVariableKind(@Nonnull VariableKind variableKind);

  @Nonnull
  public abstract String getSuffixByVariableKind(@Nonnull VariableKind variableKind);

  public abstract int findEntryIndex(@Nonnull PsiImportStatementBase statement);

  /**
   * Replaces fully-qualified class names in the contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element the element to shorten references in.
   * @return the element in the PSI tree after the shorten references operation corresponding to the original element.
   * @throws IncorrectOperationException if the file to shorten references in is read-only.
   */
  @Nonnull
  public abstract PsiElement shortenClassReferences(@Nonnull PsiElement element) throws IncorrectOperationException;

  /**
   * Replaces fully-qualified class names in a part of contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element     the element to shorten references in.
   * @param startOffset the start offset in the <b>element</b> of the part where class references are shortened.
   * @param endOffset   the end offset in the <b>element</b> of the part where class references are shortened.
   * @throws IncorrectOperationException if the file to shorten references in is read-only.
   */
  public abstract void shortenClassReferences(@Nonnull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException;

  /**
   * Optimizes imports in the specified Java or JSP file.
   *
   * @param file the file to optimize the imports in.
   * @throws IncorrectOperationException if the file is read-only.
   */
  public abstract void optimizeImports(@Nonnull PsiFile file) throws IncorrectOperationException;

  /**
   * Calculates the import list that would be substituted in the specified Java or JSP
   * file if an Optimize Imports operation was performed on it.
   *
   * @param file the file to calculate the import list for.
   * @return the calculated import list.
   */
  public abstract PsiImportList prepareOptimizeImportsResult(@Nonnull PsiJavaFile file);

  /**
   * Single-static-import {@code import static classFQN.referenceName;} shadows on-demand static imports, like described
   * JLS 6.4.1
   * A single-static-import declaration d in a compilation unit c of package p that imports a {member} named n
   * shadows the declaration of any static {member} named n imported by a static-import-on-demand declaration in c, throughout c.
   *
   * @return true if file contains import which would be shadowed
   * false otherwise
   */
  public boolean hasConflictingOnDemandImport(@Nonnull PsiJavaFile file, @Nonnull PsiClass psiClass, @Nonnull String referenceName) {
    return false;
  }

  /**
   * Returns the kind of the specified variable (local, parameter, field, static field or static final field).
   *
   * @param variable the variable to get the kind for.
   * @return the variable kind.
   */
  @Nonnull
  public VariableKind getVariableKind(@Nonnull PsiVariable variable) {
    if (variable instanceof PsiField) {
      if (variable.hasModifierProperty(PsiModifier.STATIC)) {
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          return VariableKind.STATIC_FINAL_FIELD;
        }
        return VariableKind.STATIC_FIELD;
      }
      return VariableKind.FIELD;
    } else {
      if (variable instanceof PsiParameter) {
        if (((PsiParameter) variable).getDeclarationScope() instanceof PsiForeachStatement) {
          return VariableKind.LOCAL_VARIABLE;
        }
        return VariableKind.PARAMETER;
      }
      return VariableKind.LOCAL_VARIABLE;
    }
  }

  public SuggestedNameInfo suggestVariableName(@Nonnull final VariableKind kind, @Nullable final String propertyName, @Nullable final PsiExpression expr, @Nullable PsiType type) {
    return suggestVariableName(kind, propertyName, expr, type, true);
  }

  /**
   * Generates compiled parameter name for given type.
   * Should not access indices due to performance reasons (e.g. see IDEA-116803)
   */
  @Nonnull
  public SuggestedNameInfo suggestCompiledParameterName(@Nonnull PsiType type) {
    return suggestVariableName(VariableKind.PARAMETER, null, null, type, true);
  }


  @Nonnull
  public abstract SuggestedNameInfo suggestVariableName(@Nonnull VariableKind kind, @Nullable String propertyName, @Nullable PsiExpression expr, @Nullable PsiType type, boolean correctKeywords);

  /**
   * Generates a stripped-down name (with no code style defined prefixes or suffixes, usable as
   * a property name) from the specified name of a variable of the specified kind.
   *
   * @param name         the name of the variable.
   * @param variableKind the kind of the variable.
   * @return the stripped-down name.
   */
  @Nonnull
  public abstract String variableNameToPropertyName(@NonNls @Nonnull String name, @Nonnull VariableKind variableKind);

  /**
   * Appends code style defined prefixes and/or suffixes for the specified variable kind
   * to the specified variable name.
   *
   * @param propertyName the base name of the variable.
   * @param variableKind the kind of the variable.
   * @return the variable name.
   */
  @Nonnull
  public abstract String propertyNameToVariableName(@NonNls @Nonnull String propertyName, @Nonnull VariableKind variableKind);

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseName    the base name for the variable.
   * @param place       the location where the variable will be used.
   * @param lookForward if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name,
   */
  @Nonnull
  public abstract String suggestUniqueVariableName(@NonNls @Nonnull String baseName, PsiElement place, boolean lookForward);

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseNameInfo the base name info for the variable.
   * @param place        the location where the variable will be used.
   * @param lookForward  if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name
   */
  @Nonnull
  public SuggestedNameInfo suggestUniqueVariableName(@Nonnull SuggestedNameInfo baseNameInfo, PsiElement place, boolean lookForward) {
    return suggestUniqueVariableName(baseNameInfo, place, false, lookForward);
  }

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseNameInfo    the base name info for the variable.
   * @param place           the location where the variable will be used.
   * @param ignorePlaceName if true and place is PsiNamedElement, place.getName() would be still treated as unique name
   * @param lookForward     if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name
   */

  @Nonnull
  public abstract SuggestedNameInfo suggestUniqueVariableName(@Nonnull SuggestedNameInfo baseNameInfo, PsiElement place, boolean ignorePlaceName, boolean lookForward);

  /**
   * Replaces all references to Java classes in the contents of the specified element,
   * except for references to classes in the same package or in implicitly imported packages,
   * with full-qualified references.
   *
   * @param element the element to replace the references in.
   * @return the element in the PSI tree after the qualify operation corresponding to the original element.
   */
  @Nonnull
  public abstract PsiElement qualifyClassReferences(@Nonnull PsiElement element);

  /**
   * Removes unused import statements from the specified Java file.
   *
   * @param file the file to remove the import statements from.
   * @throws IncorrectOperationException if the operation fails for some reason (for example, the file is read-only).
   */
  public abstract void removeRedundantImports(@Nonnull PsiJavaFile file) throws IncorrectOperationException;

  @Nullable
  public abstract Collection<PsiImportStatementBase> findRedundantImports(@Nonnull PsiJavaFile file);
}
