package consulo.java.jam.impl;

import com.intellij.jam.JamClassGenerator;
import com.intellij.jam.annotations.JamPsiConnector;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElementRef;
import consulo.proxy.advanced.AdvancedProxyBuilder;
import jakarta.inject.Singleton;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 14-Jan-17
 */
@Singleton
@ServiceImpl
public class JamClassGeneratorImpl extends JamClassGenerator {
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
          if(method.isAnnotationPresent(JamPsiConnector.class)) {
            return myPsiElementRef.getPsiElement();
          }
          throw new UnsupportedOperationException(method.getName() + " is not supported in " + myClass.getName());
      }
    }
  }

  @Override
  public <T> Function<PsiElementRef, T> generateJamElementFactory(Class<T> aClass) {
    return psiElementRef -> AdvancedProxyBuilder.create(aClass).withInvocationHandler(new InvocationHandlerImpl<>(aClass, psiElementRef)).build();
  }
}
