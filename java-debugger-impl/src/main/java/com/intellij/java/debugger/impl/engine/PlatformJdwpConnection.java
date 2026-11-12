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

import consulo.internal.com.sun.jdi.connect.spi.ClosedConnectionException;
import consulo.internal.com.sun.jdi.connect.spi.Connection;
import consulo.platform.PlatformServerSocket;
import consulo.platform.PlatformSocket;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A JDWP connection over a socket of a platform - the debuggee connects to a port of its machine (the local one, or another
 * platform, where the port is bound through the platform), the IDE reads and writes the packets through the platform.
 *
 * @author VISTALL
 * @since 2026-11-12
 */
final class PlatformJdwpConnection extends Connection {
    private static final byte[] HANDSHAKE = "JDWP-Handshake".getBytes(StandardCharsets.US_ASCII);

    private final PlatformSocket mySocket;
    private final DataInputStream myInput;
    private final OutputStream myOutput;
    private final Object myReadLock = new Object();
    private final Object myWriteLock = new Object();
    private volatile boolean myOpen = true;

    private PlatformJdwpConnection(PlatformSocket socket) throws IOException {
        mySocket = socket;
        myInput = new DataInputStream(socket.getInputStream());
        myOutput = socket.getOutputStream();
    }

    /**
     * Waits for the debuggee to connect, and shakes hands with it.
     */
    static PlatformJdwpConnection accept(PlatformServerSocket serverSocket) throws IOException {
        PlatformSocket socket = serverSocket.accept();
        try {
            PlatformJdwpConnection connection = new PlatformJdwpConnection(socket);
            connection.handshake();
            return connection;
        }
        catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * The debugger greets first, the debuggee answers the same - whichever side listens.
     */
    private void handshake() throws IOException {
        myOutput.write(HANDSHAKE);
        myOutput.flush();
        byte[] received = new byte[HANDSHAKE.length];
        myInput.readFully(received);
        if (!Arrays.equals(received, HANDSHAKE)) {
            throw new IOException("Not a JDWP handshake: " + new String(received, StandardCharsets.US_ASCII));
        }
    }

    @Override
    public byte[] readPacket() throws IOException {
        if (!myOpen) {
            throw new ClosedConnectionException("Connection is closed");
        }
        synchronized (myReadLock) {
            int length;
            try {
                length = myInput.readInt();
            }
            catch (EOFException e) {
                // the debuggee is gone - an empty packet tells JDI so
                return new byte[0];
            }
            if (length < 11) {
                throw new IOException("Invalid JDWP packet length: " + length);
            }
            byte[] packet = new byte[length];
            packet[0] = (byte) (length >>> 24);
            packet[1] = (byte) (length >>> 16);
            packet[2] = (byte) (length >>> 8);
            packet[3] = (byte) length;
            myInput.readFully(packet, 4, length - 4);
            return packet;
        }
    }

    @Override
    public void writePacket(byte[] packet) throws IOException {
        if (!myOpen) {
            throw new ClosedConnectionException("Connection is closed");
        }
        if (packet.length < 11) {
            throw new IllegalArgumentException("Invalid JDWP packet length: " + packet.length);
        }
        synchronized (myWriteLock) {
            myOutput.write(packet);
            myOutput.flush();
        }
    }

    @Override
    public void close() throws IOException {
        myOpen = false;
        mySocket.close();
    }

    @Override
    public boolean isOpen() {
        return myOpen;
    }
}
