// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.analysis.impl.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.component.ProcessCanceledException;
import consulo.container.PluginException;
import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginManager;
import consulo.find.FindManager;
import consulo.find.FindUsagesHandler;
import consulo.ide.impl.idea.find.findUsages.FindUsagesManager;
import consulo.ide.impl.idea.find.impl.FindManagerImpl;
import consulo.ide.localize.IdeLocalize;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.java.impl.codeInsight.JavaTargetElementUtilEx;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageInfo;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ChangeMethodSignatureFromUsageFix implements SyntheticIntentionAction {
    private static class ParameterTypes {
        @Nullable
        private final PsiType[] myParamTypes;

        private ParameterTypes(@Nullable ParameterInfoImpl[] parameterInfos, @Nonnull PsiElement context) {
            if (parameterInfos == null) {
                myParamTypes = null;
                return;
            }
            int n = parameterInfos.length;
            myParamTypes = new PsiType[n];
            for (int i = 0; i < n; i++) {
                PsiType paramType;
                try {
                    paramType = parameterInfos[i].createType(context);
                }
                catch (IncorrectOperationException e) {
                    paramType = null;
                }
                myParamTypes[i] = paramType;
            }
        }

        @Nonnull
        public String getPresentableText() {
            if (myParamTypes == null) {
                return IdeLocalize.textNotApplicable().get();
            }
            StringBuilder result = new StringBuilder();
            for (PsiType paramType : myParamTypes) {
                if (result.length() != 0) {
                    result.append(", ");
                }
                result.append(paramType != null ? paramType.getPresentableText() : IdeLocalize.textNotApplicable().get());
            }
            return result.toString();
        }

        public boolean isValid() {
            if (myParamTypes == null) {
                return false;
            }
            for (PsiType paramType : myParamTypes) {
                if (paramType == null) {
                    return false;
                }
            }
            return true;
        }
    }

    final PsiMethod myTargetMethod;
    final PsiExpression[] myExpressions;
    final PsiSubstitutor mySubstitutor;
    @Nonnull
    final PsiElement myContext;
    private final boolean myChangeAllUsages;
    private final int myMinUsagesNumberToShowDialog;
    ParameterInfoImpl[] myNewParametersInfo;
    private LocalizeValue myShortName = LocalizeValue.empty();
    private static final Logger LOG = Logger.getInstance(ChangeMethodSignatureFromUsageFix.class);

    public ChangeMethodSignatureFromUsageFix(
        @Nonnull PsiMethod targetMethod,
        @Nonnull PsiExpression[] expressions,
        @Nonnull PsiSubstitutor substitutor,
        @Nonnull PsiElement context,
        boolean changeAllUsages, int minUsagesNumberToShowDialog
    ) {
        myTargetMethod = targetMethod;
        myExpressions = expressions;
        mySubstitutor = substitutor;
        myContext = context;
        myChangeAllUsages = changeAllUsages;
        myMinUsagesNumberToShowDialog = minUsagesNumberToShowDialog;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        LocalizeValue shortText = myShortName;
        if (shortText.isNotEmpty()) {
            return shortText;
        }
        return JavaQuickFixLocalize.changeMethodSignatureFromUsageText(
            JavaHighlightUtil.formatMethod(myTargetMethod),
            myTargetMethod.getName(),
            new ParameterTypes(myNewParametersInfo, myContext).getPresentableText()
        );
    }

    private LocalizeValue getShortText(
        StringBuilder buf,
        Set<? extends ParameterInfoImpl> newParams,
        Set<? extends ParameterInfoImpl> removedParams,
        Set<? extends ParameterInfoImpl> changedParams
    ) {
        String targetMethodName = myTargetMethod.getName();
        if (myTargetMethod.getContainingClass().findMethodsByName(targetMethodName, true).length == 1) {
            if (newParams.size() == 1) {
                ParameterInfoImpl p = newParams.iterator().next();
                return JavaQuickFixLocalize.addParameterFromUsageText(
                    p.getTypeText(),
                    ArrayUtil.find(myNewParametersInfo, p) + 1,
                    targetMethodName
                );
            }
            if (removedParams.size() == 1) {
                ParameterInfoImpl p = removedParams.iterator().next();
                return JavaQuickFixLocalize.removeParameterFromUsageText(p.getOldIndex() + 1, targetMethodName);
            }
            if (changedParams.size() == 1) {
                ParameterInfoImpl p = changedParams.iterator().next();
                return JavaQuickFixLocalize.changeParameterFromUsageText(
                    p.getOldIndex() + 1,
                    targetMethodName,
                    Objects.requireNonNull(myTargetMethod.getParameterList().getParameter(p.getOldIndex())).getType().getPresentableText(),
                    p.getTypeText()
                );
            }
        }
        return LocalizeValue.localizeTODO("<html> Change signature of " + targetMethodName + "(" + buf + ")</html>");
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) {
            return false;
        }
        for (PsiExpression expression : myExpressions) {
            if (!expression.isValid()) {
                return false;
            }
        }
        if (!mySubstitutor.isValid()) {
            return false;
        }

        StringBuilder buf = new StringBuilder();
        Set<ParameterInfoImpl> newParams = new HashSet<>();
        Set<ParameterInfoImpl> removedParams = new HashSet<>();
        Set<ParameterInfoImpl> changedParams = new HashSet<>();
        myNewParametersInfo =
            getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor, buf, newParams, removedParams, changedParams);
        if (!new ParameterTypes(myNewParametersInfo, myContext).isValid()) {
            return false;
        }
        myShortName = getShortText(buf, newParams, removedParams, changedParams);
        return !isMethodSignatureExists();
    }

    @RequiredReadAction
    public boolean isMethodSignatureExists() {
        PsiClass target = myTargetMethod.getContainingClass();
        LOG.assertTrue(target != null);
        PsiMethod[] methods = target.findMethodsByName(myTargetMethod.getName(), false);
        for (PsiMethod method : methods) {
            if (PsiUtil.isApplicable(method, PsiSubstitutor.EMPTY, myExpressions)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }

        PsiMethod method = SuperMethodWarningUtil.checkSuperMethod(myTargetMethod, RefactoringLocalize.toRefactor());
        if (method == null) {
            return;
        }
        myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor);

        List<ParameterInfoImpl> parameterInfos = performChange(
            project,
            editor,
            file,
            method,
            myMinUsagesNumberToShowDialog,
            myNewParametersInfo,
            myChangeAllUsages,
            false,
            null
        );
        if (parameterInfos != null) {
            myNewParametersInfo = parameterInfos.toArray(new ParameterInfoImpl[0]);
        }
    }

    @RequiredUIAccess
    static List<ParameterInfoImpl> performChange(
        @Nonnull Project project,
        Editor editor,
        PsiFile file,
        @Nonnull PsiMethod method,
        int minUsagesNumber,
        final ParameterInfoImpl[] newParametersInfo,
        final boolean changeAllUsages,
        boolean allowDelegation,
        @Nullable final Consumer<? super List<ParameterInfoImpl>> callback
    ) {
        if (!FileModificationService.getInstance().prepareFileForWrite(method.getContainingFile())) {
            return null;
        }
        FindUsagesManager findUsagesManager = ((FindManagerImpl) FindManager.getInstance(project)).getFindUsagesManager();
        FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(method, false);
        if (handler == null) {
            return null;//on failure or cancel (e.g. cancel of super methods dialog)
        }

        JavaMethodFindUsagesOptions options = new JavaMethodFindUsagesOptions(project);
        options.isImplementingMethods = true;
        options.isOverridingMethods = true;
        options.isUsages = true;
        options.isSearchForTextOccurrences = false;
        int[] usagesFound = new int[1];
        Runnable runnable = () -> {
            Predicate<UsageInfo> processor = t -> ++usagesFound[0] < minUsagesNumber;

            handler.processElementUsages(method, processor, options);
        };
        LocalizeValue progressTitle = JavaQuickFixLocalize.searchingForUsagesProgressTitle();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, progressTitle, true, project)) {
            return null;
        }

        Application application = project.getApplication();
        if (application.isUnitTestMode() || usagesFound[0] < minUsagesNumber) {
            ChangeSignatureProcessor processor = new ChangeSignatureProcessor(
                project,
                method,
                false, null,
                method.getName(),
                method.getReturnType(),
                newParametersInfo
            ) {
                @Nonnull
                @Override
                protected UsageInfo[] findUsages() {
                    return changeAllUsages ? super.findUsages() : UsageInfo.EMPTY_ARRAY;
                }

                @Override
                @RequiredReadAction
                protected void performRefactoring(@Nonnull UsageInfo[] usages) {
                    CommandProcessor.getInstance().setCurrentCommandName(getCommandName());
                    super.performRefactoring(usages);
                    if (callback != null) {
                        callback.accept(Arrays.asList(newParametersInfo));
                    }
                }
            };
            processor.run();
            application.runWriteAction(() -> LanguageUndoUtil.markPsiFileForUndo(file));
            return Arrays.asList(newParametersInfo);
        }
        else {
            List<ParameterInfoImpl> parameterInfos = newParametersInfo != null
                ? new ArrayList<>(Arrays.asList(newParametersInfo))
                : new ArrayList<>();
            PsiReferenceExpression refExpr = JavaTargetElementUtilEx.findReferenceExpression(editor);
            JavaChangeSignatureDialog dialog =
                JavaChangeSignatureDialog.createAndPreselectNew(project, method, parameterInfos, allowDelegation, refExpr, callback);
            dialog.setParameterInfos(parameterInfos);
            dialog.show();
            return dialog.isOK() ? dialog.getParameters() : null;
        }
    }

    public static String getNewParameterNameByOldIndex(int oldIndex, ParameterInfoImpl[] parametersInfo) {
        if (parametersInfo == null) {
            return null;
        }
        for (ParameterInfoImpl info : parametersInfo) {
            if (info.oldParameterIndex == oldIndex) {
                return info.getName();
            }
        }
        return null;
    }

    @RequiredReadAction
    protected ParameterInfoImpl[] getNewParametersInfo(
        PsiExpression[] expressions,
        PsiMethod targetMethod,
        PsiSubstitutor substitutor
    ) {
        return getNewParametersInfo(
            expressions,
            targetMethod,
            substitutor,
            new StringBuilder(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>()
        );
    }

    @RequiredReadAction
    private ParameterInfoImpl[] getNewParametersInfo(
        PsiExpression[] expressions,
        PsiMethod targetMethod,
        PsiSubstitutor substitutor,
        StringBuilder buf,
        Set<? super ParameterInfoImpl> newParams,
        Set<? super ParameterInfoImpl> removedParams,
        Set<? super ParameterInfoImpl> changedParams
    ) {
        PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
        List<ParameterInfoImpl> result = new ArrayList<>();
        if (expressions.length < parameters.length) {
            // find which parameters to remove
            int ei = 0;
            int pi = 0;

            while (ei < expressions.length && pi < parameters.length) {
                PsiExpression expression = expressions[ei];
                PsiParameter parameter = parameters[pi];
                PsiType paramType = substitutor.substitute(parameter.getType());
                if (buf.length() > 0) {
                    buf.append(", ");
                }
                PsiType parameterType = PsiUtil.convertAnonymousToBaseType(paramType);
                String presentableText = escapePresentableType(parameterType);
                ParameterInfoImpl parameterInfo =
                    ParameterInfoImpl.create(pi).withName(parameter.getName()).withType(parameter.getType());
                if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
                    buf.append(presentableText);
                    result.add(parameterInfo);
                    pi++;
                    ei++;
                }
                else {
                    buf.append("<s>").append(presentableText).append("</s>");
                    removedParams.add(parameterInfo);
                    pi++;
                }
            }
            if (result.size() != expressions.length) {
                return null;
            }
            for (int i = pi; i < parameters.length; i++) {
                if (buf.length() > 0) {
                    buf.append(", ");
                }
                buf.append("<s>").append(escapePresentableType(parameters[i].getType())).append("</s>");
                ParameterInfoImpl parameterInfo = ParameterInfoImpl.create(pi)
                    .withName(parameters[i].getName())
                    .withType(parameters[i].getType());
                removedParams.add(parameterInfo);
            }
        }
        else if (expressions.length > parameters.length) {
            if (!findNewParamsPlace(expressions, targetMethod, substitutor, buf, newParams, parameters, result)) {
                return null;
            }
        }
        else {
            //parameter type changed
            for (int i = 0; i < parameters.length; i++) {
                if (buf.length() > 0) {
                    buf.append(", ");
                }
                PsiParameter parameter = parameters[i];
                PsiExpression expression = expressions[i];
                PsiType bareParamType = parameter.getType();
                if (!bareParamType.isValid()) {
                    try {
                        PsiUtil.ensureValidType(bareParamType);
                    }
                    catch (ProcessCanceledException e) {
                        throw e;
                    }
                    catch (Throwable e) {
                        PluginDescriptor plugin = PluginManager.getPlugin(parameter.getClass());

                        throw new PluginException(
                            parameter.getClass() + "; valid=" + parameter.isValid() + "; method.valid=" + targetMethod.isValid(),
                            e,
                            plugin.getPluginId()
                        );
                    }
                }
                PsiType paramType = substitutor.substitute(bareParamType);
                PsiUtil.ensureValidType(paramType);
                String presentableText = escapePresentableType(paramType);
                if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
                    result.add(ParameterInfoImpl.create(i).withName(parameter.getName()).withType(paramType));
                    buf.append(presentableText);
                }
                else {
                    if (PsiPolyExpressionUtil.isPolyExpression(expression)) {
                        return null;
                    }
                    PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
                    if (exprType == null || PsiType.VOID.equals(exprType)) {
                        return null;
                    }
                    if (exprType instanceof PsiDisjunctionType disjunctionType) {
                        exprType = disjunctionType.getLeastUpperBound();
                    }
                    if (!PsiTypesUtil.allTypeParametersResolved(myTargetMethod, exprType)) {
                        return null;
                    }
                    ParameterInfoImpl changedParameterInfo =
                        ParameterInfoImpl.create(i).withName(parameter.getName()).withType(exprType);
                    result.add(changedParameterInfo);
                    changedParams.add(changedParameterInfo);
                    buf.append("<s>").append(presentableText).append("</s> <b>").append(escapePresentableType(exprType)).append("</b>");
                }
            }
            // do not perform silly refactorings
            boolean isSilly = true;
            for (int i = 0; i < result.size(); i++) {
                PsiParameter parameter = parameters[i];
                PsiType paramType = substitutor.substitute(parameter.getType());
                ParameterInfoImpl parameterInfo = result.get(i);
                String typeText = parameterInfo.getTypeText();
                if (!paramType.equalsToText(typeText) && !paramType.getPresentableText().equals(typeText)) {
                    isSilly = false;
                    break;
                }
            }
            if (isSilly) {
                return null;
            }
        }
        return result.toArray(new ParameterInfoImpl[0]);
    }

    @Nonnull
    protected static String escapePresentableType(@Nonnull PsiType exprType) {
        return StringUtil.escapeXmlEntities(exprType.getPresentableText());
    }

    @RequiredReadAction
    protected boolean findNewParamsPlace(
        PsiExpression[] expressions,
        PsiMethod targetMethod,
        PsiSubstitutor substitutor,
        StringBuilder buf,
        Set<? super ParameterInfoImpl> newParams,
        PsiParameter[] parameters,
        List<? super ParameterInfoImpl> result
    ) {
        // find which parameters to introduce and where
        Set<String> existingNames = new HashSet<>();
        for (PsiParameter parameter : parameters) {
            existingNames.add(parameter.getName());
        }
        int ei = 0;
        int pi = 0;
        PsiParameter varargParam = targetMethod.isVarArgs() ? parameters[parameters.length - 1] : null;
        while (ei < expressions.length || pi < parameters.length) {
            if (buf.length() > 0) {
                buf.append(", ");
            }
            PsiExpression expression = ei < expressions.length ? expressions[ei] : null;
            PsiParameter parameter = pi < parameters.length ? parameters[pi] : null;
            PsiType paramType = parameter == null ? null : substitutor.substitute(parameter.getType());
            boolean parameterAssignable = paramType != null
                && (expression == null || TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression));
            if (parameterAssignable) {
                PsiType type = parameter.getType();
                result.add(ParameterInfoImpl.create(pi).withName(parameter.getName()).withType(type));
                buf.append(escapePresentableType(type));
                pi++;
                ei++;
            }
            else if (isArgumentInVarargPosition(expressions, ei, varargParam, substitutor)) {
                if (pi == parameters.length - 1) {
                    PsiType type = varargParam.getType();
                    result.add(ParameterInfoImpl.create(pi).withName(varargParam.getName()).withType(type));
                    buf.append(escapePresentableType(type));
                }
                pi++;
                ei++;
            }
            else if (expression != null) {
                if (varargParam != null && pi >= parameters.length) {
                    return false;
                }
                if (PsiPolyExpressionUtil.isPolyExpression(expression)) {
                    return false;
                }
                PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
                if (exprType == null || PsiType.VOID.equals(exprType)) {
                    return false;
                }
                if (exprType instanceof PsiDisjunctionType disjunctionType) {
                    exprType = disjunctionType.getLeastUpperBound();
                }
                if (!PsiTypesUtil.allTypeParametersResolved(myTargetMethod, exprType)) {
                    return false;
                }
                JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
                String name = suggestUniqueParameterName(codeStyleManager, expression, exprType, existingNames);
                ParameterInfoImpl newParameterInfo = ParameterInfoImpl.createNew()
                    .withName(name)
                    .withType(exprType)
                    .withDefaultValue(expression.getText().replace('\n', ' '));
                result.add(newParameterInfo);
                newParams.add(newParameterInfo);
                buf.append("<b>").append(escapePresentableType(exprType)).append("</b>");
                ei++;
            }
        }
        return result.size() == expressions.length || varargParam != null;
    }

    static boolean isArgumentInVarargPosition(PsiExpression[] expressions, int ei, PsiParameter varargParam, PsiSubstitutor substitutor) {
        if (varargParam == null) {
            return false;
        }
        PsiExpression expression = expressions[ei];
        if (expression == null || TypeConversionUtil.areTypesAssignmentCompatible(
            substitutor.substitute(((PsiEllipsisType) varargParam.getType()).getComponentType()),
            expression
        )) {
            int lastExprIdx = expressions.length - 1;
            return ei == lastExprIdx || expressions[lastExprIdx].getType() != PsiType.NULL;
        }
        return false;
    }

    @RequiredReadAction
    static String suggestUniqueParameterName(
        JavaCodeStyleManager codeStyleManager,
        PsiExpression expression,
        PsiType exprType,
        Set<? super String> existingNames
    ) {
        SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, expression, exprType);
        String[] names = nameInfo.names;
        if (expression instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiVariable variable) {
            VariableKind variableKind = codeStyleManager.getVariableKind(variable);
            String propertyName = codeStyleManager.variableNameToPropertyName(variable.getName(), variableKind);
            String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
            names = ArrayUtil.mergeArrays(new String[]{parameterName}, names);
        }
        if (names.length == 0) {
            names = new String[]{"param"};
        }
        int suffix = 0;
        while (true) {
            for (String name : names) {
                String suggested = name + (suffix == 0 ? "" : String.valueOf(suffix));
                if (existingNames.add(suggested)) {
                    return suggested;
                }
            }
            suffix++;
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
