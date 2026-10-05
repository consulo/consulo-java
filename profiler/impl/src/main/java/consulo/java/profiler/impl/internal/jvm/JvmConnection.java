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
package consulo.java.profiler.impl.internal.jvm;

import consulo.java.profiler.jfr.JfrRecorder;
import consulo.logging.Logger;
import net.bytebuddy.agent.VirtualMachine;
import org.jspecify.annotations.Nullable;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A JMX connection to a local JVM, made by starting its local management agent through our own attach implementation.
 * Only {@code java.management} is used: the JVM's beans are read by object name, so no JDK-internal module of the IDE
 * runtime decides what the profiled JVM supports.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JvmConnection implements Closeable {
    public static final ObjectName MEMORY = objectName("java.lang:type=Memory");
    public static final ObjectName THREADING = objectName("java.lang:type=Threading");
    public static final ObjectName OPERATING_SYSTEM = objectName("java.lang:type=OperatingSystem");
    public static final ObjectName RUNTIME = objectName("java.lang:type=Runtime");
    public static final ObjectName GARBAGE_COLLECTORS = objectName("java.lang:type=GarbageCollector,*");

    private static final Logger LOG = Logger.getInstance(JvmConnection.class);

    private final JMXConnector myConnector;
    private final MBeanServerConnection myServer;
    private final List<ObjectName> myCollectors;
    private final @Nullable JfrRecorder myFlightRecorder;
    private final int myFeatureVersion;
    private final boolean myLinux;

    private JvmConnection(JMXConnector connector, MBeanServerConnection server) throws IOException {
        myConnector = connector;
        myServer = server;
        Set<ObjectName> collectors = server.queryNames(GARBAGE_COLLECTORS, null);
        myCollectors = List.copyOf(collectors);
        myFlightRecorder = findFlightRecorder(server);
        myFeatureVersion = parseFeatureVersion(stringAttribute(server, RUNTIME, "SpecVersion"));
        myLinux = stringAttribute(server, OPERATING_SYSTEM, "Name").toLowerCase(Locale.ROOT).contains("linux");
    }

    public static JvmConnection connect(int pid) throws IOException {
        VirtualMachine machine = VirtualMachine.ForHotSpot.attach(String.valueOf(pid));
        String address;
        try {
            address = machine.startLocalManagementAgent();
        }
        finally {
            machine.detach();
        }
        if (address == null) {
            throw new IOException("The JVM " + pid + " did not report its local management agent address");
        }

        JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(address));
        try {
            return new JvmConnection(connector, connector.getMBeanServerConnection());
        }
        catch (IOException | RuntimeException e) {
            connector.close();
            throw e;
        }
    }

    private static @Nullable JfrRecorder findFlightRecorder(MBeanServerConnection server) {
        try {
            JfrRecorder recorder = JfrRecorder.connect(server);
            if (recorder == null) {
                LOG.warn("The JVM has no Flight Recorder");
            }
            return recorder;
        }
        catch (IOException e) {
            LOG.warn("Can't connect to the Flight Recorder of the JVM", e);
            return null;
        }
    }

    public MBeanServerConnection getServer() {
        return myServer;
    }

    public List<ObjectName> getCollectors() {
        return myCollectors;
    }

    public @Nullable JfrRecorder getFlightRecorder() {
        return myFlightRecorder;
    }

    /**
     * @return the feature release of the JVM, such as 21, or 8 when the version can't be read
     */
    public int getFeatureVersion() {
        return myFeatureVersion;
    }

    public boolean isLinux() {
        return myLinux;
    }

    public Object getAttribute(ObjectName name, String attribute) throws IOException, JMException {
        return myServer.getAttribute(name, attribute);
    }

    @Override
    public void close() {
        try {
            myConnector.close();
        }
        catch (IOException e) {
            LOG.debug("Closing the JMX connection failed", e);
        }
    }

    private static String stringAttribute(MBeanServerConnection server, ObjectName name, String attribute) throws IOException {
        try {
            return String.valueOf(server.getAttribute(name, attribute));
        }
        catch (JMException e) {
            LOG.warn("Can't read " + name + "." + attribute, e);
            return "";
        }
    }

    static int parseFeatureVersion(String specVersion) {
        String feature = specVersion.startsWith("1.") ? specVersion.substring(2) : specVersion;
        int dot = feature.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? feature : feature.substring(0, dot));
        }
        catch (NumberFormatException e) {
            return 8;
        }
    }

    private static ObjectName objectName(String name) {
        try {
            return new ObjectName(name);
        }
        catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(name, e);
        }
    }
}
