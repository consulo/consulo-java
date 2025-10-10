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

import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement.Role;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author Pavel.Dolgov
 */
public class MergePackageAccessibilityStatementsFix extends MergeModuleStatementsFix<PsiPackageAccessibilityStatement> {
    private static final Logger LOG = Logger.getInstance(MergePackageAccessibilityStatementsFix.class);
    private final String myPackageName;
    private final Role myRole;

    protected MergePackageAccessibilityStatementsFix(@Nonnull PsiJavaModule javaModule, @Nonnull String packageName, @Nonnull Role role) {
        super(javaModule);
        myPackageName = packageName;
        myRole = role;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.java9MergeModuleStatementsFixName(getKeyword(), myPackageName);
    }

    @Nonnull
    @Override
    protected String getReplacementText(@Nonnull List<PsiPackageAccessibilityStatement> statementsToMerge) {
        List<String> moduleNames = getModuleNames(statementsToMerge);
        if (!moduleNames.isEmpty()) {
            return getKeyword() + " " + myPackageName + " " + PsiKeyword.TO + " " + joinUniqueNames(moduleNames) + ";";
        }
        return getKeyword() + " " + myPackageName + ";";
    }

    @Nonnull
    private static List<String> getModuleNames(@Nonnull List<PsiPackageAccessibilityStatement> statements) {
        List<String> result = new ArrayList<>();
        for (PsiPackageAccessibilityStatement statement : statements) {
            List<String> moduleNames = statement.getModuleNames();
            if (moduleNames.isEmpty()) {
                return Collections.emptyList();
            }
            result.addAll(moduleNames);
        }
        return result;
    }

    @Nonnull
    @Override
    protected List<PsiPackageAccessibilityStatement> getStatementsToMerge(@Nonnull PsiJavaModule javaModule) {
        return StreamSupport.stream(getStatements(javaModule, myRole).spliterator(), false)
            .filter(statement -> myPackageName.equals(statement.getPackageName()))
            .collect(Collectors.toList());
    }

    @Nullable
    public static MergeModuleStatementsFix createFix(@Nullable PsiPackageAccessibilityStatement statement) {
        if (statement != null && statement.getParent() instanceof PsiJavaModule javaModule) {
            String packageName = statement.getPackageName();
            if (packageName != null) {
                return new MergePackageAccessibilityStatementsFix(javaModule, packageName, statement.getRole());
            }
        }
        return null;
    }

    @Nonnull
    private static Iterable<PsiPackageAccessibilityStatement> getStatements(@Nonnull PsiJavaModule javaModule, @Nonnull Role role) {
        return switch (role) {
            case OPENS -> javaModule.getOpens();
            case EXPORTS -> javaModule.getExports();
        };
    }

    @Nonnull
    private String getKeyword() {
        return switch (myRole) {
            case OPENS -> PsiKeyword.OPENS;
            case EXPORTS -> PsiKeyword.EXPORTS;
        };
    }
}
