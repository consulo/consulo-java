/*
 * Copyright 2003-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class SerialVersionUIDBuilder extends JavaRecursiveElementVisitor {

  @NonNls private static final String ACCESS_METHOD_NAME_PREFIX = "access$";

  private final PsiClass clazz;
  private int index = -1;
  private final Set<MemberSignature> nonPrivateConstructors;
  private final Set<MemberSignature> nonPrivateMethods;
  private final Set<MemberSignature> nonPrivateFields;
  private final List<MemberSignature> staticInitializers;
  private boolean assertStatement = false;
  private boolean classObjectAccessExpression = false;
  private final Map<PsiElement, String> memberMap =
    new HashMap<PsiElement, String>();

  private static final Comparator<PsiClass> INTERFACE_COMPARATOR =
    new Comparator<PsiClass>() {
      public int compare(PsiClass object1, PsiClass object2) {
        if (object1 == null && object2 == null) {
          return 0;
        }
        if (object1 == null) {
          return 1;
        }
        if (object2 == null) {
          return -1;
        }
        String name1 = object1.getQualifiedName();
        String name2 = object2.getQualifiedName();
        if (name1 == null && name2 == null) {
          return 0;
        }
        if (name1 == null) {
          return 1;
        }
        if (name2 == null) {
          return -1;
        }
        return name1.compareTo(name2);
      }
    };
  @NonNls private static final String CLASS_ACCESS_METHOD_PREFIX = "class$";

  private SerialVersionUIDBuilder(PsiClass clazz) {
    super();
    this.clazz = clazz;
    nonPrivateMethods = new HashSet<MemberSignature>();
    PsiMethod[] methods = clazz.getMethods();
    for (PsiMethod method : methods) {
      if (!method.isConstructor() &&
          !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        MemberSignature methodSignature =
          new MemberSignature(method);
        nonPrivateMethods.add(methodSignature);
      }
    }
    nonPrivateFields = new HashSet<MemberSignature>();
    PsiField[] fields = clazz.getFields();
    for (PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.PRIVATE) ||
          !(field.hasModifierProperty(PsiModifier.STATIC) ||
            field.hasModifierProperty(PsiModifier.TRANSIENT))) {
        MemberSignature fieldSignature =
          new MemberSignature(field);
        nonPrivateFields.add(fieldSignature);
      }
    }

    staticInitializers = new ArrayList<MemberSignature>();
    PsiClassInitializer[] initializers = clazz.getInitializers();
    if (initializers.length > 0) {
      for (PsiClassInitializer initializer : initializers) {
        PsiModifierList modifierList =
          initializer.getModifierList();
        if (modifierList != null &&
            modifierList.hasModifierProperty(PsiModifier.STATIC)) {
          MemberSignature initializerSignature =
            MemberSignature.getStaticInitializerMemberSignature();
          staticInitializers.add(initializerSignature);
          break;
        }
      }
    }
    if (staticInitializers.isEmpty()) {
      PsiField[] psiFields = clazz.getFields();
      for (PsiField field : psiFields) {
        if (hasStaticInitializer(field)) {
          MemberSignature initializerSignature =
            MemberSignature.getStaticInitializerMemberSignature();
          staticInitializers.add(initializerSignature);
          break;
        }
      }
    }

    nonPrivateConstructors = new HashSet<MemberSignature>();
    PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length == 0 && !clazz.isInterface()) {
      // generated empty constructor if no constructor is defined in the source
      MemberSignature constructorSignature;
      if (clazz.hasModifierProperty(PsiModifier.PUBLIC)) {
        constructorSignature = MemberSignature.getPublicConstructor();
      }
      else {
        constructorSignature = MemberSignature.getPackagePrivateConstructor();
      }
      nonPrivateConstructors.add(constructorSignature);
    }
    for (PsiMethod constructor : constructors) {
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
        MemberSignature constructorSignature =
          new MemberSignature(constructor);
        nonPrivateConstructors.add(constructorSignature);
      }
    }
  }

  public static long computeDefaultSUID(PsiClass psiClass) {
    Project project = psiClass.getProject();
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiClass serializable = psiFacade.findClass(CommonClassNames.JAVA_IO_SERIALIZABLE, scope);
    if (serializable == null) {
      // no jdk defined for project.
      return -1L;
    }

    boolean isSerializable = psiClass.isInheritor(serializable, true);
    if (!isSerializable) {
      return 0L;
    }

    SerialVersionUIDBuilder serialVersionUIDBuilder = new SerialVersionUIDBuilder(psiClass);
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

      String className = PsiFormatUtil.getExternalName(psiClass);
      dataOutputStream.writeUTF(className);

      PsiModifierList classModifierList = psiClass.getModifierList();
      int classModifiers = classModifierList != null ? MemberSignature.calculateModifierBitmap(classModifierList) : 0;
      MemberSignature[] methodSignatures = serialVersionUIDBuilder.getNonPrivateMethodSignatures();
      if (psiClass.isInterface()) {
        classModifiers |= Modifier.INTERFACE;
        if (methodSignatures.length == 0) {
          // interfaces were not marked abstract when they did't have methods in java 1.0
          // For serialization compatibility the abstract modifier is ignored.
          classModifiers &= ~Modifier.ABSTRACT;
        }
      }
      dataOutputStream.writeInt(classModifiers);

      PsiClass[] interfaces = psiClass.getInterfaces();
      Arrays.sort(interfaces, INTERFACE_COMPARATOR);
      for (PsiClass aInterfaces : interfaces) {
        String name = aInterfaces.getQualifiedName();
        dataOutputStream.writeUTF(name);
      }

      MemberSignature[] fields = serialVersionUIDBuilder.getNonPrivateFields();
      Arrays.sort(fields);
      for (MemberSignature field : fields) {
        dataOutputStream.writeUTF(field.getName());
        dataOutputStream.writeInt(field.getModifiers());
        dataOutputStream.writeUTF(field.getSignature());
      }

      MemberSignature[] staticInitializers = serialVersionUIDBuilder.getStaticInitializers();
      for (MemberSignature staticInitializer : staticInitializers) {
        dataOutputStream.writeUTF(staticInitializer.getName());
        dataOutputStream.writeInt(staticInitializer.getModifiers());
        dataOutputStream.writeUTF(staticInitializer.getSignature());
      }

      MemberSignature[] constructors = serialVersionUIDBuilder.getNonPrivateConstructors();
      Arrays.sort(constructors);
      for (MemberSignature constructor : constructors) {
        dataOutputStream.writeUTF(constructor.getName());
        dataOutputStream.writeInt(constructor.getModifiers());
        dataOutputStream.writeUTF(constructor.getSignature());
      }

      Arrays.sort(methodSignatures);
      for (MemberSignature methodSignature : methodSignatures) {
        dataOutputStream.writeUTF(methodSignature.getName());
        dataOutputStream.writeInt(methodSignature.getModifiers());
        dataOutputStream.writeUTF(methodSignature.getSignature());
      }

      dataOutputStream.flush();
      @NonNls String algorithm = "SHA";
      MessageDigest digest = MessageDigest.getInstance(algorithm);
      byte[] digestBytes = digest.digest(byteArrayOutputStream.toByteArray());
      long serialVersionUID = 0L;
      for (int i = Math.min(digestBytes.length, 8) - 1; i >= 0; i--) {
        serialVersionUID = serialVersionUID << 8 | digestBytes[i] & 0xFF;
      }
      return serialVersionUID;
    }
    catch (IOException exception) {
      InternalError internalError = new InternalError(exception.getMessage());
      internalError.initCause(exception);
      throw internalError;
    }
    catch (NoSuchAlgorithmException exception) {
      SecurityException securityException = new SecurityException(exception.getMessage());
      securityException.initCause(exception);
      throw securityException;
    }
  }

  private void createClassObjectAccessSynthetics(PsiType type) {
    if (!classObjectAccessExpression) {
      MemberSignature syntheticMethod =
        MemberSignature.getClassAccessMethodMemberSignature();
      nonPrivateMethods.add(syntheticMethod);
    }
    PsiType unwrappedType = type;
    @NonNls StringBuffer fieldNameBuffer;
    if (type instanceof PsiArrayType) {
      fieldNameBuffer = new StringBuffer();
      fieldNameBuffer.append("array");
      while (unwrappedType instanceof PsiArrayType) {
        PsiArrayType arrayType = (PsiArrayType)unwrappedType;
        unwrappedType = arrayType.getComponentType();
        fieldNameBuffer.append('$');
      }
    }
    else {
      fieldNameBuffer = new StringBuffer(CLASS_ACCESS_METHOD_PREFIX);
    }
    if (unwrappedType instanceof PsiPrimitiveType) {
      PsiPrimitiveType primitiveType = (PsiPrimitiveType)unwrappedType;
      fieldNameBuffer.append(MemberSignature.createPrimitiveType(primitiveType));
    }
    else {
      String text = unwrappedType.getCanonicalText().replace('.',
                                                                   '$');
      fieldNameBuffer.append(text);
    }
    String fieldName = fieldNameBuffer.toString();
    MemberSignature memberSignature =
      new MemberSignature(fieldName, Modifier.STATIC,
                          "Ljava/lang/Class;");
    if (!nonPrivateFields.contains(memberSignature)) {
      nonPrivateFields.add(memberSignature);
    }
    classObjectAccessExpression = true;
  }

  private String getAccessMethodIndex(PsiElement element) {
    String cache = memberMap.get(element);
    if (cache == null) {
      cache = String.valueOf(index);
      index++;
      memberMap.put(element, cache);
    }
    return cache;
  }

  public MemberSignature[] getNonPrivateConstructors() {
    init();
    return nonPrivateConstructors.toArray(new MemberSignature[nonPrivateConstructors.size()]);
  }

  public MemberSignature[] getNonPrivateFields() {
    init();
    return nonPrivateFields.toArray(new MemberSignature[nonPrivateFields.size()]);
  }

  public MemberSignature[] getNonPrivateMethodSignatures() {
    init();
    return nonPrivateMethods.toArray(new MemberSignature[nonPrivateMethods.size()]);
  }

  public MemberSignature[] getStaticInitializers() {
    init();
    return staticInitializers.toArray(new MemberSignature[staticInitializers.size()]);
  }

  private static boolean hasStaticInitializer(PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      PsiExpression initializer = field.getInitializer();
      if (initializer == null) {
        return false;
      }
      PsiType fieldType = field.getType();
      PsiType stringType = TypeUtils.getStringType(field);
      if (field.hasModifierProperty(PsiModifier.FINAL) && (fieldType instanceof PsiPrimitiveType || fieldType.equals(stringType))) {
        return !PsiUtil.isConstantExpression(initializer);
      }
      else {
        return true;
      }
    }
    return false;
  }

  private void init() {
    if (index < 0) {
      index = 0;
      clazz.acceptChildren(this);
    }
  }

  @Override
  public void visitAssertStatement(PsiAssertStatement statement) {
    super.visitAssertStatement(statement);
    if (assertStatement) {
      return;
    }
    MemberSignature memberSignature =
      MemberSignature.getAssertionsDisabledFieldMemberSignature();
    nonPrivateFields.add(memberSignature);
    PsiManager manager = clazz.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    PsiClassType classType = factory.createType(clazz);
    createClassObjectAccessSynthetics(classType);
    if (staticInitializers.isEmpty()) {
      MemberSignature initializerSignature =
        MemberSignature.getStaticInitializerMemberSignature();
      staticInitializers.add(initializerSignature);
    }
    assertStatement = true;
  }

  @Override
  public void visitClassObjectAccessExpression(
    PsiClassObjectAccessExpression expression) {
    PsiTypeElement operand = expression.getOperand();
    PsiType type = operand.getType();
    if (!(type instanceof PsiPrimitiveType)) {
      createClassObjectAccessSynthetics(type);
    }
    super.visitClassObjectAccessExpression(expression);
  }

  @Override
  public void visitMethodCallExpression(
    @Nonnull PsiMethodCallExpression methodCallExpression) {
    // for navigating the psi tree in the order javac navigates its AST
    PsiExpressionList argumentList =
      methodCallExpression.getArgumentList();
    PsiExpression[] expressions = argumentList.getExpressions();
    for (PsiExpression expression : expressions) {
      expression.accept(this);
    }
    PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    methodExpression.accept(this);
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);
    PsiElement parentClass = ClassUtils.getContainingClass(reference);
    if (reference.getParent() instanceof PsiTypeElement) {
      return;
    }
    PsiElement element = reference.resolve();
    if (!(element instanceof PsiClass)) {
      return;
    }
    PsiClass elementParentClass =
      ClassUtils.getContainingClass(element);
    if (elementParentClass == null ||
        !elementParentClass.equals(clazz) ||
        element.equals(parentClass)) {
      return;
    }
    PsiClass innerClass = (PsiClass)element;
    if (!innerClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      return;
    }
    PsiMethod[] constructors = innerClass.getConstructors();
    if (constructors.length == 0) {
      getAccessMethodIndex(innerClass);
    }
  }

  @Override
  public void visitReferenceExpression(
    @Nonnull PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    PsiElement element = expression.resolve();
    PsiElement elementParentClass =
      ClassUtils.getContainingClass(element);
    PsiElement expressionParentClass =
      ClassUtils.getContainingClass(expression);
    if (expressionParentClass == null || expressionParentClass
      .equals(elementParentClass)) {
      return;
    }
    PsiElement parentOfParentClass =
      ClassUtils.getContainingClass(expressionParentClass);
    while (parentOfParentClass != null &&
           !parentOfParentClass.equals(clazz)) {
      if (!(expressionParentClass instanceof PsiAnonymousClass)) {
        getAccessMethodIndex(expressionParentClass);
      }
      getAccessMethodIndex(parentOfParentClass);
      parentOfParentClass = ClassUtils.getContainingClass(parentOfParentClass);
    }
    if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        boolean isStatic = false;
        PsiType type = field.getType();
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
          if (field.hasModifierProperty(PsiModifier.FINAL) &&
              type instanceof PsiPrimitiveType) {
            PsiExpression initializer = field.getInitializer();
            if (PsiUtil.isConstantExpression(initializer)) {
              return;
            }
          }
          isStatic = true;
        }
        String returnTypeSignature =
          MemberSignature.createTypeSignature(type).replace('/',
                                                            '.');
        String className = clazz.getQualifiedName();
        @NonNls StringBuilder signatureBuffer =
          new StringBuilder("(");
        if (!isStatic) {
          signatureBuffer.append('L').append(className).append(';');
        }
        String accessMethodIndex = getAccessMethodIndex(field);
        if (!field.getContainingClass().equals(clazz)) {
          return;
        }
        @NonNls String name = null;
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiAssignmentExpression) {
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
          if (assignment.getLExpression().equals(expression)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "02";
            signatureBuffer.append(returnTypeSignature);
          }
        }
        else if (parent instanceof PsiPostfixExpression) {
          PsiPostfixExpression postfixExpression =
            (PsiPostfixExpression)parent;
          IElementType tokenType = postfixExpression.getOperationTokenType();
          if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "08";
          }
          else if (tokenType.equals(JavaTokenType.MINUSMINUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "10";
          }
        }
        else if (parent instanceof PsiPrefixExpression) {
          PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
          IElementType tokenType = prefixExpression.getOperationTokenType();
          if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "04";
          }
          else if (tokenType.equals(JavaTokenType.MINUSMINUS)) {
            name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                   "06";
          }
        }
        if (name == null) {
          name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "00";
        }
        signatureBuffer.append(')').append(returnTypeSignature);
        String signature = signatureBuffer.toString();
        MemberSignature methodSignature =
          new MemberSignature(name, Modifier.STATIC, signature);
        nonPrivateMethods.add(methodSignature);
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (method.hasModifierProperty(PsiModifier.PRIVATE) &&
          method.getContainingClass().equals(clazz)) {
        String signature;
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          signature =
            MemberSignature.createMethodSignature(method)
              .replace('/', '.');
        }
        else {
          String returnTypeSignature =
            MemberSignature.createTypeSignature(method.getReturnType())
              .replace('/', '.');
          @NonNls StringBuilder signatureBuffer =
            new StringBuilder();
          signatureBuffer.append("(L");
          signatureBuffer.append(clazz.getQualifiedName())
            .append(';');
          PsiParameter[] parameters = method.getParameterList()
            .getParameters();
          for (PsiParameter parameter : parameters) {
            PsiType type = parameter.getType();
            String typeSignature = MemberSignature.createTypeSignature(type)
              .replace('/', '.');
            signatureBuffer.append(typeSignature);
          }
          signatureBuffer.append(')');
          signatureBuffer.append(returnTypeSignature);
          signature = signatureBuffer.toString();
        }
        String accessMethodIndex = getAccessMethodIndex(method);
        MemberSignature methodSignature =
          new MemberSignature(ACCESS_METHOD_NAME_PREFIX +
                              accessMethodIndex + "00",
                              Modifier.STATIC, signature);
        nonPrivateMethods.add(methodSignature);
      }
    }
  }
}
