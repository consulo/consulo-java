package consulo.java.jam.impl;

import com.intellij.jam.JamClassGenerator;
import com.intellij.jam.annotations.JamPsiConnector;
import com.intellij.psi.PsiElementRef;
import com.intellij.util.NotNullFunction;
import consulo.util.advandedProxy.AdvancedProxyBuilder;

import jakarta.inject.Singleton;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author VISTALL
 * @since 14-Jan-17
 */
@Singleton
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
  public <T> NotNullFunction<PsiElementRef, T> generateJamElementFactory(Class<T> aClass) {
    return psiElementRef -> AdvancedProxyBuilder.create(aClass).withInvocationHandler(new InvocationHandlerImpl<>(aClass, psiElementRef)).build();
  }
}
