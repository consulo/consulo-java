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

/*
 * Class DebuggerUtilsEx
 * @author Jeka
 */
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.SourcePositionHighlighter;
import com.intellij.java.debugger.engine.evaluation.*;
import com.intellij.java.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.impl.engine.*;
import com.intellij.java.debugger.impl.engine.evaluation.CodeFragmentFactoryContextWrapper;
import com.intellij.java.debugger.impl.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.java.debugger.impl.engine.requests.RequestManagerImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.requests.Requestor;
import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import com.intellij.java.execution.filters.ExceptionFilters;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.util.DocumentUtil;
import consulo.document.util.TextRange;
import consulo.execution.debug.*;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.ui.RunContentManager;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.LineNumbersMapping;
import consulo.execution.ui.console.TextConsoleBuilder;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.ui.layout.RunnerLayoutUi;
import consulo.execution.unscramble.ThreadDumpPanel;
import consulo.execution.unscramble.ThreadState;
import consulo.internal.com.sun.jdi.*;
import consulo.internal.com.sun.jdi.event.Event;
import consulo.internal.com.sun.jdi.event.EventSet;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.content.Content;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizable;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;

public abstract class DebuggerUtilsEx extends DebuggerUtils {
    private static final Logger LOG = Logger.getInstance(DebuggerUtilsEx.class);

    /**
     * @param context
     * @return all CodeFragmentFactoryProviders that provide code fragment factories suitable in the context given
     */
    public static List<CodeFragmentFactory> getCodeFragmentFactories(@Nullable PsiElement context) {
        final DefaultCodeFragmentFactory defaultFactory = DefaultCodeFragmentFactory.getInstance();
        final List<CodeFragmentFactory> providers = ApplicationManager.getApplication().getExtensionList(CodeFragmentFactory.class);
        final List<CodeFragmentFactory> suitableFactories = new ArrayList<>(providers.size());
        if (providers.size() > 0) {
            for (CodeFragmentFactory factory : providers) {
                if (factory != defaultFactory && factory.isContextAccepted(context)) {
                    suitableFactories.add(factory);
                }
            }
        }
        suitableFactories.add(defaultFactory); // let default factory be the last one
        return suitableFactories;
    }

    public static PsiMethod findPsiMethod(PsiFile file, int offset) {
        PsiElement element = null;

        while (offset >= 0) {
            element = file.findElementAt(offset);
            if (element != null) {
                break;
            }
            offset--;
        }

        for (; element != null; element = element.getParent()) {
            if (element instanceof PsiClass || element instanceof PsiLambdaExpression) {
                return null;
            }
            if (element instanceof PsiMethod) {
                return (PsiMethod)element;
            }
        }
        return null;
    }

