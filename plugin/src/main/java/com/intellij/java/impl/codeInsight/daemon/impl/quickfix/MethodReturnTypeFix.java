// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.OverriderUsageInfo;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.java.language.impl.psi.controlFlow.AnalysisCanceledException;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlow;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MethodReturnTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnBooleanFix");

    private final SmartTypePointer myReturnTypePointer;
    private final boolean myFixWholeHierarchy;
    private final String myName;
    private final String myCanonicalText;

    public MethodReturnTypeFix(@Nonnull PsiMethod method, @Nonnull PsiType returnType, boolean fixWholeHierarchy) {
        super(method);
        myReturnTypePointer = SmartTypePointerManager.getInstance(method.getProject()).createSmartTypePointer(returnType);
        myFixWholeHierarchy = fixWholeHierarchy;
        myName = method.getName();
        myCanonicalText = returnType.getCanonicalText();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.fixReturnTypeText(myName, myCanonicalText);
    }

    @Override
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiMethod myMethod = (PsiMethod) startElement;

        PsiType myReturnType = myReturnTypePointer.getType();
        if (myMethod.getManager().isInProject(myMethod) && myReturnType != null && myReturnType.isValid() && !TypeConversionUtil.isNullType(
            myReturnType)) {
            PsiType returnType = myMethod.getReturnType();
            if (returnType != null && returnType.isValid() && !Comparing.equal(myReturnType, returnType)) {
                return PsiTypesUtil.allTypeParametersResolved(myMethod, myReturnType);
            }
        }
        return false;
    }

    @Override
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiMethod myMethod = (PsiMethod) startElement;

        if (!FileModificationService.getInstance().prepareFileForWrite(myMethod.getContainingFile())) {
            return;
        }
        PsiType myReturnType = myReturnTypePointer.getType();
        if (myReturnType == null) {
            return;
        }
        if (myFixWholeHierarchy) {
            PsiMethod superMethod = myMethod.findDeepestSuperMethod();
            PsiType superReturnType = superMethod == null ? null : superMethod.getReturnType();
            if (superReturnType != null && !Comparing.equal(myReturnType, superReturnType) && !changeClassTypeArgument(
                myMethod,
                project,
                superReturnType,
                superMethod.getContainingClass(),
                editor,
                myReturnType
            )) {
                return;
            }
        }

        List<PsiMethod> affectedMethods = changeReturnType(myMethod, myReturnType);

        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiReturnStatement statementToSelect = null;
        if (!PsiType.VOID.equals(myReturnType)) {
            ReturnStatementAdder adder = new ReturnStatementAdder(factory, myReturnType);

            for (PsiMethod affectedMethod : affectedMethods) {
                PsiReturnStatement statement = adder.addReturnForMethod(file, affectedMethod);
                if (statement != null && affectedMethod == myMethod) {
                    statementToSelect = statement;
                }
            }
        }

        if (statementToSelect != null) {
            Editor editorForMethod = getEditorForMethod(myMethod, project, editor, file);
            if (editorForMethod != null) {
                selectReturnValueInEditor(statementToSelect, editorForMethod);
            }
        }
    }

    // to clearly separate data
    private static class ReturnStatementAdder {
        @Nonnull
        private final PsiElementFactory factory;
        @Nonnull
        private final PsiType myTargetType;

        private ReturnStatementAdder(@Nonnull PsiElementFactory factory, @Nonnull PsiType targetType) {
            this.factory = factory;
            myTargetType = targetType;
        }

        private PsiReturnStatement addReturnForMethod(PsiFile file, PsiMethod method) {
            PsiModifierList modifiers = method.getModifierList();
            if (modifiers.hasModifierProperty(PsiModifier.ABSTRACT) || method.getBody() == null) {
                return null;
            }

            try {
                ConvertReturnStatementsVisitor visitor = new ConvertReturnStatementsVisitor(factory, method, myTargetType);

                ControlFlow controlFlow;
                try {
                    controlFlow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(method.getBody());
                }
                catch (AnalysisCanceledException e) {
                    return null; //must be an error
                }
                PsiReturnStatement returnStatement;
                if (ControlFlowUtil.processReturns(controlFlow, visitor)) {
                    // extra return statement not needed
                    // get latest modified return statement and select...
                    returnStatement = visitor.getLatestReturn();
                }
                else {
                    returnStatement = visitor.createReturnInLastStatement();
                }
                if (method.getContainingFile() != file) {
                    LanguageUndoUtil.markPsiFileForUndo(file);
                }
                return returnStatement;
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }

            return null;
        }
    }

    private static Editor getEditorForMethod(PsiMethod myMethod, @Nonnull Project project, Editor editor, PsiFile file) {

        PsiFile containingFile = myMethod.getContainingFile();
        if (containingFile != file) {
            OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(project).builder(containingFile.getVirtualFile()).build();
            return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        }
        return editor;
    }

    @Nonnull
    private PsiMethod[] getChangeRoots(PsiMethod method, @Nonnull PsiType returnType) {
        if (!myFixWholeHierarchy) {
            return new PsiMethod[]{method};
        }

        PsiMethod[] methods = method.findDeepestSuperMethods();

        if (methods.length > 0) {
            for (PsiMethod psiMethod : methods) {
                if (returnType.equals(psiMethod.getReturnType())) {
                    return new PsiMethod[]{method};
                }
            }
            return methods;
        }
        // no - only base
        return new PsiMethod[]{method};
    }

    @Nonnull
    private List<PsiMethod> changeReturnType(PsiMethod method, @Nonnull PsiType returnType) {
        PsiMethod[] methods = getChangeRoots(method, returnType);

        MethodSignatureChangeVisitor methodSignatureChangeVisitor = new MethodSignatureChangeVisitor();
        for (PsiMethod targetMethod : methods) {
            methodSignatureChangeVisitor.addBase(targetMethod);
            ChangeSignatureProcessor processor = new UsagesAwareChangeSignatureProcessor(
                method.getProject(),
                targetMethod,
                false,
                null,
                myName,
                returnType,
                RemoveUnusedParameterFix
                    .getNewParametersInfo(targetMethod, null),
                methodSignatureChangeVisitor
            );
            processor.run();
        }

        return methodSignatureChangeVisitor.getAffectedMethods();
    }

    private static class MethodSignatureChangeVisitor implements UsageVisitor {
        private final List<PsiMethod> myAffectedMethods;

        private MethodSignatureChangeVisitor() {
            myAffectedMethods = new ArrayList<>();
        }

        public void addBase(PsiMethod baseMethod) {
            myAffectedMethods.add(baseMethod);
        }

        @Override
        public void visit(UsageInfo usage) {
            if (usage instanceof OverriderUsageInfo) {
                myAffectedMethods.add(((OverriderUsageInfo) usage).getOverridingMethod());
            }
        }

        public List<PsiMethod> getAffectedMethods() {
            return myAffectedMethods;
        }

        @Override
        public void preprocessCovariantOverriders(List<UsageInfo> covariantOverriderInfos) {
            for (Iterator<UsageInfo> usageInfoIterator = covariantOverriderInfos.iterator(); usageInfoIterator.hasNext(); ) {
                UsageInfo info = usageInfoIterator.next();
                if (info instanceof OverriderUsageInfo) {
                    OverriderUsageInfo overrideUsage = (OverriderUsageInfo) info;
                    if (myAffectedMethods.contains(overrideUsage.getOverridingMethod())) {
                        usageInfoIterator.remove();
                    }
                }
            }
        }
    }

    private interface UsageVisitor {
        void visit(UsageInfo usage);

        void preprocessCovariantOverriders(List<UsageInfo> covariantOverriderInfos);
    }

    private static class UsagesAwareChangeSignatureProcessor extends ChangeSignatureProcessor {
        private final UsageVisitor myUsageVisitor;

        private UsagesAwareChangeSignatureProcessor(
            Project project,
            PsiMethod method,
            boolean generateDelegate,
            @PsiModifier.ModifierConstant String newVisibility,
            String newName,
            PsiType newType,
            @Nonnull ParameterInfoImpl[] parameterInfo,
            UsageVisitor usageVisitor
        ) {
            super(project, method, generateDelegate, newVisibility, newName, newType, parameterInfo);
            myUsageVisitor = usageVisitor;
        }

        @Override
        protected void preprocessCovariantOverriders(List<UsageInfo> covariantOverriderInfos) {
            myUsageVisitor.preprocessCovariantOverriders(covariantOverriderInfos);
        }

        @Override
        protected void performRefactoring(@Nonnull UsageInfo[] usages) {
            super.performRefactoring(usages);

            for (UsageInfo usage : usages) {
                myUsageVisitor.visit(usage);
            }
        }
    }

    static void selectReturnValueInEditor(PsiReturnStatement returnStatement, Editor editor) {
        PsiExpression returnValue = returnStatement.getReturnValue();
        LOG.assertTrue(returnValue != null, returnStatement);
        TextRange range = returnValue.getTextRange();
        int offset = range.getStartOffset();

        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getEndOffset(), range.getStartOffset());
    }

    private static boolean changeClassTypeArgument(
        PsiMethod myMethod,
        Project project,
        PsiType superReturnType,
        PsiClass superClass,
        Editor editor,
        PsiType returnType
    ) {
        if (superClass == null || !superClass.hasTypeParameters()) {
            return true;
        }
        PsiClass superReturnTypeClass = PsiUtil.resolveClassInType(superReturnType);
        if (superReturnTypeClass == null || !(superReturnTypeClass instanceof PsiTypeParameter || superReturnTypeClass.hasTypeParameters())) {
            return true;
        }

        PsiClass derivedClass = myMethod.getContainingClass();
        if (derivedClass == null) {
            return true;
        }

        PsiReferenceParameterList referenceParameterList = findTypeArgumentsList(superClass, derivedClass);
        if (referenceParameterList == null) {
            return true;
        }

        PsiElement resolve = ((PsiJavaCodeReferenceElement) referenceParameterList.getParent()).resolve();
        if (!(resolve instanceof PsiClass)) {
            return true;
        }
        PsiClass baseClass = (PsiClass) resolve;

        if (returnType instanceof PsiPrimitiveType) {
            returnType = ((PsiPrimitiveType) returnType).getBoxedType(derivedClass);
        }

        PsiSubstitutor superClassSubstitutor =
            TypeConversionUtil.getSuperClassSubstitutor(superClass, baseClass, PsiSubstitutor.EMPTY);
        PsiType superReturnTypeInBaseClassType = superClassSubstitutor.substitute(superReturnType);
        PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
        PsiSubstitutor psiSubstitutor = resolveHelper.inferTypeArguments(PsiTypesUtil.filterUnusedTypeParameters(
            superReturnTypeInBaseClassType,
            baseClass.getTypeParameters()
        ), new
            PsiType[]{superReturnTypeInBaseClassType}, new PsiType[]{returnType}, PsiUtil.getLanguageLevel(superClass));

        TypeMigrationRules rules = new TypeMigrationRules(project);
        PsiSubstitutor compoundSubstitutor =
            TypeConversionUtil.getSuperClassSubstitutor(superClass, derivedClass, PsiSubstitutor.EMPTY).putAll(psiSubstitutor);
        rules.setBoundScope(new LocalSearchScope(derivedClass));
        TypeMigrationProcessor.runHighlightingTypeMigration(
            project,
            editor,
            rules,
            referenceParameterList,
            JavaPsiFacade.getElementFactory(project).createType(baseClass, compoundSubstitutor)
        );

        return false;
    }

    @Nullable
    private static PsiReferenceParameterList findTypeArgumentsList(PsiClass superClass, PsiClass derivedClass) {
        PsiReferenceParameterList referenceParameterList = null;
        if (derivedClass instanceof PsiAnonymousClass) {
            referenceParameterList = ((PsiAnonymousClass) derivedClass).getBaseClassReference().getParameterList();
        }
        else {
            PsiReferenceList implementsList = derivedClass.getImplementsList();
            if (implementsList != null) {
                referenceParameterList = extractReferenceParameterList(superClass, implementsList);
            }
            if (referenceParameterList == null) {
                PsiReferenceList extendsList = derivedClass.getExtendsList();
                if (extendsList != null) {
                    referenceParameterList = extractReferenceParameterList(superClass, extendsList);
                }
            }
        }
        return referenceParameterList;
    }

    @Nullable
    private static PsiReferenceParameterList extractReferenceParameterList(PsiClass superClass, PsiReferenceList extendsList) {
        for (PsiJavaCodeReferenceElement referenceElement : extendsList.getReferenceElements()) {
            PsiElement element = referenceElement.resolve();
            if (element instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass) element, superClass, true)) {
                return referenceElement.getParameterList();
            }
        }
        return null;
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
