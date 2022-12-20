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
package consulo.java.analysis.impl.util;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.codeInsight.template.macro.MacroUtil;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ParameterizedCachedValue;
import consulo.application.util.ParameterizedCachedValueProvider;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author max
 */
public class JavaI18nUtil {
  private JavaI18nUtil() {
  }

  @Nullable
  public static TextRange getSelectedRange(Editor editor, final PsiFile psiFile) {
    if (editor == null) return null;
    String selectedText = editor.getSelectionModel().getSelectedText();
    if (selectedText != null) {
      return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
    }
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement == null || psiElement instanceof PsiWhiteSpace) return null;
    return psiElement.getTextRange();
  }

  public static boolean mustBePropertyKey(@Nonnull Project project,
                                          @Nonnull PsiLiteralExpression expression,
                                          @Nonnull Map<String, Object> annotationAttributeValues) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation((PsiVariable)parent, AnnotationUtil.PROPERTY_KEY);
      if (annotation != null) {
        return processAnnotationAttributes(annotationAttributeValues, annotation);
      }
    }
    return isPassedToAnnotatedParam(project, expression, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null);
  }

  public static boolean isPassedToAnnotatedParam(@Nonnull Project project,
                                                 @Nonnull PsiExpression expression,
                                                 final String annFqn,
                                                 @Nullable Map<String, Object> annotationAttributeValues,
                                                 @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    expression = getToplevelExpression(project, expression);
    final PsiElement parent = expression.getParent();

    if (!(parent instanceof PsiExpressionList)) return false;
    int idx = -1;
    final PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];
      if (PsiTreeUtil.isAncestor(arg, expression, false)) {
        idx = i;
        break;
      }
    }
    if (idx == -1) return false;

    PsiElement grParent = parent.getParent();

    if (grParent instanceof PsiAnonymousClass) {
      grParent = grParent.getParent();
    }

    if (grParent instanceof PsiCall) {
      PsiMethod method = ((PsiCall)grParent).resolveMethod();
      if (method != null && isMethodParameterAnnotatedWith(method, idx, null, annFqn, annotationAttributeValues, nonNlsTargets)) {
        return true;
      }
    }

    return false;
  }

  private static final Key<ParameterizedCachedValue<PsiExpression, Pair<Project, PsiExpression>>> TOP_LEVEL_EXPRESSION =
    Key.create("TOP_LEVEL_EXPRESSION");
  private static final ParameterizedCachedValueProvider<PsiExpression, Pair<Project, PsiExpression>> TOP_LEVEL_PROVIDER =
    new ParameterizedCachedValueProvider<PsiExpression, Pair<Project, PsiExpression>>() {
      @Override
      public CachedValueProvider.Result<PsiExpression> compute(Pair<Project, PsiExpression> pair) {
        PsiExpression param = pair.second;
        Project project = pair.first;
        PsiExpression topLevel = getTopLevel(project, param);
        ParameterizedCachedValue<PsiExpression, Pair<Project, PsiExpression>> cachedValue = param.getUserData(TOP_LEVEL_EXPRESSION);
        assert cachedValue != null;
        int i = 0;
        for (PsiElement element = param; element != topLevel; element = element.getParent(), i++) {
          if (i % 10 == 0) {   // optimization: store up link to the top level expression in each 10nth element
            element.putUserData(TOP_LEVEL_EXPRESSION, cachedValue);
          }
        }
        return CachedValueProvider.Result.create(topLevel, PsiManager.getInstance(project).getModificationTracker());
      }
    };

  @Nonnull
  public static PsiExpression getToplevelExpression(@Nonnull final Project project, @Nonnull final PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression || expression.getParent() instanceof PsiBinaryExpression) {  //can be large, cache
      return CachedValuesManager.getManager(project).getParameterizedCachedValue(expression, TOP_LEVEL_EXPRESSION, TOP_LEVEL_PROVIDER, true,
                                                                                 Pair.create(project, expression));
    }
    return getTopLevel(project, expression);
  }

  @Nonnull
  private static PsiExpression getTopLevel(Project project, @Nonnull PsiExpression expression) {
    int i = 0;
    while (expression.getParent() instanceof PsiExpression) {
      i++;
      final PsiExpression parent = (PsiExpression)expression.getParent();
      if (parent instanceof PsiConditionalExpression &&
        ((PsiConditionalExpression)parent).getCondition() == expression) break;
      expression = parent;
      if (expression instanceof PsiAssignmentExpression) break;
      if (i > 10 && expression instanceof PsiBinaryExpression) {
        ParameterizedCachedValue<PsiExpression, Pair<Project, PsiExpression>> value = expression.getUserData(TOP_LEVEL_EXPRESSION);
        if (value != null && value.hasUpToDateValue()) {
          return getToplevelExpression(project, expression); // optimization: use caching for big hierarchies
        }
      }
    }
    return expression;
  }

  public static boolean isMethodParameterAnnotatedWith(final PsiMethod method,
                                                       final int idx,
                                                       @Nullable Collection<PsiMethod> processed,
                                                       final String annFqn,
                                                       @Nullable Map<String, Object> annotationAttributeValues,
                                                       @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    if (processed != null) {
      if (processed.contains(method)) return false;
    }
    else {
      processed = new HashSet<PsiMethod>();
    }
    processed.add(method);

    final PsiParameter[] params = method.getParameterList().getParameters();
    PsiParameter param;
    if (idx >= params.length) {
      if (params.length == 0) {
        return false;
      }
      PsiParameter lastParam = params[params.length - 1];
      if (lastParam.isVarArgs()) {
        param = lastParam;
      }
      else {
        return false;
      }
    }
    else {
      param = params[idx];
    }
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(param, annFqn);
    if (annotation != null) {
      return processAnnotationAttributes(annotationAttributeValues, annotation);
    }
    if (nonNlsTargets != null) {
      nonNlsTargets.add(param);
    }

    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (isMethodParameterAnnotatedWith(superMethod, idx, processed, annFqn, annotationAttributeValues, null))
        return true;
    }

    return false;
  }

  private static boolean processAnnotationAttributes(@Nullable Map<String, Object> annotationAttributeValues,
                                                     @Nonnull PsiAnnotation annotation) {
    if (annotationAttributeValues != null) {
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        final String name = attribute.getName();
        if (annotationAttributeValues.containsKey(name)) {
          annotationAttributeValues.put(name, attribute.getValue());
        }
      }
    }
    return true;
  }

  public static Set<String> suggestExpressionOfType(final PsiClassType type, final PsiLiteralExpression context) {
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(context, "");
    Set<String> result = new LinkedHashSet<String>();
    for (PsiVariable var : variables) {
      PsiType varType = var.getType();
      if (type == null || type.isAssignableFrom(varType)) {
        result.add(var.getNameIdentifier().getText());
      }
    }

    PsiExpression[] expressions = MacroUtil.getStandardExpressionsOfType(context, type);
    for (PsiExpression expression : expressions) {
      result.add(expression.getText());
    }
    if (type != null) {
      addAvailableMethodsOfType(type, context, result);
    }
    return result;
  }

  private static void addAvailableMethodsOfType(final PsiClassType type,
                                                final PsiLiteralExpression context,
                                                final Collection<String> result) {
    PsiScopesUtil.treeWalkUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@Nonnull PsiElement element, ResolveState state) {
        if (element instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)element;
          PsiType returnType = method.getReturnType();
          if (returnType != null && TypeConversionUtil.isAssignable(type, returnType)
            && method.getParameterList().getParametersCount() == 0) {
            result.add(method.getName() + "()");
          }
        }
        return true;
      }

      @Override
      public <T> T getHint(@Nonnull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, Object associated) {

      }
    }, context, null);
  }
}
