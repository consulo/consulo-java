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
package com.intellij.java.impl.openapi.vfs.impl.jrt;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A reader of the jimage of a modular runtime - its {@code lib/modules} - through the {@link SeekableByteChannel} of a
 * {@link Path}, of whatever machine.
 * <p>
 * The index (header, tables, locations, strings) is read once; a resource is one read of its bytes.
 * <p>
 * The layout of the file:
 * <pre>
 * header      magic 0xCAFEDADA, version (major &lt;&lt; 16 | minor), flags, resource count, table length,
 *             locations size, strings size - ints in the byte order of the image (the one of its magic)
 * redirect    table length ints (the perfect hash of the names - not needed to list all resources)
 * offsets     table length ints: the offset of each location in the locations
 * locations   attributes of the resources: a byte (kind &lt;&lt; 3 | value length - 1), then the value, big endian;
 *             a location ends with the kind 0
 * strings     NUL terminated UTF-8 names, referenced by their offsets
 * resources   from the end of the index, at the offset of each location
 * </pre>
 * A compressed resource starts with a header (magic 0xCAFEFAFA, compressed size, uncompressed size, the decompressor name, the
 * content offset, is terminal) - possibly several of them, applied from the outermost.
 *
 * @author VISTALL
 * @since 2026-11-12
 */
final class JImageFile implements Closeable {
    private static final int IMAGE_MAGIC = 0xCAFEDADA;
    private static final int MAJOR_VERSION = 1;
    private static final int HEADER_SIZE = 7 * 4;

    private static final int RESOURCE_MAGIC = 0xCAFEFAFA;
    private static final int RESOURCE_HEADER_SIZE = 4 + 8 + 8 + 4 + 4 + 1;

    private static final int ATTRIBUTE_END = 0;
    private static final int ATTRIBUTE_MODULE = 1;
    private static final int ATTRIBUTE_PARENT = 2;
    private static final int ATTRIBUTE_BASE = 3;
    private static final int ATTRIBUTE_EXTENSION = 4;
    private static final int ATTRIBUTE_OFFSET = 5;
    private static final int ATTRIBUTE_COMPRESSED = 6;
    private static final int ATTRIBUTE_UNCOMPRESSED = 7;
    private static final int ATTRIBUTE_COUNT = 8;

    /**
     * A resource: its offset from the end of the index, and its sizes - compressed 0 for a stored one.
     */
    record Resource(long offset, long compressedSize, long uncompressedSize) {
    }

    private final Path myModules;
    private final SeekableByteChannel myChannel;
    private final ByteOrder myByteOrder;
    private final long myIndexSize;
    /**
     * {@code <module>/<path>} of every resource of the modules
     */
    private final Map<String, Resource> myResources;
    private final ByteBuffer myStrings;

    JImageFile(Path modules) throws IOException {
        myModules = modules;
        myChannel = Files.newByteChannel(modules, StandardOpenOption.READ);
        try {
            ByteBuffer header = read(0, HEADER_SIZE);
            ByteOrder order = ByteOrder.nativeOrder();
            if (header.order(order).getInt(0) != IMAGE_MAGIC) {
                order = order == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                if (header.order(order).getInt(0) != IMAGE_MAGIC) {
                    throw new IOException("Not a jimage: " + modules);
                }
            }
            myByteOrder = order;
            header.order(order);

            int version = header.getInt(4);
            if (version >>> 16 != MAJOR_VERSION) {
                throw new IOException("Unsupported jimage version " + (version >>> 16) + "." + (version & 0xFFFF) + ": " + modules);
            }
            int tableLength = header.getInt(16);
            int locationsSize = header.getInt(20);
            int stringsSize = header.getInt(24);

            long offsetsStart = HEADER_SIZE + (long) tableLength * 4;
            long locationsStart = offsetsStart + (long) tableLength * 4;
            long stringsStart = locationsStart + locationsSize;
            myIndexSize = stringsStart + stringsSize;

            ByteBuffer index = read(offsetsStart, (int) (myIndexSize - offsetsStart)).order(order);
            ByteBuffer offsets = index.slice(0, tableLength * 4).order(order);
            ByteBuffer locations = index.slice(tableLength * 4, locationsSize);
            myStrings = index.slice(tableLength * 4 + locationsSize, stringsSize);

            myResources = new LinkedHashMap<>(tableLength * 2);
            long[] attributes = new long[ATTRIBUTE_COUNT];
            for (int i = 0; i < tableLength; i++) {
                readAttributes(locations, offsets.getInt(i * 4), attributes);
                String module = getString((int) attributes[ATTRIBUTE_MODULE]);
                if (module.isEmpty() || module.equals("modules") || module.equals("packages")) {
                    // the directories of the jrt file system, not resources of the modules
                    continue;
                }
                String parent = getString((int) attributes[ATTRIBUTE_PARENT]);
                String base = getString((int) attributes[ATTRIBUTE_BASE]);
                String extension = getString((int) attributes[ATTRIBUTE_EXTENSION]);

                StringBuilder name = new StringBuilder(module.length() + parent.length() + base.length() + extension.length() + 3);
                name.append(module).append('/');
                if (!parent.isEmpty()) {
                    name.append(parent).append('/');
                }
                name.append(base);
                if (!extension.isEmpty()) {
                    name.append('.').append(extension);
                }
                myResources.put(
                    name.toString(),
                    new Resource(attributes[ATTRIBUTE_OFFSET], attributes[ATTRIBUTE_COMPRESSED], attributes[ATTRIBUTE_UNCOMPRESSED])
                );
            }
        }
        catch (IOException | RuntimeException e) {
            myChannel.close();
            throw e;
        }
    }

