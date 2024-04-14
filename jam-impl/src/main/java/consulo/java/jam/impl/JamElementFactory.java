package consulo.java.jam.impl;

import com.intellij.jam.annotations.JamPsiConnector;
import consulo.language.psi.PsiElementRef;
import consulo.proxy.advanced.AdvancedProxyBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2024-04-14
 */
public class JamElementFactory<T> implements Function<PsiElementRef, T> {
  private static class InvocationHandlerImpl<R> implements InvocationHandler {
    private Class<R> myClass;
    private PsiElementRef myPsiElementRef;

    public InvocationHandlerImpl(Class<R> clazz, PsiElementRef psiElementRef) {
      myPsiElementRef = psiElementRef;
      myClass = clazz;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
      String name = method.getName();
      switch (name) {
        case "hashCode":
          return myClass.hashCode();
        case "equals":
          Object object = objects[0];
          return myClass.hashCode() == object.hashCode();
        default:
          if (method.isAnnotationPresent(JamPsiConnector.class)) {
            return myPsiElementRef.getPsiElement();
          }
          throw new UnsupportedOperationException(method.getName() + " is not supported in " + myClass.getName());
      }
    }
  }

  private final Class<T> myTargetClass;

  private final boolean myHasDefaultConstructor;

  public JamElementFactory(Class<T> targetClass) {
    this.myTargetClass = targetClass;
    myHasDefaultConstructor = findDefaultConstructor(targetClass) != null;
  }

  @Override
  public T apply(PsiElementRef psiElementRef) {
    AdvancedProxyBuilder<T> builder = AdvancedProxyBuilder.create(myTargetClass);
    if (!myHasDefaultConstructor) {
      builder.withSuperConstructorArguments(psiElementRef.getPsiElement());
    }
    builder.withInvocationHandler(new InvocationHandlerImpl<>(myTargetClass, psiElementRef));
    return builder.build();
  }

  private <T> Constructor<T> findDefaultConstructor(Class<T> aClass) {
    try {
      return aClass.getConstructor();
    }
    catch (NoSuchMethodException ignored) {
    }
    return null;
  }
}
