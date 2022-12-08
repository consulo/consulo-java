package com.intellij.java.impl.vfs.impl.jar;

import consulo.virtualFileSystem.archive.ArchiveEntry;
import consulo.virtualFileSystem.archive.ArchiveFile;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * @author VISTALL
 * @since 07/12/2022
 */
public class JarArchiveFile implements ArchiveFile {
  private final JarFile myZipFile;

  public JarArchiveFile(@Nonnull String path) throws IOException {
    myZipFile = new JarFile(path);
  }

  @Nonnull
  @Override
  public String getName() {
    return myZipFile.getName();
  }

  @Override
  public ArchiveEntry getEntry(String name) {
    ZipEntry entry = myZipFile.getEntry(name);
    if (entry == null) return null;
    return new JarArchiveEntry(entry);
  }

  @Override
  public InputStream getInputStream(@Nonnull ArchiveEntry entry) throws IOException {
    return myZipFile.getInputStream(((JarArchiveEntry)entry).getEntry());
  }

  @Nonnull
  @Override
  public Iterator<? extends ArchiveEntry> entries() {
    final Enumeration<? extends ZipEntry> entries = myZipFile.entries();
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return entries.hasMoreElements();
      }

      @Override
      public ArchiveEntry next() {
        ZipEntry entry = entries.nextElement();
        if (entry == null) return null;
        return new JarArchiveEntry(entry);
      }
    };
  }

  @Override
  public int getSize() {
    return myZipFile.size();
  }

  @Override
  public void close() throws IOException {
    myZipFile.close();
  }
}