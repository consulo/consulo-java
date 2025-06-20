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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author Pavel.Dolgov
 */
public class MergeProvidesStatementsFix extends MergeModuleStatementsFix<PsiProvidesStatement> {
    private final String myInterfaceName;

    MergeProvidesStatementsFix(@Nonnull PsiJavaModule javaModule, @Nonnull String interfaceName) {
        super(javaModule);
        myInterfaceName = interfaceName;
    }

    @Nonnull
    @Override
    public String getText() {
        return JavaQuickFixBundle.message("java.9.merge.module.statements.fix.name", PsiKeyword.PROVIDES, myInterfaceName);
    }

    @Nls
    @Nonnull
    @Override
    public String getFamilyName() {
        return JavaQuickFixBundle.message("java.9.merge.module.statements.fix.family.name", PsiKeyword.PROVIDES);
    }

    @Nonnull
    @Override
    protected String getReplacementText(@Nonnull List<PsiProvidesStatement> statementsToMerge) {
        final List<String> implementationNames = getImplementationNames(statementsToMerge);
        LOG.assertTrue(!implementationNames.isEmpty());
        return PsiKeyword.PROVIDES + " " + myInterfaceName + " " + PsiKeyword.WITH + " " + joinUniqueNames(implementationNames) + ";";
    }

    @Nonnull
    private static List<String> getImplementationNames(@Nonnull List<PsiProvidesStatement> statements) {
        List<String> list = new ArrayList<>();
        for (PsiProvidesStatement statement : statements) {
            PsiReferenceList implementationList = statement.getImplementationList();
            if (implementationList == null) {
                continue;
            }
            for (PsiJavaCodeReferenceElement element : implementationList.getReferenceElements()) {
                ContainerUtil.addIfNotNull(list, element.getQualifiedName());
            }
        }
        return list;
    }

    @Nonnull
    @Override
    protected List<PsiProvidesStatement> getStatementsToMerge(@Nonnull PsiJavaModule javaModule) {
        return StreamSupport.stream(javaModule.getProvides().spliterator(), false).filter(statement ->
        {
            final PsiJavaCodeReferenceElement reference = statement.getInterfaceReference();
            return reference != null && myInterfaceName.equals(reference.getQualifiedName());
        }).collect(Collectors.toList());
    }

    @Nullable
    public static MergeModuleStatementsFix createFix(@Nullable PsiProvidesStatement statement) {
        if (statement != null) {
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiJavaModule) {
                final PsiJavaCodeReferenceElement interfaceReference = statement.getInterfaceReference();
                if (interfaceReference != null) {
                    final String interfaceName = interfaceReference.getQualifiedName();
                    if (interfaceName != null) {
                        return new MergeProvidesStatementsFix((PsiJavaModule) parent, interfaceName);
                    }
                }
            }
        }
        return null;
    }
}
