/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.engine;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.dumb.IndexNotReadyException;
import consulo.dataContext.DataContext;
import consulo.ide.ServiceManager;
import consulo.internal.com.sun.jdi.*;
import consulo.internal.com.sun.jdi.connect.spi.TransportService;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

@ServiceAPI(ComponentScope.APPLICATION)
public abstract class DebuggerUtils {
    private static final Logger LOG = Logger.getInstance(DebuggerUtils.class);
    private static final Key<Method> TO_STRING_METHOD_KEY = new Key<Method>("CachedToStringMethod");
    public static final Set<String> ourPrimitiveTypeNames =
        new HashSet<String>(Arrays.asList("byte", "short", "int", "long", "float", "double", "boolean", "char"));

    public static void cleanupAfterProcessFinish(DebugProcess debugProcess) {
        debugProcess.putUserData(TO_STRING_METHOD_KEY, null);
    }

    @NonNls
    public static String getValueAsString(final EvaluationContext evaluationContext, Value value) throws EvaluateException {
        try {
            if (value == null) {
                return "null";
            }
            if (value instanceof StringReference) {
                return ((StringReference)value).value();
            }
            if (isInteger(value)) {
                long v = ((PrimitiveValue)value).longValue();
                return String.valueOf(v);
            }
            if (isNumeric(value)) {
                double v = ((PrimitiveValue)value).doubleValue();
                return String.valueOf(v);
            }
            if (value instanceof BooleanValue) {
                boolean v = ((PrimitiveValue)value).booleanValue();
                return String.valueOf(v);
            }
            if (value instanceof CharValue) {
                char v = ((PrimitiveValue)value).charValue();
                return String.valueOf(v);
            }
            if (value instanceof ObjectReference) {
                if (value instanceof ArrayReference) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append("[");
                    for (Iterator<Value> iterator = ((ArrayReference)value).getValues().iterator(); iterator.hasNext(); ) {
                        final Value element = iterator.next();
                        builder.append(getValueAsString(evaluationContext, element));
                        if (iterator.hasNext()) {
                            builder.append(",");
                        }
                    }
                    builder.append("]");
                    return builder.toString();
                }

                final ObjectReference objRef = (ObjectReference)value;
                final DebugProcess debugProcess = evaluationContext.getDebugProcess();
                Method toStringMethod = debugProcess.getUserData(TO_STRING_METHOD_KEY);
                if (toStringMethod == null) {
                    try {
                        ReferenceType refType = objRef.virtualMachine().classesByName(JavaClassNames.JAVA_LANG_OBJECT).get(0);
                        toStringMethod = findMethod(refType, "toString", "()Ljava/lang/String;");
                        debugProcess.putUserData(TO_STRING_METHOD_KEY, toStringMethod);
                    }
                    catch (Exception ignored) {
                        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message(
                            "evaluation.error.cannot.evaluate.tostring",
                            objRef.referenceType().name()
                        ));
                    }
                }
                if (toStringMethod == null) {
                    throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message(
                        "evaluation.error.cannot.evaluate.tostring",
                        objRef.referenceType().name()
                    ));
                }
                // while result must be of com.sun.jdi.StringReference type, it turns out that sometimes (jvm bugs?)
                // it is a plain com.sun.tools.jdi.ObjectReferenceImpl
                final Value result =
                    debugProcess.invokeInstanceMethod(evaluationContext, objRef, toStringMethod, Collections.emptyList(), 0);
                if (result == null) {
                    return "null";
                }
                return result instanceof StringReference ? ((StringReference)result).value() : result.toString();
            }
            throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.unsupported.expression.type"));
        }
        catch (ObjectCollectedException ignored) {
            throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
        }
    }

    public static final int MAX_DISPLAY_LABEL_LENGTH = 1024 * 5;

    public static String convertToPresentationString(String str) {
        if (str.length() > MAX_DISPLAY_LABEL_LENGTH) {
            str = translateStringValue(str.substring(0, MAX_DISPLAY_LABEL_LENGTH));
            StringBuilder buf = new StringBuilder();
            buf.append(str);
            if (!str.endsWith("...")) {
                buf.append("...");
            }
            return buf.toString();
        }
        return translateStringValue(str);
    }

    @Nullable
    public static Method findMethod(@Nonnull ReferenceType refType, @NonNls String methodName, @NonNls String methodSignature) {
        if (refType instanceof ArrayType) {
            // for array types methodByName() in JDI always returns empty list
            final Method method =
                findMethod(refType.virtualMachine().classesByName(JavaClassNames.JAVA_LANG_OBJECT).get(0), methodName, methodSignature);
            if (method != null) {
                return method;
            }
        }

        Method method = null;
        if (methodSignature != null) {
            if (refType instanceof ClassType) {
                method = ((ClassType)refType).concreteMethodByName(methodName, methodSignature);
            }
            if (method == null) {
                final List<Method> methods = refType.methodsByName(methodName, methodSignature);
                if (methods.size() > 0) {
                    method = methods.get(0);
                }
            }
        }
        else {
            List<Method> methods = null;
            if (refType instanceof ClassType) {
                methods = refType.methodsByName(methodName);
            }
            if (methods != null && methods.size() > 0) {
                method = methods.get(0);
            }
        }
        return method;
    }

    public static boolean isNumeric(Value value) {
        return value != null && (isInteger(value) ||
            value instanceof FloatValue ||
            value instanceof DoubleValue);
    }

    public static boolean isInteger(Value value) {
        return value != null && (value instanceof ByteValue ||
            value instanceof ShortValue ||
            value instanceof LongValue ||
            value instanceof IntegerValue);
    }

    public static String translateStringValue(final String str) {
        int length = str.length();
        final StringBuilder buffer = new StringBuilder();
        StringUtil.escapeStringCharacters(length, str, buffer);
        if (str.length() > length) {
            buffer.append("...");
        }
        return buffer.toString();
    }

    @Nullable
    protected static ArrayClass getArrayClass(@Nonnull String className) {
        boolean searchBracket = false;
        int dims = 0;
        int pos;

        for (pos = className.lastIndexOf(']'); pos >= 0; pos--) {
            char c = className.charAt(pos);

            if (searchBracket) {
                if (c == '[') {
                    dims++;
                    searchBracket = false;
                }
                else if (!Character.isWhitespace(c)) {
                    break;
                }
            }
            else {
                if (c == ']') {
                    searchBracket = true;
                }
                else if (!Character.isWhitespace(c)) {
                    break;
                }
            }
        }

        if (searchBracket) {
            return null;
        }

        if (dims == 0) {
            return null;
        }

        return new ArrayClass(className.substring(0, pos + 1), dims);
    }

    public static boolean instanceOf(@Nonnull String subType, @Nonnull String superType, @Nullable Project project) {
        if (project == null) {
            return subType.equals(superType);
        }

        ArrayClass nodeClass = getArrayClass(subType);
        ArrayClass rendererClass = getArrayClass(superType);
        if (nodeClass == null || rendererClass == null) {
            return false;
        }

        if (nodeClass.dims == rendererClass.dims) {
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            PsiClass psiNodeClass = JavaPsiFacade.getInstance(project).findClass(nodeClass.className, scope);
            PsiClass psiRendererClass = JavaPsiFacade.getInstance(project).findClass(rendererClass.className, scope);
            return InheritanceUtil.isInheritorOrSelf(psiNodeClass, psiRendererClass, true);
        }
        else if (nodeClass.dims > rendererClass.dims) {
            return rendererClass.className.equals(JavaClassNames.JAVA_LANG_OBJECT);
        }
        return false;
    }

    @Nullable
    public static Type getSuperType(@Nullable Type subType, @Nonnull String superType) {
        if (subType == null) {
            return null;
        }

        if (JavaClassNames.JAVA_LANG_OBJECT.equals(superType)) {
            List list = subType.virtualMachine().classesByName(JavaClassNames.JAVA_LANG_OBJECT);
            if (list.size() > 0) {
                return (ReferenceType)list.get(0);
            }
            return null;
        }

        return getSuperTypeInt(subType, superType);
    }

    private static boolean typeEquals(@Nonnull Type type, @Nonnull String typeName) {
        int genericPos = typeName.indexOf('<');
        if (genericPos > -1) {
            typeName = typeName.substring(0, genericPos);
        }
        return type.name().replace('$', '.').equals(typeName.replace('$', '.'));
    }

    private static Type getSuperTypeInt(@Nonnull Type subType, @Nonnull String superType) {
        if (typeEquals(subType, superType)) {
            return subType;
        }

        Type result;
        if (subType instanceof ClassType) {
            try {
                ClassType clsType = (ClassType)subType;
                result = getSuperType(clsType.superclass(), superType);
                if (result != null) {
                    return result;
                }

                for (InterfaceType iface : clsType.allInterfaces()) {
                    if (typeEquals(iface, superType)) {
                        return iface;
                    }
                }
            }
            catch (ClassNotPreparedException e) {
                LOG.info(e);
            }
            return null;
        }

        if (subType instanceof InterfaceType) {
            try {
                for (InterfaceType iface : ((InterfaceType)subType).superinterfaces()) {
                    result = getSuperType(iface, superType);
                    if (result != null) {
                        return result;
                    }
                }
            }
            catch (ClassNotPreparedException e) {
                LOG.info(e);
            }
        }
        else if (subType instanceof ArrayType) {
            if (superType.endsWith("[]")) {
                try {
                    String superTypeItem = superType.substring(0, superType.length() - 2);
                    Type subTypeItem = ((ArrayType)subType).componentType();
                    return instanceOf(subTypeItem, superTypeItem) ? subType : null;
                }
                catch (ClassNotLoadedException e) {
                    LOG.info(e);
                }
            }
        }
        else if (subType instanceof PrimitiveType) {
            //noinspection HardCodedStringLiteral
            if (superType.equals("java.lang.Primitive")) {
                return subType;
            }
        }

        //only for interfaces and arrays
        if (JavaClassNames.JAVA_LANG_OBJECT.equals(superType)) {
            List list = subType.virtualMachine().classesByName(JavaClassNames.JAVA_LANG_OBJECT);
            if (list.size() > 0) {
                return (ReferenceType)list.get(0);
            }
        }
        return null;
    }

    public static boolean instanceOf(@Nullable Type subType, @Nonnull String superType) {
        return getSuperType(subType, superType) != null;
    }

    @Nullable
    public static PsiClass findClass(@Nonnull final String className, @Nonnull Project project, final GlobalSearchScope scope) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        try {
            if (getArrayClass(className) != null) {
                return JavaPsiFacade.getInstance(project).getElementFactory().getArrayClass(LanguageLevel.HIGHEST);
            }
            if (project.isDefault()) {
                return null;
            }

            PsiManager psiManager = PsiManager.getInstance(project);
            PsiClass psiClass = ClassUtil.findPsiClass(psiManager, className, null, true, scope);
            if (psiClass == null) {
                GlobalSearchScope globalScope = GlobalSearchScope.allScope(project);
                if (!globalScope.equals(scope)) {
                    psiClass = ClassUtil.findPsiClass(psiManager, className, null, true, globalScope);
                }
            }

            return psiClass;
        }
        catch (IndexNotReadyException ignored) {
            return null;
        }
    }

    @Nullable
    public static PsiType getType(@Nonnull String className, @Nonnull Project project) {
        ApplicationManager.getApplication().assertReadAccessAllowed();

        final PsiManager psiManager = PsiManager.getInstance(project);
        try {
            if (getArrayClass(className) != null) {
                return JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createTypeFromText(className, null);
            }
            if (project.isDefault()) {
                return null;
            }
            final PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject())
                .findClass(className.replace('$', '.'), GlobalSearchScope.allScope(project));
            if (aClass != null) {
                return JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(aClass);
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        return null;
    }

    public static void checkSyntax(PsiCodeFragment codeFragment) throws EvaluateException {
        PsiElement[] children = codeFragment.getChildren();

        if (children.length == 0) {
            throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.empty.code.fragment"));
        }
        for (PsiElement child : children) {
            if (child instanceof PsiErrorElement) {
                throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message(
                    "evaluation.error.invalid.expression",
                    child.getText()
                ));
            }
        }
    }

    public static boolean hasSideEffects(PsiElement element) {
        return hasSideEffectsOrReferencesMissingVars(element, null);
    }

    public static boolean hasSideEffectsOrReferencesMissingVars(PsiElement element, @Nullable final Set<String> visibleLocalVariables) {
        final Ref<Boolean> rv = new Ref<Boolean>(Boolean.FALSE);
        element.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitPostfixExpression(final PsiPostfixExpression expression) {
                rv.set(Boolean.TRUE);
            }

            @Override
            public void visitReferenceExpression(final PsiReferenceExpression expression) {
                final PsiElement psiElement = expression.resolve();
                if (psiElement instanceof PsiLocalVariable) {
                    if (visibleLocalVariables != null) {
                        if (!visibleLocalVariables.contains(((PsiLocalVariable)psiElement).getName())) {
                            rv.set(Boolean.TRUE);
                        }
                    }
                }
                else if (psiElement instanceof PsiMethod) {
                    rv.set(Boolean.TRUE);
                    //final PsiMethod method = (PsiMethod)psiElement;
                    //if (!isSimpleGetter(method)) {
                    //  rv.set(Boolean.TRUE);
                    //}
                }
                if (!rv.get().booleanValue()) {
                    super.visitReferenceExpression(expression);
                }
            }

            @Override
            public void visitPrefixExpression(final PsiPrefixExpression expression) {
                final IElementType op = expression.getOperationTokenType();
                if (JavaTokenType.PLUSPLUS.equals(op) || JavaTokenType.MINUSMINUS.equals(op)) {
                    rv.set(Boolean.TRUE);
                }
                else {
                    super.visitPrefixExpression(expression);
                }
            }

            @Override
            public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
                rv.set(Boolean.TRUE);
            }

            @Override
            public void visitCallExpression(final PsiCallExpression callExpression) {
                rv.set(Boolean.TRUE);
                //final PsiMethod method = callExpression.resolveMethod();
                //if (method == null || !isSimpleGetter(method)) {
                //  rv.set(Boolean.TRUE);
                //}
                //else {
                //  super.visitCallExpression(callExpression);
                //}
            }
        });
        return rv.get().booleanValue();
    }

    @Nonnull
    public abstract TransportService.ListenKey findAvailableDebugAddress(int type) throws ExecutionException;

    public static boolean isSynthetic(TypeComponent typeComponent) {
        if (typeComponent == null) {
            return false;
        }

        for (SyntheticTypeComponentProvider syntheticTypeComponentProvider
            : Application.get().getExtensionList(SyntheticTypeComponentProvider.class)) {
            if (syntheticTypeComponentProvider.isSynthetic(typeComponent)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInsideSimpleGetter(@Nonnull PsiElement contextElement) {
        for (SimplePropertyGetterProvider provider : SimplePropertyGetterProvider.EP_NAME.getExtensionList()) {
            if (provider.isInsideSimpleGetter(contextElement)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPrimitiveType(final String typeName) {
        return ourPrimitiveTypeNames.contains(typeName);
    }

    protected static class ArrayClass {
        public String className;
        public int dims;

        public ArrayClass(String className, int dims) {
            this.className = className;
            this.dims = dims;
        }
    }

    public static DebuggerUtils getInstance() {
        return ServiceManager.getService(DebuggerUtils.class);
    }

    public abstract PsiExpression substituteThis(
        PsiExpression expressionWithThis,
        PsiExpression howToEvaluateThis,
        Value howToEvaluateThisValue,
        StackFrameContext context
    ) throws EvaluateException;

    public abstract DebuggerContext getDebuggerContext(DataContext context);

    public abstract Element writeTextWithImports(TextWithImports text);

    public abstract TextWithImports readTextWithImports(Element element);

    public abstract void writeTextWithImports(Element root, @NonNls String name, TextWithImports value);

    public abstract TextWithImports readTextWithImports(Element root, @NonNls String name);

    public abstract TextWithImports createExpressionWithImports(@NonNls String expression);

    public abstract PsiElement getContextElement(final StackFrameContext context);

    public abstract PsiClass chooseClassDialog(String title, Project project);
}
