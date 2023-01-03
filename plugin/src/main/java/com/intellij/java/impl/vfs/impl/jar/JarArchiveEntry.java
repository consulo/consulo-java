package com.intellij.java.impl.vfs.impl.jar;

import consulo.virtualFileSystem.archive.ArchiveEntry;

import java.util.zip.ZipEntry;

/**
 * @author VISTALL
 * @since 07/12/2022
 */
public class JarArchiveEntry implements ArchiveEntry {
  private final ZipEntry myEntry;

  public JarArchiveEntry(ZipEntry entry) {
    myEntry = entry;
  }

  public ZipEntry getEntry() {
    return myEntry;
  }

  @Override
  public String getName() {
    return myEntry.getName();
  }

  @Override
  public long getSize() {
    return myEntry.getSize();
  }

  @Override
  public long getTime() {
    return myEntry.getTime();
  }

  @Override
  public boolean isDirectory() {
    return myEntry.isDirectory();
  }
}
