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
package consulo.java.compiler.impl.javaCompiler;

import consulo.platform.PlatformServerSocket;
import consulo.platform.PlatformSocket;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.IOException;

/**
 * A thrift server transport over a port of a platform - of the local machine, or of another one, bound through its agent.
 *
 * @author VISTALL
 * @since 2026-11-12
 */
public class PlatformServerTransport extends TServerTransport {
    private final PlatformServerSocket myServerSocket;

    public PlatformServerTransport(PlatformServerSocket serverSocket) {
        myServerSocket = serverSocket;
    }

    @Override
    public void listen() {
        // bound already
    }

    @Override
    public TTransport accept() throws TTransportException {
        PlatformSocket socket;
        try {
            socket = myServerSocket.accept();
        }
        catch (IOException e) {
            throw new TTransportException(TTransportException.NOT_OPEN, e);
        }
        try {
            return new TIOStreamTransport(socket.getInputStream(), socket.getOutputStream()) {
                @Override
                public void close() {
                    super.close();
                    try {
                        socket.close();
                    }
                    catch (IOException ignored) {
                    }
                }
            };
        }
        catch (IOException e) {
            throw new TTransportException(TTransportException.NOT_OPEN, e);
        }
    }

    /**
     * The server stops - an accept waiting for a connection ends.
     */
    @Override
    public void interrupt() {
        close();
    }

    @Override
    public void close() {
        try {
            myServerSocket.close();
        }
        catch (IOException ignored) {
        }
    }
}
