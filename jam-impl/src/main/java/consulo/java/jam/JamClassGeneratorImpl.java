package consulo.java.jam;

import java.lang.reflect.Method;

import javax.inject.Singleton;

import net.sf.cglib.proxy.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;

import com.intellij.jam.JamClassGenerator;
import com.intellij.jam.annotations.JamPsiConnector;
import com.intellij.psi.PsiElementRef;
import com.intellij.util.NotNullFunction;

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
    return psiElementRef -> AdvancedProxy.createProxy(new InvocationHandlerImpl<>(aClass, psiElementRef), aClass);
  }
}
