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

/*
 * Class BreakpointManager
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.*;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaExceptionBreakpointProperties;
import com.intellij.java.debugger.impl.engine.BreakpointStepMethodFilter;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.requests.RequestManagerImpl;
import com.intellij.java.language.psi.PsiField;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.XBreakpointManager;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointType;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.breakpoint.XLineBreakpointType;
import consulo.execution.debug.ui.XDebuggerUIConstants;
import consulo.ide.impl.idea.xdebugger.impl.DebuggerSupport;
import consulo.ide.impl.idea.xdebugger.impl.XDebuggerSupport;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import consulo.internal.com.sun.jdi.InternalException;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.internal.com.sun.jdi.request.*;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.project.ui.notification.NotificationType;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import javax.swing.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class BreakpointManager {
    private static final Logger LOG = Logger.getInstance(BreakpointManager.class);

    private static final String MASTER_BREAKPOINT_TAGNAME = "master_breakpoint";
    private static final String SLAVE_BREAKPOINT_TAGNAME = "slave_breakpoint";
    private static final String DEFAULT_SUSPEND_POLICY_ATTRIBUTE_NAME = "default_suspend_policy";
    private static final String DEFAULT_CONDITION_STATE_ATTRIBUTE_NAME = "default_condition_enabled";

    private static final String RULES_GROUP_NAME = "breakpoint_rules";
    private static final String CONVERTED_PARAM = "converted";

    private final Project myProject;
    private final Map<String, String> myUIProperties = new LinkedHashMap<>();

    private final StartupManager myStartupManager;

    public BreakpointManager(@Nonnull Project project, @Nonnull StartupManager startupManager, @Nonnull DebuggerManagerImpl debuggerManager) {
        myProject = project;
        myStartupManager = startupManager;

        debuggerManager.getContextManager().addListener(new DebuggerContextListener() {
            private DebuggerSession myPreviousSession;

            @Override
            public void changeEvent(@Nonnull DebuggerContextImpl newContext, DebuggerSession.Event event) {
                if (event == DebuggerSession.Event.ATTACHED) {
                    for (XBreakpoint breakpoint : getXBreakpointManager().getAllBreakpoints()) {
                        if (checkAndNotifyPossiblySlowBreakpoint(breakpoint)) {
                            break;
                        }
                    }
                }
                if (newContext.getDebuggerSession() != myPreviousSession || event == DebuggerSession.Event.DETACHED) {
                    updateBreakpointsUI();
                    myPreviousSession = newContext.getDebuggerSession();
                }
            }
        });
    }

    private static boolean checkAndNotifyPossiblySlowBreakpoint(XBreakpoint breakpoint) {
        if (breakpoint.isEnabled() && (breakpoint.getType() instanceof JavaMethodBreakpointType || breakpoint.getType() instanceof JavaWildcardMethodBreakpointType)) {
            XDebuggerUIConstants.NOTIFICATION_GROUP.createNotification("Method breakpoints may dramatically slow down debugging", NotificationType.WARNING).notify((breakpoint).getProject());
            return true;
        }
        return false;
    }

    private XBreakpointManager getXBreakpointManager() {
        return XDebuggerManager.getInstance(myProject).getBreakpointManager();
    }

    public void editBreakpoint(final Breakpoint breakpoint, final Editor editor) {
        DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
            XBreakpoint xBreakpoint = breakpoint.myXBreakpoint;
            if (xBreakpoint instanceof XLineBreakpointImpl) {
                RangeHighlighter highlighter = ((XLineBreakpointImpl) xBreakpoint).getHighlighter();
                if (highlighter != null) {
                    GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
                    if (renderer != null) {
                        DebuggerSupport.getDebuggerSupport(XDebuggerSupport.class).getEditBreakpointAction().editBreakpoint(myProject, editor, breakpoint.myXBreakpoint, renderer);
                    }
                }
            }
        });
    }

    public void setBreakpointDefaults(Key<? extends Breakpoint> category, BreakpointDefaults defaults) {
        Class typeCls = null;
        if (LineBreakpoint.CATEGORY.toString().equals(category.toString())) {
            typeCls = JavaLineBreakpointType.class;
        }
        else if (MethodBreakpoint.CATEGORY.toString().equals(category.toString())) {
            typeCls = JavaMethodBreakpointType.class;
        }
        else if (FieldBreakpoint.CATEGORY.toString().equals(category.toString())) {
            typeCls = JavaFieldBreakpointType.class;
        }
        else if (ExceptionBreakpoint.CATEGORY.toString().equals(category.toString())) {
            typeCls = JavaExceptionBreakpointType.class;
        }
        if (typeCls != null) {
            XBreakpointType<XBreakpoint<?>, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeCls);
            ((XBreakpointManagerImpl) getXBreakpointManager()).getBreakpointDefaults(type).setSuspendPolicy(Breakpoint.transformSuspendPolicy(defaults.getSuspendPolicy()));
        }
    }

    @Nullable
    public RunToCursorBreakpoint addRunToCursorBreakpoint(@Nonnull XSourcePosition position, final boolean ignoreBreakpoints) {
        return RunToCursorBreakpoint.create(myProject, position, ignoreBreakpoints);
    }

    @Nullable
    public StepIntoBreakpoint addStepIntoBreakpoint(@Nonnull BreakpointStepMethodFilter filter) {
        return StepIntoBreakpoint.create(myProject, filter);
    }

    @Nullable
    public LineBreakpoint addLineBreakpoint(Document document, int lineIndex) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (!LineBreakpoint.canAddLineBreakpoint(myProject, document, lineIndex)) {
            return null;
        }
        XLineBreakpoint xLineBreakpoint = addXLineBreakpoint(JavaLineBreakpointType.class, document, lineIndex);
        Breakpoint breakpoint = getJavaBreakpoint(xLineBreakpoint);
        if (breakpoint instanceof LineBreakpoint) {
            addBreakpoint(breakpoint);
            return ((LineBreakpoint) breakpoint);
        }
        return null;
    }

    @Nullable
    public FieldBreakpoint addFieldBreakpoint(@Nonnull Document document, int offset) {
        PsiField field = FieldBreakpoint.findField(myProject, document, offset);
        if (field == null) {
            return null;
        }

        int line = document.getLineNumber(offset);

        if (document.getLineNumber(field.getNameIdentifier().getTextOffset()) < line) {
            return null;
        }

        return addFieldBreakpoint(document, line, field.getName());
    }

    @Nullable
    public FieldBreakpoint addFieldBreakpoint(Document document, int lineIndex, String fieldName) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        XLineBreakpoint xBreakpoint = addXLineBreakpoint(JavaFieldBreakpointType.class, document, lineIndex);
        Breakpoint javaBreakpoint = getJavaBreakpoint(xBreakpoint);
        if (javaBreakpoint instanceof FieldBreakpoint) {
            FieldBreakpoint fieldBreakpoint = (FieldBreakpoint) javaBreakpoint;
            fieldBreakpoint.setFieldName(fieldName);
            addBreakpoint(javaBreakpoint);
            return fieldBreakpoint;
        }
        return null;
    }

    @Nonnull
    public ExceptionBreakpoint addExceptionBreakpoint(@Nonnull final String exceptionClassName, final String packageName) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        final JavaExceptionBreakpointType type = (JavaExceptionBreakpointType) XDebuggerUtil.getInstance().findBreakpointType(JavaExceptionBreakpointType.class);
        return ApplicationManager.getApplication().runWriteAction(new Computable<ExceptionBreakpoint>() {
            @Override
            public ExceptionBreakpoint compute() {
                XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint = XDebuggerManager.getInstance(myProject).getBreakpointManager().addBreakpoint(type,
                    new JavaExceptionBreakpointProperties(exceptionClassName, packageName));
                Breakpoint javaBreakpoint = getJavaBreakpoint(xBreakpoint);
                if (javaBreakpoint instanceof ExceptionBreakpoint) {
                    ExceptionBreakpoint exceptionBreakpoint = (ExceptionBreakpoint) javaBreakpoint;
                    exceptionBreakpoint.setQualifiedName(exceptionClassName);
                    exceptionBreakpoint.setPackageName(packageName);
                    addBreakpoint(exceptionBreakpoint);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("ExceptionBreakpoint Added");
                    }
                    return exceptionBreakpoint;
                }
                return null;
            }
        });
    }

    @Nullable
    public MethodBreakpoint addMethodBreakpoint(Document document, int lineIndex) {
        ApplicationManager.getApplication().assertIsDispatchThread();

        XLineBreakpoint xBreakpoint = addXLineBreakpoint(JavaMethodBreakpointType.class, document, lineIndex);
        Breakpoint javaBreakpoint = getJavaBreakpoint(xBreakpoint);
        if (javaBreakpoint instanceof MethodBreakpoint) {
            addBreakpoint(javaBreakpoint);
            return (MethodBreakpoint) javaBreakpoint;
        }
        return null;
    }

    private <B extends XBreakpoint<?>> XLineBreakpoint addXLineBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls, Document document, final int lineIndex) {
        final XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeCls);
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        return ApplicationManager.getApplication().runWriteAction(new Computable<XLineBreakpoint>() {
            @Override
            public XLineBreakpoint compute() {
                return XDebuggerManager.getInstance(myProject).getBreakpointManager().addLineBreakpoint((XLineBreakpointType) type, file.getUrl(), lineIndex,
                    ((XLineBreakpointType) type).createBreakpointProperties(file, lineIndex));
            }
        });
    }

    /**
     * @param category breakpoint category, null if the category does not matter
     */
    @Nullable
    public <T extends BreakpointWithHighlighter> T findBreakpoint(final Document document, final int offset, @Nullable final Key<T> category) {
        for (final Breakpoint breakpoint : getBreakpoints()) {
            if (breakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter) breakpoint).isAt(document, offset)) {
                if (category == null || category.equals(breakpoint.getCategory())) {
                    //noinspection CastConflictsWithInstanceof,unchecked
                    return (T) breakpoint;
                }
            }
        }
        return null;
    }

    private final Map<String, Element> myOriginalBreakpointsNodes = new LinkedHashMap<>();

    public void readExternal(@Nonnull final Element parentNode) {
        myOriginalBreakpointsNodes.clear();
        // save old breakpoints
        for (Element element : parentNode.getChildren()) {
            myOriginalBreakpointsNodes.put(element.getName(), element.clone());
        }
        if (myProject.isOpen()) {
            doRead(parentNode);
        }
        else {
            myStartupManager.registerPostStartupActivity(new Runnable() {
                @Override
                public void run() {
                    doRead(parentNode);
                }
            });
        }
    }

    private void doRead(@Nonnull final Element parentNode) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            @SuppressWarnings({"HardCodedStringLiteral"})
            public void run() {
                final Map<String, Breakpoint> nameToBreakpointMap = new HashMap<>();
                try {
                    final List<Element> groups = parentNode.getChildren();
                    for (final Element group : groups) {
                        if (group.getName().equals(RULES_GROUP_NAME)) {
                            continue;
                        }
                        // skip already converted
                        if (group.getAttribute(CONVERTED_PARAM) != null) {
                            continue;
                        }
                        final String categoryName = group.getName();
                        final Key<Breakpoint> breakpointCategory = BreakpointCategory.lookup(categoryName);
                        final String defaultPolicy = group.getAttributeValue(DEFAULT_SUSPEND_POLICY_ATTRIBUTE_NAME);
                        final boolean conditionEnabled = Boolean.parseBoolean(group.getAttributeValue(DEFAULT_CONDITION_STATE_ATTRIBUTE_NAME, "true"));
                        setBreakpointDefaults(breakpointCategory, new BreakpointDefaults(defaultPolicy, conditionEnabled));
                        Element anyExceptionBreakpointGroup;
                        if (!AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.equals(breakpointCategory)) {
                            // for compatibility with previous format
                            anyExceptionBreakpointGroup = group.getChild(AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.toString());
                            //final BreakpointFactory factory = BreakpointFactory.getInstance(breakpointCategory);
                            //if (factory != null) {
                            for (Element breakpointNode : group.getChildren("breakpoint")) {
                                //Breakpoint breakpoint = factory.createBreakpoint(myProject, breakpointNode);
                                Breakpoint breakpoint = createBreakpoint(categoryName, breakpointNode);
                                breakpoint.readExternal(breakpointNode);
                                nameToBreakpointMap.put(breakpoint.getDisplayName(), breakpoint);
                            }
                            //}
                        }
                        else {
                            anyExceptionBreakpointGroup = group;
                        }

                        if (anyExceptionBreakpointGroup != null) {
                            final Element breakpointElement = group.getChild("breakpoint");
                            if (breakpointElement != null) {
                                XBreakpointManager manager = XDebuggerManager.getInstance(myProject).getBreakpointManager();
                                JavaExceptionBreakpointType type = (JavaExceptionBreakpointType) XDebuggerUtil.getInstance().findBreakpointType(JavaExceptionBreakpointType.class);
                                XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint = manager.getDefaultBreakpoint(type);
                                Breakpoint breakpoint = getJavaBreakpoint(xBreakpoint);
                                if (breakpoint != null) {
                                    breakpoint.readExternal(breakpointElement);
                                    addBreakpoint(breakpoint);
                                }
                            }
                        }
                    }
                }
                catch (InvalidDataException ignored) {
                }

                final Element rulesGroup = parentNode.getChild(RULES_GROUP_NAME);
                if (rulesGroup != null) {
                    final List<Element> rules = rulesGroup.getChildren("rule");
                    for (Element rule : rules) {
                        // skip already converted
                        if (rule.getAttribute(CONVERTED_PARAM) != null) {
                            continue;
                        }
                        final Element master = rule.getChild(MASTER_BREAKPOINT_TAGNAME);
                        if (master == null) {
                            continue;
                        }
                        final Element slave = rule.getChild(SLAVE_BREAKPOINT_TAGNAME);
                        if (slave == null) {
                            continue;
                        }
                        final Breakpoint masterBreakpoint = nameToBreakpointMap.get(master.getAttributeValue("name"));
                        if (masterBreakpoint == null) {
                            continue;
                        }
                        final Breakpoint slaveBreakpoint = nameToBreakpointMap.get(slave.getAttributeValue("name"));
                        if (slaveBreakpoint == null) {
                            continue;
                        }

                        boolean leaveEnabled = "true".equalsIgnoreCase(rule.getAttributeValue("leaveEnabled"));
                        XDependentBreakpointManager dependentBreakpointManager = ((XBreakpointManagerImpl) getXBreakpointManager()).getDependentBreakpointManager();
                        dependentBreakpointManager.setMasterBreakpoint(slaveBreakpoint.myXBreakpoint, masterBreakpoint.myXBreakpoint, leaveEnabled);
                        //addBreakpointRule(new EnableBreakpointRule(BreakpointManager.this, masterBreakpoint, slaveBreakpoint, leaveEnabled));
                    }
                }

                DebuggerInvocationUtil.invokeLater(myProject, () -> updateBreakpointsUI());
            }
        });

        myUIProperties.clear();
        final Element props = parentNode.getChild("ui_properties");
        if (props != null) {
            final List children = props.getChildren("property");
            for (Object child : children) {
                Element property = (Element) child;
                final String name = property.getAttributeValue("name");
                final String value = property.getAttributeValue("value");
                if (name != null && value != null) {
                    myUIProperties.put(name, value);
                }
            }
        }
    }

    private Breakpoint createBreakpoint(String category, Element breakpointNode) throws InvalidDataException {
        XBreakpoint xBreakpoint = null;
        if (category.equals(LineBreakpoint.CATEGORY.toString())) {
            xBreakpoint = createXLineBreakpoint(JavaLineBreakpointType.class, breakpointNode);
        }
        else if (category.equals(MethodBreakpoint.CATEGORY.toString())) {
            if (breakpointNode.getAttribute("url") != null) {
                xBreakpoint = createXLineBreakpoint(JavaMethodBreakpointType.class, breakpointNode);
            }
            else {
                xBreakpoint = createXBreakpoint(JavaWildcardMethodBreakpointType.class);
            }
        }
        else if (category.equals(FieldBreakpoint.CATEGORY.toString())) {
            xBreakpoint = createXLineBreakpoint(JavaFieldBreakpointType.class, breakpointNode);
        }
        else if (category.equals(ExceptionBreakpoint.CATEGORY.toString())) {
            xBreakpoint = createXBreakpoint(JavaExceptionBreakpointType.class);
        }
        if (xBreakpoint == null) {
            throw new IllegalStateException("Unknown breakpoint category " + category);
        }
        return getJavaBreakpoint(xBreakpoint);
    }

    private <B extends XBreakpoint<?>> XBreakpoint createXBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls) {
        final XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeCls);
        return ApplicationManager.getApplication().runWriteAction((Supplier<XBreakpoint>) () -> XDebuggerManager.getInstance(myProject).getBreakpointManager().addBreakpoint((XBreakpointType) type, type.createProperties()));
    }

    private <B extends XBreakpoint<?>> XLineBreakpoint createXLineBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls, Element breakpointNode) throws InvalidDataException {
        final String url = breakpointNode.getAttributeValue("url");
        VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
        if (vFile == null) {
            throw new InvalidDataException(DebuggerBundle.message("error.breakpoint.file.not.found", url));
        }
        final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        if (doc == null) {
            throw new InvalidDataException(DebuggerBundle.message("error.cannot.load.breakpoint.file", url));
        }

        final int line;
        try {
            //noinspection HardCodedStringLiteral
            line = Integer.parseInt(breakpointNode.getAttributeValue("line"));
        }
        catch (Exception e) {
            throw new InvalidDataException("Line number is invalid for breakpoint");
        }
        return addXLineBreakpoint(typeCls, doc, line);
    }

    public static void addBreakpoint(@Nonnull Breakpoint breakpoint) {
        assert breakpoint.myXBreakpoint.getUserData(Breakpoint.DATA_KEY) == breakpoint;
        breakpoint.updateUI();
        checkAndNotifyPossiblySlowBreakpoint(breakpoint.myXBreakpoint);
    }

    public void removeBreakpoint(@Nullable final Breakpoint breakpoint) {
        if (breakpoint == null) {
            return;
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                getXBreakpointManager().removeBreakpoint(breakpoint.myXBreakpoint);
            }
        });
    }

    public void writeExternal(@Nonnull final Element parentNode) {
        // restore old breakpoints
        for (Element group : myOriginalBreakpointsNodes.values()) {
            if (group.getAttribute(CONVERTED_PARAM) == null) {
                group.setAttribute(CONVERTED_PARAM, "true");
            }
            parentNode.addContent(group.clone());
        }
    }

    @Nonnull
    public List<Breakpoint> getBreakpoints() {
        return ApplicationManager.getApplication().runReadAction(new Computable<List<Breakpoint>>() {
            @Override
            public List<Breakpoint> compute() {
                return ContainerUtil.mapNotNull(getXBreakpointManager().getAllBreakpoints(), BreakpointManager::getJavaBreakpoint);
            }
        });
    }

    @Nullable
    public static Breakpoint getJavaBreakpoint(@Nullable final XBreakpoint xBreakpoint) {
        if (xBreakpoint == null) {
            return null;
        }
        Breakpoint breakpoint = xBreakpoint.getUserData(Breakpoint.DATA_KEY);
        if (breakpoint == null && xBreakpoint.getType() instanceof JavaBreakpointType) {
            Project project = xBreakpoint.getProject();
            breakpoint = ((JavaBreakpointType) xBreakpoint.getType()).createJavaBreakpoint(project, xBreakpoint);
            xBreakpoint.putUserData(Breakpoint.DATA_KEY, breakpoint);
        }
        return breakpoint;
    }

    //interaction with RequestManagerImpl
    public void disableBreakpoints(@Nonnull final DebugProcessImpl debugProcess) {
        final List<Breakpoint> breakpoints = getBreakpoints();
        if (!breakpoints.isEmpty()) {
            final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
            for (Breakpoint breakpoint : breakpoints) {
                breakpoint.markVerified(requestManager.isVerified(breakpoint));
                requestManager.deleteRequest(breakpoint);
            }
            SwingUtilities.invokeLater(() -> updateBreakpointsUI());
        }
    }

    public void enableBreakpoints(final DebugProcessImpl debugProcess) {
        final List<Breakpoint> breakpoints = getBreakpoints();
        if (!breakpoints.isEmpty()) {
            for (Breakpoint breakpoint : breakpoints) {
                breakpoint.markVerified(false); // clean cached state
                breakpoint.createRequest(debugProcess);
            }
            SwingUtilities.invokeLater(() -> updateBreakpointsUI());
        }
    }

    public void applyThreadFilter(@Nonnull final DebugProcessImpl debugProcess, @Nullable ThreadReference newFilterThread) {
        final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
        final ThreadReference oldFilterThread = requestManager.getFilterThread();
        if (Comparing.equal(newFilterThread, oldFilterThread)) {
            // the filter already added
            return;
        }
        requestManager.setFilterThread(newFilterThread);
        if (newFilterThread == null || oldFilterThread != null) {
            final List<Breakpoint> breakpoints = getBreakpoints();
            for (Breakpoint breakpoint : breakpoints) {
                if (LineBreakpoint.CATEGORY.equals(breakpoint.getCategory()) || MethodBreakpoint.CATEGORY.equals(breakpoint.getCategory())) {
                    requestManager.deleteRequest(breakpoint);
                    breakpoint.createRequest(debugProcess);
                }
            }
        }
        else {
            // important! need to add filter to _existing_ requests, otherwise Requestor->Request mapping will be lost
            // and debugger trees will not be restored to original state
            abstract class FilterSetter<T extends EventRequest> {
                void applyFilter(@Nonnull final List<T> requests, final ThreadReference thread) {
                    for (T request : requests) {
                        try {
                            final boolean wasEnabled = request.isEnabled();
                            if (wasEnabled) {
                                request.disable();
                            }
                            addFilter(request, thread);
                            if (wasEnabled) {
                                request.enable();
                            }
                        }
                        catch (InternalException | InvalidRequestStateException e) {
                            LOG.info(e);
                        }
                    }
                }

                protected abstract void addFilter(final T request, final ThreadReference thread);
            }

            final EventRequestManager eventRequestManager = requestManager.getVMRequestManager();
            if (eventRequestManager != null) {
                new FilterSetter<BreakpointRequest>() {
                    @Override
                    protected void addFilter(@Nonnull final BreakpointRequest request, final ThreadReference thread) {
                        request.addThreadFilter(thread);
                    }
                }.applyFilter(eventRequestManager.breakpointRequests(), newFilterThread);

                new FilterSetter<MethodEntryRequest>() {
                    @Override
                    protected void addFilter(@Nonnull final MethodEntryRequest request, final ThreadReference thread) {
                        request.addThreadFilter(thread);
                    }
                }.applyFilter(eventRequestManager.methodEntryRequests(), newFilterThread);

                new FilterSetter<MethodExitRequest>() {
                    @Override
                    protected void addFilter(@Nonnull final MethodExitRequest request, final ThreadReference thread) {
                        request.addThreadFilter(thread);
                    }
                }.applyFilter(eventRequestManager.methodExitRequests(), newFilterThread);
            }
        }
    }

    public void updateBreakpointsUI() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        for (Breakpoint breakpoint : getBreakpoints()) {
            breakpoint.updateUI();
        }
    }

    public void reloadBreakpoints() {
        ApplicationManager.getApplication().assertIsDispatchThread();

        for (Breakpoint breakpoint : getBreakpoints()) {
            breakpoint.reload();
        }
    }

    public static void fireBreakpointChanged(Breakpoint breakpoint) {
        breakpoint.reload();
        breakpoint.updateUI();
    }

    public void setBreakpointEnabled(@Nonnull final Breakpoint breakpoint, final boolean enabled) {
        if (breakpoint.isEnabled() != enabled) {
            breakpoint.setEnabled(enabled);
        }
    }

    @Nullable
    public Breakpoint findMasterBreakpoint(@Nonnull Breakpoint dependentBreakpoint) {
        XDependentBreakpointManager dependentBreakpointManager = ((XBreakpointManagerImpl) getXBreakpointManager()).getDependentBreakpointManager();
        return getJavaBreakpoint(dependentBreakpointManager.getMasterBreakpoint(dependentBreakpoint.myXBreakpoint));
    }

    public String getProperty(String name) {
        return myUIProperties.get(name);
    }

    public String setProperty(String name, String value) {
        return myUIProperties.put(name, value);
    }
}
