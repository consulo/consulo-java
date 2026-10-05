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
package consulo.java.profiler.jfr;

import com.microsoft.jfr.FlightRecorderConnection;
import com.microsoft.jfr.JfrStreamingException;
import com.microsoft.jfr.Recording;
import com.microsoft.jfr.RecordingConfiguration;
import com.microsoft.jfr.RecordingOptions;
import com.microsoft.jfr.dcmd.FlightRecorderDiagnosticCommandConnection;
import org.jspecify.annotations.Nullable;

import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Controls the Flight Recorder of a JVM over JMX: through its {@code FlightRecorderMXBean}, or through its
 * {@code DiagnosticCommand} MBean when the JVM has no such bean (JDK 8).
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JfrRecorder {
    private static final String FLIGHT_RECORDER = "jdk.management.jfr:type=FlightRecorder";

    private final MBeanServerConnection myServer;
    private final FlightRecorderConnection myConnection;

    private JfrRecorder(MBeanServerConnection server, FlightRecorderConnection connection) {
        myServer = server;
        myConnection = connection;
    }

    /**
     * @return the recorder of the JVM, or null when the JVM has no Flight Recorder
     */
    public static @Nullable JfrRecorder connect(MBeanServerConnection server) throws IOException {
        try {
            return new JfrRecorder(server, FlightRecorderConnection.connect(server));
        }
        catch (InstanceNotFoundException e) {
            try {
                return new JfrRecorder(server, FlightRecorderDiagnosticCommandConnection.connect(server));
            }
            catch (InstanceNotFoundException | JfrStreamingException | UnsupportedOperationException fallback) {
                return null;
            }
        }
        catch (JfrStreamingException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Starts a recording with a predefined configuration of the JVM, such as {@code profile}, and event settings on
     * top of it, such as {@code jdk.ExecutionSample#period=10 ms}.
     *
     * @param dumpOnExit where the JVM writes the recording when it exits before the recording is stopped, or null
     * @throws IOException also when the JVM does not have the predefined configuration to apply the settings to
     */
    public JfrRecording start(String name, String predefinedConfiguration, Map<String, String> settings, @Nullable Path dumpOnExit)
        throws IOException {
        RecordingOptions.Builder options = new RecordingOptions.Builder().name(name).disk("true");
        if (dumpOnExit != null) {
            options.dumpOnExit("true").destination(dumpOnExit.toAbsolutePath().toString());
        }

        Recording recording = myConnection.newRecording(options.build(), configuration(predefinedConfiguration, settings));
        try {
            recording.start();
        }
        catch (JfrStreamingException e) {
            throw new IOException(e.getMessage(), e);
        }
        return new JfrRecording(recording);
    }

    /**
     * Copies what a running recording has recorded so far into the file, through a stopped clone of it, the way a JFR
     * dump of a running recording works. The recording itself keeps running.
     *
     * @return false when the JVM has no recording with that name
     */
    public boolean copyRunning(String name, Path file) throws IOException {
        long id = findRecordingId(name);
        if (id < 0) {
            return false;
        }

        try {
            long clone = myConnection.cloneRecording(id, true);
            try (InputStream stream = myConnection.getStream(clone, null, null, 0)) {
                Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            }
            finally {
                myConnection.closeRecording(clone);
            }
        }
        catch (JfrStreamingException e) {
            throw new IOException(e.getMessage(), e);
        }
        return true;
    }

    private RecordingConfiguration configuration(String predefinedConfiguration, Map<String, String> settings) throws IOException {
        if (settings.isEmpty()) {
            return new RecordingConfiguration.PredefinedConfiguration(predefinedConfiguration);
        }

        Map<String, String> merged = readPredefinedSettings(predefinedConfiguration);
        merged.putAll(settings);
        return new RecordingConfiguration.MapConfiguration(merged);
    }

    private Map<String, String> readPredefinedSettings(String predefinedConfiguration) throws IOException {
        Object configurations = attribute("Configurations");
        if (configurations instanceof CompositeData[] infos) {
            for (CompositeData info : infos) {
                if (predefinedConfiguration.equals(info.get("name")) && info.get("settings") instanceof TabularData table) {
                    Map<String, String> settings = new HashMap<>();
                    for (Object row : table.values()) {
                        if (row instanceof CompositeData entry) {
                            settings.put(String.valueOf(entry.get("key")), String.valueOf(entry.get("value")));
                        }
                    }
                    return settings;
                }
            }
        }
        throw new IOException("The JVM has no '" + predefinedConfiguration + "' Flight Recorder configuration to adjust");
    }

    private long findRecordingId(String name) throws IOException {
        Object recordings = attribute("Recordings");
        if (recordings instanceof CompositeData[] infos) {
            for (CompositeData info : infos) {
                if (name.equals(info.get("name")) && info.get("id") instanceof Long id) {
                    return id;
                }
            }
        }
        return -1;
    }

    private @Nullable Object attribute(String attribute) throws IOException {
        try {
            return myServer.getAttribute(new ObjectName(FLIGHT_RECORDER), attribute);
        }
        catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        catch (JMException e) {
            throw new IOException("Can't read the Flight Recorder " + attribute + ": " + e.getMessage(), e);
        }
    }
}
