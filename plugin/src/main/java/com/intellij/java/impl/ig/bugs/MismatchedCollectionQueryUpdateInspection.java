/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.siyeh.ig.psiutils.ClassUtils.isImmutable;

@ExtensionImpl
public class MismatchedCollectionQueryUpdateInspection
    extends BaseInspection {

    private static final CallMatcher TRANSFORMED = CallMatcher.staticCall(
        CommonClassNames.JAVA_UTIL_COLLECTIONS,
        "asLifoQueue",
        "checkedCollection",
        "checkedList",
        "checkedMap",
        "checkedNavigableMap",
        "checkedNavigableSet",
        "checkedQueue",
        "checkedSet",
        "checkedSortedMap",
        "checkedSortedSet",
        "newSetFromMap",
        "synchronizedCollection",
        "synchronizedList",
        "synchronizedMap",
        "synchronizedNavigableMap",
        "synchronizedNavigableSet",
        "synchronizedSet",
        "synchronizedSortedMap",
        "synchronizedSortedSet"
    );
    private static final CallMatcher DERIVED = CallMatcher.anyOf(
        CollectionUtils.DERIVED_COLLECTION,
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "subList"),
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_SORTED_MAP, "headMap", "tailMap", "subMap"),
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_SORTED_SET, "headSet", "tailSet", "subSet")
    );
    private static final CallMatcher COLLECTION_SAFE_ARGUMENT_METHODS =
        CallMatcher.anyOf(
            CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "addAll", "removeAll", "containsAll", "remove"),
            CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "putAll", "remove")
        );
    private static final Set<String> COLLECTIONS_QUERIES =
        Set.of("binarySearch", "disjoint", "indexOfSubList", "lastIndexOfSubList", "max", "min");
    private static final Set<String> COLLECTIONS_UPDATES = Set.of("addAll", "fill", "copy", "replaceAll", "sort");
    private static final Set<String> COLLECTIONS_ALL =
        StreamEx.of(COLLECTIONS_QUERIES).append(COLLECTIONS_UPDATES).toImmutableSet();
    @SuppressWarnings("PublicField")
    public final ExternalizableStringSet queryNames = new ExternalizableStringSet(
        "contains",
        "copyInto",
        "equals",
        "forEach",
        "get",
        "hashCode",
        "iterator",
        "parallelStream",
        "propertyNames",
        "replaceAll",
        "save",
        "size",
        "store",
        "stream",
        "toArray",
        "toString",
        "write"
    );
    @SuppressWarnings("PublicField")
    public final ExternalizableStringSet updateNames = new ExternalizableStringSet(
        "add",
        "clear",
        "insert",
        "load",
        "merge",
        "offer",
        "poll",
        "pop",
        "push",
        "put",
        "remove",
        "replace",
        "retain",
        "set",
        "take"
    );
    @SuppressWarnings("PublicField")
    public final ExternalizableStringSet ignoredClasses = new ExternalizableStringSet();

    @Override
    public JComponent createOptionsPanel() {
        ListTable queryNamesTable =
            new ListTable(new ListWrappingTableModel(queryNames, InspectionGadgetsLocalize.queryColumnName().get()));
        JPanel queryNamesPanel = UiUtils.createAddRemovePanel(queryNamesTable);

        ListTable updateNamesTable =
            new ListTable(new ListWrappingTableModel(updateNames, InspectionGadgetsLocalize.updateColumnName().get()));
        JPanel updateNamesPanel = UiUtils.createAddRemovePanel(updateNamesTable);

        LocalizeValue ignoreClassesMessage = InspectionGadgetsLocalize.ignoredClassNames();
        ListTable ignoredClassesTable = new ListTable(new ListWrappingTableModel(ignoredClasses, ignoreClassesMessage.get()));
        JPanel ignoredClassesPanel = UiUtils.createAddRemoveTreeClassChooserPanel(
            ignoredClassesTable,
            ignoreClassesMessage.get(),
            CommonClassNames.JAVA_UTIL_COLLECTION,
            CommonClassNames.JAVA_UTIL_MAP
        );

        JPanel namesPanel = new JPanel(new GridLayout(1, 2, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
        namesPanel.add(queryNamesPanel);
        namesPanel.add(updateNamesPanel);

        JPanel panel = new JPanel(new GridLayout(2, 1, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
        panel.add(namesPanel);
        panel.add(ignoredClassesPanel);
        return panel;
    }

    @Pattern(VALID_ID_PATTERN)
    @Override
    @Nonnull
    public String getID() {
        return "MismatchedQueryAndUpdateOfCollection";
    }

    @Override
    @Nonnull
    public String getDisplayName() {
        return InspectionGadgetsLocalize.mismatchedUpdateCollectionDisplayName().get();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        boolean updated = (Boolean)infos[0];
        return updated
            ? InspectionGadgetsLocalize.mismatchedUpdateCollectionProblemDescriptorUpdatedNotQueried().get()
            : InspectionGadgetsLocalize.mismatchedUpdateCollectionProblemDescriptionQueriedNotUpdated().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MismatchedCollectionQueryUpdateVisitor();
    }

    private QueryUpdateInfo getCollectionQueryUpdateInfo(@Nullable PsiVariable variable, PsiElement context) {
        QueryUpdateInfo info = new QueryUpdateInfo();
        class Visitor extends JavaRecursiveElementWalkingVisitor {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression ref) {
                super.visitReferenceExpression(ref);
                if (variable == null) {
                    if (ref.getQualifierExpression() == null) {
                        makeUpdated();
                        makeQueried();
                    }
                }
                else if (ref.isReferenceTo(variable)) {
                    process(findEffectiveReference(ref));
                }
            }

            @Override
            @RequiredReadAction
            public void visitThisExpression(@Nonnull PsiThisExpression expression) {
                super.visitThisExpression(expression);
                if (variable == null) {
                    process(findEffectiveReference(expression));
                }
            }

            private void makeUpdated() {
                info.updated = true;
                if (info.queried) {
                    stopWalking();
                }
            }

            private void makeQueried() {
                info.queried = true;
                if (info.updated) {
                    stopWalking();
                }
            }

            @RequiredReadAction
            private void process(PsiExpression reference) {
                PsiMethodCallExpression qualifiedCall = ExpressionUtils.getCallForQualifier(reference);
                if (qualifiedCall != null) {
                    processQualifiedCall(qualifiedCall);
                    return;
                }
                PsiElement parent = reference.getParent();
                if (parent instanceof PsiExpressionList args) {
                    PsiCallExpression surroundingCall = ObjectUtil.tryCast(args.getParent(), PsiCallExpression.class);
                    if (surroundingCall != null) {
                        if (surroundingCall instanceof PsiMethodCallExpression methodCall
                            && processCollectionMethods(methodCall, reference)) {
                            return;
                        }
                        makeQueried();
                        if (!isQueryMethod(surroundingCall) && !COLLECTION_SAFE_ARGUMENT_METHODS.matches(surroundingCall)) {
                            makeUpdated();
                        }
                        return;
                    }
                }
                if (parent instanceof PsiMethodReferenceExpression methodRefExpr) {
                    processQualifiedMethodReference(methodRefExpr);
                    return;
                }
                if (parent instanceof PsiForeachStatement forEach && forEach.getIteratedValue() == reference) {
                    makeQueried();
                    return;
                }
                if (parent instanceof PsiAssignmentExpression assignment && assignment.getLExpression() == reference) {
                    PsiExpression rValue = assignment.getRExpression();
                    if (rValue == null) {
                        return;
                    }
                    if (ExpressionUtils.nonStructuralChildren(rValue)
                        .allMatch(MismatchedCollectionQueryUpdateInspection::isEmptyCollectionInitializer)) {
                        return;
                    }
                    if (ExpressionUtils.nonStructuralChildren(rValue)
                        .allMatch(MismatchedCollectionQueryUpdateInspection::isCollectionInitializer)) {
                        makeUpdated();
                        return;
                    }
                }
                if (parent instanceof PsiPolyadicExpression polyadic) {
                    IElementType tokenType = polyadic.getOperationTokenType();
                    if (tokenType.equals(JavaTokenType.PLUS)) {
                        // String concatenation
                        makeQueried();
                        return;
                    }
                    if (JavaTokenType.EQEQ.equals(tokenType) || JavaTokenType.NE.equals(tokenType)) {
                        return;
                    }
                }
                if (parent instanceof PsiAssertStatement assertStmt && assertStmt.getAssertDescription() == reference) {
                    makeQueried();
                    return;
                }
                if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiSynchronizedStatement) {
                    return;
                }
                // Any other reference
                makeUpdated();
                makeQueried();
            }

            @RequiredReadAction
            private void processQualifiedMethodReference(PsiMethodReferenceExpression expression) {
                String methodName = expression.getReferenceName();
                if (isQueryMethodName(methodName)) {
                    makeQueried();
                }
                if (isUpdateMethodName(methodName)) {
                    makeUpdated();
                }
                PsiMethod method = ObjectUtil.tryCast(expression.resolve(), PsiMethod.class);
                if (method == null ||
                    PsiType.VOID.equals(method.getReturnType()) ||
                    PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(expression))) {
                    return;
                }
                makeQueried();
            }

            private boolean processCollectionMethods(PsiMethodCallExpression call, PsiExpression arg) {
                PsiExpressionList expressionList = call.getArgumentList();
                String name = call.getMethodExpression().getReferenceName();
                if (!COLLECTIONS_ALL.contains(name) || !isCollectionsClassMethod(call)) {
                    return false;
                }
                if (COLLECTIONS_QUERIES.contains(name) && !(call.getParent() instanceof PsiExpressionStatement)) {
                    makeQueried();
                    return true;
                }
                if (COLLECTIONS_UPDATES.contains(name)) {
                    int index = ArrayUtil.indexOf(expressionList.getExpressions(), arg);
                    if (index == 0) {
                        makeUpdated();
                    }
                    else {
                        makeQueried();
                    }
                    return true;
                }
                return false;
            }

            private void processQualifiedCall(PsiMethodCallExpression call) {
                boolean voidContext = ExpressionUtils.isVoidContext(call);
                String name = call.getMethodExpression().getReferenceName();
                boolean queryQualifier = isQueryMethodName(name);
                boolean updateQualifier = isUpdateMethodName(name);
                if (queryQualifier &&
                    (!voidContext || PsiType.VOID.equals(call.getType()) || "toArray".equals(name) && !call.getArgumentList().isEmpty())) {
                    makeQueried();
                }
                if (updateQualifier) {
                    makeUpdated();
                    if (!voidContext) {
                        makeQueried();
                    }
                }
                if (!queryQualifier && !updateQualifier) {
                    if (!isQueryMethod(call)) {
                        makeUpdated();
                    }
                    makeQueried();
                }
            }

            private PsiExpression findEffectiveReference(PsiExpression expression) {
                while (true) {
                    PsiElement parent = expression.getParent();
                    if (parent instanceof PsiParenthesizedExpression
                        || parent instanceof PsiTypeCastExpression
                        || parent instanceof PsiConditionalExpression) {
                        expression = (PsiExpression)parent;
                        continue;
                    }
                    if (parent instanceof PsiReferenceExpression) {
                        PsiMethodCallExpression grandParent = ObjectUtil.tryCast(parent.getParent(), PsiMethodCallExpression.class);
                        if (DERIVED.test(grandParent)) {
                            expression = grandParent;
                            continue;
                        }
                    }
                    if (parent instanceof PsiExpressionList) {
                        PsiMethodCallExpression grandParent = ObjectUtil.tryCast(parent.getParent(), PsiMethodCallExpression.class);
                        if (TRANSFORMED.test(grandParent)) {
                            expression = grandParent;
                            continue;
                        }
                    }
                    break;
                }
                return expression;
            }
        }
        Visitor visitor = new Visitor();
        context.accept(visitor);
        return info;
    }

    private boolean isQueryMethodName(String methodName) {
        return isQueryUpdateMethodName(methodName, queryNames);
    }

    private boolean isUpdateMethodName(String methodName) {
        return isQueryUpdateMethodName(methodName, updateNames);
    }

    static boolean isEmptyCollectionInitializer(PsiExpression initializer) {
        if (!(initializer instanceof PsiNewExpression newExpression)) {
            return ConstructionUtils.isEmptyCollectionInitializer(initializer);
        }
        PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
            return false;
        }
        PsiExpression[] arguments = argumentList.getExpressions();
        for (PsiExpression argument : arguments) {
            PsiType argumentType = argument.getType();
            if (argumentType == null) {
                return false;
            }
            if (CollectionUtils.isCollectionClassOrInterface(argumentType)) {
                return false;
            }
            if (argumentType instanceof PsiArrayType) {
                return false;
            }
        }
        return true;
    }

    static boolean isCollectionInitializer(PsiExpression initializer) {
        return isEmptyCollectionInitializer(initializer) || ConstructionUtils.isPrepopulatedCollectionInitializer(initializer);
    }

    private static boolean isQueryUpdateMethodName(String methodName, Set<String> myNames) {
        if (methodName == null) {
            return false;
        }
        if (myNames.contains(methodName)) {
            return true;
        }
        for (String updateName : myNames) {
            if (methodName.startsWith(updateName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCollectionsClassMethod(PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method == null) {
            return false;
        }
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return false;
        }
        String qualifiedName = aClass.getQualifiedName();
        return CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(qualifiedName);
    }

    private static boolean isQueryMethod(@Nonnull PsiCallExpression call) {
        PsiType type = call.getType();
        boolean immutable = isImmutable(type);
        // If pure method returns mutable object, then it's possible that further mutation of that object will modify the original collection
        if (!immutable) {
            immutable = call instanceof PsiNewExpression && CollectionUtils.isConcreteCollectionClass(type);
        }
        PsiMethod method = call.resolveMethod();
        if (!immutable && method != null) {
            if (PsiUtil.resolveClassInClassTypeOnly(method.getReturnType()) instanceof PsiTypeParameter returnTypeParam) {
                // method returning unbounded type parameter is unlikely to allow modify original collection via the returned value
                immutable = returnTypeParam.getExtendsList().getReferencedTypes().length == 0;
            }
            if (!immutable) {
                immutable = Mutability.getMutability(method).isUnmodifiable();
            }
        }
        return immutable && !SideEffectChecker.mayHaveSideEffects(call);
    }

    static class QueryUpdateInfo {
        boolean updated;
        boolean queried;
    }

    private class MismatchedCollectionQueryUpdateVisitor extends BaseInspectionVisitor {
        private void register(PsiVariable variable, boolean written) {
            if (written) {
                PsiExpression initializer = variable.getInitializer();
                if (initializer != null) {
                    List<PsiExpression> expressions = ExpressionUtils.nonStructuralChildren(initializer).collect(Collectors.toList());
                    if (!expressions.stream().allMatch(MismatchedCollectionQueryUpdateInspection::isCollectionInitializer)) {
                        expressions.stream().filter(MismatchedCollectionQueryUpdateInspection::isCollectionInitializer)
                            .forEach(emptyCollection -> registerError(emptyCollection, Boolean.TRUE));
                        return;
                    }
                }
            }
            registerVariableError(variable, written);
        }

        @Override
        public void visitField(@Nonnull PsiField field) {
            super.visitField(field);
            if (!field.isPrivate()) {
                PsiClass aClass = field.getContainingClass();
                if (aClass == null || !aClass.isPrivate() || field.isPublic()) {
                    // Public field within private class can be written/read via reflection even without setAccessible hacks
                    // so we don't analyze such fields to reduce false-positives
                    return;
                }
            }
            PsiClass containingClass = PsiUtil.getTopLevelClass(field);
            if (!checkVariable(field, containingClass)) {
                return;
            }
            QueryUpdateInfo info = getCollectionQueryUpdateInfo(field, containingClass);
            boolean written = info.updated || updatedViaInitializer(field);
            boolean read = info.queried || queriedViaInitializer(field);
            if (read == written || UnusedSymbolUtil.isImplicitWrite(field) || UnusedSymbolUtil.isImplicitRead(field)) {
                // Even implicit read of the mutable collection field may cause collection change
                return;
            }
            register(field, written);
        }

        @Override
        public void visitLocalVariable(@Nonnull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (!checkVariable(variable, codeBlock)) {
                return;
            }
            QueryUpdateInfo info = getCollectionQueryUpdateInfo(variable, codeBlock);
            boolean written = info.updated || updatedViaInitializer(variable);
            boolean read = info.queried || queriedViaInitializer(variable);
            if (read != written) {
                register(variable, written);
            }
        }

        private boolean checkVariable(PsiVariable variable, PsiElement context) {
            if (context == null) {
                return false;
            }
            PsiType type = variable.getType();
            return CollectionUtils.isCollectionClassOrInterface(type)
                && ignoredClasses.stream().noneMatch(className -> InheritanceUtil.isInheritor(type, className));
        }

        private boolean updatedViaInitializer(PsiVariable variable) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer != null &&
                !ExpressionUtils.nonStructuralChildren(initializer)
                    .allMatch(MismatchedCollectionQueryUpdateInspection::isEmptyCollectionInitializer)) {
                return true;
            }
            if (initializer instanceof PsiNewExpression newExpression) {
                PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
                if (anonymousClass != null) {
                    if (getCollectionQueryUpdateInfo(null, anonymousClass).updated) {
                        return true;
                    }
                    ThisPassedAsArgumentVisitor visitor = new ThisPassedAsArgumentVisitor();
                    anonymousClass.accept(visitor);
                    if (visitor.isPassed()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean queriedViaInitializer(PsiVariable variable) {
            PsiExpression initializer = variable.getInitializer();
            return initializer != null &&
                ExpressionUtils.nonStructuralChildren(initializer)
                    .noneMatch(MismatchedCollectionQueryUpdateInspection::isCollectionInitializer);
        }
    }
}
