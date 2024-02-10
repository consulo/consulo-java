/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.language.impl.codeInsight;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.impl.parser.JavaParser;
import com.intellij.java.language.impl.parser.JavaParserUtil;
import com.intellij.java.language.impl.psi.impl.source.JavaDummyElement;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.application.util.LowMemoryWatcher;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.ast.CharTableImpl;
import consulo.language.impl.psi.DummyHolder;
import consulo.language.impl.psi.DummyHolderFactory;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.parser.PsiBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ConcurrentMostlySingularMultiMap;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MostlySingularMultiMap;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;
import consulo.util.io.CharSequenceReader;
import consulo.util.lang.Pair;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import jakarta.annotation.Nonnull;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static consulo.util.lang.Pair.pair;

public abstract class BaseExternalAnnotationsManager extends ExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance(BaseExternalAnnotationsManager.class);
  private static final Key<Boolean> EXTERNAL_ANNO_MARKER = Key.create("EXTERNAL_ANNO_MARKER");
  private static final List<PsiFile> NULL_LIST = Collections.emptyList();

  protected final PsiManager myPsiManager;

  private final ConcurrentMap<VirtualFile, List<PsiFile>> myExternalAnnotations = ContainerUtil.createConcurrentSoftValueMap();
  private final Map<AnnotationData, AnnotationData> myAnnotationDataCache = ContainerUtil.createWeakKeyWeakValueMap();
  private final ConcurrentMap<PsiFile, Pair<MostlySingularMultiMap<String, AnnotationData>, Long>> myAnnotationFileToDataAndModStamp = ContainerUtil.createConcurrentSoftMap();

  public BaseExternalAnnotationsManager(@Nonnull PsiManager psiManager) {
    myPsiManager = psiManager;
    LowMemoryWatcher.register(new Runnable() {
      @Override
      public void run() {
        dropCache();
      }
    }, psiManager.getProject());
  }

  @Nullable
  protected static String getExternalName(@Nonnull PsiModifierListOwner listOwner, boolean showParamName) {
    return PsiFormatUtil.getExternalName(listOwner, showParamName, Integer.MAX_VALUE);
  }

  @Nonnull
  static PsiModifierListOwner preferCompiledElement(@Nonnull PsiModifierListOwner element) {
    PsiElement original = element.getOriginalElement();
    return original instanceof PsiModifierListOwner ? (PsiModifierListOwner) original : element;
  }

  protected abstract boolean hasAnyAnnotationsRoots();

  @Override
  public boolean isExternalAnnotation(@Nonnull PsiAnnotation annotation) {
    return annotation.getUserData(EXTERNAL_ANNO_MARKER) != null;
  }

  @Override
  @Nullable
  public PsiAnnotation findExternalAnnotation(@Nonnull final PsiModifierListOwner listOwner, @Nonnull final String annotationFQN) {
    List<AnnotationData> list = collectExternalAnnotations(listOwner);
    AnnotationData data = findByFQN(list, annotationFQN);
    return data == null ? null : data.getAnnotation(this);
  }

  @Override
  public boolean isExternalAnnotationWritable(@Nonnull PsiModifierListOwner listOwner, @Nonnull final String annotationFQN) {
    // note that this method doesn't cache it's result
    List<AnnotationData> map = doCollect(listOwner, true);
    return findByFQN(map, annotationFQN) != null;
  }

  private static AnnotationData findByFQN(@Nonnull List<AnnotationData> map, @Nonnull final String annotationFQN) {
    return ContainerUtil.find(map, new Condition<AnnotationData>() {
      @Override
      public boolean value(AnnotationData data) {
        return data.myFqName.equals(annotationFQN);
      }
    });
  }

  @Override
  @Nullable
  public PsiAnnotation[] findExternalAnnotations(@Nonnull final PsiModifierListOwner listOwner) {
    final List<AnnotationData> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : ContainerUtil.map2Array(result, PsiAnnotation.EMPTY_ARRAY, new Function<AnnotationData, PsiAnnotation>() {
      @Override
      public PsiAnnotation apply(AnnotationData data) {
        return data.getAnnotation(BaseExternalAnnotationsManager.this);
      }
    });
  }

  private static final List<AnnotationData> NO_DATA = new ArrayList<AnnotationData>(1);
  private final ConcurrentMostlySingularMultiMap<PsiModifierListOwner, AnnotationData> cache = new ConcurrentMostlySingularMultiMap<PsiModifierListOwner, AnnotationData>();

  // interner for storing annotation FQN
  private final CharTableImpl charTable = new CharTableImpl();

  @Nonnull
  private List<AnnotationData> collectExternalAnnotations(@Nonnull PsiModifierListOwner listOwner) {
    if (!hasAnyAnnotationsRoots()) {
      return Collections.emptyList();
    }

    List<AnnotationData> cached;
    while (true) {
      cached = (List<AnnotationData>) cache.get(listOwner);
      if (cached == NO_DATA || !cached.isEmpty()) {
        return cached;
      }
      List<AnnotationData> computed = doCollect(listOwner, false);
      if (cache.replace(listOwner, cached, computed)) {
        cached = computed;
        break;
      }
    }
    return cached;
  }

  @Nonnull
  private AnnotationData internAnnotationData(@Nonnull AnnotationData data) {
    synchronized (myAnnotationDataCache) {
      AnnotationData interned = myAnnotationDataCache.get(data);
      if (interned == null) {
        myAnnotationDataCache.put(data, data);
        interned = data;
      }
      return interned;
    }
  }

  @Nonnull
  private MostlySingularMultiMap<String, AnnotationData> getDataFromFile(@Nonnull PsiFile file) {
    Pair<MostlySingularMultiMap<String, AnnotationData>, Long> cached = myAnnotationFileToDataAndModStamp.get(file);
    long fileModificationStamp = file.getModificationStamp();
    if (cached != null && cached.getSecond() == fileModificationStamp) {
      return cached.getFirst();
    }

    DataParsingSaxHandler handler = new DataParsingSaxHandler(file);
    try {
      SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
      saxParser.parse(new InputSource(new CharSequenceReader(escapeAttributes(file.getViewProvider().getContents()))), handler);
    } catch (IOException e) {
      LOG.error(e);
    } catch (ParserConfigurationException e) {
      LOG.error(e);
    } catch (SAXException e) {
      LOG.error(e);
    }

    MostlySingularMultiMap<String, AnnotationData> result = handler.getResult();
    myAnnotationFileToDataAndModStamp.put(file, pair(result, fileModificationStamp));
    return result;
  }

  protected void duplicateError(@Nonnull PsiFile file, @Nonnull String externalName, @Nonnull String text) {
    LOG.error(text + "; for signature: '" + externalName + "' in the " + file.getVirtualFile());
  }

  @Nonnull
  private String intern(@Nonnull String annotationFQN) {
    synchronized (charTable) {
      return charTable.doIntern(annotationFQN).toString();
    }
  }

  @Nonnull
  private List<AnnotationData> doCollect(@Nonnull PsiModifierListOwner listOwner, boolean onlyWritable) {
    String externalName = getExternalName(listOwner, false);
    if (externalName == null) {
      return NO_DATA;
    }

    List<PsiFile> files = findExternalAnnotationsFiles(listOwner);
    if (files == null) {
      return NO_DATA;
    }

    SmartList<AnnotationData> result = new SmartList<AnnotationData>();
    for (PsiFile file : files) {
      if (!file.isValid()) {
        continue;
      }
      if (onlyWritable && !file.isWritable()) {
        continue;
      }

      MostlySingularMultiMap<String, AnnotationData> fileData = getDataFromFile(file);
      ContainerUtil.addAll(result, fileData.get(externalName));
    }
    if (result.isEmpty()) {
      return NO_DATA;
    }

    result.trimToSize();
    return result;
  }

  @Nonnull
  @Override
  public AnnotationPlace chooseAnnotationsPlaceNoUi(@Nonnull PsiElement element) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public List<PsiFile> findExternalAnnotationsFiles(@Nonnull PsiModifierListOwner listOwner) {
    final PsiFile containingFile = preferCompiledElement(listOwner).getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      return null;
    }

    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }

    final List<PsiFile> files = myExternalAnnotations.get(virtualFile);
    if (files == NULL_LIST) {
      return null;
    }

    if (files != null) {
      boolean allValid = true;
      for (PsiFile file : files) {
        if (!file.isValid()) {
          allValid = false;
          break;
        }
      }
      if (allValid) {
        return files;
      }
    }

    Set<PsiFile> possibleAnnotations = new HashSet<PsiFile>();
    String relativePath = ((PsiJavaFile) containingFile).getPackageName().replace('.', '/') + '/' + ANNOTATIONS_XML;
    for (VirtualFile root : getExternalAnnotationsRoots(virtualFile)) {
      VirtualFile ext = root.findFileByRelativePath(relativePath);
      if (ext != null && ext.isValid()) {
        PsiFile psiFile = myPsiManager.findFile(ext);
        if (psiFile != null) {
          possibleAnnotations.add(psiFile);
        }
      }
    }

    if (possibleAnnotations.isEmpty()) {
      myExternalAnnotations.put(virtualFile, NULL_LIST);
      return null;
    }

    List<PsiFile> result = new SmartList<PsiFile>(possibleAnnotations);
    // writable go first
    Collections.sort(result, new Comparator<PsiFile>() {
      @Override
      public int compare(PsiFile f1, PsiFile f2) {
        boolean w1 = f1.isWritable();
        boolean w2 = f2.isWritable();
        return w1 == w2 ? 0 : w1 ? -1 : 1;
      }
    });
    myExternalAnnotations.put(virtualFile, result);
    return result;
  }

  @Nonnull
  protected abstract List<VirtualFile> getExternalAnnotationsRoots(@Nonnull VirtualFile libraryFile);

  protected void dropCache() {
    myExternalAnnotations.clear();
    myAnnotationFileToDataAndModStamp.clear();
    cache.clear();
  }

  // This method is used for legacy reasons.
  // Old external annotations sometimes are bad XML: they have "<" and ">" characters in attributes values. To prevent SAX parser from
  // failing, we escape attributes values.
  @Nonnull
  private static CharSequence escapeAttributes(@Nonnull CharSequence invalidXml) {
    // We assume that XML has single- and double-quote characters only for attribute values, therefore we don't any complex parsing,
    // just have binary inAttribute state
    StringBuilder buf = new StringBuilder(invalidXml.length());
    boolean inAttribute = false;
    for (int i = 0; i < invalidXml.length(); i++) {
      char c = invalidXml.charAt(i);
      if (inAttribute && c == '<') {
        buf.append("&lt;");
      } else if (inAttribute && c == '>') {
        buf.append("&gt;");
      } else if (c == '\"' || c == '\'') {
        buf.append('\"');
        inAttribute = !inAttribute;
      } else {
        buf.append(c);
      }
    }
    return buf;
  }

  @Override
  public void annotateExternally(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQName, @Nonnull PsiFile fromFile, @Nullable PsiNameValuePair[] value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deannotate(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean editExternalAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN, @Nullable PsiNameValuePair[] value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AnnotationPlace chooseAnnotationsPlace(@Nonnull PsiElement element) {
    throw new UnsupportedOperationException();
  }

  protected void cacheExternalAnnotations(@SuppressWarnings("UnusedParameters") @Nonnull String packageName, @Nonnull PsiFile fromFile, @Nonnull List<PsiFile> annotationFiles) {
    VirtualFile virtualFile = fromFile.getVirtualFile();
    if (virtualFile != null) {
      myExternalAnnotations.put(virtualFile, annotationFiles);
    }
  }

  private static class AnnotationData {
    private final String myFqName;
    private final String myParameters;

    private volatile PsiAnnotation myAnnotation;

    private AnnotationData(@Nonnull String fqn, @Nonnull String parameters) {
      myFqName = fqn;
      myParameters = parameters;
    }

    @Nonnull
    private PsiAnnotation getAnnotation(@Nonnull BaseExternalAnnotationsManager context) {
      PsiAnnotation a = myAnnotation;
      if (a == null) {
        String text = "@" + myFqName + (myParameters.isEmpty() ? "" : "(" + myParameters + ")");
        myAnnotation = a = markAsExternalAnnotation(context.createAnnotationFromText(text));
      }
      return a;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      AnnotationData data = (AnnotationData) o;

      return myFqName.equals(data.myFqName) && myParameters.equals(data.myParameters);
    }

    @Override
    public int hashCode() {
      int result = myFqName.hashCode();
      result = 31 * result + myParameters.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return myFqName + "(" + myParameters + ")";
    }
  }

  private static PsiAnnotation markAsExternalAnnotation(@Nonnull PsiAnnotation annotation) {
    annotation.putUserData(EXTERNAL_ANNO_MARKER, Boolean.TRUE);
    ((LightVirtualFile) annotation.getContainingFile().getViewProvider().getVirtualFile()).markReadOnly();
    return annotation;
  }

  @Nonnull
  private PsiAnnotation createAnnotationFromText(@Nonnull final String text) throws IncorrectOperationException {
    // synchronize during interning in charTable
    synchronized (charTable) {
      DummyHolder holder = DummyHolderFactory.createHolder(myPsiManager, new JavaDummyElement(text, ANNOTATION, LanguageLevel.HIGHEST), null, charTable);
      PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
      if (!(element instanceof PsiAnnotation)) {
        throw new IncorrectOperationException("Incorrect annotation \"" + text + "\".");
      }
      return markAsExternalAnnotation((PsiAnnotation) element);
    }
  }

  private static final JavaParserUtil.ParserWrapper ANNOTATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);
    }
  };

  private class DataParsingSaxHandler extends DefaultHandler {
    private final MostlySingularMultiMap<String, AnnotationData> myData = new MostlySingularMultiMap<String, AnnotationData>();
    private final PsiFile myFile;

    private String myExternalName = null;
    private String myAnnotationFqn = null;
    private StringBuilder myArguments = null;

    private DataParsingSaxHandler(PsiFile file) {
      myFile = file;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if ("item".equals(qName)) {
        myExternalName = attributes.getValue("name");
      } else if ("annotation".equals(qName)) {
        myAnnotationFqn = attributes.getValue("name");
        myArguments = new StringBuilder();
      } else if ("val".equals(qName)) {
        if (myArguments.length() != 0) {
          myArguments.append(",");
        }
        String name = attributes.getValue("name");
        if (name != null) {
          myArguments.append(name);
          myArguments.append("=");
        }
        myArguments.append(attributes.getValue("val"));
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if ("item".equals(qName)) {
        myExternalName = null;
      } else if ("annotation".equals(qName) && myExternalName != null && myAnnotationFqn != null) {
        String argumentsString = myArguments.length() == 0 ? "" : intern(myArguments.toString());
        for (AnnotationData existingData : myData.get(myExternalName)) {
          if (existingData.myFqName.equals(myAnnotationFqn)) {
            duplicateError(myFile, myExternalName, "Duplicate annotation '" + myAnnotationFqn + "'");
          }
        }

        AnnotationData data = new AnnotationData(myAnnotationFqn, argumentsString);
        myData.add(myExternalName, internAnnotationData(data));

        myAnnotationFqn = null;
        myArguments = null;
      }
    }

    public MostlySingularMultiMap<String, AnnotationData> getResult() {
      if (myData.isEmpty()) {
        return MostlySingularMultiMap.emptyMap();
      }
      myData.compact();
      return myData;
    }
  }
}
