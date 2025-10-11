/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.language.psi.PsiUtilCore;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.table.ListTable;
import consulo.ui.ex.awt.table.ListWrappingTableModel;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ExtensionImpl
public class IgnoreResultOfCallInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_reportAllNonLibraryCalls = false;

    /**
     * @noinspection PublicField
     */
    @NonNls
    public String callCheckString = "java.io.InputStream,read," +
        "java.io.InputStream,skip," +
        "java.lang.StringBuffer,toString," +
        "java.lang.StringBuilder,toString," +
        "java.lang.String,.*," +
        "java.math.BigInteger,.*," +
        "java.math.BigDecimal,.*," +
        "java.net.InetAddress,.*," +
        "java.io.File,.*," +
        "java.lang.Object,equals|hashCode";

    final List<String> methodNamePatterns = new ArrayList();
    final List<String> classNames = new ArrayList();
    Map<String, Pattern> patternCache = null;

    public IgnoreResultOfCallInspection() {
        parseString(callCheckString, classNames, methodNamePatterns);
    }

    @Nonnull
    @Override
    public String getID() {
        return "ResultOfMethodCallIgnored";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.resultOfMethodCallIgnoredDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final PsiClass containingClass = (PsiClass) infos[0];
        final String className = containingClass.getName();
        return InspectionGadgetsLocalize.resultOfMethodCallIgnoredProblemDescriptor(className).get();
    }

    @Override
    public void readSettings(@Nonnull Element element) throws InvalidDataException {
        super.readSettings(element);
        parseString(callCheckString, classNames, methodNamePatterns);
    }

    @Override
    public void writeSettings(@Nonnull Element element) throws WriteExternalException {
        callCheckString = formatString(classNames, methodNamePatterns);
        super.writeSettings(element);
    }

    @Override
    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        final ListTable table = new ListTable(new ListWrappingTableModel(
            Arrays.asList(classNames, methodNamePatterns),
            InspectionGadgetsLocalize.resultOfMethodCallIgnoredClassColumnTitle().get(),
            InspectionGadgetsLocalize.resultOfMethodCallIgnoredMethodColumnTitle().get()
        ));
        final JPanel tablePanel = UiUtils.createAddRemovePanel(table);
        final CheckBox checkBox = new CheckBox(
            InspectionGadgetsLocalize.resultOfMethodCallIgnoredNonLibraryOption().get(),
            this,
            "m_reportAllNonLibraryCalls"
        );
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(checkBox, BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IgnoreResultOfCallVisitor();
    }

    private class IgnoreResultOfCallVisitor extends BaseInspectionVisitor {

        @Override
        public void visitExpressionStatement(@Nonnull PsiExpressionStatement statement) {
            super.visitExpressionStatement(statement);
            final PsiExpression expression = statement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
            final PsiMethod method = call.resolveMethod();
            if (method == null || method.isConstructor()) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if (PsiType.VOID.equals(returnType)) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (PsiUtilCore.hasErrorElementChild(statement)) {
                return;
            }
            if (m_reportAllNonLibraryCalls && !LibraryUtil.classIsInLibrary(aClass)) {
                registerMethodCallError(call, aClass);
                return;
            }
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (methodName == null) {
                return;
            }
            for (int i = 0; i < methodNamePatterns.size(); i++) {
                final String methodNamePattern = methodNamePatterns.get(i);
                if (!methodNamesMatch(methodName, methodNamePattern)) {
                    continue;
                }
                final String className = classNames.get(i);
                if (!InheritanceUtil.isInheritor(aClass, className)) {
                    continue;
                }
                registerMethodCallError(call, aClass);
                return;
            }
        }

        private boolean methodNamesMatch(String methodName, String methodNamePattern) {
            Pattern pattern;
            if (patternCache != null) {
                pattern = patternCache.get(methodNamePattern);
            }
            else {
                patternCache = new HashMap(methodNamePatterns.size());
                pattern = null;
            }
            if (pattern == null) {
                try {
                    pattern = Pattern.compile(methodNamePattern);
                    patternCache.put(methodNamePattern, pattern);
                }
                catch (PatternSyntaxException ignore) {
                    return false;
                }
                catch (NullPointerException ignore) {
                    return false;
                }
            }
            if (pattern == null) {
                return false;
            }
            final Matcher matcher = pattern.matcher(methodName);
            return matcher.matches();
        }
    }
}