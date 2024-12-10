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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.NameMapper;
import com.intellij.java.debugger.PositionManager;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebugProcessListener;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.apiAdapters.TransportServiceWrapper;
import com.intellij.java.debugger.impl.engine.DebugProcessEvents;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.RemoteDebugProcessHandler;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.GetJPDADialog;
import com.intellij.java.debugger.impl.ui.breakpoints.BreakpointManager;
import com.intellij.java.debugger.impl.ui.tree.render.BatchEvaluator;
import com.intellij.java.execution.configurations.RemoteConnection;
import com.intellij.java.language.impl.projectRoots.ex.JavaSdkUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.colorScheme.event.EditorColorsListener;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.content.bundle.Sdk;
import consulo.execution.ExecutionResult;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.KillableProcessHandler;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.Lists;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@Singleton
@State(name = "DebuggerManager", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
@ServiceImpl
public class DebuggerManagerImpl extends DebuggerManagerEx {
    private static final Logger LOG = Logger.getInstance(DebuggerManagerImpl.class);

    private final Project myProject;
    private final HashMap<ProcessHandler, DebuggerSession> mySessions = new HashMap<>();
    private final BreakpointManager myBreakpointManager;
    private final List<NameMapper> myNameMappers = Lists.newLockFreeCopyOnWriteList();
    private final List<Function<DebugProcess, PositionManager>> myCustomPositionManagerFactories = new SmartList<>();

    private final MyDebuggerStateManager myDebuggerStateManager = new MyDebuggerStateManager();

    private final DebuggerContextListener mySessionListener = (newContext, event) -> {
        final DebuggerSession session = newContext.getDebuggerSession();
        if (event == DebuggerSession.Event.PAUSE && myDebuggerStateManager.myDebuggerSession != session) {
            // if paused in non-active session; switch current session
            myDebuggerStateManager.setState(newContext, session != null ? session.getState() : DebuggerSession.State.DISPOSED, event, null);
            return;
        }

        if (myDebuggerStateManager.myDebuggerSession == session) {
            myDebuggerStateManager.fireStateChanged(newContext, event);
        }
        if (event == DebuggerSession.Event.ATTACHED) {
            listener().sessionAttached(session);
        }
        else if (event == DebuggerSession.Event.DETACHED) {
            listener().sessionDetached(session);
        }
        else if (event == DebuggerSession.Event.DISPOSE) {
            dispose(session);
            if (myDebuggerStateManager.myDebuggerSession == session) {
                myDebuggerStateManager.setState(
                    DebuggerContextImpl.EMPTY_CONTEXT,
                    DebuggerSession.State.DISPOSED,
                    DebuggerSession.Event.DISPOSE,
                    null
                );
            }
        }
    };

    private static final String DEBUG_KEY_NAME = "idea.xdebug.key";

    @Override
    public void addClassNameMapper(final NameMapper mapper) {
        myNameMappers.add(mapper);
    }

    @Override
    public void removeClassNameMapper(final NameMapper mapper) {
        myNameMappers.remove(mapper);
    }

    @Override
    public String getVMClassQualifiedName(@Nonnull final PsiClass aClass) {
        for (NameMapper nameMapper : myNameMappers) {
            final String qName = nameMapper.getQualifiedName(aClass);
            if (qName != null) {
                return qName;
            }
        }
        return aClass.getQualifiedName();
    }

    @Inject
    public DebuggerManagerImpl(Project project) {
        myProject = project;
        myBreakpointManager = new BreakpointManager(myProject, this);
    }

    @Nonnull
    private DebuggerManagerListener listener() {
        return myProject.getMessageBus().syncPublisher(DebuggerManagerListener.class);
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public DebuggerSession getSession(DebugProcess process) {
        Application.get().assertIsDispatchThread();
        return getSessions().stream().filter(debuggerSession -> process == debuggerSession.getProcess()).findFirst().orElse(null);
    }

    @Nonnull
    @Override
    public Collection<DebuggerSession> getSessions() {
        synchronized (mySessions) {
            final Collection<DebuggerSession> values = mySessions.values();
            return values.isEmpty() ? Collections.emptyList() : new ArrayList<>(values);
        }
    }

    /**
     * @deprecated to be removed with {@link DebuggerManager#registerPositionManagerFactory(Function)}
     */
    @Deprecated
    public Stream<Function<DebugProcess, PositionManager>> getCustomPositionManagerFactories() {
        return myCustomPositionManagerFactories.stream();
    }

    @Override
    @Nullable
    @RequiredUIAccess
    public DebuggerSession attachVirtualMachine(@Nonnull DebugEnvironment environment) throws ExecutionException {
        Application.get().assertIsDispatchThread();
        DebugProcessEvents debugProcess = new DebugProcessEvents(myProject);
        DebuggerSession session = DebuggerSession.create(environment.getSessionName(), debugProcess, environment);
        ExecutionResult executionResult = session.getProcess().getExecutionResult();
        if (executionResult == null) {
            return null;
        }
        session.getContextManager().addListener(mySessionListener);
        getContextManager().setState(
            DebuggerContextUtil.createDebuggerContext(
                session,
                session.getContextManager().getContext().getSuspendContext()
            ),
            session.getState(),
            DebuggerSession.Event.CONTEXT,
            null
        );

        final ProcessHandler processHandler = executionResult.getProcessHandler();

        synchronized (mySessions) {
            mySessions.put(processHandler, session);
        }

        if (!(processHandler instanceof RemoteDebugProcessHandler)) {
            // add listener only to non-remote process handler:
            // on Unix systems destroying process does not cause VMDeathEvent to be generated,
            // so we need to call debugProcess.stop() explicitly for graceful termination.
            // RemoteProcessHandler on the other hand will call debugProcess.stop() as a part of destroyProcess() and detachProcess() implementation,
            // so we shouldn't add the listener to avoid calling stop() twice
            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
                    ProcessHandler processHandler = event.getProcessHandler();
                    final DebugProcessImpl debugProcess = getDebugProcess(processHandler);
                    if (debugProcess != null) {
                        // if current thread is a "debugger manager thread", stop will execute synchronously
                        // it is KillableProcessHandler responsibility to terminate VM
                        debugProcess.stop(
                            willBeDestroyed
                                && !(processHandler instanceof KillableProcessHandler killableProcessHandler && killableProcessHandler.canKillProcess())
                        );

                        // wait at most 10 seconds: the problem is that debugProcess.stop() can hang if there are troubles in the debuggee
                        // if processWillTerminate() is called from AWT thread debugProcess.waitFor() will block it and the whole app will hang
                        if (!DebuggerManagerThreadImpl.isManagerThread()) {
                            if (SwingUtilities.isEventDispatchThread()) {
                                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                                    () -> {
                                        ProgressManager.getInstance()
                                            .getProgressIndicator()
                                            .setIndeterminate(true);
                                        debugProcess.waitFor(10000);
                                    },
                                    "Waiting For Debugger Response",
                                    false,
                                    debugProcess.getProject()
                                );
                            }
                            else {
                                debugProcess.waitFor(10000);
                            }
                        }
                    }
                }
            });
        }
        listener().sessionCreated(session);

        if (debugProcess.isDetached() || debugProcess.isDetaching()) {
            session.dispose();
            return null;
        }
        if (environment.isRemote()) {
            // optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
            // which is an expensive operation when executed first time
            debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
        }

        return session;
    }

    @Override
    public DebugProcessImpl getDebugProcess(final ProcessHandler processHandler) {
        synchronized (mySessions) {
            DebuggerSession session = mySessions.get(processHandler);
            return session != null ? session.getProcess() : null;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @Nullable
    public DebuggerSession getDebugSession(final ProcessHandler processHandler) {
        synchronized (mySessions) {
            return mySessions.get(processHandler);
        }
    }

    @Override
    public void addDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
        DebugProcessImpl debugProcess = getDebugProcess(processHandler);
        if (debugProcess != null) {
            debugProcess.addDebugProcessListener(listener);
        }
        else {
            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void startNotified(ProcessEvent event) {
                    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
                    if (debugProcess != null) {
                        debugProcess.addDebugProcessListener(listener);
                    }
                    processHandler.removeProcessListener(this);
                }
            });
        }
    }

    @Override
    public void removeDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
        DebugProcessImpl debugProcess = getDebugProcess(processHandler);
        if (debugProcess != null) {
            debugProcess.removeDebugProcessListener(listener);
        }
        else {
            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void startNotified(ProcessEvent event) {
                    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
                    if (debugProcess != null) {
                        debugProcess.removeDebugProcessListener(listener);
                    }
                    processHandler.removeProcessListener(this);
                }
            });
        }
    }

    @Override
    public boolean isDebuggerManagerThread() {
        return DebuggerManagerThreadImpl.isManagerThread();
    }

    @Nonnull
    @Override
    public BreakpointManager getBreakpointManager() {
        return myBreakpointManager;
    }

    @Nonnull
    @Override
    public DebuggerContextImpl getContext() {
        return getContextManager().getContext();
    }

    @Nonnull
    @Override
    public DebuggerStateManager getContextManager() {
        return myDebuggerStateManager;
    }

    @Override
    public void registerPositionManagerFactory(final Function<DebugProcess, PositionManager> factory) {
        myCustomPositionManagerFactories.add(factory);
    }

    @Override
    public void unregisterPositionManagerFactory(final Function<DebugProcess, PositionManager> factory) {
        myCustomPositionManagerFactories.remove(factory);
    }

    /* Remoting */
    private static void checkTargetJPDAInstalled(OwnJavaParameters parameters) throws ExecutionException {
        final Sdk jdk = parameters.getJdk();
        if (jdk == null) {
            throw new ExecutionException(DebuggerBundle.message("error.jdk.not.specified"));
        }
        final JavaSdkVersion version = JavaSdkTypeUtil.getVersion(jdk);
        String versionString = jdk.getVersionString();
        if (version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1) {
            throw new ExecutionException(DebuggerBundle.message("error.unsupported.jdk.version", versionString));
        }
        if (Platform.current().os().isWindows() && version == JavaSdkVersion.JDK_1_2) {
            final VirtualFile homeDirectory = jdk.getHomeDirectory();
            if (homeDirectory == null || !homeDirectory.isValid()) {
                throw new ExecutionException(DebuggerBundle.message("error.invalid.jdk.home", versionString));
            }
            //noinspection HardCodedStringLiteral
            File dllFile =
                new File(homeDirectory.getPath().replace('/', File.separatorChar) + File.separator + "bin" + File.separator + "jdwp.dll");
            if (!dllFile.exists()) {
                GetJPDADialog dialog = new GetJPDADialog();
                dialog.show();
                throw new ExecutionException(DebuggerBundle.message("error.debug.libraries.missing"));
            }
        }
    }

    /**
     * for Target JDKs versions 1.2.x - 1.3.0 the Classic VM should be used for debugging
     */
    @Deprecated
    private static boolean shouldForceClassicVM(Sdk jdk) {
        return false;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static RemoteConnection createDebugParameters(
        final OwnJavaParameters parameters,
        final boolean debuggerInServerMode,
        int transport,
        final String debugPort,
        boolean checkValidity
    ) throws ExecutionException {
        if (checkValidity) {
            checkTargetJPDAInstalled(parameters);
        }

        final boolean useSockets = transport == DebuggerSettings.SOCKET_TRANSPORT;

        String address = "";
        if (StringUtil.isEmptyOrSpaces(debugPort)) {
            try {
                address = DebuggerUtils.getInstance().findAvailableDebugAddress(transport).address();
            }
            catch (ExecutionException e) {
                if (checkValidity) {
                    throw e;
                }
            }
        }
        else {
            address = debugPort;
        }

        final TransportServiceWrapper transportService = TransportServiceWrapper.createTransportService(transport);
        final String debugAddress = debuggerInServerMode && useSockets ? "127.0.0.1:" + address : address;
        String debuggeeRunProperties = "transport=" + transportService.transportId() + ",address=" + debugAddress;
        if (debuggerInServerMode) {
            debuggeeRunProperties += ",suspend=y,server=n";
        }
        else {
            debuggeeRunProperties += ",suspend=n,server=y";
        }

        if (StringUtil.containsWhitespaces(debuggeeRunProperties)) {
            debuggeeRunProperties = "\"" + debuggeeRunProperties + "\"";
        }
        final String _debuggeeRunProperties = debuggeeRunProperties;

        Application.get().runReadAction(() -> {
            JavaSdkUtil.addRtJar(parameters.getClassPath());

            final Sdk jdk = parameters.getJdk();
            final boolean forceClassicVM = shouldForceClassicVM(jdk);
            final boolean forceNoJIT = shouldForceNoJIT(jdk);
            final String debugKey = Platform.current().jvm().getRuntimeProperty(DEBUG_KEY_NAME, "-Xdebug");
            final boolean needDebugKey = shouldAddXdebugKey(jdk) || !"-Xdebug".equals(debugKey) /*the key is non-standard*/;

            if (forceClassicVM || forceNoJIT || needDebugKey || !isJVMTIAvailable(jdk)) {
                parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "-Xrunjdwp:" + _debuggeeRunProperties);
            }
            else {
                // use newer JVMTI if available
                parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "");
                parameters.getVMParametersList().replaceOrPrepend("-agentlib:jdwp=", "-agentlib:jdwp=" + _debuggeeRunProperties);
            }

            if (forceNoJIT) {
                parameters.getVMParametersList().replaceOrPrepend("-Djava.compiler=", "-Djava.compiler=NONE");
                parameters.getVMParametersList().replaceOrPrepend("-Xnoagent", "-Xnoagent");
            }

            if (needDebugKey) {
                parameters.getVMParametersList().replaceOrPrepend(debugKey, debugKey);
            }
            else {
                // deliberately skip outdated parameter because it can disable full-speed debugging for some jdk builds
                // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6272174
                parameters.getVMParametersList().replaceOrPrepend("-Xdebug", "");
            }

            parameters.getVMParametersList().replaceOrPrepend("-classic", forceClassicVM ? "-classic" : "");
        });

        return new RemoteConnection(useSockets, "127.0.0.1", address, debuggerInServerMode);
    }

    @Deprecated
    private static boolean shouldForceNoJIT(Sdk jdk) {
        return DebuggerSettings.getInstance().DISABLE_JIT;
    }

    @Deprecated
    private static boolean shouldAddXdebugKey(Sdk jdk) {
        if (jdk == null) {
            return true; // conservative choice
        }
        return DebuggerSettings.getInstance().DISABLE_JIT;
    }

    @Deprecated
    private static boolean isJVMTIAvailable(Sdk jdk) {
        if (jdk == null) {
            return false; // conservative choice
        }

        return true;
    }

    public static RemoteConnection createDebugParameters(
        final OwnJavaParameters parameters,
        GenericDebuggerRunnerSettings settings,
        boolean checkValidity
    ) throws ExecutionException {
        return createDebugParameters(parameters, settings.LOCAL, settings.getTransport(), settings.getDebugPort(), checkValidity);
    }

    private static class MyDebuggerStateManager extends DebuggerStateManager {
        private DebuggerSession myDebuggerSession;

        @Nonnull
        @Override
        public DebuggerContextImpl getContext() {
            return myDebuggerSession == null ? DebuggerContextImpl.EMPTY_CONTEXT : myDebuggerSession.getContextManager().getContext();
        }

        @Override
        @RequiredUIAccess
        public void setState(
            @Nonnull final DebuggerContextImpl context,
            DebuggerSession.State state,
            DebuggerSession.Event event,
            String description
        ) {
            Application.get().assertIsDispatchThread();
            myDebuggerSession = context.getDebuggerSession();
            if (myDebuggerSession != null) {
                myDebuggerSession.getContextManager().setState(context, state, event, description);
            }
            else {
                fireStateChanged(context, event);
            }
        }
    }

    private void dispose(DebuggerSession session) {
        ProcessHandler processHandler = session.getProcess().getProcessHandler();
        synchronized (mySessions) {
            DebuggerSession removed = mySessions.remove(processHandler);
            LOG.assertTrue(removed != null);
            listener().sessionRemoved(session);
        }
    }
}
