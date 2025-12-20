/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.BindFieldsFromParametersAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class BindFieldsFromParametersAction extends BaseIntentionAction implements HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(CreateFieldFromParameterAction.class);
  private static final Key<Map<SmartPsiElementPointer<PsiParameter>, Boolean>> PARAMS = Key.create("FIELDS_FROM_PARAMS");

  private static final Object LOCK = new Object();

  public BindFieldsFromParametersAction() {
    setText(CodeInsightLocalize.intentionBindFieldsFromParametersFamily());
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    PsiParameter psiParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    PsiMethod method = findMethod(psiParameter, editor, file);
    if (method == null) return false;

    List<PsiParameter> parameters = getAvailableParameters(method);

    synchronized (LOCK) {
      Collection<SmartPsiElementPointer<PsiParameter>> params = getUnboundedParams(method);
      params.clear();
      for (PsiParameter parameter : parameters) {
        params.add(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parameter));
      }
      if (params.isEmpty()) return false;
      if (params.size() == 1 && psiParameter != null) return false;
      if (psiParameter == null) {
        psiParameter = params.iterator().next().getElement();
        LOG.assertTrue(psiParameter != null);
      }

      setText(CodeInsightLocalize.intentionBindFieldsFromParametersText(method.isConstructor() ? "Constructor" : "Method"));
    }
    return isAvailable(psiParameter);
  }

  @Nullable
  @RequiredReadAction
  private static PsiMethod findMethod(@Nullable PsiParameter parameter, @Nonnull Editor editor, @Nonnull PsiFile file) {
    if (parameter == null) {
      PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
      if (elementAt instanceof PsiIdentifier) {
        PsiElement parent = elementAt.getParent();
        if (parent instanceof PsiMethod method) {
          return method;
        }
      }
    }
    else {
      PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod method) {
        return method;
      }
    }

    return null;
  }

  @Nonnull
  @RequiredReadAction
  private static List<PsiParameter> getAvailableParameters(@Nonnull PsiMethod method) {
    List<PsiParameter> parameters = new ArrayList<>();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (isAvailable(parameter)) {
        parameters.add(parameter);
      }
    }
    return parameters;
  }

  @RequiredReadAction
  private static boolean isAvailable(PsiParameter psiParameter) {
    PsiType type = FieldFromParameterUtils.getSubstitutedType(psiParameter);
    PsiClass targetClass = PsiTreeUtil.getParentOfType(psiParameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(psiParameter, type, targetClass) &&
      psiParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Nonnull
  private static Collection<SmartPsiElementPointer<PsiParameter>> getUnboundedParams(PsiMethod psiMethod) {
    Map<SmartPsiElementPointer<PsiParameter>, Boolean> params = psiMethod.getUserData(PARAMS);
    if (params == null) psiMethod.putUserData(PARAMS, params = ContainerUtil.createConcurrentWeakMap());
    final Map<SmartPsiElementPointer<PsiParameter>, Boolean> finalParams = params;
    return new AbstractCollection<>() {
      @Override
      public boolean add(SmartPsiElementPointer<PsiParameter> psiVariable) {
        return finalParams.put(psiVariable, Boolean.TRUE) == null;
      }

      @Override
      public Iterator<SmartPsiElementPointer<PsiParameter>> iterator() {
        return finalParams.keySet().iterator();
      }

      @Override
      public int size() {
        return finalParams.size();
      }

      @Override
      public void clear() {
        finalParams.clear();
      }
    };
  }

  @Override
  @RequiredUIAccess
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, !project.getApplication().isUnitTestMode());
  }

  @RequiredUIAccess
  private static void invoke(Project project, Editor editor, PsiFile file, boolean isInteractive) {
    PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiMethod method = myParameter != null ? (PsiMethod)myParameter.getDeclarationScope()
      : PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiMethod.class);
    LOG.assertTrue(method != null);

    HashSet<String> usedNames = new HashSet<>();
    for (PsiParameter selected : selectParameters(project, method, copyUnboundedParamsAndClearOriginal(method), isInteractive)) {
      processParameter(project, selected, usedNames);
    }
  }

  @Nonnull
  @RequiredReadAction
  private static Iterable<PsiParameter> selectParameters(
    @Nonnull Project project,
    @Nonnull PsiMethod method,
    @Nonnull Collection<SmartPsiElementPointer<PsiParameter>> unboundedParams,
    boolean isInteractive
  ) {
    if (unboundedParams.size() < 2 || !isInteractive) {
      return revealPointers(unboundedParams);
    }

    ParameterClassMember[] members = sortByParameterIndex(toClassMemberArray(unboundedParams), method);

    MemberChooser<ParameterClassMember> chooser = showChooser(project, method, members);

    List<ParameterClassMember> selectedElements = chooser.getSelectedElements();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE || selectedElements == null) {
      return Collections.emptyList();
    }

    return revealParameterClassMembers(selectedElements);
  }

  @Nonnull
  private static MemberChooser<ParameterClassMember> showChooser(
    @Nonnull Project project,
    @Nonnull PsiMethod method,
    @Nonnull ParameterClassMember[] members
  ) {
    MemberChooser<ParameterClassMember> chooser = new MemberChooser<>(members, false, true, project);
    chooser.selectElements(members);
    chooser.setTitle("Choose " + (method.isConstructor() ? "Constructor" : "Method") + " Parameters");
    chooser.show();
    return chooser;
  }

  @Nonnull
  private static ParameterClassMember[] sortByParameterIndex(@Nonnull ParameterClassMember[] members, @Nonnull PsiMethod method) {
    PsiParameterList parameterList = method.getParameterList();
    Arrays.sort(
      members,
      (o1, o2) -> parameterList.getParameterIndex(o1.getParameter()) - parameterList.getParameterIndex(o2.getParameter())
    );
    return members;
  }

  @Nonnull
  @RequiredReadAction
  private static <T extends PsiElement> List<T> revealPointers(@Nonnull Iterable<SmartPsiElementPointer<T>> pointers) {
    List<T> result = new ArrayList<>();
    for (SmartPsiElementPointer<T> pointer : pointers) {
      result.add(pointer.getElement());
    }
    return result;
  }

  @Nonnull
  private static List<PsiParameter> revealParameterClassMembers(@Nonnull Iterable<ParameterClassMember> parameterClassMembers) {
    List<PsiParameter> result = new ArrayList<>();
    for (ParameterClassMember parameterClassMember : parameterClassMembers) {
      result.add(parameterClassMember.getParameter());
    }
    return result;
  }

  @Nonnull
  @RequiredReadAction
  private static ParameterClassMember[] toClassMemberArray(@Nonnull Collection<SmartPsiElementPointer<PsiParameter>> unboundedParams) {
    ParameterClassMember[] result = new ParameterClassMember[unboundedParams.size()];
    int i = 0;
    for (SmartPsiElementPointer<PsiParameter> pointer : unboundedParams) {
      result[i++] = new ParameterClassMember(pointer.getElement());
    }
    return result;
  }

  @Nonnull
  private static Collection<SmartPsiElementPointer<PsiParameter>> copyUnboundedParamsAndClearOriginal(@Nonnull PsiMethod method) {
    synchronized (LOCK) {
      Collection<SmartPsiElementPointer<PsiParameter>> unboundedParams = getUnboundedParams(method);
      Collection<SmartPsiElementPointer<PsiParameter>> result = new ArrayList<>(unboundedParams);
      unboundedParams.clear();
      return result;
    }
  }

  @RequiredUIAccess
  private static void processParameter(
    Project project,
    PsiParameter myParameter,
    Set<String> usedNames
  ) {
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    PsiType type = FieldFromParameterUtils.getSubstitutedType(myParameter);
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    boolean isFinal = !isMethodStatic && method.isConstructor();
    String name = names[0];
    if (targetClass != null) {
      for (String curName : names) {
        if (!usedNames.contains(curName) && targetClass.findFieldByName(curName, false) != null) {
          name = curName;
          break;
        }
      }
    }
    String fieldName = usedNames.add(name) ? name
      : JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, myParameter, true);

    project.getApplication().runWriteAction(() -> {
      try {
        FieldFromParameterUtils.createFieldAndAddAssignment(
          project,
          targetClass,
          method,
          myParameter,
          type,
          fieldName,
          isMethodStatic,
          isFinal);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
