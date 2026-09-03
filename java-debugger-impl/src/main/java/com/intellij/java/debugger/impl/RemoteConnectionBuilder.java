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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.apiAdapters.TransportServiceWrapper;
import com.intellij.java.debugger.impl.engine.AsyncStacksUtils;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.GetJPDADialog;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.execution.configurations.RemoteConnection;
import com.intellij.java.language.impl.projectRoots.ex.JavaSdkUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.application.Application;
import consulo.application.util.registry.Registry;
import consulo.content.bundle.Sdk;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.PathsList;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class RemoteConnectionBuilder {
    private static final Logger LOG = Logger.getInstance(RemoteConnectionBuilder.class);

    private final int myTransport;
    private final boolean myServer;
    private final String myAddress;
    private boolean myCheckValidity;
    private boolean myAsyncAgent;
    private boolean myQuiet;
    private boolean mySuspend = true;
    private @Nullable Project myProject;

    public RemoteConnectionBuilder(boolean server, int transport, String address) {
        myTransport = transport;
        myServer = server;
        myAddress = address;
    }

    public RemoteConnectionBuilder checkValidity(boolean check) {
        myCheckValidity = check;
        return this;
    }

    public RemoteConnectionBuilder asyncAgent(boolean useAgent) {
        myAsyncAgent = useAgent;
        return this;
    }

    public RemoteConnectionBuilder project(@Nullable Project project) {
        myProject = project;
        return this;
    }

    public RemoteConnectionBuilder quiet() {
        myQuiet = true;
        return this;
    }

    public RemoteConnectionBuilder suspend(boolean suspend) {
        mySuspend = suspend;
        return this;
    }

    @RequiredUIAccess
    public RemoteConnection create(OwnJavaParameters parameters) throws ExecutionException {
        if (myCheckValidity) {
            checkTargetJPDAInstalled(parameters);
        }

        boolean useSockets = myTransport == DebuggerSettings.SOCKET_TRANSPORT;

        String address = "";
        if (StringUtil.isEmptyOrSpaces(myAddress)) {
            try {
                address = DebuggerUtils.getInstance().findAvailableDebugAddress(useSockets);
            }
            catch (ExecutionException e) {
                if (myCheckValidity) {
                    throw e;
                }
            }
        }
        else {
            address = myAddress;
        }

        TransportServiceWrapper transportService = TransportServiceWrapper.createTransportService(myTransport);
        String debugAddress = myServer && useSockets ? DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK + ":" + address : address;
        StringBuilder debuggeeRunProperties = new StringBuilder();
        debuggeeRunProperties.append("transport=").append(transportService.transportId());
        debuggeeRunProperties.append(",address=").append(debugAddress);
        debuggeeRunProperties.append(mySuspend ? ",suspend=y" : ",suspend=n");
        debuggeeRunProperties.append(myServer ? ",server=n" : ",server=y");

        if (StringUtil.containsWhitespaces(debuggeeRunProperties)) {
            debuggeeRunProperties.insert(0, "\"").append("\"");
        }

        if (myQuiet) {
            debuggeeRunProperties.append(",quiet=y");
        }

        if (DebuggerSettings.getInstance().INCLUDE_VIRTUAL_THREADS) {
            debuggeeRunProperties.append(",includevirtualthreads=y");
        }

        String _debuggeeRunProperties = debuggeeRunProperties.toString();

        Application.get().runReadAction(() -> {
            addRtJar(parameters.getClassPath());

            if (myAsyncAgent) {
                AsyncStacksUtils.addDebuggerAgent(parameters, myProject, true, null);
            }

            parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "");
            parameters.getVMParametersList().replaceOrPrepend("-agentlib:jdwp=", "-agentlib:jdwp=" + _debuggeeRunProperties);
        });

        return new RemoteConnection(useSockets, DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK, address, myServer);
    }

    private static void addRtJar(PathsList pathsList) {
        if (Registry.is("debugger.add.rt.jar", true)) {
            JavaSdkUtil.addRtJar(pathsList);
            LOG.debug("Running from release IDE, add rt.jar");
        }
    }

    @RequiredUIAccess
    private static void checkTargetJPDAInstalled(OwnJavaParameters parameters) throws ExecutionException {
        Sdk jdk = parameters.getJdk();
        if (jdk == null) {
            throw new ExecutionException(JavaDebuggerLocalize.errorJdkNotSpecified().get());
        }
        JavaSdkVersion version = JavaSdkTypeUtil.getVersion(jdk);
        String versionString = jdk.getVersionString();
        if (version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1) {
            throw new ExecutionException(JavaDebuggerLocalize.errorUnsupportedJdkVersion(versionString).get());
        }
        if (Platform.current().os().isWindows() && version == JavaSdkVersion.JDK_1_2) {
            VirtualFile homeDirectory = jdk.getHomeDirectory();
            if (homeDirectory == null || !homeDirectory.isValid()) {
                throw new ExecutionException(JavaDebuggerLocalize.errorInvalidJdkHome(versionString).get());
            }
            //noinspection HardCodedStringLiteral
            File dllFile = new File(
                homeDirectory.getPath().replace('/', File.separatorChar) +
                    File.separator + "bin" + File.separator + "jdwp.dll"
            );
            if (!dllFile.exists()) {
                GetJPDADialog dialog = new GetJPDADialog();
                dialog.show();
                throw new ExecutionException(JavaDebuggerLocalize.errorDebugLibrariesMissing().get());
            }
        }
    }
}