    @Nullable
    public static TextRange intersectWithLine(@Nullable TextRange range, @Nullable PsiFile file, int line) {
        if (range != null && file != null) {
            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document != null) {
                range = range.intersection(DocumentUtil.getLineTextRange(document, line));
            }
        }
        return range;
    }

    public static boolean isAssignableFrom(@Nonnull final String baseQualifiedName, @Nonnull ReferenceType checkedType) {
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(baseQualifiedName)) {
            return true;
        }
        return getSuperClass(baseQualifiedName, checkedType) != null;
    }

    public static ReferenceType getSuperClass(@Nonnull final String baseQualifiedName, @Nonnull ReferenceType checkedType) {
        if (baseQualifiedName.equals(checkedType.name())) {
            return checkedType;
        }

        if (checkedType instanceof ClassType) {
            ClassType classType = (ClassType)checkedType;
            ClassType superClassType = classType.superclass();
            if (superClassType != null) {
                ReferenceType superClass = getSuperClass(baseQualifiedName, superClassType);
                if (superClass != null) {
                    return superClass;
                }
            }
            List<InterfaceType> interfaces = classType.allInterfaces();
            for (InterfaceType iface : interfaces) {
                ReferenceType superClass = getSuperClass(baseQualifiedName, iface);
                if (superClass != null) {
                    return superClass;
                }
            }
        }

        if (checkedType instanceof InterfaceType) {
            List<InterfaceType> list = ((InterfaceType)checkedType).superinterfaces();
            for (InterfaceType superInterface : list) {
                ReferenceType superClass = getSuperClass(baseQualifiedName, superInterface);
                if (superClass != null) {
                    return superClass;
                }
            }
        }
        return null;
    }

    public static boolean valuesEqual(Value val1, Value val2) {
        if (val1 == null) {
            return val2 == null;
        }
        if (val2 == null) {
            return false;
        }
        if (val1 instanceof StringReference && val2 instanceof StringReference) {
            return ((StringReference)val1).value().equals(((StringReference)val2).value());
        }
        return val1.equals(val2);
    }

    public static String getValueOrErrorAsString(final EvaluationContext evaluationContext, Value value) {
        try {
            return getValueAsString(evaluationContext, value);
        }
        catch (EvaluateException e) {
            return e.getMessage();
        }
    }

    public static boolean isCharOrInteger(Value value) {
        return value instanceof CharValue || isInteger(value);
    }

    private static Set<String> myCharOrIntegers;

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean isCharOrIntegerArray(Value value) {
        if (value == null) {
            return false;
        }
        if (myCharOrIntegers == null) {
            myCharOrIntegers = new HashSet<>();
            myCharOrIntegers.add("C");
            myCharOrIntegers.add("B");
            myCharOrIntegers.add("S");
            myCharOrIntegers.add("I");
            myCharOrIntegers.add("J");
        }

        String signature = value.type().signature();
        int i;
        for (i = 0; signature.charAt(i) == '['; i++) {
            ;
        }
        if (i == 0) {
            return false;
        }
        signature = signature.substring(i, signature.length());
        return myCharOrIntegers.contains(signature);
    }

    public static ClassFilter create(Element element) throws InvalidDataException {
        ClassFilter filter = new ClassFilter();
        DefaultJDOMExternalizer.readExternal(filter, element);
        return filter;
    }

    private static boolean isFiltered(ClassFilter classFilter, String qName) {
        if (!classFilter.isEnabled()) {
            return false;
        }
        try {
            if (classFilter.matches(qName)) {
                return true;
            }
        }
        catch (PatternSyntaxException e) {
            LOG.debug(e);
        }
        return false;
    }

    public static boolean isFiltered(@Nonnull String qName, ClassFilter[] classFilters) {
        return isFiltered(qName, Arrays.asList(classFilters));
    }

    public static boolean isFiltered(@Nonnull String qName, List<ClassFilter> classFilters) {
        if (qName.indexOf('[') != -1) {
            return false; //is array
        }

        return classFilters.stream().anyMatch(filter -> isFiltered(filter, qName));
    }

    public static int getEnabledNumber(ClassFilter[] classFilters) {
        return (int)Arrays.stream(classFilters).filter(ClassFilter::isEnabled).count();
    }

    public static ClassFilter[] readFilters(List<Element> children) throws InvalidDataException {
        if (ContainerUtil.isEmpty(children)) {
            return ClassFilter.EMPTY_ARRAY;
        }

        ClassFilter[] filters = new ClassFilter[children.size()];
        for (int i = 0, size = children.size(); i < size; i++) {
            filters[i] = create(children.get(i));
        }
        return filters;
    }

    public static void writeFilters(Element parentNode, @NonNls String tagName, ClassFilter[] filters) throws WriteExternalException {
        for (ClassFilter filter : filters) {
            Element element = new Element(tagName);
            parentNode.addContent(element);
            DefaultJDOMExternalizer.writeExternal(filter, element);
        }
    }

    public static boolean filterEquals(ClassFilter[] filters1, ClassFilter[] filters2) {
        if (filters1.length != filters2.length) {
            return false;
        }
        final Set<ClassFilter> f1 = new HashSet<>(Math.max((int)(filters1.length / .75f) + 1, 16));
        final Set<ClassFilter> f2 = new HashSet<>(Math.max((int)(filters2.length / .75f) + 1, 16));
        Collections.addAll(f1, filters1);
        Collections.addAll(f2, filters2);
        return f2.equals(f1);
    }

    private static boolean elementListsEqual(List<Element> l1, List<Element> l2) {
        if (l1 == null) {
            return l2 == null;
        }
        if (l2 == null) {
            return false;
        }

        if (l1.size() != l2.size()) {
            return false;
        }

        Iterator<Element> i1 = l1.iterator();

        for (Element aL2 : l2) {
            Element elem1 = i1.next();

            if (!elementsEqual(elem1, aL2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean attributeListsEqual(List<Attribute> l1, List<Attribute> l2) {
        if (l1 == null) {
            return l2 == null;
        }
        if (l2 == null) {
            return false;
        }

        if (l1.size() != l2.size()) {
            return false;
        }

        Iterator<Attribute> i1 = l1.iterator();

        for (Attribute aL2 : l2) {
            Attribute attr1 = i1.next();

            if (!Comparing.equal(attr1.getName(), aL2.getName()) || !Comparing.equal(attr1.getValue(), aL2.getValue())) {
                return false;
            }
        }
        return true;
    }

    public static boolean elementsEqual(Element e1, Element e2) {
        if (e1 == null) {
            return e2 == null;
        }
        if (!Comparing.equal(e1.getName(), e2.getName())) {
            return false;
        }
        if (!elementListsEqual(e1.getChildren(), e2.getChildren())) {
            return false;
        }
        if (!attributeListsEqual(e1.getAttributes(), e2.getAttributes())) {
            return false;
        }
        return true;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static boolean externalizableEqual(JDOMExternalizable e1, JDOMExternalizable e2) {
        Element root1 = new Element("root");
        Element root2 = new Element("root");
        try {
            e1.writeExternal(root1);
        }
        catch (WriteExternalException e) {
            LOG.debug(e);
        }
        try {
            e2.writeExternal(root2);
        }
        catch (WriteExternalException e) {
            LOG.debug(e);
        }

        return elementsEqual(root1, root2);
    }

    @Nonnull
    public static List<Pair<Breakpoint, Event>> getEventDescriptors(@Nullable SuspendContextImpl suspendContext) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        if (suspendContext != null) {
            EventSet events = suspendContext.getEventSet();
            if (!ContainerUtil.isEmpty(events)) {
                List<Pair<Breakpoint, Event>> eventDescriptors = new ArrayList<>();
                RequestManagerImpl requestManager = suspendContext.getDebugProcess().getRequestsManager();
                for (Event event : events) {
                    Requestor requestor = requestManager.findRequestor(event.request());
                    if (requestor instanceof Breakpoint) {
                        eventDescriptors.add(Pair.create((Breakpoint)requestor, event));
                    }
                }
                return eventDescriptors;
            }
        }
        return Collections.emptyList();
    }

    public static TextWithImports getEditorText(final Editor editor) {
        if (editor == null) {
            return null;
        }
        final Project project = editor.getProject();
        if (project == null) {
            return null;
        }

        String defaultExpression = editor.getSelectionModel().getSelectedText();
        if (defaultExpression == null) {
            int offset = editor.getCaretModel().getOffset();
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (psiFile != null) {
                PsiElement elementAtCursor = psiFile.findElementAt(offset);
                if (elementAtCursor != null) {
                    final EditorTextProvider textProvider = EditorTextProvider.forLanguage(elementAtCursor.getLanguage());
                    if (textProvider != null) {
                        final TextWithImports editorText = textProvider.getEditorText(elementAtCursor);
                        if (editorText != null) {
                            return editorText;
                        }
                    }
                }
            }
        }
        else {
            return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, defaultExpression);
        }
        return null;
    }

    private static int myThreadDumpsCount = 0;
    private static int myCurrentThreadDumpId = 1;

    private static final String THREAD_DUMP_CONTENT_PREFIX = "Dump";

    public static void addThreadDump(Project project, List<ThreadState> threads, final RunnerLayoutUi ui, DebuggerSession session) {
        final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        consoleBuilder.filters(ExceptionFilters.getFilters(session.getSearchScope()));
        final ConsoleView consoleView = consoleBuilder.getConsole();
        final DefaultActionGroup toolbarActions = new DefaultActionGroup();
        consoleView.allowHeavyFilters();
        final ThreadDumpPanel panel = ThreadDumpPanel.create(project, consoleView, toolbarActions, threads);

        final String id = THREAD_DUMP_CONTENT_PREFIX + " #" + myCurrentThreadDumpId;
        final Content content = ui.createContent(id, panel.getComponent(), id, null, null);
        content.putUserData(RunContentManager.LIGHTWEIGHT_CONTENT_MARKER, Boolean.TRUE);
        content.setCloseable(true);
        content.setDescription("Thread Dump");
        ui.addContent(content);
        ui.selectAndFocus(content, true, true);
        myThreadDumpsCount++;
        myCurrentThreadDumpId++;
        Disposer.register(content, new Disposable() {
            @Override
            public void dispose() {
                myThreadDumpsCount--;
                if (myThreadDumpsCount == 0) {
                    myCurrentThreadDumpId = 1;
                }
            }
        });
        Disposer.register(content, consoleView);
        ui.selectAndFocus(content, true, false);
        if (threads.size() > 0) {
            panel.selectStackFrame(0);
        }
    }

    public static void keep(Value value, EvaluationContext context) {
        if (value instanceof ObjectReference) {
            ((SuspendContextImpl)context.getSuspendContext()).keep((ObjectReference)value);
        }
    }

    public abstract DebuggerTreeNode getSelectedNode(DataContext context);

    public abstract EvaluatorBuilder getEvaluatorBuilder();

    public static CodeFragmentFactory getCodeFragmentFactory(@Nullable PsiElement context, @Nullable FileType fileType) {
        DefaultCodeFragmentFactory defaultFactory = DefaultCodeFragmentFactory.getInstance();
        if (fileType == null) {
            if (context == null) {
                return defaultFactory;
            }
            else {
                PsiFile file = context.getContainingFile();
                fileType = file != null ? file.getFileType() : null;
            }
        }
        for (CodeFragmentFactory factory : ApplicationManager.getApplication().getExtensionList(CodeFragmentFactory.class)) {
            if (factory != defaultFactory && (fileType == null || factory.getFileType().equals(fileType)) && factory.isContextAccepted(
                context)) {
                return factory;
            }
        }
        return defaultFactory;
    }

    @Nonnull
    public static CodeFragmentFactory findAppropriateCodeFragmentFactory(final TextWithImports text, final PsiElement context) {
        CodeFragmentFactory factory = ReadAction.compute(() -> getCodeFragmentFactory(context, text.getFileType()));
        return new CodeFragmentFactoryContextWrapper(factory);
    }

    private static class SigReader {
        final String buffer;
        int pos = 0;

        SigReader(String s) {
            buffer = s;
        }

        int get() {
            return buffer.charAt(pos++);
        }

        int peek() {
            return buffer.charAt(pos);
        }

        boolean eof() {
            return buffer.length() <= pos;
        }

        @NonNls
        String getSignature() {
            if (eof()) {
                return "";
            }

            switch (get()) {
                case 'Z':
                    return "boolean";
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'S':
                    return "short";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'F':
                    return "float";
                case 'D':
                    return "double";
                case 'V':
                    return "void";
                case 'L':
                    int start = pos;
                    pos = buffer.indexOf(';', start) + 1;
                    LOG.assertTrue(pos > 0);
                    return buffer.substring(start, pos - 1).replace('/', '.');
                case '[':
                    return getSignature() + "[]";
                case '(':
                    StringBuilder result = new StringBuilder("(");
                    String separator = "";
                    while (peek() != ')') {
                        result.append(separator);
                        result.append(getSignature());
                        separator = ", ";
                    }
                    get();
                    result.append(")");
                    return getSignature() + " " + getClassName() + "." + getMethodName() + " " + result;
                default:
                    //          LOG.assertTrue(false, "unknown signature " + buffer);
                    return null;
            }
        }

        String getMethodName() {
            return "";
        }

        String getClassName() {
            return "";
        }
    }

    public static String methodNameWithArguments(Method m) {
        return m.name() + "(" + StringUtil.join(m.argumentTypeNames(), DebuggerUtilsEx::getSimpleName, ", ") + ")";
    }

    public static String getSimpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    public static String methodName(final Method m) {
        return methodName(signatureToName(m.declaringType().signature()), m.name(), m.signature());
    }

    public static String methodName(final String className, final String methodName, final String signature) {
        try {
            return new SigReader(signature) {
                @Override
                String getMethodName() {
                    return methodName;
                }

                @Override
                String getClassName() {
                    return className;
                }
            }.getSignature();
        }
        catch (Exception ignored) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Internal error : unknown signature" + signature);
            }
            return className + "." + methodName;
        }
    }

    public static String signatureToName(String s) {
        return new SigReader(s).getSignature();
    }

    @Nonnull
    public static List<Location> allLineLocations(Method method) {
        try {
            return method.allLineLocations();
        }
        catch (AbsentInformationException ignored) {
            return Collections.emptyList();
        }
    }

    @Nonnull
    public static List<Location> allLineLocations(ReferenceType cls) {
        try {
            return cls.allLineLocations();
        }
        catch (AbsentInformationException | ObjectCollectedException ignored) {
            return Collections.emptyList();
        }
    }

    public static int getLineNumber(Location location, boolean zeroBased) {
        try {
            return location.lineNumber() - (zeroBased ? 1 : 0);
        }
        catch (InternalError | IllegalArgumentException e) {
            return -1;
        }
    }

    @Nullable
    public static Method getMethod(Location location) {
        try {
            return location.method();
        }
        catch (IllegalArgumentException e) { // Invalid method id
            LOG.info(e);
        }
        return null;
    }

    public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, double value) {
        if (PsiType.DOUBLE.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf(value);
        }
        if (PsiType.FLOAT.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((float)value);
        }
        return createValue(vm, expectedType, (long)value);
    }

    public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, long value) {
        if (PsiType.LONG.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf(value);
        }
        if (PsiType.INT.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((int)value);
        }
        if (PsiType.SHORT.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((short)value);
        }
        if (PsiType.BYTE.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((byte)value);
        }
        if (PsiType.CHAR.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((char)value);
        }
        if (PsiType.DOUBLE.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((double)value);
        }
        if (PsiType.FLOAT.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((float)value);
        }
        return null;
    }

    public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, boolean value) {
        if (PsiType.BOOLEAN.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf(value);
        }
        return null;
    }

    public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, char value) {
        if (PsiType.CHAR.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf(value);
        }
        if (PsiType.LONG.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((long)value);
        }
        if (PsiType.INT.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((int)value);
        }
        if (PsiType.SHORT.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((short)value);
        }
        if (PsiType.BYTE.getPresentableText().equals(expectedType)) {
            return vm.mirrorOf((byte)value);
        }
        return null;
    }

    public static String truncateString(final String str) {
        // leave a small gap over XValueNode.MAX_VALUE_LENGTH to detect oversize
        if (str.length() > XValueNode.MAX_VALUE_LENGTH + 5) {
            return str.substring(0, XValueNode.MAX_VALUE_LENGTH + 5);
        }
        return str;
    }

    public static String getThreadStatusText(int statusId) {
        switch (statusId) {
            case ThreadReference.THREAD_STATUS_MONITOR:
                return DebuggerBundle.message("status.thread.monitor");
            case ThreadReference.THREAD_STATUS_NOT_STARTED:
                return DebuggerBundle.message("status.thread.not.started");
            case ThreadReference.THREAD_STATUS_RUNNING:
                return DebuggerBundle.message("status.thread.running");
            case ThreadReference.THREAD_STATUS_SLEEPING:
                return DebuggerBundle.message("status.thread.sleeping");
            case ThreadReference.THREAD_STATUS_UNKNOWN:
                return DebuggerBundle.message("status.thread.unknown");
            case ThreadReference.THREAD_STATUS_WAIT:
                return DebuggerBundle.message("status.thread.wait");
            case ThreadReference.THREAD_STATUS_ZOMBIE:
                return DebuggerBundle.message("status.thread.zombie");
            default:
                return DebuggerBundle.message("status.thread.undefined");
        }
    }

    public static String prepareValueText(String text, Project project) {
        text = StringUtil.unquoteString(text);
        text = StringUtil.unescapeStringCharacters(text);
        int tabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(JavaFileType.INSTANCE);
        if (tabSize < 0) {
            tabSize = 0;
        }
        return text.replace("\t", StringUtil.repeat(" ", tabSize));
    }

    private static final Key<Map<String, String>> DEBUGGER_ALTERNATIVE_SOURCE_MAPPING = Key.create("DEBUGGER_ALTERNATIVE_SOURCE_MAPPING");

    public static void setAlternativeSourceUrl(String className, String source, Project project) {
        Map<String, String> map = project.getUserData(DEBUGGER_ALTERNATIVE_SOURCE_MAPPING);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            project.putUserData(DEBUGGER_ALTERNATIVE_SOURCE_MAPPING, map);
        }
        map.put(className, source);
    }

    @Nullable
    public static String getAlternativeSourceUrl(@Nullable String className, Project project) {
        Map<String, String> map = project.getUserData(DEBUGGER_ALTERNATIVE_SOURCE_MAPPING);
        return map != null ? map.get(className) : null;
    }

    @Nullable
    public static XSourcePosition toXSourcePosition(@Nullable SourcePosition position) {
        if (position != null) {
            VirtualFile file = position.getFile().getVirtualFile();
            if (file == null) {
                file = position.getFile().getOriginalFile().getVirtualFile();
            }
            if (file != null) {
                return new JavaXSourcePosition(position, file);
            }
        }
        return null;
    }

    @Nullable
    public static SourcePosition toSourcePosition(@Nullable XSourcePosition position, Project project) {
        if (position != null) {
            if (position instanceof JavaXSourcePosition) {
                return ((JavaXSourcePosition)position).mySourcePosition;
            }
            PsiFile psiFile = getPsiFile(position, project);
            if (psiFile != null) {
                return SourcePosition.createFromLine(psiFile, position.getLine());
            }
        }
        return null;
    }

    private static class JavaXSourcePosition implements XSourcePosition, XSourcePositionWithHighlighter {
        private final SourcePosition mySourcePosition;
        @Nonnull
        private final VirtualFile myFile;

        public JavaXSourcePosition(@Nonnull SourcePosition sourcePosition, @Nonnull VirtualFile file) {
            mySourcePosition = sourcePosition;
            myFile = file;
        }

        @Override
        public int getLine() {
            return mySourcePosition.getLine();
        }

        @Override
        public int getOffset() {
            return mySourcePosition.getOffset();
        }

        @Nonnull
        @Override
        public VirtualFile getFile() {
            return myFile;
        }

        @Nonnull
        @Override
        public Navigatable createNavigatable(@Nonnull Project project) {
            return project.getApplication().getInstance(XSourcePositionFactory.class).createDefaultNavigatable(project, this);
        }

        @Nullable
        @Override
        public TextRange getHighlightRange() {
            return intersectWithLine(
                SourcePositionHighlighter.getHighlightRangeFor(mySourcePosition),
                mySourcePosition.getFile(),
                getLine()
            );
        }
    }

    @Nullable
    public static PsiFile getPsiFile(@Nullable XSourcePosition position, Project project) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        if (position != null) {
            VirtualFile file = position.getFile();
            if (file.isValid()) {
                return PsiManager.getInstance(project).findFile(file);
            }
        }
        return null;
    }

    /**
     * Decompiler aware version
     */
    @Nullable
    public static PsiElement findElementAt(@Nullable PsiFile file, int offset) {
        return file != null ? file.findElementAt(offset) : null;
    }

    public static String getLocationMethodQName(@Nonnull Location location) {
        StringBuilder res = new StringBuilder();
        ReferenceType type = location.declaringType();
        if (type != null) {
            res.append(type.name()).append('.');
        }
        res.append(location.method().name());
        return res.toString();
    }

    private static PsiElement getNextElement(PsiElement element) {
        PsiElement sibling = element.getNextSibling();
        if (sibling != null) {
            return sibling;
        }
        element = element.getParent();
        if (element != null) {
            return getNextElement(element);
        }
        return null;
    }

    public static boolean isLambdaClassName(String typeName) {
        return getLambdaBaseClassName(typeName) != null;
    }

    @Nullable
    public static String getLambdaBaseClassName(String typeName) {
        return StringUtil.substringBefore(typeName, "$$Lambda$");
    }

    public static boolean isLambdaName(@Nullable String name) {
        return !StringUtil.isEmpty(name) && name.startsWith("lambda$");
    }

    public static boolean isLambda(@Nullable Method method) {
        return method != null && isLambdaName(method.name());
    }

    public static final Comparator<Method> LAMBDA_ORDINAL_COMPARATOR = Comparator.comparingInt(m -> getLambdaOrdinal(m.name()));

    public static int getLambdaOrdinal(@Nonnull String name) {
        int pos = name.lastIndexOf('$');
        if (pos > -1) {
            try {
                return Integer.parseInt(name.substring(pos + 1));
            }
            catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    public static List<PsiLambdaExpression> collectLambdas(@Nonnull SourcePosition position, final boolean onlyOnTheLine) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        PsiFile file = position.getFile();
        final int line = position.getLine();
        final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null || line >= document.getLineCount()) {
            return Collections.emptyList();
        }
        PsiElement element = position.getElementAt();
        if (element == null) {
            return Collections.emptyList();
        }
        final TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
        do {
            PsiElement parent = element.getParent();
            if (parent == null || (parent.getTextOffset() < lineRange.getStartOffset())) {
                break;
            }
            element = parent;
        }
        while (true);

        final List<PsiLambdaExpression> lambdas = new SmartList<>();
        final PsiElementVisitor lambdaCollector = new JavaRecursiveElementVisitor() {
            @Override
            public void visitLambdaExpression(PsiLambdaExpression expression) {
                super.visitLambdaExpression(expression);
                if (!onlyOnTheLine || getFirstElementOnTheLine(expression, document, line) != null) {
                    lambdas.add(expression);
                }
            }
        };
        element.accept(lambdaCollector);
        for (PsiElement sibling = getNextElement(element); sibling != null; sibling = getNextElement(sibling)) {
            if (!intersects(lineRange, sibling)) {
                break;
            }
            sibling.accept(lambdaCollector);
        }
        // add initial lambda if we're inside already
        PsiElement method = getContainingMethod(element);
        if (method instanceof PsiLambdaExpression && !lambdas.contains(method)) {
            lambdas.add((PsiLambdaExpression)method);
        }
        return lambdas;
    }

    @Nullable
    public static PsiElement getBody(PsiElement method) {
        if (method instanceof PsiParameterListOwner) {
            return ((PsiParameterListOwner)method).getBody();
        }
        else if (method instanceof PsiClassInitializer) {
            return ((PsiClassInitializer)method).getBody();
        }
        return null;
    }

    @Nonnull
    public static PsiParameter[] getParameters(PsiElement method) {
        if (method instanceof PsiParameterListOwner) {
            return ((PsiParameterListOwner)method).getParameterList().getParameters();
        }
        return PsiParameter.EMPTY_ARRAY;
    }

    public static boolean evaluateBoolean(ExpressionEvaluator evaluator, EvaluationContextImpl context) throws EvaluateException {
        Object value = UnBoxingEvaluator.unbox(evaluator.evaluate(context), context);
        if (!(value instanceof BooleanValue)) {
            throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.boolean.expected"));
        }
        return ((BooleanValue)value).booleanValue();
    }

    public static boolean intersects(@Nonnull TextRange range, @Nonnull PsiElement elem) {
        TextRange elemRange = elem.getTextRange();
        return elemRange != null && elemRange.intersects(range);
    }

    @Nullable
    public static PsiElement getFirstElementOnTheLine(PsiLambdaExpression lambda, Document document, int line) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
        if (!intersects(lineRange, lambda)) {
            return null;
        }
        PsiElement body = lambda.getBody();
        if (body == null || !intersects(lineRange, body)) {
            return null;
        }
        if (body instanceof PsiCodeBlock) {
            PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
            if (statements.length > 0) {
                for (PsiStatement statement : statements) {
                    // return first statement starting on the line
                    if (lineRange.contains(statement.getTextOffset())) {
                        return statement;
                    }
                    // otherwise check all children
                    else if (intersects(lineRange, statement)) {
                        for (PsiElement element : SyntaxTraverser.psiTraverser(statement)) {
                            if (lineRange.contains(element.getTextOffset())) {
                                return element;
                            }
                        }
                    }
                }
                return null;
            }
        }
        return body;
    }

    public static boolean inTheMethod(@Nonnull SourcePosition pos, @Nonnull PsiElement method) {
        PsiElement elem = pos.getElementAt();
        if (elem == null) {
            return false;
        }
        return Comparing.equal(getContainingMethod(elem), method);
    }

    public static boolean inTheSameMethod(@Nonnull SourcePosition pos1, @Nonnull SourcePosition pos2) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        PsiElement elem1 = pos1.getElementAt();
        PsiElement elem2 = pos2.getElementAt();
        if (elem1 == null) {
            return elem2 == null;
        }
        if (elem2 != null) {
            PsiElement expectedMethod = getContainingMethod(elem1);
            PsiElement currentMethod = getContainingMethod(elem2);
            return Comparing.equal(expectedMethod, currentMethod);
        }
        return false;
    }

    public static boolean methodMatches(
        @Nonnull PsiMethod psiMethod,
        String className,
        String name,
        String signature,
        DebugProcessImpl process
    ) {
        PsiClass containingClass = psiMethod.getContainingClass();
        try {
            return containingClass != null
                && Objects.equals(JVMNameUtil.getClassVMName(containingClass), className)
                && JVMNameUtil.getJVMMethodName(psiMethod).equals(name)
                && JVMNameUtil.getJVMSignature(psiMethod).getName(process).equals(signature);
        }
        catch (EvaluateException e) {
            LOG.debug(e);
            return false;
        }
    }

    @Nullable
    public static PsiElement getContainingMethod(@Nullable PsiElement elem) {
        return PsiTreeUtil.getContextOfType(elem, PsiMethod.class, PsiLambdaExpression.class, PsiClassInitializer.class);
    }

    @Nullable
    public static PsiElement getContainingMethod(@Nullable SourcePosition position) {
        if (position == null) {
            return null;
        }
        return getContainingMethod(position.getElementAt());
    }

    public static void disableCollection(ObjectReference reference) {
        try {
            reference.disableCollection();
        }
        catch (UnsupportedOperationException ignored) {
            // ignore: some J2ME implementations does not provide this operation
        }
    }

    public static void enableCollection(ObjectReference reference) {
        try {
            reference.enableCollection();
        }
        catch (UnsupportedOperationException ignored) {
            // ignore: some J2ME implementations does not provide this operation
        }
    }

    /**
     * Provides mapping from decompiled file line number to the original source code line numbers
     *
     * @param psiFile      decompiled file
     * @param originalLine zero-based decompiled file line number
     * @return zero-based source code line number
     */
    public static int bytecodeToSourceLine(PsiFile psiFile, int originalLine) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
            LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
            if (mapping != null) {
                int line = mapping.bytecodeToSource(originalLine + 1);
                if (line > -1) {
                    return line - 1;
                }
            }
        }
        return -1;
    }

    public static boolean isInLibraryContent(@Nullable VirtualFile file, @Nonnull Project project) {
        return ReadAction.compute(() -> {
            if (file == null) {
                return true;
            }
            else {
                ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                return projectFileIndex.isInLibraryClasses(file) || projectFileIndex.isInLibrarySource(file);
            }
        });
    }

    public static boolean isInJavaSession(AnActionEvent e) {
        XDebugSession session = e.getData(XDebugSession.DATA_KEY);
        if (session == null) {
            Project project = e.getData(Project.KEY);
            if (project != null) {
                session = XDebuggerManager.getInstance(project).getCurrentSession();
            }
        }
        return session != null && session.getDebugProcess() instanceof JavaDebugProcess;
    }
}
