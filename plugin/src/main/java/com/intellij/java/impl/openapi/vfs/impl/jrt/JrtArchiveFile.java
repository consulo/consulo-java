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

import consulo.virtualFileSystem.archive.ArchiveEntry;
import consulo.virtualFileSystem.archive.ArchiveFile;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * The modules of a modular runtime, as an archive: its home is the archive, the entries are {@code <module>/<path>}.
 * The jimage ({@code lib/modules}) is read through the {@link Path} of the home - of the local machine, or of another platform.
 *
 * @author VISTALL
 * @since 2026-11-12
 */
public class JrtArchiveFile implements ArchiveFile {
    private record Entry(String name, JImageFile.Resource resource) implements ArchiveEntry {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getSize() {
            return resource.uncompressedSize();
        }

        @Override
        public long getTime() {
            return -1;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }
    }

    private final Path myHome;
    private final JImageFile myImage;

    public JrtArchiveFile(Path home) throws IOException {
        myHome = home;
        myImage = new JImageFile(home.resolve("lib").resolve("modules"));
    }

    @Override
    public String getName() {
        return myHome.toString();
    }

    @Override
    public @Nullable ArchiveEntry getEntry(String name) {
        JImageFile.Resource resource = myImage.getResources().get(name);
        return resource == null ? null : new Entry(name, resource);
    }

    @Override
    public InputStream getInputStream(ArchiveEntry entry) throws IOException {
        return new ByteArrayInputStream(myImage.getContent(((Entry) entry).resource()));
    }

    @Override
    public Iterator<? extends ArchiveEntry> entries() {
        Iterator<Map.Entry<String, JImageFile.Resource>> iterator = myImage.getResources().entrySet().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ArchiveEntry next() {
                Map.Entry<String, JImageFile.Resource> next = iterator.next();
                return new Entry(next.getKey(), next.getValue());
            }
        };
    }

    @Override
    public int getSize() {
        return myImage.getResources().size();
    }

    @Override
    public void close() throws IOException {
        myImage.close();
    }
}