    /**
     * @return {@code <module>/<path>} to the resource
     */
    Map<String, Resource> getResources() {
        return myResources;
    }

    byte[] getContent(Resource resource) throws IOException {
        long start = myIndexSize + resource.offset();
        if (resource.compressedSize() == 0) {
            return toArray(read(start, (int) resource.uncompressedSize()));
        }
        byte[] content = toArray(read(start, (int) resource.compressedSize()));
        return decompress(content);
    }

    private byte[] decompress(byte[] content) throws IOException {
        byte[] result = content;
        while (result.length >= RESOURCE_HEADER_SIZE) {
            ByteBuffer header = ByteBuffer.wrap(result, 0, RESOURCE_HEADER_SIZE).order(myByteOrder);
            if (header.getInt(0) != RESOURCE_MAGIC) {
                break;
            }
            long compressedSize = header.getLong(4);
            long uncompressedSize = header.getLong(12);
            String decompressor = getString(header.getInt(20));
            if (!"zip".equals(decompressor)) {
                throw new IOException("Unsupported jimage compression: " + decompressor);
            }
            result = inflate(result, RESOURCE_HEADER_SIZE, (int) compressedSize, (int) uncompressedSize);
        }
        return result;
    }

    private static byte[] inflate(byte[] data, int offset, int length, int uncompressedSize) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data, offset, length);
            ByteArrayOutputStream stream = new ByteArrayOutputStream(uncompressedSize);
            byte[] buffer = new byte[Math.max(1024, Math.min(uncompressedSize, 64 * 1024))];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IOException("Truncated jimage resource");
                }
                stream.write(buffer, 0, count);
            }
            return stream.toByteArray();
        }
        catch (DataFormatException e) {
            throw new IOException(e);
        }
        finally {
            inflater.end();
        }
    }

    private static void readAttributes(ByteBuffer locations, int offset, long[] attributes) {
        Arrays.fill(attributes, 0);
        int position = offset;
        while (position < locations.limit()) {
            int data = locations.get(position++) & 0xFF;
            int kind = data >>> 3;
            if (kind == ATTRIBUTE_END) {
                break;
            }
            int length = (data & 0x7) + 1;
            long value = 0;
            for (int i = 0; i < length; i++) {
                value = (value << 8) | (locations.get(position++) & 0xFF);
            }
            if (kind < ATTRIBUTE_COUNT) {
                attributes[kind] = value;
            }
        }
    }

    private String getString(int offset) {
        int end = offset;
        while (end < myStrings.limit() && myStrings.get(end) != 0) {
            end++;
        }
        byte[] bytes = new byte[end - offset];
        myStrings.get(offset, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads of several threads go side by side: a file channel reads by position, a channel of another kind (a file of another
     * platform) is opened per read - a read of it is a request of its own anyway.
     */
    private ByteBuffer read(long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        if (myChannel instanceof FileChannel fileChannel) {
            while (buffer.hasRemaining()) {
                if (fileChannel.read(buffer, position + buffer.position()) < 0) {
                    throw new EOFException("jimage ends at " + (position + buffer.position()));
                }
            }
        }
        else {
            try (SeekableByteChannel channel = Files.newByteChannel(myModules, StandardOpenOption.READ)) {
                channel.position(position);
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) {
                        throw new EOFException("jimage ends at " + (position + buffer.position()));
                    }
                }
            }
        }
        return buffer.flip();
    }

    private static byte[] toArray(ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.array().length == buffer.remaining()) {
            return buffer.array();
        }
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    @Override
    public void close() throws IOException {
        myChannel.close();
    }
}
