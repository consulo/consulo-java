package com.intellij.java.analysis.impl.codeInspection.booleanIsAlwaysInverted;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataManager;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefGraphAnnotator;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author anna
 * @since 2006-01-06
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.BooleanMethodIsAlwaysInvertedInspection", fileExtensions = "java", categories = {"Java", "Boolean"})
public class BooleanMethodIsAlwaysInvertedInspection extends GlobalJavaInspectionTool {
    private static final Key<Boolean> ALWAYS_INVERTED = Key.create("ALWAYS_INVERTED_METHOD");

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.booleanMethodIsAlwaysInvertedDisplayName();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDataFlowIssues();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "BooleanMethodIsAlwaysInverted";
    }

    @Nullable
    @Override
    public RefGraphAnnotator getAnnotator(@Nonnull RefManager refManager, @Nonnull Object state) {
        return new BooleanInvertedAnnotator();
    }

    @Override
    @RequiredReadAction
    public CommonProblemDescriptor[] checkElement(
        @Nonnull RefEntity refEntity,
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull Object state
    ) {
        if (refEntity instanceof RefMethod refMethod) {
            if (!refMethod.isReferenced() || hasNonInvertedCalls(refMethod) || !refMethod.getSuperMethods().isEmpty()) {
                return null;
            }
            PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
            PsiIdentifier psiIdentifier = psiMethod.getNameIdentifier();
            if (psiIdentifier != null) {
                return new ProblemDescriptor[]{
                    manager.newProblemDescriptor(InspectionLocalize.booleanMethodIsAlwaysInvertedProblemDescriptor())
                        .range(psiIdentifier)
                        .withFix(new InvertMethodFix())
                        .create()
                };
            }
        }
        return null;
    }

    private static boolean hasNonInvertedCalls(RefMethod refMethod) {
        Boolean alwaysInverted = refMethod.getUserData(ALWAYS_INVERTED);
        if (alwaysInverted == null || refMethod.isExternalOverride() || (refMethod.isReferenced() && !alwaysInverted)) {
            return true;
        }
        Collection<RefMethod> superMethods = refMethod.getSuperMethods();
        for (RefMethod superMethod : superMethods) {
            if (hasNonInvertedCalls(superMethod)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean queryExternalUsagesRequests(
        RefManager manager,
        final GlobalJavaInspectionContext context,
        final ProblemDescriptionsProcessor descriptionsProcessor,
        Object state
    ) {
        manager.iterate(new RefJavaVisitor() {
            @Override
            @RequiredReadAction
            public void visitMethod(@Nonnull RefMethod refMethod) {
                if (descriptionsProcessor.getDescriptions(refMethod) != null) { //suspicious method -> need to check external usages
                    GlobalJavaInspectionContext.UsagesProcessor usagesProcessor = psiReference -> {
                        PsiElement psiReferenceExpression = psiReference.getElement();
                        if (psiReferenceExpression instanceof PsiReferenceExpression referenceExpression
                            && !isInvertedMethodCall(referenceExpression)) {
                            descriptionsProcessor.ignoreElement(refMethod);
                        }
                        return false;
                    };
                    traverseSuperMethods(refMethod, context, usagesProcessor);
                }
            }
        });
        return false;
    }

    private static void traverseSuperMethods(
        RefMethod refMethod,
        GlobalJavaInspectionContext globalContext,
        GlobalJavaInspectionContext.UsagesProcessor processor
    ) {
        Collection<RefMethod> superMethods = refMethod.getSuperMethods();
        for (RefMethod superMethod : superMethods) {
            traverseSuperMethods(superMethod, globalContext, processor);
        }
        globalContext.enqueueMethodUsagesProcessor(refMethod, processor);
    }

    private static void checkMethodCall(RefElement refWhat, PsiElement element) {
        if (refWhat instanceof RefMethod refMethod
            && refMethod.getElement() instanceof PsiMethod method
            && PsiType.BOOLEAN.equals(method.getReturnType())) {
            element.accept(new JavaRecursiveElementVisitor() {
                @Override
                @RequiredReadAction
                public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    PsiReferenceExpression methodExpression = call.getMethodExpression();
                    if (methodExpression.isReferenceTo(method)) {
                        if (isInvertedMethodCall(methodExpression)) {
                            return;
                        }
                        refMethod.putUserData(ALWAYS_INVERTED, Boolean.FALSE);
                    }
                }
            });
        }
    }

    private static boolean isInvertedMethodCall(PsiReferenceExpression methodExpression) {
        PsiPrefixExpression prefixExpression = PsiTreeUtil.getParentOfType(methodExpression, PsiPrefixExpression.class);
        if (methodExpression.getQualifierExpression() instanceof PsiSuperExpression) {
            return true; //don't flag super calls
        }
        if (prefixExpression != null) {
            IElementType tokenType = prefixExpression.getOperationTokenType();
            if (tokenType.equals(JavaTokenType.EXCL)) {
                return true;
            }
        }
        return false;
    }

    private static class BooleanInvertedAnnotator extends RefGraphAnnotator {
        @Override
        public void onInitialize(RefElement refElement) {
            if (refElement instanceof RefMethod refMethod
                && refMethod.getElement() instanceof PsiMethod method
                && PsiType.BOOLEAN.equals(method.getReturnType())) {
                refElement.putUserData(ALWAYS_INVERTED, Boolean.TRUE); //initial mark boolean methods
            }
        }

        @Override
        public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
            checkMethodCall(refWhat, refFrom.getElement());
        }
    }

    @Override
    public QuickFix getQuickFix(String hint) {
        return new InvertMethodFix();
    }

    private static class InvertMethodFix implements LocalQuickFix {
        @Override
        @Nonnull
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Invert method");
        }

        @Override
        @RequiredReadAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            assert psiMethod != null;
            RefactoringActionHandler invertBooleanHandler = JavaRefactoringActionHandlerFactory.getInstance().createInvertBooleanHandler();
            Runnable runnable =
                () -> invertBooleanHandler.invoke(project, new PsiElement[]{psiMethod}, DataManager.getInstance().getDataContext());
            if (project.getApplication().isUnitTestMode()) {
                runnable.run();
            }
            else {
                project.getApplication().invokeLater(runnable, project.getDisposed());
            }
        }
    }
}
