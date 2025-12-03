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

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.SimpleReference;
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
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesPropertiesFiles();
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return CodeInsightLocalize.inspectionUnresolvedPropertyKeyReferenceName();
    }

    @Override
    @Nonnull
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
    @Nullable
    public ProblemDescriptor[] checkMethod(
        @Nonnull PsiMethod method,
        @Nonnull InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
        return checkElement(method, manager, isOnTheFly);
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkClass(@Nonnull PsiClass aClass, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
        PsiClassInitializer[] initializers = aClass.getInitializers();
        List<ProblemDescriptor> result = new ArrayList<>();
        for (PsiClassInitializer initializer : initializers) {
            ProblemDescriptor[] descriptors = checkElement(initializer, manager, isOnTheFly);
            if (descriptors != null) {
                ContainerUtil.addAll(result, descriptors);
            }
        }

        return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkField(@Nonnull PsiField field, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
        List<ProblemDescriptor> result = new ArrayList<>();
        appendProblems(manager, isOnTheFly, result, field.getInitializer());
        appendProblems(manager, isOnTheFly, result, field.getModifierList());
        if (field instanceof PsiEnumConstant enumConstant) {
            appendProblems(manager, isOnTheFly, result, enumConstant.getArgumentList());
        }
        return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
    }

    private static void appendProblems(InspectionManager manager, boolean isOnTheFly, List<ProblemDescriptor> result, PsiElement element) {
        if (element != null) {
            ProblemDescriptor[] descriptors = checkElement(element, manager, isOnTheFly);
            if (descriptors != null) {
                Collections.addAll(result, descriptors);
            }
        }
    }

    @Nullable
    private static ProblemDescriptor[] checkElement(PsiElement element, InspectionManager manager, boolean onTheFly) {
        UnresolvedPropertyVisitor visitor = new UnresolvedPropertyVisitor(manager, onTheFly);
        element.accept(visitor);
        List<ProblemDescriptor> problems = visitor.getProblems();
        return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    private static class UnresolvedPropertyVisitor extends JavaRecursiveElementWalkingVisitor {
        private final InspectionManager myManager;
        private final List<ProblemDescriptor> myProblems = new ArrayList<>();
        private final boolean onTheFly;

        public UnresolvedPropertyVisitor(InspectionManager manager, boolean onTheFly) {
            myManager = manager;
            this.onTheFly = onTheFly;
        }

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) {
            PsiExpressionList argList = aClass.getArgumentList();
            if (argList != null) {
                argList.accept(this);
            }
        }

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
        }

        @Override
        public void visitField(@Nonnull PsiField field) {
        }

        @Override
        @RequiredReadAction
        public void visitLiteralExpression(PsiLiteralExpression expression) {
            Object value = expression.getValue();
            if (!(value instanceof String key)) {
                return;
            }
            if (isComputablePropertyExpression(expression)) {
                return;
            }
            SimpleReference<String> resourceBundleName = new SimpleReference<>();
            if (!JavaPropertiesUtil.isValidPropertyReference(myManager.getProject(), expression, key, resourceBundleName)) {
                String bundleName = resourceBundleName.get();
                if (bundleName != null) { // can be null if we were unable to resolve literal expression, e.g. when JDK was not set
                    appendPropertyKeyNotFoundProblem(bundleName, key, expression, myManager, myProblems, onTheFly);
                }
            }
            else if (expression.getParent() instanceof PsiNameValuePair nvp) {
                if (Comparing.equal(nvp.getName(), AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
                    PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(expression.getProject());
                    Module module = expression.getModule();
                    if (module != null) {
                        List<PropertiesFile> propFiles = manager.findPropertiesFiles(module, key);
                        if (propFiles.isEmpty()) {
                            LocalizeValue description = CodeInsightLocalize.inspectionInvalidResourceBundleReference(key);
                            ProblemDescriptor problem = myManager.newProblemDescriptor(description)
                                .range(expression)
                                .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                                .onTheFly(onTheFly)
                                .create();
                            myProblems.add(problem);
                        }
                    }
                }
            }
            else if (expression.getParent() instanceof PsiExpressionList expressions
                && expression.getParent().getParent() instanceof PsiMethodCallExpression methodCall) {
                Map<String, Object> annotationParams = new HashMap<>();
                annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
                if (!JavaI18nUtil.mustBePropertyKey(myManager.getProject(), expression, annotationParams)) {
                    return;
                }

                int paramsCount = JavaPropertiesUtil.getPropertyValueParamsMaxCount(expression);
                if (paramsCount == -1) {
                    return;
                }

                PsiMethod method = methodCall.resolveMethod();
                PsiExpression[] args = expressions.getExpressions();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == expression) {
                        if (i + paramsCount >= args.length
                            && method != null
                            && method.getParameterList().getParametersCount() == i + 2
                            && method.getParameterList().getParameters()[i + 1].isVarArgs()
                            && !hasArrayTypeAt(i + 1, methodCall)) {
                            LocalQuickFix[] fixes = new LocalQuickFix[0];
                            myProblems.add(
                                myManager.newProblemDescriptor(CodeInsightLocalize.propertyHasMoreParametersThanPassed(
                                        key,
                                        paramsCount,
                                        args.length - i - 1
                                    ))
                                    .range(methodCall)
                                    .highlightType(ProblemHighlightType.GENERIC_ERROR)
                                    .onTheFly(onTheFly)
                                    .withFixes(fixes)
                                    .create()
                            );
                        }
                        break;
                    }
                }
            }
        }

        @RequiredReadAction
        private static void appendPropertyKeyNotFoundProblem(
            @Nonnull String bundleName,
            @Nonnull String key,
            @Nonnull PsiLiteralExpression expression,
            @Nonnull InspectionManager manager,
            @Nonnull List<ProblemDescriptor> problems,
            boolean onTheFly
        ) {
            List<PropertiesFile> propertiesFiles =
                filterNotInLibrary(expression.getProject(), I18nUtil.propertiesFilesByBundleName(bundleName, expression));
            problems.add(
                manager.newProblemDescriptor(CodeInsightLocalize.inspectionUnresolvedPropertyKeyReferenceMessage(key))
                    .range(expression)
                    .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                    .onTheFly(onTheFly)
                    .withOptionalFix(propertiesFiles.isEmpty() ? null : new JavaCreatePropertyFix(expression, key, propertiesFiles))
                    .create()
            );
        }

        @Nonnull
        private static List<PropertiesFile> filterNotInLibrary(@Nonnull Project project, @Nonnull List<PropertiesFile> propertiesFiles) {
            ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

            List<PropertiesFile> result = new ArrayList<>(propertiesFiles.size());
            for (PropertiesFile file : propertiesFiles) {
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
            while (expression != null && expression.getParent() instanceof PsiParenthesizedExpression parenthesizedExpr) {
                expression = parenthesizedExpr;
            }
            return expression != null && expression.getParent() instanceof PsiExpression;
        }

        public List<ProblemDescriptor> getProblems() {
            return myProblems;
        }
    }
}
