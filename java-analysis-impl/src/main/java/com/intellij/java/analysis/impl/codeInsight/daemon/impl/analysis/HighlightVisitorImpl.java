/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.DefaultHighlightUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.JavaVersionService;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.colorScheme.TextAttributesScheme;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.Pass;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoHolder;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.editor.rawHighlight.HighlightVisitor;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.MostlySingularMultiMap;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.*;

import static consulo.util.lang.ObjectUtil.notNull;

public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
    private final PsiResolveHelper myResolveHelper;

    private HighlightInfoHolder myHolder;
    private RefCountHolder myRefCountHolder;
    private LanguageLevel myLanguageLevel;
    private JavaSdkVersion myJavaSdkVersion;

    @SuppressWarnings("StatefulEp")
    private PsiFile myFile;
    @SuppressWarnings("StatefulEp")
    private PsiJavaModule myJavaModule;

    // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
    private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new HashMap<>();
    // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
    private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new HashMap<>();

    // value==1: no info if the parameter was reassigned (but the parameter is present in current file), value==2: parameter was reassigned
    private final ObjectIntMap<PsiParameter> myReassignedParameters = ObjectMaps.newObjectIntHashMap();

    private final Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> mySingleImportedClasses = new HashMap<>();
    private final Map<String, Pair<PsiImportStaticReferenceElement, PsiField>> mySingleImportedFields = new HashMap<>();

    private final PsiElementVisitor REGISTER_REFERENCES_VISITOR = new PsiRecursiveElementWalkingVisitor() {
        @Override
        @RequiredReadAction
        public void visitElement(PsiElement element) {
            super.visitElement(element);
            for (PsiReference reference : element.getReferences()) {
                if (reference.resolve() instanceof PsiNamedElement namedElem) {
                    myRefCountHolder.registerLocallyReferenced(namedElem);
                    if (namedElem instanceof PsiMember member) {
                        myRefCountHolder.registerReference(reference, new CandidateInfo(member, PsiSubstitutor.EMPTY));
                    }
                }
            }
        }
    };
    private final Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new HashMap<>();
    private final Set<PsiClass> myOverrideEquivalentMethodsVisitedClasses = new HashSet<>();

    @Inject
    public HighlightVisitorImpl(@Nonnull PsiResolveHelper resolveHelper) {
        myResolveHelper = resolveHelper;
    }

    @Nonnull
    private MostlySingularMultiMap<MethodSignature, PsiMethod> getDuplicateMethods(@Nonnull PsiClass aClass) {
        MostlySingularMultiMap<MethodSignature, PsiMethod> signatures = myDuplicateMethods.get(aClass);
        if (signatures == null) {
            signatures = new MostlySingularMultiMap<>();
            for (PsiMethod method : aClass.getMethods()) {
                if (method instanceof ExternallyDefinedPsiElement) {
                    continue; // ignore aspectj-weaved methods; they are checked elsewhere
                }
                MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
                signatures.add(signature, method);
            }

            myDuplicateMethods.put(aClass, signatures);
        }
        return signatures;
    }

    @Override
    public void visit(@Nonnull PsiElement element) {
        element.accept(this);
    }

    @RequiredReadAction
    private void registerReferencesFromInjectedFragments(@Nonnull PsiElement element) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFile.getProject());
        manager.enumerateEx(element, myFile, false, (injectedPsi, places) -> injectedPsi.accept(REGISTER_REFERENCES_VISITOR));
    }

    @Override
    @RequiredReadAction
    public boolean analyze(
        @Nonnull PsiFile file,
        boolean updateWholeFile,
        @Nonnull HighlightInfoHolder holder,
        @Nonnull Runnable highlight
    ) {
        try {
            prepare(holder, file);
            if (updateWholeFile) {
                ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
                if (progress == null) {
                    throw new IllegalStateException("Must be run under progress");
                }
                Project project = file.getProject();
                Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                TextRange dirtyScope = document == null ? null : DaemonCodeAnalyzer.getInstance(project)
                    .getFileStatusMap()
                    .getFileDirtyScope(document, Pass.UPDATE_ALL);
                if (dirtyScope == null) {
                    dirtyScope = file.getTextRange();
                }
                RefCountHolder refCountHolder = RefCountHolder.get(file, dirtyScope);
                if (refCountHolder == null) {
                    // RefCountHolder was GCed and queried again for some inner code block
                    // "highlight.run()" can't fill it again because it runs for only a subset of elements,
                    // so we have to restart the daemon for the whole file
                    return false;
                }
                myRefCountHolder = refCountHolder;

                highlight.run();
                ProgressManager.checkCanceled();
                refCountHolder.storeReadyHolder(file);
                if (document != null) {
                    new PostHighlightingVisitor(file, document, refCountHolder).collectHighlights(holder, progress);
                }
            }
            else {
                myRefCountHolder = null;
                highlight.run();
            }
        }
        finally {
            myUninitializedVarProblems.clear();
            myFinalVarProblems.clear();
            mySingleImportedClasses.clear();
            mySingleImportedFields.clear();
            myReassignedParameters.clear();

            myRefCountHolder = null;
            myJavaModule = null;
            myFile = null;
            myHolder = null;
            myDuplicateMethods.clear();
            myOverrideEquivalentMethodsVisitedClasses.clear();
        }

        return true;
    }

    @RequiredReadAction
    protected void prepareToRunAsInspection(@Nonnull HighlightInfoHolder holder) {
        prepare(holder, holder.getContextFile());
    }

    @RequiredReadAction
    private void prepare(HighlightInfoHolder holder, PsiFile file) {
        myHolder = holder;
        myFile = file;
        myLanguageLevel = PsiUtil.getLanguageLevel(file);
        myJavaSdkVersion =
            notNull(JavaVersionService.getInstance().getJavaSdkVersion(file), JavaSdkVersion.fromLanguageLevel(myLanguageLevel));
        myJavaModule = myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9) ? ModuleHighlightUtil.getModuleDescriptor(file) : null;
    }

    @Override
    public void visitElement(PsiElement element) {
        if (myRefCountHolder != null && myFile instanceof ServerPageFile) {
            // in JSP, XmlAttributeValue may contain java references
            try {
                for (PsiReference reference : element.getReferences()) {
                    if (reference instanceof PsiJavaReference javaRef) {
                        myRefCountHolder.registerReference(javaRef, javaRef.advancedResolve(false));
                    }
                }
            }
            catch (IndexNotReadyException ignored) {
            }
        }

        if (!(myFile instanceof ServerPageFile)) {
            myHolder.add(DefaultHighlightUtil.checkBadCharacter(element));
        }
    }

    @Nullable
    @RequiredReadAction
    public static JavaResolveResult resolveJavaReference(@Nonnull PsiReference reference) {
        if (reference instanceof PsiJavaReference javaRef) {
            return javaRef.advancedResolve(false);
        }
        if (reference instanceof PsiPolyVariantReference polyVariantReference &&
            reference instanceof ResolvingHint hint && hint.canResolveTo(PsiClass.class)) {
            ResolveResult[] resolve = polyVariantReference.multiResolve(false);
            if (resolve.length == 1 && resolve[0] instanceof JavaResolveResult resolveResult) {
                return resolveResult;
            }
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public void visitAnnotation(@Nonnull PsiAnnotation annotation) {
        super.visitAnnotation(annotation);
        if (!myHolder.hasErrorResults()) {
            add(checkFeature(annotation, JavaFeature.ANNOTATIONS));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkApplicability(annotation, myLanguageLevel, myFile));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkAnnotationType(annotation));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkMissingAttributes(annotation));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkTargetAnnotationDuplicates(annotation));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkDuplicateAnnotations(annotation, myLanguageLevel));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkFunctionalInterface(annotation, myLanguageLevel));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkRepeatableAnnotation(annotation));
        }
        if (CommonClassNames.JAVA_LANG_OVERRIDE.equals(annotation.getQualifiedName())) {
            PsiAnnotationOwner owner = annotation.getOwner();
            PsiElement parent = owner instanceof PsiModifierList modifierList ? modifierList.getParent() : null;
            if (parent instanceof PsiMethod method) {
                add(GenericsHighlightUtil.checkOverrideAnnotation(method, annotation, myLanguageLevel));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
        PsiMethod method = null;

        PsiElement parent = initializer.getParent();
        if (parent instanceof PsiNameValuePair) {
            PsiReference reference = parent.getReference();
            if (reference != null) {
                method = (PsiMethod)reference.resolve();
            }
        }
        else if (PsiUtil.isAnnotationMethod(parent)) {
            method = (PsiMethod)parent;
        }

        if (method != null) {
            PsiType type = method.getReturnType();
            if (type instanceof PsiArrayType arrayType) {
                type = arrayType.getComponentType();
                PsiAnnotationMemberValue[] initializers = initializer.getInitializers();
                for (PsiAnnotationMemberValue initializer1 : initializers) {
                    myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(initializer1, type));
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitAnnotationMethod(PsiAnnotationMethod method) {
        PsiType returnType = method.getReturnType();
        PsiAnnotationMemberValue value = method.getDefaultValue();
        if (returnType != null && value != null) {
            myHolder.add(AnnotationsHighlightUtil.checkMemberValueType(value, returnType));
        }

        add(AnnotationsHighlightUtil.checkValidAnnotationType(method.getReturnType(), method.getReturnTypeElement()));
        PsiClass aClass = method.getContainingClass();
        add(AnnotationsHighlightUtil.checkCyclicMemberType(method.getReturnTypeElement(), aClass));
        add(AnnotationsHighlightUtil.checkClashesWithSuperMethods(method));

        if (!myHolder.hasErrorResults() && aClass != null) {
            add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
        }
    }

    @Override
    @RequiredReadAction
    public void visitArrayInitializerExpression(@Nonnull PsiArrayInitializerExpression expression) {
        super.visitArrayInitializerExpression(expression);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkArrayInitializerApplicable(expression));
        }
        if (!(expression.getParent() instanceof PsiNewExpression)) {
            if (!myHolder.hasErrorResults()) {
                myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression assignment) {
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkAssignmentCompatibleTypes(assignment));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkAssignmentOperatorApplicable(assignment));
        }
        if (!myHolder.hasErrorResults()) {
            visitExpression(assignment);
        }
    }

    @Override
    @RequiredReadAction
    public void visitPolyadicExpression(@Nonnull PsiPolyadicExpression expression) {
        super.visitPolyadicExpression(expression);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkPolyadicOperatorApplicable(expression));
        }
    }

    @Override
    @RequiredReadAction
    public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
        add(checkFeature(expression, JavaFeature.LAMBDA_EXPRESSIONS));
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiExpressionStatement) {
            return;
        }
        if (!myHolder.hasErrorResults() && !LambdaUtil.isValidLambdaContext(parent)) {
            myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip("Lambda expression not expected here")
                .create());
        }

        PsiType functionalInterfaceType = null;
        if (!myHolder.hasErrorResults()) {
            functionalInterfaceType = expression.getFunctionalInterfaceType();
            if (functionalInterfaceType != null) {
                String notFunctionalMessage = LambdaHighlightingUtil.checkInterfaceFunctional(functionalInterfaceType);
                if (notFunctionalMessage != null) {
                    myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(notFunctionalMessage)
                        .create());
                }
                else {
                    checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
                }
            }
            else if (LambdaUtil.getFunctionalInterfaceType(expression, true) != null) {
                myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip("Cannot infer functional interface type")
                    .create());
            }
        }

        if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
            String parentInferenceErrorMessage = null;
            PsiCallExpression callExpression =
                parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression call ? call : null;
            JavaResolveResult containingCallResolveResult = callExpression != null ? callExpression.resolveMethodGenerics() : null;
            if (containingCallResolveResult instanceof MethodCandidateInfo methodCandidateInfo) {
                parentInferenceErrorMessage = methodCandidateInfo.getInferenceErrorMessage();
            }
            Map<PsiElement, String> returnErrors =
                LambdaUtil.checkReturnTypeCompatible(expression, LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType));
            if (parentInferenceErrorMessage != null && (returnErrors == null || !returnErrors.containsValue(parentInferenceErrorMessage))) {
                myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(parentInferenceErrorMessage)
                    .create());
            }
            else if (returnErrors != null) {
                for (Map.Entry<PsiElement, String> entry : returnErrors.entrySet()) {
                    myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(entry.getKey())
                        .descriptionAndTooltip(entry.getValue())
                        .create());
                }
            }
        }

        if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
            PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
            PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
            if (interfaceMethod != null) {
                PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
                myHolder.add(LambdaHighlightingUtil.checkParametersCompatible(
                    expression,
                    parameters,
                    LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)
                ));
            }
        }

        if (!myHolder.hasErrorResults() && expression.getBody() instanceof PsiCodeBlock bodyCodeBlock) {
            add(HighlightControlFlowUtil.checkUnreachableStatement(bodyCodeBlock));
        }
    }

    @Override
    @RequiredReadAction
    public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
        super.visitBreakStatement(statement);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findExitedStatement()));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkBreakOutsideLoop(statement));
        }
    }

    @Override
    @RequiredReadAction
    public void visitClass(@Nonnull PsiClass aClass) {
        super.visitClass(aClass);
        if (aClass instanceof PsiSyntheticClass) {
            return;
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkInterfaceMultipleInheritance(aClass));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.areSupersAccessible(aClass));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightClassUtil.checkDuplicateTopLevelClass(aClass));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkEnumMustNotBeLocal(aClass));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkEnumWithoutConstantsCantHaveAbstractMethods(aClass));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkImplicitThisReferenceBeforeSuper(aClass, myJavaSdkVersion));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkClassAndPackageConflict(aClass));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkPublicClassInRightFile(aClass));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkTypeParameterOverrideEquivalentMethods(aClass, myLanguageLevel));
        }
    }

    @Override
    @RequiredReadAction
    public void visitClassInitializer(@Nonnull PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightControlFlowUtil.checkInitializerCompleteNormally(initializer));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightControlFlowUtil.checkUnreachableStatement(initializer.getBody()));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkThingNotAllowedInInterface(initializer, initializer.getContainingClass()));
        }
    }

    @Override
    @RequiredReadAction
    public void visitClassObjectAccessExpression(@Nonnull PsiClassObjectAccessExpression expression) {
        super.visitClassObjectAccessExpression(expression);
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkClassObjectAccessExpression(expression));
        }
    }

    @Override
    @RequiredReadAction
    public void visitComment(PsiComment comment) {
        super.visitComment(comment);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkUnclosedComment(comment));
        }
        if (myRefCountHolder != null && !myHolder.hasErrorResults()) {
            registerReferencesFromInjectedFragments(comment);
        }
    }

    @Override
    @RequiredReadAction
    public void visitContinueStatement(@Nonnull PsiContinueStatement statement) {
        super.visitContinueStatement(statement);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkLabelDefined(statement.getLabelIdentifier(), statement.findContinuedStatement()));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkContinueOutsideLoop(statement));
        }
    }

    @Override
    @RequiredReadAction
    public void visitJavaToken(@Nonnull PsiJavaToken token) {
        super.visitJavaToken(token);
        if (!myHolder.hasErrorResults()
            && token.getTokenType() == JavaTokenType.RBRACE
            && token.getParent() instanceof PsiCodeBlock tokenCodeBlock) {
            PsiElement gParent = tokenCodeBlock.getParent();
            PsiCodeBlock codeBlock;
            PsiType returnType;
            if (gParent instanceof PsiMethod method) {
                codeBlock = method.getBody();
                returnType = method.getReturnType();
            }
            else if (gParent instanceof PsiLambdaExpression lambda) {
                PsiElement body = lambda.getBody();
                if (!(body instanceof PsiCodeBlock lambdaCodeBlock)) {
                    return;
                }
                codeBlock = lambdaCodeBlock;
                returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
            }
            else {
                return;
            }
            myHolder.add(HighlightControlFlowUtil.checkMissingReturnStatement(codeBlock, returnType));
        }
    }

    @Override
    @RequiredReadAction
    public void visitDocComment(@Nonnull PsiDocComment comment) {
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkUnclosedComment(comment));
        }
    }

    @Override
    @RequiredReadAction
    public void visitDocTagValue(PsiDocTagValue value) {
        PsiReference reference = value.getReference();
        if (reference != null) {
            PsiElement element = reference.resolve();
            TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
            if (element instanceof PsiMethod method) {
                PsiElement nameElement = ((PsiDocMethodOrFieldRef)value).getNameElement();
                if (nameElement != null) {
                    myHolder.add(HighlightNamesUtil.highlightMethodName(method, nameElement, false, colorsScheme));
                }
            }
            else if (element instanceof PsiParameter parameter) {
                myHolder.add(HighlightNamesUtil.highlightVariableName(parameter, value.getNavigationElement(), colorsScheme));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitEnumConstant(@Nonnull PsiEnumConstant enumConstant) {
        super.visitEnumConstant(enumConstant);
        if (!myHolder.hasErrorResults()) {
            GenericsHighlightUtil.checkEnumConstantForConstructorProblems(enumConstant, myHolder, myJavaSdkVersion);
        }
        if (!myHolder.hasErrorResults()) {
            registerConstructorCall(enumConstant);
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkUnhandledExceptions(enumConstant, null));
        }
    }

    @Override
    @RequiredReadAction
    public void visitEnumConstantInitializer(@Nonnull PsiEnumConstantInitializer enumConstantInitializer) {
        super.visitEnumConstantInitializer(enumConstantInitializer);
        if (!myHolder.hasErrorResults()) {
            TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(enumConstantInitializer);
            add(HighlightClassUtil.checkClassMustBeAbstract(enumConstantInitializer, textRange));
        }
    }

    @Override
    @RequiredReadAction
    public void visitExpression(@Nonnull PsiExpression expression) {
        ProgressManager.checkCanceled(); // visitLiteralExpression is invoked very often in array initializers

        super.visitExpression(expression);
        PsiType type = expression.getType();
        if (add(HighlightUtil.checkMustBeBoolean(expression, type))) {
            return;
        }

        if (expression instanceof PsiArrayAccessExpression arrayAccess) {
            add(HighlightUtil.checkValidArrayAccessExpression(arrayAccess));
        }

        PsiElement parent = expression.getParent();
        if (parent instanceof PsiNewExpression newExpr
            && newExpr.getQualifier() != expression
            && newExpr.getArrayInitializer() != expression) {
            // like in 'new String["s"]'
            add(HighlightUtil.checkAssignability(PsiType.INT, expression.getType(), expression, expression));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightControlFlowUtil.checkCannotWriteToFinal(expression, myFile));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkVariableExpected(expression));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.addAll(HighlightUtil.checkArrayInitializer(expression, type));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkTernaryOperatorConditionIsBoolean(expression, type));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkAssertOperatorTypes(expression, type));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkSynchronizedExpressionType(expression, type, myFile));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkConditionalExpressionBranchTypesMatch(expression, type));
        }
        if (!myHolder.hasErrorResults() && parent instanceof PsiThrowStatement throwStmt && throwStmt.getException() == expression) {
            add(HighlightUtil.checkMustBeThrowable(type, expression, true));
        }
        if (!myHolder.hasErrorResults()) {
            add(AnnotationsHighlightUtil.checkConstantExpression(expression));
        }
        if (!myHolder.hasErrorResults() && parent instanceof PsiForeachStatement forEach && forEach.getIteratedValue() == expression) {
            add(GenericsHighlightUtil.checkForeachExpressionTypeIsIterable(expression));
        }
    }

    @Override
    @RequiredReadAction
    public void visitExpressionList(@Nonnull PsiExpressionList list) {
        super.visitExpressionList(list);
        if (list.getParent() instanceof PsiMethodCallExpression expression) {
            if (expression.getArgumentList() == list) {
                PsiReferenceExpression referenceExpression = expression.getMethodExpression();
                JavaResolveResult[] results = resolveOptimised(referenceExpression);
                if (results == null) {
                    return;
                }
                JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
                PsiElement resolved = result.getElement();

                if ((!result.isAccessible() || !result.isStaticsScopeCorrect())
                    && !HighlightMethodUtil.isDummyConstructorCall(expression, myResolveHelper, list, referenceExpression)
                    // this check is for fake expression from JspMethodCallImpl
                    && referenceExpression.getParent() == expression) {
                    try {
                        if (PsiTreeUtil.findChildrenOfType(expression.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
                            add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(
                                referenceExpression,
                                results,
                                list,
                                resolved,
                                result,
                                expression,
                                myResolveHelper,
                                list
                            ));
                        }
                    }
                    catch (IndexNotReadyException ignored) {
                    }
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitField(@Nonnull PsiField field) {
        super.visitField(field);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightControlFlowUtil.checkFinalFieldInitialized(field));
        }
    }

    @Override
    @RequiredReadAction
    public void visitForStatement(@Nonnull PsiForStatement statement) {
        myHolder.add(HighlightUtil.checkForStatement(statement));
    }

    @Override
    @RequiredReadAction
    public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
        add(checkFeature(statement, JavaFeature.FOR_EACH));
    }

    @Override
    @RequiredReadAction
    public void visitImportStaticStatement(@Nonnull PsiImportStaticStatement statement) {
        add(checkFeature(statement, JavaFeature.STATIC_IMPORTS));
        if (!myHolder.hasErrorResults()) {
            myHolder.add(ImportsHighlightUtil.checkStaticOnDemandImportResolvesToClass(statement));
        }
    }

    @Override
    @RequiredReadAction
    public void visitIdentifier(PsiIdentifier identifier) {
        TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiVariable variable) {
            myHolder.add(HighlightUtil.checkVariableAlreadyDefined(variable));

            if (variable.getInitializer() == null) {
                PsiElement child = variable.getLastChild();
                if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) {
                    return;
                }
            }

            boolean isMethodParameter = variable instanceof PsiParameter parameter
                && parameter.getDeclarationScope() instanceof PsiMethod;
            if (isMethodParameter) {
                myReassignedParameters.putInt((PsiParameter)variable, 1); // mark param as present in current file
            }
            // method params are highlighted in visitMethod since we should make sure the method body was visited before
            else if (HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems)) {
                add(HighlightNamesUtil.highlightReassignedVariable(variable, identifier));
            }
            else {
                myHolder.add(HighlightNamesUtil.highlightVariableName(variable, identifier, colorsScheme));
            }
        }
        else if (parent instanceof PsiClass aClass) {
            if (aClass.isAnnotationType()) {
                add(checkFeature(identifier, JavaFeature.ANNOTATIONS));
            }

            myHolder.add(HighlightClassUtil.checkClassAlreadyImported(aClass, identifier));
            if (!(parent instanceof PsiAnonymousClass) && aClass.getNameIdentifier() == identifier) {
                myHolder.add(HighlightNamesUtil.highlightClassName(aClass, identifier, colorsScheme));
            }
            if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                add(GenericsHighlightUtil.checkUnrelatedDefaultMethods(aClass, identifier));
            }

            if (!myHolder.hasErrorResults()) {
                add(GenericsHighlightUtil.checkUnrelatedConcrete(aClass, identifier));
            }
        }
        else if (parent instanceof PsiMethod method) {
            if (method.isConstructor()) {
                myHolder.add(HighlightMethodUtil.checkConstructorName(method));
            }
            myHolder.add(HighlightNamesUtil.highlightMethodName(method, identifier, true, colorsScheme));
            PsiClass aClass = method.getContainingClass();
            if (aClass != null) {
                myHolder.add(GenericsHighlightUtil.checkDefaultMethodOverrideEquivalentToObjectNonPrivate(
                    myLanguageLevel,
                    aClass,
                    method,
                    identifier
                ));
            }
        }

        myHolder.add(HighlightUtil.checkUnderscore(identifier, myLanguageLevel));

        super.visitIdentifier(identifier);
    }

    @Override
    @RequiredReadAction
    public void visitImportStatement(@Nonnull PsiImportStatement statement) {
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkSingleImportClassConflict(statement, mySingleImportedClasses, myFile));
        }
    }

    @Override
    @RequiredReadAction
    public void visitImportStaticReferenceElement(@Nonnull PsiImportStaticReferenceElement ref) {
        String refName = ref.getReferenceName();
        JavaResolveResult[] results = ref.multiResolve(false);

        PsiElement referenceNameElement = ref.getReferenceNameElement();
        if (results.length == 0) {
            LocalizeValue description = JavaErrorLocalize.cannotResolveSymbol(refName);
            assert referenceNameElement != null : ref;
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
                .range(referenceNameElement)
                .descriptionAndTooltip(description)
                .registerFix(QuickFixFactory.getInstance().createSetupJDKFix())
                .create();
            myHolder.add(info);
        }
        else {
            PsiManager manager = ref.getManager();
            for (JavaResolveResult result : results) {
                PsiElement element = result.getElement();
                if (element instanceof PsiClass psiClass) {
                    Pair<PsiImportStaticReferenceElement, PsiClass> imported = mySingleImportedClasses.get(refName);
                    PsiClass importedClass = imported == null ? null : imported.getSecond();
                    if (importedClass != null && !manager.areElementsEquivalent(importedClass, psiClass)) {
                        LocalizeValue description = imported.first == null
                            ? JavaErrorLocalize.singleImportClassConflict(refName)
                            : imported.first.equals(ref)
                            ? JavaErrorLocalize.classIsAmbiguousInSingleStaticImport(refName)
                            : JavaErrorLocalize.classIsAlreadyDefinedInSingleStaticImport(refName);
                        myHolder.add(
                            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .range(ref)
                                .descriptionAndTooltip(description)
                                .create()
                        );
                    }
                    mySingleImportedClasses.put(refName, Pair.create(ref, psiClass));
                }
                else if (element instanceof PsiField field) {
                    Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
                    PsiField importedField = imported == null ? null : imported.getSecond();
                    if (importedField != null && !manager.areElementsEquivalent(importedField, field)) {
                        LocalizeValue description = imported.first.equals(ref)
                            ? JavaErrorLocalize.fieldIsAmbiguousInSingleStaticImport(refName)
                            : JavaErrorLocalize.fieldIsAlreadyDefinedInSingleStaticImport(refName);
                        myHolder.add(
                            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .range(ref)
                                .descriptionAndTooltip(description)
                                .create()
                        );
                    }
                    mySingleImportedFields.put(refName, Pair.create(ref, field));
                }
            }
        }
        if (!myHolder.hasErrorResults()) {
            PsiElement resolved = results.length >= 1 ? results[0].getElement() : null;
            if (results.length > 1) {
                for (int i = 1; i < results.length; i++) {
                    PsiElement element = results[i].getElement();
                    if (resolved instanceof PsiMethod && !(element instanceof PsiMethod)
                        || resolved instanceof PsiVariable && !(element instanceof PsiVariable)
                        || resolved instanceof PsiClass && !(element instanceof PsiClass)) {
                        resolved = null;
                        break;
                    }
                }
            }
            TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
            if (resolved instanceof PsiClass psiClass) {
                myHolder.add(HighlightNamesUtil.highlightClassName(psiClass, ref, colorsScheme));
            }
            else {
                myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref, colorsScheme));
                if (referenceNameElement != null) {
                    if (resolved instanceof PsiVariable variable) {
                        myHolder.add(HighlightNamesUtil.highlightVariableName(variable, referenceNameElement, colorsScheme));
                    }
                    else if (resolved instanceof PsiMethod method) {
                        myHolder.add(HighlightNamesUtil.highlightMethodName(method, referenceNameElement, false, colorsScheme));
                    }
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitInstanceOfExpression(@Nonnull PsiInstanceOfExpression expression) {
        super.visitInstanceOfExpression(expression);
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkInstanceOfApplicable(expression));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkInstanceOfGenericType(expression));
        }
    }

    @Override
    @RequiredReadAction
    public void visitKeyword(@Nonnull PsiKeyword keyword) {
        super.visitKeyword(keyword);
        PsiElement parent = keyword.getParent();
        String text = keyword.getText();
        if (parent instanceof PsiModifierList modifierList) {
            if (!myHolder.hasErrorResults()) {
                myHolder.add(HighlightUtil.checkNotAllowedModifier(keyword, modifierList));
            }
            if (!myHolder.hasErrorResults()) {
                myHolder.add(HighlightUtil.checkIllegalModifierCombination(keyword, modifierList));
            }
            if (PsiModifier.ABSTRACT.equals(text) && modifierList.getParent() instanceof PsiMethod method) {
                if (!myHolder.hasErrorResults()) {
                    myHolder.add(HighlightMethodUtil.checkAbstractMethodInConcreteClass(method, keyword));
                }
            }
        }
        else if (PsiKeyword.INTERFACE.equals(text) && parent instanceof PsiClass psiClass) {
            if (!myHolder.hasErrorResults()) {
                add(HighlightClassUtil.checkInterfaceCannotBeLocal(psiClass));
            }
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkStaticDeclarationInInnerClass(keyword, myLanguageLevel));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkIllegalVoidType(keyword));
        }
    }

    @Override
    @RequiredReadAction
    public void visitLabeledStatement(@Nonnull PsiLabeledStatement statement) {
        super.visitLabeledStatement(statement);
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkLabelWithoutStatement(statement));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkLabelAlreadyInUse(statement));
        }
    }

    @Override
    @RequiredReadAction
    public void visitLiteralExpression(@Nonnull PsiLiteralExpression expression) {
        super.visitLiteralExpression(expression);
        if (myHolder.hasErrorResults()) {
            return;
        }
        add(HighlightUtil.checkLiteralExpressionParsingError(expression, myLanguageLevel, myFile));
        if (myRefCountHolder != null && !myHolder.hasErrorResults()) {
            registerReferencesFromInjectedFragments(expression);
        }

        if (myRefCountHolder != null && !myHolder.hasErrorResults()) {
            for (PsiReference reference : expression.getReferences()) {
                if (reference.resolve() instanceof PsiMember member) {
                    myRefCountHolder.registerReference(reference, new CandidateInfo(member, PsiSubstitutor.EMPTY));
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitMethod(@Nonnull PsiMethod method) {
        super.visitMethod(method);
        if (!myHolder.hasErrorResults()) {
            add(HighlightControlFlowUtil.checkUnreachableStatement(method.getBody()));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightMethodUtil.checkConstructorHandleSuperClassExceptions(method));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightMethodUtil.checkRecursiveConstructorInvocation(method));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkSafeVarargsAnnotation(method, myLanguageLevel));
        }

        PsiClass aClass = method.getContainingClass();
        if (!myHolder.hasErrorResults() && method.isConstructor()) {
            add(HighlightClassUtil.checkThingNotAllowedInInterface(method, aClass));
        }
        if (!myHolder.hasErrorResults() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
            add(checkFeature(method, JavaFeature.EXTENSION_METHODS));
        }
        if (!myHolder.hasErrorResults() && aClass != null && aClass.isInterface() && method.isStatic()) {
            add(checkFeature(method, JavaFeature.EXTENSION_METHODS));
        }
        if (!myHolder.hasErrorResults() && aClass != null) {
            add(HighlightMethodUtil.checkDuplicateMethod(aClass, method, getDuplicateMethods(aClass)));
        }

        // method params are highlighted in visitMethod since we should make sure the method body was visited before
        PsiParameter[] parameters = method.getParameterList().getParameters();
        TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

        for (PsiParameter parameter : parameters) {
            int info = myReassignedParameters.getInt(parameter);
            if (info == 0) {
                continue; // out of this file
            }

            PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            if (nameIdentifier != null) {
                if (info == 2) { // reassigned
                    add(HighlightNamesUtil.highlightReassignedVariable(parameter, nameIdentifier));
                }
                else {
                    myHolder.add(HighlightNamesUtil.highlightVariableName(parameter, nameIdentifier, colorsScheme));
                }
            }
        }
    }

    @RequiredReadAction
    private void highlightReferencedMethodOrClassName(@Nonnull PsiJavaCodeReferenceElement element, PsiElement resolved) {
        PsiElement parent = element.getParent();
        TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
        if (parent instanceof PsiMethodCallExpression methodCall) {
            PsiMethod method = methodCall.resolveMethod();
            PsiElement methodNameElement = element.getReferenceNameElement();
            if (method != null && methodNameElement != null && !(methodNameElement instanceof PsiKeyword)) {
                myHolder.add(HighlightNamesUtil.highlightMethodName(method, methodNameElement, false, colorsScheme));
                myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(element, colorsScheme));
            }
        }
        else if (parent instanceof PsiConstructorCall constructorCall) {
            try {
                PsiMethod method = constructorCall.resolveConstructor();
                PsiMember methodOrClass = method != null ? method : resolved instanceof PsiClass psiClass ? psiClass : null;
                if (methodOrClass != null) {
                    PsiElement referenceNameElement = element.getReferenceNameElement();
                    if (referenceNameElement != null) {
                        // exclude type parameters from the highlighted text range
                        TextRange range = referenceNameElement.getTextRange();
                        myHolder.add(HighlightNamesUtil.highlightMethodName(
                            methodOrClass,
                            referenceNameElement,
                            range,
                            colorsScheme,
                            false
                        ));
                    }
                }
            }
            catch (IndexNotReadyException ignored) {
            }
        }
        else if (resolved instanceof PsiPackage) {
            // highlight package (and following dot) as a class
            myHolder.add(HighlightNamesUtil.highlightPackage(resolved, element, colorsScheme));
        }
        else if (resolved instanceof PsiClass psiClass) {
            myHolder.add(HighlightNamesUtil.highlightClassName(psiClass, element, colorsScheme));
        }
    }

    @Override
    @RequiredReadAction
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkEnumSuperConstructorCall(expression));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkSuperQualifierType(myFile.getProject(), expression));
        }
        // in case of JSP synthetic method call, do not check
        if (myFile.isPhysical() && !myHolder.hasErrorResults()) {
            try {
                add(HighlightMethodUtil.checkMethodCall(expression, myResolveHelper, myLanguageLevel, myJavaSdkVersion, myFile));
            }
            catch (IndexNotReadyException ignored) {
            }
        }

        if (!myHolder.hasErrorResults()) {
            add(HighlightMethodUtil.checkConstructorCallMustBeFirstStatement(expression));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightMethodUtil.checkSuperAbstractMethodDirectCall(expression));
        }

        if (!myHolder.hasErrorResults()) {
            visitExpression(expression);
        }
    }

    @Override
    @RequiredReadAction
    public void visitModifierList(@Nonnull PsiModifierList list) {
        super.visitModifierList(list);
        PsiElement parent = list.getParent();
        if (parent instanceof PsiMethod method) {
            if (!myHolder.hasErrorResults()) {
                add(HighlightMethodUtil.checkMethodCanHaveBody(method, myLanguageLevel));
            }
            MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
            if (!method.isConstructor()) {
                try {
                    List<HierarchicalMethodSignature> superMethodSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
                    if (!superMethodSignatures.isEmpty()) {
                        if (!myHolder.hasErrorResults()) {
                            add(HighlightMethodUtil.checkMethodIncompatibleReturnType(
                                methodSignature,
                                superMethodSignatures,
                                true
                            ));
                        }
                        if (!myHolder.hasErrorResults()) {
                            myHolder.add(HighlightMethodUtil.checkMethodIncompatibleThrows(
                                methodSignature,
                                superMethodSignatures,
                                true,
                                method.getContainingClass()
                            ));
                        }
                        if (!method.isStatic()) {
                            if (!myHolder.hasErrorResults()) {
                                add(HighlightMethodUtil.checkMethodWeakerPrivileges(
                                    methodSignature,
                                    superMethodSignatures,
                                    true,
                                    myFile
                                ));
                            }
                            if (!myHolder.hasErrorResults()) {
                                add(HighlightMethodUtil.checkMethodOverridesFinal(methodSignature, superMethodSignatures));
                            }
                        }
                    }
                }
                catch (IndexNotReadyException ignored) {
                }
            }
            PsiClass aClass = method.getContainingClass();
            if (!myHolder.hasErrorResults()) {
                add(HighlightMethodUtil.checkMethodMustHaveBody(method, aClass));
            }
            if (!myHolder.hasErrorResults()) {
                add(HighlightMethodUtil.checkConstructorCallsBaseClassConstructor(method, myRefCountHolder, myResolveHelper));
            }
            if (!myHolder.hasErrorResults()) {
                add(HighlightMethodUtil.checkStaticMethodOverride(method, myFile));
            }
            if (!myHolder.hasErrorResults() && aClass != null && myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
                myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
            }
        }
        else if (parent instanceof PsiClass aClass) {
            try {
                if (!myHolder.hasErrorResults()) {
                    add(HighlightClassUtil.checkDuplicateNestedClass(aClass));
                }
                if (!myHolder.hasErrorResults()) {
                    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
                    add(HighlightClassUtil.checkClassMustBeAbstract(aClass, textRange));
                }
                if (!myHolder.hasErrorResults()) {
                    add(HighlightClassUtil.checkClassDoesNotCallSuperConstructorOrHandleExceptions(
                        aClass,
                        myRefCountHolder,
                        myResolveHelper
                    ));
                }
                if (!myHolder.hasErrorResults()) {
                    add(HighlightMethodUtil.checkOverrideEquivalentInheritedMethods(aClass, myFile, myLanguageLevel));
                }
                if (!myHolder.hasErrorResults() && myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
                    myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(aClass));
                }
                if (!myHolder.hasErrorResults()) {
                    add(HighlightClassUtil.checkCyclicInheritance(aClass));
                }
            }
            catch (IndexNotReadyException ignored) {
            }
        }
        else if (parent instanceof PsiEnumConstant) {
            if (!myHolder.hasErrorResults()) {
                myHolder.addAll(GenericsHighlightUtil.checkEnumConstantModifierList(list));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitNameValuePair(@Nonnull PsiNameValuePair pair) {
        myHolder.add(AnnotationsHighlightUtil.checkNameValuePair(pair));
        if (!myHolder.hasErrorResults()) {
            PsiIdentifier nameId = pair.getNameIdentifier();
            if (nameId != null) {
                HighlightInfo result =
                    HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.ANNOTATION_ATTRIBUTE_NAME).range(nameId).create();
                myHolder.add(result);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitNewExpression(PsiNewExpression expression) {
        PsiType type = expression.getType();
        PsiClass aClass = PsiUtil.resolveClassInType(type);
        add(HighlightUtil.checkUnhandledExceptions(expression, null));
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkAnonymousInheritFinal(expression));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkQualifiedNew(expression, type, aClass));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(expression, type, aClass));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkTypeParameterInstantiation(expression));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkInstantiationOfAbstractClass(aClass, expression));
        }
        try {
            if (!myHolder.hasErrorResults()) {
                HighlightMethodUtil.checkNewExpression(expression, type, myHolder, myJavaSdkVersion);
            }
        }
        catch (IndexNotReadyException ignored) {
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression, aClass));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkGenericArrayCreation(expression, type));
        }
        if (!myHolder.hasErrorResults()) {
            registerConstructorCall(expression);
        }

        if (!myHolder.hasErrorResults()) {
            visitExpression(expression);
        }
    }

    @Override
    @RequiredReadAction
    public void visitPackageStatement(@Nonnull PsiPackageStatement statement) {
        super.visitPackageStatement(statement);
        myHolder.add(AnnotationsHighlightUtil.checkPackageAnnotationContainingFile(statement, myFile));
        if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
            if (!myHolder.hasErrorResults()) {
                myHolder.add(ModuleHighlightUtil.checkPackageStatement(statement, myFile, myJavaModule));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitParameter(@Nonnull PsiParameter parameter) {
        super.visitParameter(parameter);

        PsiElement parent = parameter.getParent();
        if (parent instanceof PsiParameterList && parameter.isVarArgs()) {
            if (!myHolder.hasErrorResults()) {
                add(checkFeature(parameter, JavaFeature.VARARGS));
            }
            if (!myHolder.hasErrorResults()) {
                add(GenericsHighlightUtil.checkVarArgParameterIsLast(parameter));
            }
        }
        else if (parent instanceof PsiCatchSection) {
            if (!myHolder.hasErrorResults() && parameter.getType() instanceof PsiDisjunctionType) {
                add(checkFeature(parameter, JavaFeature.MULTI_CATCH));
            }
            if (!myHolder.hasErrorResults()) {
                add(HighlightUtil.checkCatchParameterIsThrowable(parameter));
            }
            if (!myHolder.hasErrorResults()) {
                myHolder.addAll(GenericsHighlightUtil.checkCatchParameterIsClass(parameter));
            }
            if (!myHolder.hasErrorResults()) {
                myHolder.addAll(HighlightUtil.checkCatchTypeIsDisjoint(parameter));
            }
        }
        else if (parent instanceof PsiForeachStatement forEach) {
            if (!myHolder.hasErrorResults()) {
                add(GenericsHighlightUtil.checkForEachParameterType(forEach, parameter));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitParameterList(@Nonnull PsiParameterList list) {
        super.visitParameterList(list);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkAnnotationMethodParameters(list));
        }
    }

    @Override
    @RequiredReadAction
    public void visitPostfixExpression(@Nonnull PsiPostfixExpression expression) {
        super.visitPostfixExpression(expression);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
        }
    }

    @Override
    @RequiredReadAction
    public void visitPrefixExpression(@Nonnull PsiPrefixExpression expression) {
        super.visitPrefixExpression(expression);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkUnaryOperatorApplicable(expression.getOperationSign(), expression.getOperand()));
        }
    }

    @RequiredReadAction
    private void registerConstructorCall(@Nonnull PsiConstructorCall constructorCall) {
        if (myRefCountHolder != null) {
            if (constructorCall.resolveMethodGenerics().getElement() instanceof PsiNamedElement namedElem) {
                myRefCountHolder.registerLocallyReferenced(namedElem);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement ref) {
        JavaResolveResult result = doVisitReferenceElement(ref);
        if (result != null) {
            PsiElement resolved = result.getElement();
            if (!myHolder.hasErrorResults()) {
                add(GenericsHighlightUtil.checkRawOnParameterizedType(ref, resolved));
            }
            if (!myHolder.hasErrorResults() && resolved != null && myJavaModule != null) {
                myHolder.add(ModuleHighlightUtil.checkPackageAccessibility(ref, resolved, myJavaModule));
            }
        }
    }

    @RequiredReadAction
    private JavaResolveResult doVisitReferenceElement(@Nonnull PsiJavaCodeReferenceElement ref) {
        JavaResolveResult result = resolveOptimised(ref);
        if (result == null) {
            return null;
        }

        PsiElement resolved = result.getElement();
        PsiElement parent = ref.getParent();

        if (myRefCountHolder != null) {
            myRefCountHolder.registerReference(ref, result);
        }

        add(HighlightUtil.checkReference(ref, result, myFile, myLanguageLevel));

        if (parent instanceof PsiJavaCodeReferenceElement || ref.isQualified()) {
            if (!myHolder.hasErrorResults() && resolved instanceof PsiTypeParameter) {
                boolean canSelectFromTypeParameter = myJavaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7);
                if (canSelectFromTypeParameter) {
                    PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
                    if (containingClass != null) {
                        if (PsiTreeUtil.isAncestor(containingClass.getExtendsList(), ref, false)
                            || PsiTreeUtil.isAncestor(containingClass.getImplementsList(), ref, false)) {
                            canSelectFromTypeParameter = false;
                        }
                    }
                }
                if (!canSelectFromTypeParameter) {
                    myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .descriptionAndTooltip(LocalizeValue.localizeTODO("Cannot select from a type parameter"))
                        .range(ref)
                        .create());
                }
            }
        }

        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkAbstractInstantiation(ref));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkExtendsDuplicate(ref, resolved, myFile));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightClassUtil.checkClassExtendsForeignInnerClass(ref, resolved));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkSelectStaticClassFromParameterizedType(resolved, ref));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(
                resolved,
                ref,
                result.getSubstitutor(),
                myJavaSdkVersion
            ));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkCannotPassInner(ref));
        }

        if (resolved != null && parent instanceof PsiReferenceList referenceList && !myHolder.hasErrorResults()) {
            add(HighlightUtil.checkElementInReferenceList(ref, referenceList, result));
        }

        if (parent instanceof PsiAnonymousClass anonymousClass
            && ref.equals(anonymousClass.getBaseClassReference())
            && myOverrideEquivalentMethodsVisitedClasses.add(anonymousClass)) {
            myHolder.addAll(GenericsHighlightUtil.checkOverrideEquivalentMethods(anonymousClass));
        }

        if (resolved instanceof PsiVariable variable) {
            PsiElement containingClass = PsiTreeUtil.getNonStrictParentOfType(ref, PsiClass.class, PsiLambdaExpression.class);
            if ((containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiLambdaExpression)
                && !PsiTreeUtil.isAncestor(containingClass, variable, false)
                && !(variable instanceof PsiField) && (containingClass instanceof PsiLambdaExpression
                || !PsiTreeUtil.isAncestor(((PsiAnonymousClass)containingClass).getArgumentList(), ref, false))) {
                myHolder.add(HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.IMPLICIT_ANONYMOUS_CLASS_PARAMETER).range(ref).create());
            }

            if (variable instanceof PsiParameter parameter
                && ref instanceof PsiExpression expr
                && PsiUtil.isAccessedForWriting(expr)) {
                myReassignedParameters.putInt(parameter, 2);
            }

            TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
            if (!variable.hasModifierProperty(PsiModifier.FINAL) && isReassigned(variable)) {
                add(HighlightNamesUtil.highlightReassignedVariable(variable, ref));
            }
            else {
                PsiElement nameElement = ref.getReferenceNameElement();
                if (nameElement != null) {
                    myHolder.add(HighlightNamesUtil.highlightVariableName(variable, nameElement, colorsScheme));
                }
            }
            myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(ref, colorsScheme));
        }
        else {
            highlightReferencedMethodOrClassName(ref, resolved);
        }

        if (parent instanceof PsiNewExpression newExpr && !(resolved instanceof PsiClass) && resolved instanceof PsiNamedElement namedElem
            && newExpr.getClassOrAnonymousClassReference() == ref) {
            add(
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(ref)
                    .descriptionAndTooltip(JavaErrorLocalize.cannotResolveSymbol(namedElem.getName()))
            );
        }

        if (!myHolder.hasErrorResults() && resolved instanceof PsiClass psiClass) {
            PsiClass containingClass = psiClass.getContainingClass();
            if (containingClass != null) {
                PsiElement qualifier = ref.getQualifier();
                PsiElement place;
                if (qualifier instanceof PsiJavaCodeReferenceElement javaCodeRef) {
                    place = javaCodeRef.resolve();
                }
                else if (parent instanceof PsiNewExpression newExpr) {
                    PsiExpression newQualifier = newExpr.getQualifier();
                    place = newQualifier == null ? ref : PsiUtil.resolveClassInType(newQualifier.getType());
                }
                else {
                    place = ref;
                }
                if (place != null && PsiTreeUtil.isAncestor(containingClass, place, false) && containingClass.hasTypeParameters()) {
                    myHolder.add(HighlightClassUtil.checkCreateInnerClassFromStaticContext(ref, place, (PsiClass)resolved));
                }
            }
            else if (resolved instanceof PsiTypeParameter typeParam) {
                PsiTypeParameterListOwner owner = typeParam.getOwner();
                if (owner instanceof PsiClass outerClass) {
                    if (!InheritanceUtil.hasEnclosingInstanceInScope(outerClass, ref, false, false)) {
                        myHolder.add(HighlightClassUtil.reportIllegalEnclosingUsage(ref, null, (PsiClass)owner, ref));
                    }
                }
            }
        }

        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkPackageAndClassConflict(ref, myFile));
        }

        return result;
    }

    @Nullable
    private JavaResolveResult resolveOptimised(@Nonnull PsiJavaCodeReferenceElement ref) {
        try {
            if (ref instanceof PsiReferenceExpressionImpl) {
                PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
                JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(ref, resolver, true, true, myFile);
                return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
            }
            else {
                return ref.advancedResolve(true);
            }
        }
        catch (IndexNotReadyException e) {
            return null;
        }
    }

    @Nullable
    private JavaResolveResult[] resolveOptimised(@Nonnull PsiReferenceExpression expression) {
        try {
            if (expression instanceof PsiReferenceExpressionImpl) {
                PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
                return JavaResolveUtil.resolveWithContainingFile(expression, resolver, true, true, myFile);
            }
            else {
                return expression.multiResolve(true);
            }
        }
        catch (IndexNotReadyException e) {
            return null;
        }
    }

    @Override
    @RequiredReadAction
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
        JavaResolveResult resultForIncompleteCode = doVisitReferenceElement(expression);

        if (!myHolder.hasErrorResults()) {
            visitExpression(expression);
            if (myHolder.hasErrorResults()) {
                return;
            }
        }

        JavaResolveResult[] results = resolveOptimised(expression);
        if (results == null) {
            return;
        }
        JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;

        PsiElement resolved = result.getElement();
        if (resolved instanceof PsiVariable variable && variable.getContainingFile() == expression.getContainingFile()) {
            if (!myHolder.hasErrorResults()) {
                try {
                    myHolder.add(HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(
                        expression,
                        variable,
                        myUninitializedVarProblems,
                        myFile
                    ));
                }
                catch (IndexNotReadyException ignored) {
                }
            }
            boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
            if (isFinal && !variable.hasInitializer()) {
                if (!myHolder.hasErrorResults()) {
                    myHolder.add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(
                        variable,
                        expression,
                        myFinalVarProblems
                    ));
                }
                if (!myHolder.hasErrorResults()) {
                    myHolder.add(HighlightControlFlowUtil.checkFinalVariableInitializedInLoop(expression, variable));
                }
            }
        }

        if (expression.getParent() instanceof PsiMethodCallExpression methodCall
            && methodCall.getMethodExpression() == expression
            && (!result.isAccessible() || !result.isStaticsScopeCorrect())) {
            PsiExpressionList list = methodCall.getArgumentList();
            if (!HighlightMethodUtil.isDummyConstructorCall(methodCall, myResolveHelper, list, expression)) {
                try {
                    add(HighlightMethodUtil.checkAmbiguousMethodCallIdentifier(
                        expression,
                        results,
                        list,
                        resolved,
                        result,
                        methodCall,
                        myResolveHelper,
                        myLanguageLevel,
                        myFile
                    ));

                    if (!PsiTreeUtil.findChildrenOfType(methodCall.getArgumentList(), PsiLambdaExpression.class).isEmpty()) {
                        PsiElement nameElement = expression.getReferenceNameElement();
                        if (nameElement != null) {
                            add(HighlightMethodUtil.checkAmbiguousMethodCallArguments(
                                expression,
                                results,
                                list,
                                resolved,
                                result,
                                methodCall,
                                myResolveHelper,
                                nameElement
                            ));
                        }
                    }
                }
                catch (IndexNotReadyException ignored) {
                }
            }
        }

        if (!myHolder.hasErrorResults() && resultForIncompleteCode != null) {
            myHolder.add(HighlightUtil.checkExpressionRequired(expression, resultForIncompleteCode));
        }

        if (!myHolder.hasErrorResults() && resolved instanceof PsiField field) {
            try {
                myHolder.add(HighlightUtil.checkIllegalForwardReferenceToField(expression, field));
            }
            catch (IndexNotReadyException ignored) {
            }
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkAccessStaticFieldFromEnumConstructor(expression, result));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkClassReferenceAfterQualifier(expression, resolved));
        }
        PsiExpression qualifierExpression = expression.getQualifierExpression();
        myHolder.add(HighlightUtil.checkUnqualifiedSuperInDefaultMethod(myLanguageLevel, expression, qualifierExpression));
        if (!myHolder.hasErrorResults() && qualifierExpression != null) {
            PsiType type = qualifierExpression.getType();
            if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
                type = capturedWildcardType.getUpperBound();
            }
            PsiClass psiClass = PsiUtil.resolveClassInType(type);
            if (psiClass != null) {
                add(GenericsHighlightUtil.areSupersAccessible(psiClass, expression));
            }
        }

        if (!myHolder.hasErrorResults() && resolved != null && myJavaModule != null) {
            myHolder.add(ModuleHighlightUtil.checkPackageAccessibility(expression, resolved, myJavaModule));
        }
    }

    @Override
    @RequiredReadAction
    public void visitMethodReferenceExpression(@Nonnull PsiMethodReferenceExpression expression) {
        add(checkFeature(expression, JavaFeature.METHOD_REFERENCES));
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiExpressionStatement) {
            return;
        }

        JavaResolveResult result;
        JavaResolveResult[] results;
        try {
            results = expression.multiResolve(true);
            result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
        }
        catch (IndexNotReadyException e) {
            return;
        }
        if (myRefCountHolder != null) {
            myRefCountHolder.registerReference(expression, result);
        }
        PsiElement method = result.getElement();
        if (method != null && !result.isAccessible()) {
            HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(HighlightUtil.buildProblemWithAccessDescription(expression, result));
            HighlightUtil.registerAccessQuickFixAction(
                (PsiMember)method,
                expression,
                hlBuilder,
                expression.getTextRange(),
                result.getCurrentFileResolveScope()
            );
            myHolder.add(hlBuilder.create());
        }
        else {
            TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
            if (method instanceof PsiMethod method1 && !expression.isConstructor()) {
                PsiElement methodNameElement = expression.getReferenceNameElement();
                if (methodNameElement != null) {
                    myHolder.add(HighlightNamesUtil.highlightMethodName(method1, methodNameElement, false, colorsScheme));
                }
            }
            myHolder.add(HighlightNamesUtil.highlightClassNameInQualifier(expression, colorsScheme));
        }

        if (!LambdaUtil.isValidLambdaContext(parent)) {
            add(
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(LocalizeValue.localizeTODO("Method reference expression is not expected here"))
            );
        }

        PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
        if (!myHolder.hasErrorResults()) {
            if (functionalInterfaceType != null) {
                boolean notFunctional = !LambdaUtil.isFunctionalType(functionalInterfaceType);
                if (notFunctional) {
                    add(
                        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(LocalizeValue.localizeTODO(
                            functionalInterfaceType.getPresentableText() + " is not a functional interface"
                        ))
                    );
                }
            }
        }

        if (!myHolder.hasErrorResults()) {
            PsiElement referenceNameElement = expression.getReferenceNameElement();
            if (referenceNameElement instanceof PsiKeyword && !PsiMethodReferenceUtil.isValidQualifier(expression)) {
                PsiElement qualifier = expression.getQualifier();
                if (qualifier != null) {
                    add(
                        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(qualifier)
                            .descriptionAndTooltip(LocalizeValue.localizeTODO("Cannot find class " + qualifier.getText()))
                    );
                }
            }
        }

        if (!myHolder.hasErrorResults()) {
            checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
        }

        if (!myHolder.hasErrorResults() && functionalInterfaceType != null) {
            String errorMessage = PsiMethodReferenceUtil.checkMethodReferenceContext(expression);
            if (errorMessage != null) {
                HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(errorMessage);
                if (method instanceof PsiMethod method1 && !method1.isConstructor() && !method1.isAbstract()) {
                    boolean shouldHave = !method1.isStatic();
                    info.registerFix(
                        QuickFixFactory.getInstance()
                            .createModifierFixBuilder((PsiModifierListOwner)method)
                            .toggle(PsiModifier.STATIC, shouldHave)
                            .create()
                    );
                }
                myHolder.add(info.create());
            }
        }

        if (!myHolder.hasErrorResults() && expression.getQualifier() instanceof PsiTypeElement typeElem) {
            PsiType psiType = typeElem.getType();
            HighlightInfo genericArrayCreationInfo = GenericsHighlightUtil.checkGenericArrayCreation(typeElem, psiType);
            if (genericArrayCreationInfo != null) {
                myHolder.add(genericArrayCreationInfo);
            }
            else {
                String wildcardMessage = PsiMethodReferenceUtil.checkTypeArguments(typeElem, psiType);
                if (wildcardMessage != null) {
                    add(
                        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(typeElem)
                            .descriptionAndTooltip(wildcardMessage)
                    );
                }
            }
        }

        if (!myHolder.hasErrorResults()) {
            add(PsiMethodReferenceHighlightingUtil.checkRawConstructorReference(expression));
        }

        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkUnhandledExceptions(expression, expression.getTextRange()));
        }

        if (!myHolder.hasErrorResults()) {
            String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
            if (badReturnTypeMessage != null) {
                myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(expression)
                    .descriptionAndTooltip(badReturnTypeMessage)
                    .create());
            }
        }

        if (!myHolder.hasErrorResults()) {
            if (results.length == 0
                || results[0] instanceof MethodCandidateInfo candidate && !candidate.isApplicable() && functionalInterfaceType != null) {
                String description = null;
                if (results.length == 1) {
                    description = ((MethodCandidateInfo)results[0]).getInferenceErrorMessage();
                }
                if (expression.isConstructor()) {
                    PsiClass containingClass = PsiMethodReferenceUtil.getQualifierResolveResult(expression).getContainingClass();

                    if (containingClass != null
                        && !add(HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, expression))
                        && !myHolder.add(GenericsHighlightUtil.checkEnumInstantiation(expression, containingClass))
                        && containingClass.isPhysical() && description == null) {
                        description = JavaErrorLocalize.cannotResolveConstructor(containingClass.getName()).get();
                    }
                }
                else if (description == null) {
                    description = JavaErrorLocalize.cannotResolveMethod(expression.getReferenceName()).get();
                }

                if (description != null) {
                    PsiElement referenceNameElement = notNull(expression.getReferenceNameElement(), expression);
                    HighlightInfoType type = results.length == 0 ? HighlightInfoType.WRONG_REF : HighlightInfoType.ERROR;
                    HighlightInfo.Builder hlBuilder = HighlightInfo.newHighlightInfo(type)
                        .descriptionAndTooltip(description)
                        .range(referenceNameElement)
                        .registerFix(
                            QuickFixFactory.getInstance().createCreateMethodFromUsageFix(expression),
                            HighlightMethodUtil.getFixRange(referenceNameElement)
                        );
                    myHolder.add(hlBuilder.create());
                }
            }
        }
    }

    // 15.13 | 15.27
    // It is a compile-time error if any class or interface mentioned by either U or the function type of U
    // is not accessible from the class or interface in which the method reference expression appears.
    @RequiredReadAction
    private void checkFunctionalInterfaceTypeAccessible(@Nonnull PsiFunctionalExpression expression, PsiType functionalInterfaceType) {
        PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null) {
            if (!PsiUtil.isAccessible(myFile.getProject(), psiClass, expression, null)) {
                myHolder.add(
                    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expression)
                        .descriptionAndTooltip(HighlightUtil.buildProblemWithAccessDescription(expression, resolveResult))
                        .create()
                );
            }
            else {
                for (PsiType type : resolveResult.getSubstitutor().getSubstitutionMap().values()) {
                    checkFunctionalInterfaceTypeAccessible(expression, type);
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitReferenceList(PsiReferenceList list) {
        if (list.getFirstChild() == null) {
            return;
        }
        PsiElement parent = list.getParent();
        if (!(parent instanceof PsiTypeParameter)) {
            myHolder.add(AnnotationsHighlightUtil.checkAnnotationDeclaration(parent, list));
            if (!myHolder.hasErrorResults()) {
                add(HighlightClassUtil.checkExtendsAllowed(list));
            }
            if (!myHolder.hasErrorResults()) {
                add(HighlightClassUtil.checkImplementsAllowed(list));
            }
            if (!myHolder.hasErrorResults()) {
                add(HighlightClassUtil.checkClassExtendsOnlyOneClass(list));
            }
            if (!myHolder.hasErrorResults()) {
                add(GenericsHighlightUtil.checkGenericCannotExtendException(list));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitReferenceParameterList(PsiReferenceParameterList list) {
        if (list.getTextLength() == 0) {
            return;
        }

        add(checkFeature(list, JavaFeature.GENERICS));
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkParametersAllowed(list));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkParametersOnRaw(list));
        }
        if (!myHolder.hasErrorResults()) {
            for (PsiTypeElement typeElement : list.getTypeParameterElements()) {
                if (typeElement.getType() instanceof PsiDiamondType) {
                    add(checkFeature(list, JavaFeature.DIAMOND_TYPES));
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
        try {
            add(HighlightUtil.checkReturnStatementType(statement));
        }
        catch (IndexNotReadyException ignore) {
        }
    }

    @Override
    @RequiredReadAction
    public void visitStatement(@Nonnull PsiStatement statement) {
        super.visitStatement(statement);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkNotAStatement(statement));
        }
    }

    @Override
    @RequiredReadAction
    public void visitSuperExpression(@Nonnull PsiSuperExpression expr) {
        myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier(), myLanguageLevel));
        if (!myHolder.hasErrorResults()) {
            visitExpression(expr);
        }
    }

    @Override
    @RequiredReadAction
    public void visitSwitchLabelStatement(@Nonnull PsiSwitchLabelStatement statement) {
        super.visitSwitchLabelStatement(statement);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkCaseStatement(statement));
        }
    }

    @Override
    @RequiredReadAction
    public void visitSwitchLabeledRuleStatement(@Nonnull PsiSwitchLabeledRuleStatement statement) {
        super.visitSwitchLabeledRuleStatement(statement);
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkCaseStatement(statement));
        }
    }


    @Override
    @RequiredReadAction
    public void visitSwitchStatement(@Nonnull PsiSwitchStatement statement) {
        super.visitSwitchStatement(statement);
        checkSwitchBlock(statement);
    }

    @RequiredReadAction
    private void checkSwitchBlock(PsiSwitchBlock switchBlock) {
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkSwitchBlockStatements(switchBlock, myLanguageLevel, myFile));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkSwitchSelectorType(switchBlock, myLanguageLevel));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.addAll(HighlightUtil.checkSwitchLabelValues(switchBlock));
        }
    }

    @Override
    @RequiredReadAction
    public void visitThisExpression(PsiThisExpression expr) {
        if (!(expr.getParent() instanceof PsiReceiverParameter)) {
            myHolder.add(HighlightUtil.checkThisOrSuperExpressionInIllegalContext(expr, expr.getQualifier(), myLanguageLevel));
            if (!myHolder.hasErrorResults()) {
                add(HighlightUtil.checkMemberReferencedBeforeConstructorCalled(expr, null, myFile));
            }
            if (!myHolder.hasErrorResults()) {
                visitExpression(expr);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitThrowStatement(@Nonnull PsiThrowStatement statement) {
        add(HighlightUtil.checkUnhandledExceptions(statement, null));
        if (!myHolder.hasErrorResults()) {
            visitStatement(statement);
        }
    }

    @Override
    @RequiredReadAction
    public void visitTryStatement(@Nonnull PsiTryStatement statement) {
        super.visitTryStatement(statement);
        if (!myHolder.hasErrorResults()) {
            Set<PsiClassType> thrownTypes = HighlightUtil.collectUnhandledExceptions(statement);
            for (PsiParameter parameter : statement.getCatchBlockParameters()) {
                boolean added = myHolder.addAll(HighlightUtil.checkExceptionAlreadyCaught(parameter));
                if (!added) {
                    added = myHolder.addAll(HighlightUtil.checkExceptionThrownInTry(parameter, thrownTypes));
                }
                if (!added) {
                    myHolder.addAll(HighlightUtil.checkWithImprovedCatchAnalysis(parameter, thrownTypes, myFile));
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitResourceList(@Nonnull PsiResourceList resourceList) {
        super.visitResourceList(resourceList);
        if (!myHolder.hasErrorResults()) {
            add(checkFeature(resourceList, JavaFeature.TRY_WITH_RESOURCES));
        }
    }

    @Override
    @RequiredReadAction
    public void visitResourceVariable(@Nonnull PsiResourceVariable resource) {
        super.visitResourceVariable(resource);
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkTryResourceIsAutoCloseable(resource));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkUnhandledCloserExceptions(resource));
        }
    }

    @Override
    @RequiredReadAction
    public void visitResourceExpression(@Nonnull PsiResourceExpression resource) {
        super.visitResourceExpression(resource);
        if (!myHolder.hasErrorResults()) {
            add(checkFeature(resource, JavaFeature.REFS_AS_RESOURCE));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkResourceVariableIsFinal(resource));
        }
        if (!myHolder.hasErrorResults()) {
            add(HighlightUtil.checkTryResourceIsAutoCloseable(resource));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkUnhandledCloserExceptions(resource));
        }
    }

    @Override
    @RequiredReadAction
    public void visitTypeElement(@Nonnull PsiTypeElement type) {
        if (!myHolder.hasErrorResults()) {
            myHolder.add(HighlightUtil.checkIllegalType(type));
        }
        if (!myHolder.hasErrorResults()) {
            add(GenericsHighlightUtil.checkReferenceTypeUsedAsTypeArgument(type, myLanguageLevel));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(GenericsHighlightUtil.checkWildcardUsage(type));
        }
    }

    @Override
    @RequiredReadAction
    public void visitTypeCastExpression(@Nonnull PsiTypeCastExpression typeCast) {
        super.visitTypeCastExpression(typeCast);
        try {
            if (!myHolder.hasErrorResults()) {
                add(HighlightUtil.checkIntersectionInTypeCast(typeCast, myLanguageLevel, myFile));
            }
            if (!myHolder.hasErrorResults()) {
                myHolder.add(HighlightUtil.checkInconvertibleTypeCast(typeCast));
            }
        }
        catch (IndexNotReadyException ignored) {
        }
    }

    @Override
    @RequiredReadAction
    public void visitTypeParameterList(PsiTypeParameterList list) {
        PsiTypeParameter[] typeParameters = list.getTypeParameters();
        if (typeParameters.length > 0) {
            add(checkFeature(list, JavaFeature.GENERICS));
            if (!myHolder.hasErrorResults()) {
                add(GenericsHighlightUtil.checkTypeParametersList(list, typeParameters, myLanguageLevel));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitVariable(@Nonnull PsiVariable variable) {
        super.visitVariable(variable);
        try {
            if (!myHolder.hasErrorResults()) {
                add(HighlightUtil.checkVariableInitializerType(variable));
            }
        }
        catch (IndexNotReadyException ignored) {
        }
    }

    private boolean isReassigned(@Nonnull PsiVariable variable) {
        try {
            boolean reassigned;
            if (variable instanceof PsiParameter) {
                reassigned = myReassignedParameters.getInt((PsiParameter)variable) == 2;
            }
            else {
                reassigned = HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems);
            }

            return reassigned;
        }
        catch (IndexNotReadyException e) {
            return false;
        }
    }

    @Override
    @RequiredReadAction
    public void visitConditionalExpression(@Nonnull PsiConditionalExpression expression) {
        super.visitConditionalExpression(expression);
        if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) && PsiPolyExpressionUtil.isPolyExpression(expression)) {
            PsiExpression thenExpression = expression.getThenExpression();
            PsiExpression elseExpression = expression.getElseExpression();
            if (thenExpression != null && elseExpression != null) {
                PsiType conditionalType = expression.getType();
                if (conditionalType != null) {
                    PsiExpression[] sides = {
                        thenExpression,
                        elseExpression
                    };
                    for (PsiExpression side : sides) {
                        PsiType sideType = side.getType();
                        if (sideType != null && !TypeConversionUtil.isAssignable(conditionalType, sideType)) {
                            add(HighlightUtil.checkAssignability(conditionalType, sideType, side, side));
                        }
                    }
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitReceiverParameter(@Nonnull PsiReceiverParameter parameter) {
        super.visitReceiverParameter(parameter);
        if (!myHolder.hasErrorResults()) {
            add(checkFeature(parameter, JavaFeature.RECEIVERS));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(AnnotationsHighlightUtil.checkReceiverPlacement(parameter));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(AnnotationsHighlightUtil.checkReceiverType(parameter));
        }
    }

    @Override
    @RequiredReadAction
    public void visitModule(@Nonnull PsiJavaModule module) {
        super.visitModule(module);
        if (!myHolder.hasErrorResults()) {
            add(checkFeature(module, JavaFeature.MODULES));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(ModuleHighlightUtil.checkFileName(module, myFile));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(ModuleHighlightUtil.checkFileDuplicates(module, myFile));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.addAll(ModuleHighlightUtil.checkDuplicateStatements(module));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(ModuleHighlightUtil.checkClashingReads(module));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.addAll(ModuleHighlightUtil.checkUnusedServices(module));
        }
        if (!myHolder.hasErrorResults()) {
            myHolder.add(ModuleHighlightUtil.checkFileLocation(module, myFile));
        }
    }

    @Override
    @RequiredReadAction
    public void visitRequiresStatement(@Nonnull PsiRequiresStatement statement) {
        super.visitRequiresStatement(statement);
        if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
            PsiJavaModule container = (PsiJavaModule)statement.getParent();
            PsiJavaModuleReferenceElement ref = statement.getReferenceElement();
            if (!myHolder.hasErrorResults()) {
                myHolder.add(ModuleHighlightUtil.checkModuleReference(ref, container));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitPackageAccessibilityStatement(@Nonnull PsiPackageAccessibilityStatement statement) {
        super.visitPackageAccessibilityStatement(statement);
        if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
            if (!myHolder.hasErrorResults()) {
                myHolder.add(ModuleHighlightUtil.checkHostModuleStrength(statement));
            }
            if (!myHolder.hasErrorResults()) {
                myHolder.add(ModuleHighlightUtil.checkPackageReference(statement));
            }
            if (!myHolder.hasErrorResults()) {
                myHolder.addAll(ModuleHighlightUtil.checkPackageAccessTargets(statement));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitUsesStatement(@Nonnull PsiUsesStatement statement) {
        super.visitUsesStatement(statement);
        if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
            if (!myHolder.hasErrorResults()) {
                myHolder.add(ModuleHighlightUtil.checkServiceReference(statement.getClassReference()));
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitProvidesStatement(@Nonnull PsiProvidesStatement statement) {
        super.visitProvidesStatement(statement);
        if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
            if (!myHolder.hasErrorResults()) {
                myHolder.addAll(ModuleHighlightUtil.checkServiceImplementations(statement));
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private HighlightInfo.Builder checkFeature(@Nonnull PsiElement element, @Nonnull JavaFeature feature) {
        return HighlightUtil.checkFeature(element, feature, myLanguageLevel, myFile);
    }

    private boolean add(HighlightInfo.Builder hlInfoBuilder) {
        return hlInfoBuilder != null && myHolder.add(hlInfoBuilder.create());
    }
}