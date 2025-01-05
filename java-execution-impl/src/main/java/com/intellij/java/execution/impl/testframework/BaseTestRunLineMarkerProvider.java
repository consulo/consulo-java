// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.impl.testframework;

import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.application.dumb.DumbAware;
import consulo.application.dumb.IndexNotReadyException;
import consulo.execution.icon.ExecutionIconGroup;
import consulo.execution.lineMarker.ExecutorAction;
import consulo.execution.lineMarker.RunLineMarkerContributor;
import consulo.execution.test.TestIconMapper;
import consulo.execution.test.TestStateInfo;
import consulo.execution.test.TestStateStorage;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.ui.ex.action.AnAction;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class BaseTestRunLineMarkerProvider extends RunLineMarkerContributor implements DumbAware {
    private static final String URL_TEST_PREFIX = "java:test://";
    private static final String URL_SUITE_PREFIX = "java:suite://";
    private static final Logger LOG = Logger.getInstance(TestRunLineMarkerProvider.class);

    @Override
    @Nullable
    public Info getInfo(@Nonnull PsiElement e) {
        if (isIdentifier(e)) {
            PsiElement element = e.getParent();
            if (element instanceof PsiClass psiClass) {
                if (!isTestClass(psiClass)) {
                    return null;
                }
                String url = URL_SUITE_PREFIX + ClassUtil.getJVMClassName(psiClass);
                TestStateStorage.Record state = TestStateStorage.getInstance(e.getProject()).getState(url);
                return getInfo(state, true, PsiMethodUtil.hasMainInClass(psiClass) ? 1 : 0);
            }
            if (element instanceof PsiMethod psiMethod) {
                PsiClass containingClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);
                if (!isTestMethod(containingClass, psiMethod)) {
                    return null;
                }
                String urlSuffix = ClassUtil.getJVMClassName(containingClass) + "/" + psiMethod.getName();

                List<String> urlList = new ArrayList<>();
                urlList.add(URL_TEST_PREFIX + urlSuffix);

                TestStateStorage.Record state = null;
                for (String url : urlList) {
                    state = TestStateStorage.getInstance(e.getProject()).getState(url);
                    if (state != null) {
                        break;
                    }
                }
                return getInfo(state, false, 0);
            }
        }
        return null;
    }

    private static boolean isTestClass(PsiClass clazz) {
        if (clazz == null) {
            return false;
        }
        try {
            return DumbService.getInstance(clazz.getProject()).computeWithAlternativeResolveEnabled(() -> {
                TestFramework framework = TestFrameworks.detectFramework(clazz);
                return framework != null && framework.isTestClass(clazz);
            });
        }
        catch (IndexNotReadyException e) {
            LOG.error(e);
            return false;
        }
    }

    private static boolean isTestMethod(PsiClass containingClass, PsiMethod method) {
        if (containingClass == null) {
            return false;
        }
        TestFramework framework = TestFrameworks.detectFramework(containingClass);
        return framework != null && framework.isTestMethod(method, false);
    }

    @Nonnull
    private static Info getInfo(TestStateStorage.Record state, boolean isClass, int order) {
        AnAction[] actions = ExecutorAction.getActions(order);
        return new Info(getTestStateIcon(state, isClass), element -> JavaExecutionLocalize.runTest().get(), actions);
    }

    @Nonnull
    protected static Image getTestStateIcon(@Nullable TestStateStorage.Record state, boolean isClass) {
        if (state != null) {
            TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(state.magnitude);
            if (magnitude != null) {
                switch (magnitude) {
                    case ERROR_INDEX, FAILED_INDEX -> {
                        return ExecutionIconGroup.gutterRunerror();
                    }
                    case PASSED_INDEX, COMPLETE_INDEX -> {
                        return ExecutionIconGroup.gutterRunsuccess();
                    }
                    default -> {
                    }
                }
            }
        }
        return isClass ? ExecutionIconGroup.gutterRerun() : ExecutionIconGroup.gutterRun();
    }

    protected boolean isIdentifier(PsiElement e) {
        return e instanceof PsiIdentifier;
    }
}
