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
package consulo.java.properties.impl.i18n;

import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class InvalidPropertyKeyInspection extends BaseJavaLocalInspectionTool {
  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.PROPERTIES_GROUP_NAME;
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getDisplayName() {
    return CodeInsightBundle.message("inspection.unresolved.property.key.reference.name");
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getShortName() {
    return "UnresolvedPropertyKey";
  }

  @Override
  @Nonnull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @jakarta.annotation.Nullable
  public ProblemDescriptor[] checkMethod(@Nonnull PsiMethod method, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    return checkElement(method, manager, isOnTheFly);
  }

  @Override
  @jakarta.annotation.Nullable
  public ProblemDescriptor[] checkClass(@jakarta.annotation.Nonnull PsiClass aClass, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer, manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    }

    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  @Override
  @jakarta.annotation.Nullable
  public ProblemDescriptor[] checkField(@jakarta.annotation.Nonnull PsiField field, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    appendProblems(manager, isOnTheFly, result, field.getInitializer());
    appendProblems(manager, isOnTheFly, result, field.getModifierList());
    if (field instanceof PsiEnumConstant) {
      appendProblems(manager, isOnTheFly, result, ((PsiEnumConstant)field).getArgumentList());
    }
    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  private static void appendProblems(InspectionManager manager, boolean isOnTheFly, List<ProblemDescriptor> result, PsiElement element) {
    if (element != null) {
      final ProblemDescriptor[] descriptors = checkElement(element, manager, isOnTheFly);
      if (descriptors != null) {
        Collections.addAll(result, descriptors);
      }
    }
  }

  @Nullable
  private static ProblemDescriptor[] checkElement(PsiElement element, final InspectionManager manager, boolean onTheFly) {
    UnresolvedPropertyVisitor visitor = new UnresolvedPropertyVisitor(manager, onTheFly);
    element.accept(visitor);
    List<ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static class UnresolvedPropertyVisitor extends JavaRecursiveElementWalkingVisitor {
    private final InspectionManager myManager;
    private final List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();
    private final boolean onTheFly;


    public UnresolvedPropertyVisitor(final InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      this.onTheFly = onTheFly;
    }

    @Override
    public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList argList = aClass.getArgumentList();
      if (argList != null) {
        argList.accept(this);
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
    }

    @Override
    public void visitField(PsiField field) {
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return;
      String key = (String)value;
      if (isComputablePropertyExpression(expression)) return;
      Ref<String> resourceBundleName = new Ref<String>();
      if (!JavaPropertiesUtil.isValidPropertyReference(myManager.getProject(), expression, key, resourceBundleName)) {
        String bundleName = resourceBundleName.get();
        if (bundleName != null) { // can be null if we were unable to resolve literal expression, e.g. when JDK was not set
          appendPropertyKeyNotFoundProblem(bundleName, key, expression, myManager, myProblems, onTheFly);
        }
      }
      else if (expression.getParent() instanceof PsiNameValuePair) {
        PsiNameValuePair nvp = (PsiNameValuePair)expression.getParent();
        if (Comparing.equal(nvp.getName(), AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
          PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(expression.getProject());
          Module module = ModuleUtilCore.findModuleForPsiElement(expression);
          if (module != null) {
            List<PropertiesFile> propFiles = manager.findPropertiesFiles(module, key);
            if (propFiles.isEmpty()) {
              final String description = CodeInsightBundle.message("inspection.invalid.resource.bundle.reference", key);
              final ProblemDescriptor problem = myManager.createProblemDescriptor(expression,
                                                                                  description,
                                                                                  (LocalQuickFix)null,
                                                                                  ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly);
              myProblems.add(problem);
            }
          }
        }
      }
      else if (expression.getParent() instanceof PsiExpressionList && expression.getParent()
                                                                                .getParent() instanceof PsiMethodCallExpression) {
        final Map<String, Object> annotationParams = new HashMap<String, Object>();
        annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        if (!JavaI18nUtil.mustBePropertyKey(myManager.getProject(), expression, annotationParams)) return;

        final int paramsCount = JavaPropertiesUtil.getPropertyValueParamsMaxCount(expression);
        if (paramsCount == -1) return;

        final PsiExpressionList expressions = (PsiExpressionList)expression.getParent();
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expressions.getParent();
        final PsiMethod method = methodCall.resolveMethod();
        final PsiExpression[] args = expressions.getExpressions();
        for (int i = 0; i < args.length; i++) {
          if (args[i] == expression) {
            if (i + paramsCount >= args.length
              && method != null
              && method.getParameterList().getParametersCount() == i + 2
              && method.getParameterList().getParameters()[i + 1].isVarArgs()
              && !hasArrayTypeAt(i + 1, methodCall)) {
              myProblems.add(myManager.createProblemDescriptor(methodCall,
                                                               CodeInsightBundle.message("property.has.more.parameters.than.passed",
                                                                                         key,
                                                                                         paramsCount,
                                                                                         args.length - i - 1),
                                                               onTheFly, new LocalQuickFix[0],
                                                               ProblemHighlightType.GENERIC_ERROR));
            }
            break;
          }
        }
      }
    }

    private static void appendPropertyKeyNotFoundProblem(@Nonnull String bundleName,
                                                         @jakarta.annotation.Nonnull String key,
                                                         @Nonnull PsiLiteralExpression expression,
                                                         @Nonnull InspectionManager manager,
                                                         @jakarta.annotation.Nonnull List<ProblemDescriptor> problems,
                                                         boolean onTheFly) {
      final String description = CodeInsightBundle.message("inspection.unresolved.property.key.reference.message", key);
      final List<PropertiesFile> propertiesFiles =
        filterNotInLibrary(expression.getProject(), I18nUtil.propertiesFilesByBundleName(bundleName, expression));
      problems.add(
        manager.createProblemDescriptor(
          expression,
          description,
          propertiesFiles.isEmpty() ? null : new JavaCreatePropertyFix(expression, key, propertiesFiles),
          ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly
        )
      );
    }

    @jakarta.annotation.Nonnull
    private static List<PropertiesFile> filterNotInLibrary(@jakarta.annotation.Nonnull Project project,
                                                           @jakarta.annotation.Nonnull List<PropertiesFile> propertiesFiles) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

      final List<PropertiesFile> result = new ArrayList<PropertiesFile>(propertiesFiles.size());
      for (final PropertiesFile file : propertiesFiles) {
        if (!fileIndex.isInLibraryClasses(file.getVirtualFile()) && !fileIndex.isInLibrarySource(file.getVirtualFile())) {
          result.add(file);
        }
      }
      return result;
    }

    private static boolean hasArrayTypeAt(int i, PsiMethodCallExpression methodCall) {
      return methodCall != null
        && methodCall.getArgumentList().getExpressionTypes().length > i
        && methodCall.getArgumentList().getExpressionTypes()[i] instanceof PsiArrayType;
    }

    private static boolean isComputablePropertyExpression(PsiExpression expression) {
      while (expression != null && expression.getParent() instanceof PsiParenthesizedExpression)
        expression = (PsiExpression)expression.getParent();
      return expression != null && expression.getParent() instanceof PsiExpression;
    }

    public List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }
}
