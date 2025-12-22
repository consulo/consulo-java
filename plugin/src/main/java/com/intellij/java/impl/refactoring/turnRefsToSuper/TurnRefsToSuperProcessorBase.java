/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.turnRefsToSuper;

import com.intellij.java.impl.internal.diGraph.analyzer.GlobalAnalyzer;
import com.intellij.java.impl.internal.diGraph.analyzer.Mark;
import com.intellij.java.impl.internal.diGraph.analyzer.MarkedNode;
import com.intellij.java.impl.internal.diGraph.analyzer.OneEndFunctor;
import com.intellij.java.impl.internal.diGraph.impl.EdgeImpl;
import com.intellij.java.impl.internal.diGraph.impl.NodeImpl;
import com.intellij.java.impl.refactoring.rename.naming.AutomaticVariableRenamer;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.progress.ProgressManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.AutomaticRenamingDialog;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.collection.Queue;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public abstract class TurnRefsToSuperProcessorBase extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(TurnRefsToSuperProcessorBase.class);
    protected PsiClass myClass;
    protected final boolean myReplaceInstanceOf;
    protected PsiManager myManager;
    protected PsiSearchHelper mySearchHelper;
    protected Set<PsiElement> myMarkedNodes = new HashSet<>();
    private Queue<PsiExpression> myExpressionsQueue;
    protected Map<PsiElement, Node> myElementToNode = new HashMap<>();
    protected Map<SmartPsiElementPointer, String> myVariablesRenames = new HashMap<>();
    private final String mySuperClassName;
    private final List<UsageInfo> myVariablesUsages = new ArrayList<>();

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usages = refUsages.get();
        List<UsageInfo> filtered = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (usage instanceof TurnToSuperReferenceUsageInfo) {
                filtered.add(usage);
            }
        }

        myVariableRenamer = new AutomaticVariableRenamer(myClass, mySuperClassName, filtered);
        if (!myProject.getApplication().isUnitTestMode() && myVariableRenamer.hasAnythingToRename()) {
            AutomaticRenamingDialog dialog = new AutomaticRenamingDialog(myProject, myVariableRenamer);
            dialog.show();
            if (!dialog.isOK()) {
                return false;
            }

            List<PsiNamedElement> variables = myVariableRenamer.getElements();
            for (PsiNamedElement namedElement : variables) {
                PsiVariable variable = (PsiVariable)namedElement;
                SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(variable);
                myVariablesRenames.put(pointer, myVariableRenamer.getNewName(variable));
            }

            @RequiredReadAction
            Runnable runnable = () -> myVariableRenamer.findUsages(myVariablesUsages, false, false);

            if (!ProgressManager.getInstance()
                .runProcessWithProgressSynchronously(runnable, RefactoringLocalize.searchingForVariables(), true, myProject)) {
                return false;
            }
        }

        prepareSuccessful();
        return true;
    }

    private AutomaticVariableRenamer myVariableRenamer;

    @RequiredWriteAction
    protected void performVariablesRenaming() {
        try {
            //forget about smart pointers
            Map<PsiElement, String> variableRenames = new HashMap<>();
            for (Map.Entry<SmartPsiElementPointer, String> entry : myVariablesRenames.entrySet()) {
                variableRenames.put(entry.getKey().getElement(), entry.getValue());
            }

            for (UsageInfo usage : myVariablesUsages) {
                if (usage instanceof MoveRenameUsageInfo renameUsageInfo) {
                    String newName = variableRenames.get(renameUsageInfo.getUpToDateReferencedElement());
                    PsiReference reference = renameUsageInfo.getReference();
                    if (reference != null) {
                        reference.handleElementRename(newName);
                    }
                }
            }

            for (Map.Entry<SmartPsiElementPointer, String> entry : myVariablesRenames.entrySet()) {
                String newName = entry.getValue();
                if (newName != null) {
                    PsiVariable variable = (PsiVariable)entry.getKey().getElement();
                    variable.setName(newName);
                }
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    protected TurnRefsToSuperProcessorBase(Project project, boolean replaceInstanceOf, String superClassName) {
        super(project);
        mySuperClassName = superClassName;
        myManager = PsiManager.getInstance(project);
        mySearchHelper = PsiSearchHelper.SERVICE.getInstance(myManager.getProject());
        myManager = PsiManager.getInstance(myProject);
        myReplaceInstanceOf = replaceInstanceOf;
    }

    @RequiredReadAction
    protected List<UsageInfo> detectTurnToSuperRefs(PsiReference[] refs, List<UsageInfo> result) {
        buildGraph(refs);

        for (PsiReference ref : refs) {
            PsiElement element = ref.getElement();
            if (canTurnToSuper(element)) {
                result.add(new TurnToSuperReferenceUsageInfo(element));
            }
        }
        return result;
    }

    protected boolean canTurnToSuper(PsiElement ref) {
        return !myMarkedNodes.contains(ref);
    }

    @RequiredWriteAction
    protected static void processTurnToSuperRefs(UsageInfo[] usages, PsiClass aSuper) throws IncorrectOperationException {
        for (UsageInfo usage : usages) {
            if (usage instanceof TurnToSuperReferenceUsageInfo) {
                PsiElement element = usage.getElement();
                if (element != null) {
                    PsiReference ref = element.getReference();
                    assert ref != null;
                    PsiElement newElement = ref.bindToElement(aSuper);

                    if (newElement.getParent() instanceof PsiTypeElement typeElem
                        && typeElem.getParent() instanceof PsiTypeCastExpression typeCast) {
                        fixPossiblyRedundantCast(typeCast);
                    }
                }
            }
        }
    }

    @RequiredWriteAction
    private static void fixPossiblyRedundantCast(PsiTypeCastExpression cast) throws IncorrectOperationException {
        PsiTypeElement castTypeElement = cast.getCastType();
        if (castTypeElement == null) {
            return;
        }
        PsiClass castClass = PsiUtil.resolveClassInType(castTypeElement.getType());
        if (castClass == null) {
            return;
        }

        PsiExpression operand = cast.getOperand();
        if (operand == null) {
            return;
        }
        PsiClass operandClass = PsiUtil.resolveClassInType(RefactoringUtil.getTypeByExpression(operand));
        if (operandClass == null) {
            return;
        }

        if (!castClass.getManager().areElementsEquivalent(castClass, operandClass) &&
            !operandClass.isInheritor(castClass, true)) {
            return;
        }
        // OK, cast is redundant
        PsiExpression exprToReplace = cast;
        while (exprToReplace.getParent() instanceof PsiParenthesizedExpression parenthesized) {
            exprToReplace = parenthesized;
        }
        exprToReplace.replace(operand);
    }

    @RequiredReadAction
    private void buildGraph(PsiReference[] refs) {
        myMarkedNodes.clear();
        myExpressionsQueue = new Queue<>(refs.length);
        myElementToNode.clear();
        for (PsiReference ref : refs) {
            processUsage(ref.getElement());
        }

        processQueue();

        markNodes();

        spreadMarks();
    }

    @RequiredReadAction
    private void processUsage(PsiElement ref) {
        if (ref instanceof PsiReferenceExpression) {
            if (ref.getParent() instanceof PsiReferenceExpression refExpr && !isInSuper(refExpr.resolve())) {
                markNode(ref);
            }
            return;
        }

        PsiElement parent = ref.getParent();
        if (parent instanceof PsiTypeElement) {
            PsiElement grandparent = parent.getParent();
            while (grandparent instanceof PsiTypeElement) {
                addLink(grandparent, parent);
                addLink(parent, grandparent);
                parent = grandparent;
                grandparent = parent.getParent();
            }
            PsiTypeElement typeElement = (PsiTypeElement)parent;

            addLink(typeElement, ref);
            addLink(ref, typeElement);

            if (grandparent instanceof PsiVariable variable) {
                processVariableType(variable);
            }
            else if (grandparent instanceof PsiMethod method) {
                processMethodReturnType(method);
            }
            else if (grandparent instanceof PsiTypeCastExpression) {
                addLink(grandparent, typeElement);
                addLink(typeElement, grandparent);
            }
            else if (grandparent instanceof PsiReferenceParameterList refParameterList) {
                if (grandparent.getParent() instanceof PsiJavaCodeReferenceElement classReference) {
                    if (classReference.getParent() instanceof PsiReferenceList referenceList) {
                        PsiClass parentClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
                        if (parentClass != null) {
                            if (referenceList.equals(parentClass.getExtendsList())
                                || referenceList.equals(parentClass.getImplementsList())) {
                                PsiTypeElement[] typeParameterElements = refParameterList.getTypeParameterElements();
                                for (int i = 0; i < typeParameterElements.length; i++) {
                                    if (typeParameterElements[i] == typeElement
                                        && classReference.resolve() instanceof PsiClass psiClass) {
                                        PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
                                        if (typeParameters.length > i) {
                                            linkTypeParameterInstantiations(typeParameters[i], typeElement, parentClass);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (classReference.getParent() instanceof PsiTypeElement) {
                        processUsage(classReference);
                        return;
                    }
                    else if (classReference.getParent() instanceof PsiNewExpression) {
                        PsiVariable variable = PsiTreeUtil.getParentOfType(classReference, PsiVariable.class);
                        if (variable != null) {
                            processUsage(variable);
                            return;
                        }
                    }
                    else if (classReference.getParent() instanceof PsiAnonymousClass) {
                        processUsage(classReference);
                        return;
                    }
                }
                markNode(ref); //???
            }
        }
        else if (parent instanceof PsiNewExpression newExpr) {
            if (newExpr.getType() instanceof PsiArrayType) {
                addLink(newExpr, ref);
                addLink(ref, newExpr);
                PsiArrayInitializerExpression initializer = newExpr.getArrayInitializer();
                if (initializer != null) {
                    addLink(ref, initializer);
                }
                checkToArray(ref, newExpr);
            }
            else {
                markNode(ref);
            }
        }
        else if (parent instanceof PsiJavaCodeReferenceElement codeRefElem && ref.equals(codeRefElem.getQualifier())) {
            PsiElement resolved = codeRefElem.resolve();
            if (resolved == null || !isInSuper(resolved)) {
                markNode(ref);
            }
        }
        else {
            markNode(ref);
        }
    }

    private void linkTypeParameterInstantiations(
        PsiTypeParameter typeParameter,
        PsiTypeElement instantiation,
        PsiClass inheritingClass
    ) {
        if (typeParameter.getOwner() instanceof PsiClass ownerClass) {
            LocalSearchScope derivedScope = new LocalSearchScope(inheritingClass);
            PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(ownerClass, inheritingClass, PsiSubstitutor.EMPTY);
            if (substitutor == null) {
                return;
            }
            LocalSearchScope baseScope = new LocalSearchScope(ownerClass);
            ReferencesSearch.search(typeParameter, baseScope).forEach(ref -> {
                if (ref.getElement().getParent() instanceof PsiTypeElement typeElem) {
                    PsiElement grandparent = typeElem.getParent();
                    if (grandparent instanceof PsiMethod method && typeElem.equals(method.getReturnTypeElement())) {
                        MethodSignature signature = method.getSignature(substitutor);
                        if (PsiUtil.isAccessible(method, inheritingClass, null)) {
                            PsiMethod inInheritor = MethodSignatureUtil.findMethodBySignature(inheritingClass, signature, false);
                            if (inInheritor != null && inInheritor.getReturnTypeElement() != null) {
                                addLink(instantiation, method.getReturnTypeElement());
                                addLink(method.getReturnTypeElement(), instantiation);
                            }
                        }
                    }
                    else if (grandparent instanceof PsiParameter parameter
                        && parameter.getDeclarationScope() instanceof PsiMethod method) {
                        int index = ((PsiParameterList)parameter.getParent()).getParameterIndex(parameter);
                        MethodSignature signature = method.getSignature(substitutor);
                        if (PsiUtil.isAccessible(method, inheritingClass, null)) {
                            PsiMethod inInheritor =
                                MethodSignatureUtil.findMethodBySignature(inheritingClass, signature, false);
                            if (inInheritor != null) {
                                PsiParameter[] inheritorParams = inInheritor.getParameterList().getParameters();
                                LOG.assertTrue(inheritorParams.length > index);
                                PsiTypeElement hisTypeElement = inheritorParams[index].getTypeElement();
                                addLink(instantiation, hisTypeElement);
                                addLink(hisTypeElement, instantiation);
                            }
                        }
                    }
                }

                return true;
            });
        }
    }

    private void addArgumentParameterLink(PsiElement arg, PsiExpressionList actualArgsList, PsiMethod method) {
        PsiParameter[] params = method.getParameterList().getParameters();
        PsiExpression[] actualArgs = actualArgsList.getExpressions();
        int argIndex = -1;
        for (int i = 0; i < actualArgs.length; i++) {
            PsiExpression actualArg = actualArgs[i];
            if (actualArg.equals(arg)) {
                argIndex = i;
                break;
            }
        }

        if (argIndex >= 0 && argIndex < params.length) {
            addLink(params[argIndex], arg);
        }
        else if (method.isVarArgs() && argIndex >= params.length) {
            addLink(params[params.length - 1], arg);
        }
    }

    @RequiredReadAction
    private void checkToArray(PsiElement ref, PsiNewExpression newExpression) {
        PsiClass javaUtilCollectionClass = JavaPsiFacade.getInstance(myManager.getProject())
            .findClass(CommonClassNames.JAVA_UTIL_COLLECTION, ref.getResolveScope());
        if (javaUtilCollectionClass != null
            && newExpression.getParent() instanceof PsiExpressionList expressionList
            && expressionList.getParent() instanceof PsiMethodCallExpression methodCall
            && methodCall.getParent() instanceof PsiTypeCastExpression typeCast
            && methodCall.getMethodExpression().resolve() instanceof PsiMethod method
            && "toArray".equals(method.getName())) {

            PsiClass methodClass = method.getContainingClass();
            if (!methodClass.isInheritor(javaUtilCollectionClass, true)) {
                return;
            }

            // ok, this is an implementation of java.util.Collection.toArray
            addLink(typeCast, ref);
        }
    }

    @RequiredReadAction
    private void processVariableType(PsiVariable variable) {
        final PsiTypeElement type = variable.getTypeElement();
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
            addLink(type, initializer);
        }

        for (PsiReference ref : ReferencesSearch.search(variable)) {
            PsiElement element = ref.getElement();
            addLink(element, type);
            addLink(type, element);
            analyzeVarUsage(element);
        }

        if (variable instanceof PsiParameter parameter) {
            PsiElement declScope = parameter.getDeclarationScope();
            if (declScope instanceof PsiCatchSection) {
                markNode(type);
            }
            else if (declScope instanceof PsiForeachStatement forEach) {
                PsiExpression iteratedValue = forEach.getIteratedValue();
                addLink(type, iteratedValue);
                addLink(iteratedValue, type);
            }
            else if (declScope instanceof PsiMethod method) {
                final int index = method.getParameterList().getParameterIndex(parameter);

                {
                    for (PsiReference call : ReferencesSearch.search(method)) {
                        PsiElement ref = call.getElement();
                        PsiExpressionList argumentList;
                        if (ref.getParent() instanceof PsiCall psiCall) {
                            argumentList = psiCall.getArgumentList();
                        }
                        else if (ref.getParent() instanceof PsiAnonymousClass anonymousClass) {
                            argumentList = ((PsiConstructorCall)anonymousClass.getParent()).getArgumentList();
                        }
                        else {
                            continue;
                        }
                        if (argumentList == null) {
                            continue;
                        }
                        PsiExpression[] args = argumentList.getExpressions();
                        if (index >= args.length) {
                            continue;
                        }
                        addLink(type, args[index]);
                    }
                }

                final class Inner {
                    void linkInheritors(PsiMethod[] methods) {
                        for (PsiMethod superMethod : methods) {
                            PsiParameter[] parameters = superMethod.getParameterList().getParameters();
                            if (index >= parameters.length) {
                                continue;
                            }
                            PsiTypeElement superType = parameters[index].getTypeElement();
                            addLink(superType, type);
                            addLink(type, superType);
                        }
                    }
                }

                PsiMethod[] superMethods = method.findSuperMethods();
                new Inner().linkInheritors(superMethods);
                PsiClass containingClass = method.getContainingClass();
                List<PsiClass> subClasses = new ArrayList<>(ClassInheritorsSearch.search(containingClass, false).findAll());
                // ??? In the theory this is non-efficient way: too many inheritors can be processed.
                // ??? But in real use it seems reasonably fast. If poor performance problems emerged,
                // ??? should be optimized
                for (int i1 = 0; i1 != subClasses.size(); ++i1) {
                    PsiMethod[] mBSs = subClasses.get(i1).findMethodsBySignature(method, true);
                    new Inner().linkInheritors(mBSs);
                }
            }
            else {
                LOG.error("Unexpected scope: " + declScope);
            }
        }
        else if (variable instanceof PsiResourceVariable) {
            PsiJavaParserFacade facade = JavaPsiFacade.getInstance(myProject).getParserFacade();
            checkConstrainingType(type, facade.createTypeFromText(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, variable));
        }
    }

    private void analyzeVarUsage(PsiElement element) {
        PsiType constrainingType = null;

        PsiElement parent = element.getParent();
        if (parent instanceof PsiReturnStatement) {
            PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
            assert method != null;
            constrainingType = method.getReturnType();
        }
        else if (parent instanceof PsiAssignmentExpression assignment) {
            constrainingType = assignment.getLExpression().getType();
        }
        //todo[ann] this works for AImpl->A but fails on List<AImpl> (see testForEach1() and testIDEADEV23807()).
        //else if (parent instanceof PsiForeachStatement) {
        //    final PsiType exprType = ((PsiExpression)element).getType();
        //    if (!(exprType instanceof PsiArrayType)) {
        //        final PsiJavaParserFacade facade = JavaPsiFacade.getInstance(myProject).getParserFacade();
        //        constrainingType = facade.createTypeFromText(CommonClassNames.JAVA_LANG_ITERABLE, parent);
        //    }
        //}
        else if (parent instanceof PsiLocalVariable localVar) {
            constrainingType = localVar.getType();
        }

        checkConstrainingType(element, constrainingType);
    }

    private void checkConstrainingType(PsiElement element, @Nullable PsiType constrainingType) {
        if (constrainingType instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (!myClass.equals(resolved)) {
                if (resolved == null || !isSuperInheritor(resolved)) {
                    markNode(element);
                }
            }
        }
    }

    @RequiredReadAction
    private void processMethodReturnType(PsiMethod method) {
        final PsiTypeElement returnType = method.getReturnTypeElement();
        for (PsiReference call : ReferencesSearch.search(method)) {
            PsiElement ref = call.getElement();
            if (PsiTreeUtil.getParentOfType(ref, PsiDocComment.class) != null) {
                continue;
            }
            PsiElement parent = ref.getParent();
            addLink(parent, returnType);
        }

        PsiReturnStatement[] returnStatements = RefactoringUtil.findReturnStatements(method);
        for (PsiReturnStatement returnStatement : returnStatements) {
            PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue != null) {
                addLink(returnType, returnValue);
            }
        }

        PsiMethod[] superMethods = method.findSuperMethods();
        final class Inner {
            public void linkInheritors(PsiMethod[] methods) {
                for (PsiMethod superMethod : methods) {
                    PsiTypeElement superType = superMethod.getReturnTypeElement();
                    addLink(superType, returnType);
                    addLink(returnType, superType);
                }
            }
        }

        new Inner().linkInheritors(superMethods);
        // ??? In the theory this is non-efficient way: too many inheritors can be processed (and multiple times).
        // ??? But in real use it seems reasonably fast. If poor performance problems emerged,
        // ??? should be optimized
        PsiClass containingClass = method.getContainingClass();
        PsiClass[] subClasses = ClassInheritorsSearch.search(containingClass, false).toArray(PsiClass.EMPTY_ARRAY);
        for (int i1 = 0; i1 != subClasses.length; ++i1) {
            PsiMethod[] mBSs = subClasses[i1].findMethodsBySignature(method, true);
            new Inner().linkInheritors(mBSs);
        }
    }

    private void processQueue() {
        while (!myExpressionsQueue.isEmpty()) {
            PsiExpression expr = myExpressionsQueue.pullFirst();
            PsiElement parent = expr.getParent();
            if (parent instanceof PsiAssignmentExpression assignment) {
                if (assignment.getRExpression() != null) {
                    addLink(assignment.getLExpression(), assignment.getRExpression());
                }
                addLink(assignment, assignment.getLExpression());
                addLink(assignment.getLExpression(), assignment);
            }
            else if (parent instanceof PsiArrayAccessExpression arrayAccess) {
                if (expr.equals(arrayAccess.getArrayExpression())) {
                    addLink(arrayAccess, expr);
                    addLink(expr, arrayAccess);
                }
            }
            else if (parent instanceof PsiParenthesizedExpression) {
                addLink(parent, expr);
                addLink(expr, parent);
            }
            else if (parent instanceof PsiArrayInitializerExpression arrayInitializerExpr) {
                for (PsiExpression initializer : arrayInitializerExpr.getInitializers()) {
                    addLink(arrayInitializerExpr, initializer);
                }
            }
            else if (parent instanceof PsiExpressionList expressionList) {
                if (parent.getParent() instanceof PsiCallExpression call) {
                    PsiMethod method = call.resolveMethod();
                    if (method != null) {
                        addArgumentParameterLink(expr, expressionList, method);
                    }
                }
            }
        }
    }

    @RequiredReadAction
    protected void markNodes() {
        //for (Iterator iterator = myDependencyMap.keySet().getSectionsIterator(); getSectionsIterator.hasNext();) {
        for (PsiElement element : myElementToNode.keySet()) {
            if (element instanceof PsiExpression) {
                if (element.getParent() instanceof PsiReferenceExpression refExpr && element.equals(refExpr.getQualifierExpression())) {
                    PsiElement refElement = refExpr.resolve();
                    if (refElement != null && !isInSuper(refElement)) {
                        markNode(element);
                    }
                }
            }
            else if (!myReplaceInstanceOf && element.getParent() != null
                && element.getParent().getParent() instanceof PsiInstanceOfExpression) {
                markNode(element);
            }
            else if (element.getParent() instanceof PsiClassObjectAccessExpression) {
                markNode(element);
            }
            else if (element instanceof PsiParameter parameter) {
                PsiType type = TypeConversionUtil.erasure(parameter.getType());
                PsiClass aClass = PsiUtil.resolveClassInType(type);
                if (aClass != null) {
                    if (!myManager.isInProject(parameter) || !myManager.areElementsEquivalent(aClass, myClass)) {
                        if (!isSuperInheritor(aClass)) {
                            markNode(parameter);
                        }
                    }
                }
                else { // unresolvable class
                    markNode(parameter);
                }
            }
        }
    }

    protected abstract boolean isSuperInheritor(PsiClass aClass);

    protected abstract boolean isInSuper(PsiElement member);

    protected void addLink(PsiElement source, PsiElement target) {
        Node from = myElementToNode.get(source);
        Node to = myElementToNode.get(target);

        if (from == null) {
            from = new Node(source);
            if (source instanceof PsiExpression expression) {
                myExpressionsQueue.addLast(expression);
            }
            myElementToNode.put(source, from);
        }

        if (to == null) {
            to = new Node(target);
            if (target instanceof PsiExpression expression) {
                myExpressionsQueue.addLast(expression);
            }
            myElementToNode.put(target, to);
        }

        Edge.connect(from, to);
    }

    private void spreadMarks() {
        List<MarkedNode> markedNodes = new LinkedList<>();

        for (PsiElement markedNode : myMarkedNodes) {
            Node node = myElementToNode.get(markedNode);
            if (node != null) {
                markedNodes.addFirst(node);
            }
        }

        GlobalAnalyzer.doOneEnd(markedNodes, new Colorer());
    }

    private void markNode(PsiElement node) {
        myMarkedNodes.add(node);
    }

    class Colorer implements OneEndFunctor {
        @Override
        public Mark compute(Mark from, Mark edge, Mark to) {
            VisitMark mark = new VisitMark((VisitMark)to);

            myMarkedNodes.add(mark.getElement());
            mark.switchOn();

            return mark;
        }
    }

    private static class Edge extends EdgeImpl {
        private Edge(Node from, Node to) {
            super(from, to);
        }

        public static boolean connect(Node from, Node to) {
            if (from.mySuccessors.add(to)) {
                new Edge(from, to);
                return true;
            }

            return false;
        }
    }

    private static class VisitMark implements Mark {
        private boolean myVisited;
        private final PsiElement myElement;

        @Override
        public boolean coincidesWith(Mark x) {
            return ((VisitMark)x).myVisited == myVisited;
        }

        public VisitMark(VisitMark m) {
            myVisited = false;
            myElement = m.myElement;
        }

        public VisitMark(PsiElement e) {
            myVisited = false;
            myElement = e;
        }

        public void switchOn() {
            myVisited = true;
        }

        public void switchOff() {
            myVisited = false;
        }

        public PsiElement getElement() {
            return myElement;
        }
    }

    private static class Node extends NodeImpl {
        private final Set<Node> mySuccessors = new HashSet<>();
        private VisitMark myMark;

        public Node(PsiElement x) {
            super();
            myMark = new VisitMark(x);
        }

        @Override
        public Mark getMark() {
            return myMark;
        }

        @Override
        public void setMark(Mark x) {
            myMark = (VisitMark)x;
        }
    }
}
