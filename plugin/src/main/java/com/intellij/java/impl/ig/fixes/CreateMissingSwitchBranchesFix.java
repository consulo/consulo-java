// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.impl.ig.psiutils.CreateSwitchBranchesUtil;
import com.intellij.java.impl.ig.psiutils.SwitchUtils;
import com.intellij.java.language.psi.*;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class CreateMissingSwitchBranchesFix extends BaseSwitchFix {
    private final Set<String> myNames;

    public CreateMissingSwitchBranchesFix(@Nonnull PsiSwitchBlock block, Set<String> names) {
        super(block);
        myNames = names;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return getName();
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return CreateSwitchBranchesUtil.getActionName(myNames);
    }

    @Override
    protected void invoke() {
        PsiSwitchBlock switchBlock = myBlock.getElement();
        if (switchBlock == null) {
            return;
        }
        final PsiExpression switchExpression = switchBlock.getExpression();
        if (switchExpression == null) {
            return;
        }
        final PsiClassType switchType = (PsiClassType) switchExpression.getType();
        if (switchType == null) {
            return;
        }
        final PsiClass enumClass = switchType.resolve();
        if (enumClass == null) {
            return;
        }
        List<String> allEnumConstants = StreamEx.of(enumClass.getAllFields()).select(PsiEnumConstant.class).map(PsiField::getName).toList();
        Function<PsiSwitchLabelStatementBase, List<String>> caseExtractor =
            label -> ContainerUtil.map(SwitchUtils.findEnumConstants(label), PsiEnumConstant::getName);
        List<PsiSwitchLabelStatementBase> addedLabels = CreateSwitchBranchesUtil
            .createMissingBranches(switchBlock, allEnumConstants, myNames, caseExtractor);
        CreateSwitchBranchesUtil.createTemplate(switchBlock, addedLabels);
    }
}
