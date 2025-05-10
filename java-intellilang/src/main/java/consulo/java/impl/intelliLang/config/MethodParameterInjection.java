/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.config;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.java.impl.intelliLang.JavaLanguageInjectionSupport;
import consulo.language.inject.advanced.BaseInjection;
import consulo.language.inject.advanced.InjectionPlace;
import consulo.language.inject.advanced.pattern.PatternCompiler;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizableStringList;
import consulo.util.xml.serializer.JDOMExternalizer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class MethodParameterInjection extends BaseInjection {
  @Nonnull
  private String myClassName = "";

  @Nonnull
  private final Map<String, MethodInfo> myParameterMap = new HashMap<String, MethodInfo>();

  public MethodParameterInjection() {
    super(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
  }

  @Nonnull
  public String getClassName() {
    return myClassName;
  }

  public void setClassName(@Nonnull String className) {
    myClassName = className;
  }

  public void setMethodInfos(final Collection<MethodInfo> newInfos) {
    myParameterMap.clear();
    for (MethodInfo methodInfo : newInfos) {
      myParameterMap.put(methodInfo.getMethodSignature(), methodInfo);
    }
  }

  public Collection<MethodInfo> getMethodInfos() {
    return myParameterMap.values();
  }

  public MethodParameterInjection copyFrom(@Nonnull BaseInjection o) {
    super.copyFrom(o);
    if (o instanceof MethodParameterInjection) {
      final MethodParameterInjection other = (MethodParameterInjection)o;
      myClassName = other.getClassName();
      myParameterMap.clear();
      for (MethodInfo info : other.myParameterMap.values()) {
        myParameterMap.put(info.methodSignature, info.copy());
      }
    }
    return this;
  }

  protected void readExternalImpl(Element e) {
    if (e.getAttribute("injector-id") == null) {
      setClassName(JDOMExternalizer.readString(e, "CLASS"));
      //setApplyInHierarchy(JDOMExternalizer.readBoolean(e, "APPLY_IN_HIERARCHY"));
      readOldFormat(e);
      final Map<String, String> map = new HashMap<String, String>();
      JDOMExternalizer.readMap(e, map, null, "SIGNATURES");
      for (String s : map.keySet()) {
        final String fixedSignature = fixSignature(s, false);
        myParameterMap.put(fixedSignature, new MethodInfo(fixedSignature, map.get(s)));
      }
    }
  }

  private void readOldFormat(final Element e) {
    final JDOMExternalizableStringList list = new JDOMExternalizableStringList();
    try {
      list.readExternal(e);
    }
    catch (InvalidDataException e1) {
      // nothing
    }
    if (list.isEmpty()) return;
    final boolean[] selection = new boolean[list.size()];
    for (int i = 0; i < list.size(); i++) {
      selection[i] = Boolean.parseBoolean(list.get(i));
    }
    final String methodSignature = fixSignature(JDOMExternalizer.readString(e, "METHOD"), false);
    myParameterMap.put(methodSignature, new MethodInfo(methodSignature, selection, false));
  }


  @Override
  public MethodParameterInjection copy() {
    return new MethodParameterInjection().copyFrom(this);
  }

  @Override
  public void generatePlaces() {
    final PatternCompiler<PsiElement> compiler = getCompiler();
    List<String> patternString = getPatternString(this);
    InjectionPlace[] places = InjectionPlace.ARRAY_FACTORY.create(patternString.size());
    for (int i = 0, patternStringSize = patternString.size(); i < patternStringSize; i++) {
      String text = patternString.get(i);
      places[i] = new InjectionPlace(compiler.createElementPattern(text, getDisplayName()), true);
    }
    setInjectionPlaces(places);
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final MethodParameterInjection that = (MethodParameterInjection)o;

    if (!myClassName.equals(that.myClassName)) return false;
    if (!myParameterMap.equals(that.myParameterMap)) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myClassName.hashCode();
    result = 31 * result + myParameterMap.hashCode();
    return result;
  }

  @Nonnull
  public String getDisplayName() {
    final String className = getClassName();
    if (StringUtil.isEmpty(className)) return "<unnamed>";
    MethodInfo singleInfo = null;
    for (MethodInfo info : myParameterMap.values()) {
      if (info.isEnabled()) {
        if (singleInfo == null) {
          singleInfo = info;
        }
        else {
          singleInfo = null;
          break;
        }
      }
    }
    final String name = singleInfo != null
                        ? StringUtil.getShortName(className) + "." + singleInfo.methodName
                        : StringUtil.getShortName(className);
    return /*"["+getInjectedLanguageId()+"] " +*/ name + " ("+StringUtil.getPackageName(className)+")";
  }

  public static String fixSignature(final String signature, final boolean parameterNames) {
    @NonNls final StringBuilder sb = new StringBuilder();
    final StringTokenizer st = new StringTokenizer(signature, "(,)");
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 0; st.hasMoreTokens(); i++) {
      final String token = st.nextToken().trim();
      if (i > 1) sb.append(", ");
      final int idx;
      if (i == 0) {
        sb.append(token).append("(");
      }
      else if ((idx = token.indexOf(' ')) > -1) {
        if (parameterNames) {
          sb.append(token);
        }
        else {
          sb.append(token.substring(0, idx));
        }
      }
      else {
        sb.append(token);
        if (parameterNames) {
          sb.append(' ').append('p').append(i);
        }
      }
    }
    sb.append(")");
    return sb.toString();
  }

  @Nonnull
  private static String buildSignature(@Nonnull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                      PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES | PsiFormatUtil.SHOW_RAW_TYPE);
  }

  public static MethodInfo createMethodInfo(final PsiMethod method) {
    final String signature = buildSignature(method);
    return new MethodInfo(signature, new boolean[method.getParameterList().getParametersCount()], false);
  }

  public static boolean isInjectable(@Nullable final PsiType type, final Project project) {
    if (type == null) return false;
    if (type instanceof PsiPrimitiveType) return false;
    if (project.isDefault()) {
      @NonNls final String text = type.getPresentableText();
      if (text == null) return false;
      return text.equals(CommonClassNames.JAVA_LANG_STRING) || text.equals("java.lang.String...") || text.equals("java.lang.String[]");
    }
    else {
      return type.equalsToText(CommonClassNames.JAVA_LANG_STRING) || type.equalsToText("java.lang.String...") || type.equalsToText("java.lang.String[]");
    }
  }

  @Nullable
  public static PsiMethod makeMethod(final Project project, final String signature) {
    if (StringUtil.isEmpty(signature)) return null;
    try {
      return JavaPsiFacade.getInstance(project).getElementFactory().
        createMethodFromText("void " + fixSignature(signature, true) + "{}", null);
    }
    catch (IncorrectOperationException e) {
      // something wrong
    }
    return null;
  }

  public static String getParameterTypesString(final String signature) {
    @NonNls final StringBuilder sb = new StringBuilder();
    final StringTokenizer st = new StringTokenizer(signature, "(,)");
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 0; st.hasMoreTokens(); i++) {
      final String token = st.nextToken().trim();
      if (i > 1) sb.append(", ");
      final int idx;
      if (i == 0) {
        // nothing
      }
      else {
        sb.append('\"');
        if ((idx = token.indexOf(' ')) > -1) {
          sb.append(token.substring(0, idx));
        }
        else {
          sb.append(token);
        }
        sb.append('\"');
      }
    }
    return sb.toString();
  }

  public static String getPatternStringForJavaPlace(final String methodName, final String parametersStrings, final int parameterIndex, final String className) {
    final StringBuilder sb = new StringBuilder();
    if (parameterIndex >= 0) {
      sb.append("psiParameter().ofMethod(").append(parameterIndex).append(", ");
    }
    sb.append("psiMethod().withName(\"").append(methodName)
      .append("\").withParameters(").append(parametersStrings)
      .append(").definedInClass(\"").append(className).append("\")");
    if (parameterIndex >= 0) {
      sb.append(")");
    }
    return sb.toString();
  }

  public static class MethodInfo {
    @Nonnull
    final String methodSignature;
    @Nonnull
    final String methodName;
    @Nonnull
    final boolean[] paramFlags;

    boolean returnFlag;

    public MethodInfo(@Nonnull final String methodSignature, @Nonnull final boolean[] paramFlags, final boolean returnFlag) {
      this.methodSignature = methodSignature;
      this.paramFlags = paramFlags;
      this.returnFlag = returnFlag;
      methodName = calcMethodName(methodSignature);
    }

    public MethodInfo(@Nonnull final String methodSignature, @Nonnull final String paramFlags) {
      this.methodSignature = methodSignature;
      final Pair<boolean[], Boolean> flags = parseFlags(paramFlags);
      returnFlag = flags.second.booleanValue();
      this.paramFlags = flags.first;
      methodName = calcMethodName(methodSignature);
    }

    @Nonnull
    public String getMethodSignature() {
      return methodSignature;
    }

    @Nonnull
    public String getMethodName() {
      return methodName;
    }

    @Nonnull
    public boolean[] getParamFlags() {
      return paramFlags;
    }

    public boolean isReturnFlag() {
      return returnFlag;
    }

    public void setReturnFlag(final boolean returnFlag) {
      this.returnFlag = returnFlag;
    }

    public boolean isEnabled() {
      if (returnFlag) return true;
      for (boolean b : paramFlags) {
        if (b) return true;
      }
      return false;
    }

    private static Pair<boolean[], Boolean> parseFlags(final String string) {
      final int returnIdx = string.indexOf(':');
      boolean returnFlag = returnIdx != -1 && Boolean.parseBoolean(string.substring(0, returnIdx));
      final StringTokenizer st = new StringTokenizer(string.substring(returnIdx+1), ",");
      final boolean[] result = new boolean[st.countTokens()];
      for (int i = 0; i < result.length; i++) {
        result[i] = Boolean.parseBoolean(st.nextToken());
      }
      return Pair.create(result, returnFlag);
    }

    @NonNls
    private static String calcMethodName(final String methodSignature) {
      final String s = StringUtil.split(methodSignature, "(").get(0);
      return s.length() == 0 ? "<none>" : s;
    }

    public String getFlagsString() {
      final StringBuilder result = new StringBuilder();
      result.append(returnFlag).append(':');
      boolean first = true;
      for (boolean b : paramFlags) {
        if (first) first = false;
        else result.append(',');
        result.append(b);
      }
      return result.toString();
    }

    public MethodInfo copy() {
      return new MethodInfo(methodSignature, paramFlags.clone(), returnFlag);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MethodInfo that = (MethodInfo)o;

      if (returnFlag != that.returnFlag) return false;
      if (!methodName.equals(that.methodName)) return false;
      if (!methodSignature.equals(that.methodSignature)) return false;
      if (!Arrays.equals(paramFlags, that.paramFlags)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = methodSignature.hashCode();
      result = 31 * result + methodName.hashCode();
      result = 31 * result + Arrays.hashCode(paramFlags);
      result = 31 * result + (returnFlag ? 1 : 0);
      return result;
    }
  }

  public static List<String> getPatternString(final MethodParameterInjection injection) {
    final ArrayList<String> list = new ArrayList<String>();
    final String className = injection.getClassName();
    for (MethodParameterInjection.MethodInfo info : injection.getMethodInfos()) {
      final boolean[] paramFlags = info.getParamFlags();
      final int paramFlagsLength = paramFlags.length;
      final String methodName = info.getMethodName();
      final String typesString = getParameterTypesString(info.getMethodSignature());
      if (info.isReturnFlag()) {
        list.add(getPatternStringForJavaPlace(methodName, typesString, -1, className));
      }
      for (int i = 0; i < paramFlagsLength; i++) {
        if (paramFlags[i]) {
          list.add(getPatternStringForJavaPlace(methodName, typesString, i, className));
        }
      }
    }
    return list;
  }
}

