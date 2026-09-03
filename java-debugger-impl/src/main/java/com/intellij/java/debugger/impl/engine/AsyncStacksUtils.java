// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebugProcessListener;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.DebuggerUtilsImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import com.intellij.java.debugger.impl.settings.CaptureSettingsProvider;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.application.util.registry.Registry;
import consulo.container.plugin.PluginManager;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XStackFrameWithSeparatorAbove;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.internal.com.sun.jdi.ClassType;
import consulo.internal.com.sun.jdi.IncompatibleThreadStateException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.Method;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.StringReference;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.internal.com.sun.jdi.VirtualMachine;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.logging.Logger;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class AsyncStacksUtils {
    private static final Logger LOG = Logger.getInstance(AsyncStacksUtils.class);
    // TODO: obtain CaptureStorage fqn from the class somehow
    public static final String CAPTURE_STORAGE_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureStorage";
    public static final String CAPTURE_AGENT_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureAgent";
    private static final String AGENT_JAR_NAME = "debugger-agent.jar";
    /**
     * IDEA supports the agent from JDK 1.7. Consulo's agent is built against Consulo's ASM build, which is Java 11 bytecode
     * (consulo.internal.org.objectweb.asm), so the agent is only injected into Java 11+ debuggees. Lower this once the ASM
     * build targets an older class file version.
     */
    private static final JavaSdkVersion MIN_AGENT_JDK_VERSION = JavaSdkVersion.JDK_11;
    private static final Key<Boolean> ASYNC_STACKS_ENABLED = Key.create("ASYNC_STACKS_ENABLED");

    public static boolean isAsyncStacksEnabled(XDebugSession session) {
        return ASYNC_STACKS_ENABLED.get(session.getSessionData(), true);
    }

    public static void setAsyncStacksEnabled(XDebugSession session, boolean state) {
        ASYNC_STACKS_ENABLED.set(session.getSessionData(), state);
    }

    public static boolean isAgentEnabled() {
        return DebuggerSettings.getInstance().INSTRUMENTING_AGENT;
    }

    public static boolean isSuspendHelperEnabled() {
        return isAgentEnabled() && Registry.is("debugger.run.suspend.helper", true);
    }

    /**
     * Returns async stack trace captured by the debugger-agent for the thread corresponding to the given frame or null.
     *
     * If `debugger.async.stack.trace.for.all.threads` is true, returns captured async stack trace for any thread;
     * otherwise only returns captured async stack trace for the current thread and null for other threads.
     */
    public static @Nullable List<@Nullable StackFrameItem> getAgentRelatedStack(StackFrameProxyImpl frame,
                                                                                SuspendContextImpl suspendContext) {
        if (!isAgentEnabled()) {
            return null;
        }
        try {
            Method method = DebuggerUtilsEx.getMethod(frame.location());
            // TODO: use com.intellij.rt.debugger.agent.CaptureStorage.GENERATED_INSERT_METHOD_POSTFIX
            if (method != null && method.name().endsWith("$$$capture")) {
                return getCapturedStackForThread(
                    new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy()),
                    frame.threadProxy().getThreadReference()
                );
            }
        }
        catch (EvaluateException e) {
            ObjectReference targetException = e.getExceptionFromTargetVM();
            if (e.getCause() instanceof IncompatibleThreadStateException) {
                LOG.warn(e);
            }
            else if (targetException != null && DebuggerUtils.instanceOf(targetException.type(), "java.lang.StackOverflowError")) {
                LOG.warn(e);
            }
            else {
                LOG.error(e);
            }
        }
        return null;
    }

    private static final Key<Pair<ClassType, Method>> CAPTURE_STORAGE_METHOD = Key.create("CAPTURE_STORAGE_METHOD");
    private static final Pair<ClassType, Method> NO_CAPTURE_AGENT = Pair.empty();

    private static @Nullable List<StackFrameItem> getCapturedStackForThread(EvaluationContextImpl evalContext,
                                                                             ThreadReference threadReference) throws EvaluateException {
        EvaluationContextImpl evaluationContext = evalContext.withAutoLoadClasses(false);

        DebugProcessImpl process = evaluationContext.getDebugProcess();
        VirtualMachineProxyImpl virtualMachineProxy = evalContext.getDebugProcess().getVirtualMachineProxy();
        Pair<ClassType, Method> methodPair = virtualMachineProxy.getUserData(CAPTURE_STORAGE_METHOD);

        if (methodPair == null) {
            try {
                ClassType captureClass = (ClassType) process.findClass(evaluationContext, CAPTURE_STORAGE_CLASS_NAME, null);
                if (captureClass == null) {
                    methodPair = NO_CAPTURE_AGENT;
                    LOG.debug("Error loading debug agent", "agent class not found");
                }
                else {
                    methodPair = Pair.create(captureClass, DebuggerUtils.findMethod(captureClass, "getCapturedStackForThread", null));
                }
            }
            catch (EvaluateException e) {
                methodPair = NO_CAPTURE_AGENT;
                LOG.debug("Error loading debug agent", e);
            }
            virtualMachineProxy.putUserData(CAPTURE_STORAGE_METHOD, methodPair);
        }

        if (methodPair == NO_CAPTURE_AGENT) {
            return null;
        }

        Pair<ClassType, Method> finalMethodPair = methodPair;
        List<Value> args = Arrays.asList(
            evaluationContext.getDebugProcess().getVirtualMachineProxy().mirrorOf(getMaxStackLength()),
            threadReference
        );
        String value = DebuggerUtils.getInstance().processCollectibleValue(
            () -> process.invokeMethod(evaluationContext, finalMethodPair.first, finalMethodPair.second,
                                       args, ObjectReference.INVOKE_SINGLE_THREADED, true),
            result -> result instanceof StringReference ? ((StringReference) result).value() : null,
            evaluationContext);
        if (value != null) {
            return parseAgentAsyncStackTrace(value, virtualMachineProxy);
        }
        return null;
    }

    /**
     * Parses stack trace captured by the debugger-agent. Result list can contain null elements corresponding to separator frames.
     */
    public static @Nullable List<@Nullable StackFrameItem> parseAgentAsyncStackTrace(String value, VirtualMachineProxyImpl vm) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1)))) {
            return parseAgentAsyncStackTrace(dis, vm);
        }
        catch (IOException e) {
            DebuggerUtilsImpl.logError(e);
            return null;
        }
    }

    /**
     * Parses stack trace captured by the debugger-agent. Result list can contain null elements corresponding to separator frames.
     */
    public static @Nullable List<@Nullable StackFrameItem> parseAgentAsyncStackTrace(DataInputStream dis, VirtualMachineProxyImpl vm) {
        try {
            List<StackFrameItem> res = new ArrayList<>();
            while (dis.available() > 0) {
                StackFrameItem item = null;
                if (dis.readBoolean()) {
                    String className = dis.readUTF();
                    String methodName = dis.readUTF();
                    int line = dis.readInt();
                    if ("< Unknown".equals(className) && "Stack > ".equals(methodName)) {
                        item = new ThrottledStackFrameItem(vm.getVirtualMachine());
                    }
                    else {
                        Location location = DebuggerUtilsEx.findOrCreateLocation(vm.getVirtualMachine(), className, methodName, line);
                        item = new StackFrameItem(location, null);
                    }
                }
                res.add(item);
            }
            return res;
        }
        catch (Exception e) {
            DebuggerUtilsImpl.logError(e);
            return null;
        }
    }

    public static void setupAgent(DebugProcessImpl process) {
        if (!isAgentEnabled()) {
            return;
        }

        // set debug mode
        if (Registry.is("debugger.capture.points.agent.debug", false)) {
            enableAgentDebug(process);
        }

        initializeOverheadDetector(process);

        // add points
        if (DebuggerUtilsImpl.isRemote(process)) {
            Properties properties = CaptureSettingsProvider.getPointsProperties(process.getProject());
            if (!properties.isEmpty()) {
                process.addDebugProcessListener(new DebugProcessAdapterImpl() {
                    @Override
                    public void paused(SuspendContextImpl suspendContext) {
                        if (process.isEvaluationPossible()) { // evaluation is possible
                            try {
                                StackCapturingLineBreakpoint.deleteAll(process);

                                try {
                                    addAgentCapturePoints(
                                        new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy()),
                                        properties
                                    );
                                    process.removeDebugProcessListener(this);
                                }
                                finally {
                                    process.onHotSwapFinished();
                                    StackCapturingLineBreakpoint.createAll(process);
                                }
                            }
                            catch (Exception e) {
                                LOG.debug(e);
                            }
                        }
                    }

                    @Override
                    public void processDetached(DebugProcessImpl process, boolean closedByUser) {
                        process.removeDebugProcessListener(this);
                    }
                });
            }
        }
    }

    private static void enableAgentDebug(DebugProcessImpl process) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        DebuggerUtilsEx.setStaticBooleanField(process, CAPTURE_STORAGE_CLASS_NAME, "DEBUG", true);
    }

    public static void addAgentCapturePoints(EvaluationContextImpl evalContext, Properties properties) {
        EvaluationContextImpl evaluationContext = evalContext.withAutoLoadClasses(false);
        DebugProcessImpl process = evaluationContext.getDebugProcess();
        try {
            ClassType captureClass = (ClassType) process.findClass(evaluationContext, CAPTURE_AGENT_CLASS_NAME, null);
            if (captureClass == null) {
                LOG.debug("Error loading debug agent", "agent class not found");
            }
            else {
                Method method = DebuggerUtils.findMethod(captureClass, "addCapturePoints", null);
                if (method != null) {
                    StringWriter writer = new StringWriter();
                    try {
                        properties.store(writer, null);
                        StringReference stringArgs = DebuggerUtilsEx.mirrorOfString(writer.toString(), evalContext);
                        List<StringReference> args = Collections.singletonList(stringArgs);
                        try {
                            process.invokeMethod(
                                evaluationContext, captureClass, method, args, ObjectReference.INVOKE_SINGLE_THREADED, true
                            );
                        }
                        finally {
                            DebuggerUtilsEx.enableCollection(stringArgs);
                        }
                    }
                    catch (Exception e) {
                        DebuggerUtilsImpl.logError(e);
                    }
                }
            }
        }
        catch (EvaluateException e) {
            LOG.debug("Error loading debug agent", e);
        }
    }

    public static <T> void putProcessUserData(Key<T> key, @Nullable T value, DebugProcessImpl debugProcess) {
        debugProcess.putUserData(key, value);
        debugProcess.addDebugProcessListener(new DebugProcessListener() {
            @Override
            public void processDetached(DebugProcess process, boolean closedByUser) {
                process.putUserData(key, null);
            }
        });
    }

    public static int getMaxStackLength() {
        return Registry.intValue("debugger.async.stacks.max.depth", 500);
    }

    public static void addDebuggerAgent(OwnJavaParameters parameters, @Nullable Project project, boolean checkJdkVersion) {
        addDebuggerAgent(parameters, project, checkJdkVersion, null);
    }

    public static void addDebuggerAgent(OwnJavaParameters parameters,
                                        @Nullable Project project,
                                        boolean checkJdkVersion,
                                        @Nullable Disposable disposable) {
        if (isAgentEnabled()) {
            String prefix = "-javaagent:";
            ParametersList parametersList = parameters.getVMParametersList();
            if (!ContainerUtil.exists(parametersList.getParameters(), p -> p.startsWith(prefix) && p.contains(AGENT_JAR_NAME))) {
                Sdk jdk = parameters.getJdk();
                if (checkJdkVersion && jdk == null) {
                    return;
                }
                JavaSdkVersion sdkVersion = jdk != null ? JavaSdkTypeUtil.getVersion(jdk) : null;
                if (checkJdkVersion && (sdkVersion == null || !sdkVersion.isAtLeast(MIN_AGENT_JDK_VERSION))) {
                    LOG.warn("Capture agent is not supported for JRE " + sdkVersion);
                    return;
                }

                extendParametersForAgent(project, disposable, parametersList, prefix);
            }
        }
    }

    private static void extendParametersForAgent(@Nullable Project project,
                                                 @Nullable Disposable disposable,
                                                 ParametersList parametersList,
                                                 String prefix) {
        Path agentNativePath = getAgentArtifactPath(project, disposable);
        if (agentNativePath == null) {
            // errors are reported by getAgentArtifactPath
            return;
        }
        String agentPath = agentNativePath.toString();

        parametersList.prepend(prefix + agentPath + generateAgentSettings(project));
        if (Registry.is("debugger.async.stacks.coroutines", true)) {
            parametersList.addProperty("kotlinx.coroutines.debug.enable.creation.stack.trace", "false");
            parametersList.addProperty("debugger.agent.enable.coroutines", "true");
            if (Registry.is("debugger.async.stacks.flows", true)) {
                parametersList.addProperty("kotlinx.coroutines.debug.enable.flows.stack.trace", "true");
            }
            if (Registry.is("debugger.async.stacks.state.flows", true)) {
                parametersList.addProperty("kotlinx.coroutines.debug.enable.mutable.state.flows.stack.trace", "true");
            }
        }
        if (!Registry.is("debugger.async.stack.trace.for.exceptions.printing", true)) {
            parametersList.addProperty("debugger.agent.support.throwable", "false");
        }
        if (Registry.is("debugger.async.stack.trace.for.all.threads", true)) {
            parametersList.addProperty("debugger.async.stack.trace.for.all.threads", "true");
        }

        for (DebuggerAgentParametersModifier modifier : DebuggerAgentParametersModifier.getAgentModifiers()) {
            modifier.modifyParameters(parametersList, project);
        }
    }

    private static @Nullable Path getAgentArtifactPath(@Nullable Project project, @Nullable Disposable disposable) {
        return getArtifactPathForBundledAgent(project, disposable);
    }

    private static @Nullable Path getArtifactPathForBundledAgent(@Nullable Project project, @Nullable Disposable disposable) {
        // the agent jar is packaged directly in the (java) plugin directory, next to java-rt.jar
        File pluginPath = PluginManager.getPluginPath(AsyncStacksUtils.class);
        Path pluginDistDir = pluginPath != null ? pluginPath.toPath() : null;
        if (pluginDistDir == null || !Files.isDirectory(pluginDistDir)) {
            LOG.error("Unable to find the (java) plugin distribution directory by class AsyncStacksUtils");
            return null;
        }

        Path bundledAgentPath = pluginDistDir.resolve(AGENT_JAR_NAME);
        if (!Files.exists(bundledAgentPath)) {
            LOG.error("Unable to find bundled debugger agent under the (java) plugin directory: " + bundledAgentPath);
            return null;
        }

        String processedAgentPath = JavaExecutionUtil.handleSpacesInAgentPath(
            bundledAgentPath.toAbsolutePath().toString(),
            "captureAgent",
            null,
            f -> f.getName().startsWith("debugger-agent")
        );
        if (processedAgentPath != null) {
            return Path.of(processedAgentPath);
        }
        return null;
    }

    private static String generateAgentSettings(@Nullable Project project) {
        Properties properties = CaptureSettingsProvider.getPointsProperties(project);
        for (DebuggerAgentParametersModifier modifier : DebuggerAgentParametersModifier.getAgentModifiers()) {
            modifier.modifyProperties(properties, project);
        }
        if (isSuspendHelperEnabled()) {
            properties.setProperty("suspendHelper", "true");
        }
        boolean throttling = DebuggerSettings.getInstance().AGENT_THROTTLING;
        properties.setProperty("throttling", Boolean.toString(throttling));
        String overheadValue = Registry.stringValue("debugger.async.stack.trace.overhead.percent");
        double overhead = StringUtil.isEmpty(overheadValue) ? 50.0 : Double.parseDouble(overheadValue);
        properties.setProperty("overheadPercent", Double.toString(overhead));
        if (!properties.isEmpty()) {
            try {
                File file = FileUtil.createTempFile("capture", ".props", true);
                try (OutputStream out = Files.newOutputStream(file.toPath())) {
                    properties.store(out, null);
                    return "=" + file.toURI().toASCIIString();
                }
            }
            catch (IOException e) {
                LOG.error(e);
            }
        }
        return "";
    }

    private static void initializeOverheadDetector(DebugProcessImpl process) {
        AsyncStackTracesOverheadUtils.initializeOverheadListener(process);

        String className = "com.intellij.rt.debugger.agent.OverheadDetector";
        String methodName = "overheadDetected";
        SyntheticMethodBreakpoint breakpoint = new SyntheticMethodBreakpoint(className, methodName, null, process.getProject()) {
            @Override
            public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) {
                AsyncStackTracesOverheadUtils.onOverheadDetected(process);
                return false;
            }
        };
        breakpoint.setSuspendPolicy(DebuggerSettings.SUSPEND_THREAD);
        breakpoint.createRequest(process);
    }

    private static class ThrottledStackFrameItem extends StackFrameItem {
        ThrottledStackFrameItem(VirtualMachine vm) {
            super(createSyntheticLocation(vm), Collections.emptyList());
        }

        private static Location createSyntheticLocation(VirtualMachine vm) {
            return DebuggerUtilsEx.findOrCreateLocation(vm, "", "", -1);
        }

        @Override
        public XStackFrame createFrame(DebugProcessImpl debugProcess, @Nullable SourcePosition sourcePosition) {
            return new ThrottledFrame();
        }
    }

    public static class ThrottledFrame extends XStackFrame implements XStackFrameWithSeparatorAbove {
        private boolean myWithSeparator;

        @Override
        public void customizePresentation(ColoredTextContainer component) {
            component.setIcon(Image.empty(16));
            component.append(JavaDebuggerLocalize.asyncStackThrottledFrameLabel(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }

        @Override
        public void computeChildren(XCompositeNode node) {
            node.setMessage(JavaDebuggerLocalize.asyncStackThrottledFrameInfo(), null,
                            SimpleTextAttributes.REGULAR_ATTRIBUTES, StackFrameItem.CAPTURE_SETTINGS_OPENER);
            node.addChildren(XValueChildrenList.EMPTY, true);
        }

        // TODO: mark with @Override once the platform's XStackFrameWithSeparatorAbove provides getCaptionAboveOf()
        public String getCaptionAboveOf() {
            return StackFrameItem.getAsyncStacktraceMessage().get();
        }

        @Override
        public boolean hasSeparatorAbove() {
            return myWithSeparator;
        }

        // TODO: mark with @Override once the platform's XStackFrameWithSeparatorAbove provides setWithSeparator(boolean)
        public void setWithSeparator(boolean withSeparator) {
            myWithSeparator = withSeparator;
        }
    }
}
