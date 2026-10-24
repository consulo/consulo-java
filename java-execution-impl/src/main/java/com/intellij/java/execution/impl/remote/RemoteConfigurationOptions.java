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
package com.intellij.java.execution.impl.remote;

import com.dslplatform.json.CompiledJson;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-24
 */
@CompiledJson
public class RemoteConfigurationOptions extends RunConfigurationOptions {
    private boolean myUseSocketTransport = true;
    private boolean myServerMode;
    private @Nullable String myShmemAddress;
    private @Nullable String myHost = "localhost";
    private @Nullable String myPort = "5005";

    @Attribute("useSocketTransport")
    public boolean isUseSocketTransport() {
        return myUseSocketTransport;
    }

    public void setUseSocketTransport(boolean useSocketTransport) {
        myUseSocketTransport = useSocketTransport;
    }

    /**
     * {@code true} when the debugger listens for the JVM to connect, {@code false} when it attaches to the JVM.
     */
    @Attribute("serverMode")
    public boolean isServerMode() {
        return myServerMode;
    }

    public void setServerMode(boolean serverMode) {
        myServerMode = serverMode;
    }

    @Attribute("shmemAddress")
    public @Nullable String getShmemAddress() {
        return myShmemAddress;
    }

    public void setShmemAddress(@Nullable String shmemAddress) {
        myShmemAddress = StringUtil.nullize(shmemAddress, true);
    }

    @Attribute("host")
    public @Nullable String getHost() {
        return myHost;
    }

    public void setHost(@Nullable String host) {
        myHost = StringUtil.nullize(host, true);
    }

    @Attribute("port")
    public @Nullable String getPort() {
        return myPort;
    }

    public void setPort(@Nullable String port) {
        myPort = StringUtil.nullize(port, true);
    }
}
