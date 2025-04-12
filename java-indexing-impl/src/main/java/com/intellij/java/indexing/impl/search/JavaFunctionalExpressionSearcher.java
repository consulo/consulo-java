/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.indexing.impl.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearchExecutor;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.application.Application;
import consulo.application.util.ReadActionProcessor;
import consulo.application.util.function.CommonProcessors;
import consulo.content.scope.SearchScope;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.*;
import consulo.language.psi.scope.EverythingGlobalScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.UsageSearchContext;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static consulo.util.collection.ContainerUtil.addIfNotNull;
import static consulo.util.collection.ContainerUtil.process;

@ExtensionImpl
public class JavaFunctionalExpressionSearcher
    extends QueryExecutorBase<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters>
    implements FunctionalExpressionSearchExecutor {

    private static record ClassLambdaInfo(Project project, GlobalSearchScope scope, int expectedFunExprParamsCount) {
    }

    private static final Logger LOG = Logger.getInstance(JavaFunctionalExpressionSearcher.class);
    /**
     * The least number of candidate files with functional expressions that directly scanning them becomes expensive
     * and more advanced ways of searching become necessary: e.g. first searching for methods where the functional interface class is used
     * and then for their usages,
     */
    public static final int SMART_SEARCH_THRESHOLD = 5;

    @Override
    @RequiredReadAction
    public void processQuery(
        @Nonnull FunctionalExpressionSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiFunctionalExpression> consumer
    ) {
        PsiClass aClass = queryParameters.getElementToSearch();

        ClassLambdaInfo classLambdaInfo = AccessRule.read(() -> {
            if (!aClass.isValid() || !LambdaUtil.isFunctionalClass(aClass)) {
                return null;
            }

            Project project = aClass.getProject();
            Set<Module> highLevelModules = getJava8Modules(project);
            if (highLevelModules.isEmpty()) {
                return null;
            }

            GlobalSearchScope useScope =
                convertToGlobalScope(project, queryParameters.getEffectiveSearchScope());

            MethodSignature functionalInterfaceMethod = LambdaUtil.getFunction(aClass);
            LOG.assertTrue(functionalInterfaceMethod != null);
            int expectedFunExprParamsCount = functionalInterfaceMethod.getParameterTypes().length;
            return new ClassLambdaInfo(project, useScope, expectedFunExprParamsCount);
        });

        if (classLambdaInfo == null) {
            return;
        }

        Project project = classLambdaInfo.project();
        GlobalSearchScope useScope = classLambdaInfo.scope();
        int expectedFunExprParamsCount = classLambdaInfo.expectedFunExprParamsCount();

        //collect all files with '::' and '->' in useScope
        Set<VirtualFile> candidateFiles = getFilesWithFunctionalExpressionsScope(project, new JavaSourceFilterScope(useScope));
        if (candidateFiles.size() < SMART_SEARCH_THRESHOLD) {
            searchInFiles(aClass, consumer, candidateFiles, expectedFunExprParamsCount);
            return;
        }

        GlobalSearchScope candidateScope = GlobalSearchScope.filesScope(project, candidateFiles);

        //collect all methods with parameter of functional interface or free type parameter type
        Collection<PsiMethod> methodCandidates =
            getCandidateMethodsWithSuitableParams(aClass, project, useScope, candidateFiles, candidateScope);

        LinkedHashSet<VirtualFile> filesToProcess = new LinkedHashSet<>();
        FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();

        Application app = project.getApplication();

        //find all usages of method candidates in files with functional expressions
        for (PsiMethod psiMethod : methodCandidates) {
            app.runReadAction(() -> {
                if (!psiMethod.isValid()) {
                    return;
                }
                int parametersCount = psiMethod.getParameterList().getParametersCount();
                boolean varArgs = psiMethod.isVarArgs();
                PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                GlobalSearchScope methodUseScope = convertToGlobalScope(project, psiMethod.getUseScope());
                LinkedHashMap<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> holders = new LinkedHashMap<>();
                //functional expressions checker: number and type of parameters at call site should correspond to candidate method currently check
                SuitableFilesProcessor processor =
                    new SuitableFilesProcessor(holders, expectedFunExprParamsCount, parametersCount, varArgs, parameters);
                fileBasedIndex.processValues(
                    JavaFunctionalExpressionIndex.JAVA_FUNCTIONAL_EXPRESSION_INDEX_ID,
                    psiMethod.getName(),
                    null,
                    processor,
                    useScope.intersectWith(methodUseScope)
                );
                for (Map.Entry<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> entry : holders.entrySet()) {
                    for (JavaFunctionalExpressionIndex.IndexHolder holder : entry.getValue()) {
                        if (processor.canBeFunctional(holder)) {
                            filesToProcess.add(entry.getKey());
                            break;
                        }
                    }
                }
            });
        }

        //search for functional expressions in non-call contexts
        collectFilesWithTypeOccurrencesAndFieldAssignments(aClass, candidateScope, filesToProcess);

        searchInFiles(aClass, consumer, filesToProcess, expectedFunExprParamsCount);
    }

    @Nonnull
    @RequiredReadAction
    private static Set<Module> getJava8Modules(Project project) {
        Set<Module> highLevelModules = new HashSet<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            JavaModuleExtension moduleExtension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
            if (moduleExtension != null && moduleExtension.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_8)) {
                highLevelModules.add(module);
            }
        }
        return highLevelModules;
    }

    private static void searchInFiles(
        PsiClass aClass,
        Predicate<? super PsiFunctionalExpression> consumer,
        Set<VirtualFile> filesToProcess,
        int expectedFunExprParamsCount
    ) {
        LOG.info("#usage files: " + filesToProcess.size());
        process(filesToProcess, new ReadActionProcessor<>() {
            @RequiredReadAction
            @Override
            public boolean processInReadAction(VirtualFile file) {
                //resolve functional expressions to ensure that functional expression type is appropriate
                return processFileWithFunctionalInterfaces(aClass, expectedFunExprParamsCount, consumer, file);
            }
        });
    }

    @RequiredReadAction
    private static Collection<PsiMethod> getCandidateMethodsWithSuitableParams(
        PsiClass aClass,
        Project project,
        GlobalSearchScope useScope,
        Set<VirtualFile> candidateFiles,
        GlobalSearchScope candidateScope
    ) {
        return Application.get().runReadAction((Supplier<Collection<PsiMethod>>)() -> {
            if (!aClass.isValid()) {
                return Collections.emptyList();
            }

            GlobalSearchScope visibleFromCandidates = combineResolveScopes(project, candidateFiles);

            Set<String> usedMethodNames = new HashSet<>();
            FileBasedIndex.getInstance().processAllKeys(
                JavaFunctionalExpressionIndex.JAVA_FUNCTIONAL_EXPRESSION_INDEX_ID,
                new CommonProcessors.CollectProcessor<>(usedMethodNames),
                candidateScope,
                null
            );

            LinkedHashSet<PsiMethod> methods = new LinkedHashSet<>();
            Predicate<PsiMethod> methodProcessor = method -> {
                if (usedMethodNames.contains(method.getName())) {
                    methods.add(method);
                }
                return true;
            };

            StubIndexKey<String, PsiMethod> key = JavaMethodParameterTypesIndex.getInstance().getKey();
            StubIndex index = StubIndex.getInstance();
            index.processElements(
                key,
                aClass.getName(),
                project,
                useScope.intersectWith(visibleFromCandidates),
                PsiMethod.class,
                methodProcessor
            );
            //broken index.processElements(key, JavaMethodElementType.TYPE_PARAMETER_PSEUDO_NAME, project, visibleFromCandidates, PsiMethod.class, methodProcessor);
            LOG.info("#methods: " + methods.size());
            return methods;
        });
    }

    @Nonnull
    private static GlobalSearchScope combineResolveScopes(Project project, Set<VirtualFile> candidateFiles) {
        PsiManager psiManager = PsiManager.getInstance(project);
        LinkedHashSet<GlobalSearchScope> resolveScopes = new LinkedHashSet<>(candidateFiles.stream().map(file -> {
            PsiFile psiFile = file.isValid() ? psiManager.findFile(file) : null;
            return psiFile == null ? null : psiFile.getResolveScope();
        }).filter(Objects::nonNull).toList());
        return GlobalSearchScope.union(resolveScopes.toArray(new GlobalSearchScope[resolveScopes.size()]));
    }

    @Nonnull
    private static Set<VirtualFile> getFilesWithFunctionalExpressionsScope(Project project, GlobalSearchScope useScope) {
        Set<VirtualFile> files = new LinkedHashSet<>();
        PsiSearchHelper helper = PsiSearchHelper.getInstance(project);
        CommonProcessors.CollectProcessor<VirtualFile> processor = new CommonProcessors.CollectProcessor<>(files);
        helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "::", processor);
        helper.processFilesWithText(useScope, UsageSearchContext.IN_CODE, true, "->", processor);
        return files;
    }

    @Nonnull
    private static GlobalSearchScope convertToGlobalScope(Project project, SearchScope useScope) {
        GlobalSearchScope scope;
        if (useScope instanceof GlobalSearchScope globalSearchScope) {
            scope = globalSearchScope;
        }
        else if (useScope instanceof LocalSearchScope localSearchScope) {
            Set<VirtualFile> files = new HashSet<>();
            ContainerUtil.addAllNotNull(files, ContainerUtil.map(localSearchScope.getScope(), PsiUtilCore::getVirtualFile));
            scope = GlobalSearchScope.filesScope(project, files);
        }
        else {
            scope = new EverythingGlobalScope(project);
        }
        return scope;
    }

    /**
     * Collect files where:
     * aClass is used, e.g. in type declaration or method return type;
     * fields with type aClass are used on the left side of assignments. Should find Bar of the following example
     * <pre/>
     * class Foo {
     * Runnable myRunnable;
     * }
     * <p/>
     * class Bar {
     * void foo(Foo foo){
     * foo.myRunnable = () -> {};
     * }
     * }
     * </pre>
     */
    private static void collectFilesWithTypeOccurrencesAndFieldAssignments(
        PsiClass aClass,
        GlobalSearchScope filesScope,
        LinkedHashSet<VirtualFile> usageFiles
    ) {
        Set<PsiField> fields = new LinkedHashSet<>();
        Application app = Application.get();
        for (PsiReference reference : ReferencesSearch.search(aClass, filesScope)) {
            app.runReadAction(() -> {
                PsiElement element = reference.getElement();
                if (element != null) {
                    addIfNotNull(usageFiles, PsiUtilCore.getVirtualFile(element));
                    PsiElement parent = element.getParent();
                    if (parent instanceof PsiTypeElement && parent.getParent() instanceof PsiField field
                        && !field.isPrivate() && !field.isFinal()) {
                        fields.add(field);
                    }
                }
            });
        }

        for (PsiField field : fields) {
            ReferencesSearch.search(field, filesScope).forEach(new ReadActionProcessor<>() {
                @Override
                @RequiredReadAction
                public boolean processInReadAction(PsiReference fieldRef) {
                    PsiElement fieldElement = fieldRef.getElement();
                    PsiAssignmentExpression varElementParent =
                        PsiTreeUtil.getParentOfType(fieldElement, PsiAssignmentExpression.class);
                    if (varElementParent != null && PsiTreeUtil.isAncestor(varElementParent.getLExpression(), fieldElement, false)) {
                        addIfNotNull(usageFiles, PsiUtilCore.getVirtualFile(fieldElement));
                    }
                    return true;
                }
            });
        }
    }

    @RequiredReadAction
    private static boolean processFileWithFunctionalInterfaces(
        PsiClass aClass,
        int expectedParamCount,
        Predicate<? super PsiFunctionalExpression> consumer,
        VirtualFile file
    ) {
        PsiFile psiFile = aClass.getManager().findFile(file);
        if (psiFile != null) {
            SimpleReference<Boolean> ref = new SimpleReference<>(true);
            psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(PsiElement element) {
                    if (!ref.get()) {
                        return;
                    }
                    super.visitElement(element);
                }

                private void visitFunctionalExpression(PsiFunctionalExpression expression) {
                    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
                    if (InheritanceUtil.isInheritorOrSelf(PsiUtil.resolveClassInType(functionalInterfaceType), aClass, true)) {
                        if (!consumer.test(expression)) {
                            ref.set(false);
                        }
                    }
                }

                @Override
                public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
                    super.visitLambdaExpression(expression);
                    if (expression.getParameterList().getParametersCount() == expectedParamCount) {
                        visitFunctionalExpression(expression);
                    }
                }

                @Override
                public void visitMethodReferenceExpression(@Nonnull PsiMethodReferenceExpression expression) {
                    super.visitMethodReferenceExpression(expression);
                    visitFunctionalExpression(expression);
                }
            });
            if (!ref.get()) {
                return false;
            }
        }
        return true;
    }

    private static class SuitableFilesProcessor
        implements FileBasedIndex.ValueProcessor<Collection<JavaFunctionalExpressionIndex.IndexHolder>> {
        private final Map<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> myHolders;
        private final int myExpectedFunExprParamsCount;
        private final int myParametersCount;
        private final boolean myVarArgs;
        private final PsiParameter[] myParameters;

        public SuitableFilesProcessor(
            Map<VirtualFile, Set<JavaFunctionalExpressionIndex.IndexHolder>> holders,
            int expectedFunExprParamsCount,
            int parametersCount,
            boolean varArgs,
            PsiParameter[] parameters
        ) {
            myHolders = holders;
            myExpectedFunExprParamsCount = expectedFunExprParamsCount;
            myParametersCount = parametersCount;
            myVarArgs = varArgs;
            myParameters = parameters;
        }

        @Override
        public boolean process(@Nonnull VirtualFile file, Collection<JavaFunctionalExpressionIndex.IndexHolder> holders) {
            Set<JavaFunctionalExpressionIndex.IndexHolder> savedHolders = myHolders.get(file);
            for (JavaFunctionalExpressionIndex.IndexHolder holder : holders) {
                int lambdaParamsNumber = holder.getLambdaParamsNumber();
                if (lambdaParamsNumber == myExpectedFunExprParamsCount || lambdaParamsNumber == -1) {
                    boolean suitableParamNumbers;
                    if (myVarArgs) {
                        suitableParamNumbers = holder.getMethodArgsLength() >= myParametersCount - 1;
                    }
                    else {
                        suitableParamNumbers = holder.getMethodArgsLength() == myParametersCount;
                    }
                    if (suitableParamNumbers) {
                        if (savedHolders == null) {
                            savedHolders = new LinkedHashSet<>();
                            myHolders.put(file, savedHolders);
                        }
                        savedHolders.add(holder);
                        break;
                    }
                }
            }
            return true;
        }

        private boolean canBeFunctional(JavaFunctionalExpressionIndex.IndexHolder holder) {
            int paramIdx = holder.getFunctionExpressionIndex();
            PsiType paramType = myParameters[paramIdx >= myParametersCount ? myParametersCount - 1 : paramIdx].getType();
            if (paramType instanceof PsiEllipsisType ellipsisType) {
                paramType = ellipsisType.getComponentType();
            }
            PsiClass functionalCandidate = PsiUtil.resolveClassInClassTypeOnly(paramType);
            return functionalCandidate instanceof PsiTypeParameter || LambdaUtil.isFunctionalClass(functionalCandidate);
        }
    }
}
