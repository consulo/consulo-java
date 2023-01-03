package consulo.java.impl.codeInsight;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.codeEditor.Editor;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.TargetElementUtilExtender;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.psi.xml.XmlAttributeValue;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.psi.xml.XmlText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author VISTALL
 * @since 20.04.2015
 */
@ExtensionImpl
public class JavaTargetElementUtilEx implements TargetElementUtilExtender {
  private static class PsiElementFindProcessor<T extends PsiElement> implements Processor<T> {
    private final T myElement;

    public PsiElementFindProcessor(T t) {
      myElement = t;
    }

    @Override
    public boolean process(T t) {
      return !myElement.getManager().areElementsEquivalent(myElement, t);
    }
  }

  public static final String NEW_AS_CONSTRUCTOR = "new as constructor";
  public static final String THIS_ACCEPTED = "this accepted";
  public static final String SUPER_ACCEPTED = "super accepted";

  @Override
  public void collectAllAccepted(@Nonnull Set<String> set) {
    set.add(NEW_AS_CONSTRUCTOR);
    set.add(THIS_ACCEPTED);
    set.add(SUPER_ACCEPTED);
  }

  @Override
  public void collectDefinitionSearchFlags(@Nonnull Set<String> set) {
    set.add(THIS_ACCEPTED);
    set.add(SUPER_ACCEPTED);
  }

  @Override
  public void collectReferenceSearchFlags(@Nonnull Set<String> set) {
    set.add(NEW_AS_CONSTRUCTOR);
  }

  @Override
  public boolean isAcceptableReferencedElement(PsiElement element, PsiElement referenceOrReferencedElement) {
    return !isEnumConstantReference(element, referenceOrReferencedElement);
  }

  private static boolean isEnumConstantReference(final PsiElement element, final PsiElement referenceOrReferencedElement) {
    return element != null &&
        element.getParent() instanceof PsiEnumConstant &&
        referenceOrReferencedElement instanceof PsiMethod &&
        ((PsiMethod) referenceOrReferencedElement).isConstructor();
  }

  @Override
  public boolean acceptImplementationForReference(final PsiReference reference, final PsiElement element) {
    if (reference instanceof PsiReferenceExpression && element instanceof PsiMember) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          PsiClass containingClass = ((PsiMember) element).getContainingClass();
          final PsiExpression expression = ((PsiReferenceExpression) reference).getQualifierExpression();
          PsiClass psiClass;
          if (expression != null) {
            psiClass = PsiUtil.resolveClassInType(expression.getType());
          } else {
            if (element instanceof PsiClass) {
              psiClass = (PsiClass) element;
              final PsiElement resolve = reference.resolve();
              if (resolve instanceof PsiClass) {
                containingClass = (PsiClass) resolve;
              }
            } else {
              psiClass = PsiTreeUtil.getParentOfType((PsiReferenceExpression) reference, PsiClass.class);
            }
          }

          if (containingClass == null && psiClass == null) {
            return true;
          }
          if (containingClass != null) {
            PsiElementFindProcessor<PsiClass> processor1 = new PsiElementFindProcessor<PsiClass>(containingClass);
            while (psiClass != null) {
              if (!processor1.process(psiClass) ||
                  !ClassInheritorsSearch.search(containingClass).forEach(new PsiElementFindProcessor<PsiClass>(psiClass)) ||
                  !ClassInheritorsSearch.search(psiClass).forEach(processor1)) {
                return true;
              }
              psiClass = psiClass.getContainingClass();
            }
          }
          return false;
        }
      });
    }
    return true;
  }

  @Override
  public boolean includeSelfInGotoImplementation(@Nonnull PsiElement element) {
    if (element instanceof PsiModifierListOwner && ((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }

    return true;
  }

  @Nullable
  @Override
  public PsiElement getGotoDeclarationTarget(PsiElement element, PsiElement navElement) {
    if (navElement == element && element instanceof PsiCompiledElement && element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.isConstructor() && method.getParameterList().getParametersCount() == 0) {
        PsiClass aClass = method.getContainingClass();
        PsiElement navClass = aClass.getNavigationElement();
        if (aClass != navClass) {
          return navClass;
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Collection<PsiElement> getTargetCandidates(@Nonnull PsiReference reference) {
    PsiElement parent = reference.getElement().getParent();
    if (parent instanceof PsiCallExpression) {
      PsiCallExpression callExpr = (PsiCallExpression) parent;
      boolean allowStatics = false;
      PsiExpression qualifier = callExpr instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression) callExpr).getMethodExpression()
          .getQualifierExpression() : callExpr instanceof PsiNewExpression ? ((PsiNewExpression) callExpr).getQualifier() : null;
      if (qualifier == null) {
        allowStatics = true;
      } else if (qualifier instanceof PsiJavaCodeReferenceElement) {
        PsiElement referee = ((PsiJavaCodeReferenceElement) qualifier).advancedResolve(true).getElement();
        if (referee instanceof PsiClass) {
          allowStatics = true;
        }
      }
      PsiResolveHelper helper = JavaPsiFacade.getInstance(parent.getProject()).getResolveHelper();
      PsiElement[] candidates = PsiUtil.mapElements(helper.getReferencedMethodCandidates(callExpr, false));
      final Collection<PsiElement> methods = new LinkedHashSet<PsiElement>();
      for (PsiElement candidate1 : candidates) {
        PsiMethod candidate = (PsiMethod) candidate1;
        if (candidate.hasModifierProperty(PsiModifier.STATIC) && !allowStatics) {
          continue;
        }
        List<PsiMethod> supers = Arrays.asList(candidate.findSuperMethods());
        if (supers.isEmpty()) {
          methods.add(candidate);
        } else {
          methods.addAll(supers);
        }
      }
      return methods;
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement modifyReferenceOrReferencedElement(@Nullable PsiElement refElement,
                                                       @Nonnull PsiFile file,
                                                       @Nonnull Editor editor,
                                                       @Nonnull Set<String> flags,
                                                       int offset) {
    PsiReference ref = null;
    if (refElement == null) {
      ref = TargetElementUtil.findReference(editor, offset);
      if (ref instanceof PsiJavaReference) {
        refElement = ((PsiJavaReference) ref).advancedResolve(true).getElement();
      }
    }

    if (refElement != null) {
      if (flags.contains(NEW_AS_CONSTRUCTOR)) {
        if (ref == null) {
          ref = TargetElementUtil.findReference(editor, offset);
        }
        if (ref != null) {
          PsiElement parent = ref.getElement().getParent();
          if (parent instanceof PsiAnonymousClass) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiNewExpression) {
            PsiMethod constructor = ((PsiNewExpression) parent).resolveConstructor();
            if (constructor != null) {
              refElement = constructor;
            } else if (refElement instanceof PsiClass && ((PsiClass) refElement).getConstructors().length > 0) {
              return null;
            }
          }
        }
      }

      if (refElement instanceof PsiMirrorElement) {
        return ((PsiMirrorElement) refElement).getPrototype();
      }

      if (refElement instanceof PsiClass) {
        final PsiFile containingFile = refElement.getContainingFile();
        if (containingFile != null && containingFile.getVirtualFile() == null) { // in mirror file of compiled class
          String qualifiedName = ((PsiClass) refElement).getQualifiedName();
          if (qualifiedName == null) {
            return null;
          }
          return JavaPsiFacade.getInstance(refElement.getProject()).findClass(qualifiedName, refElement.getResolveScope());
        }
      }
    }
    return refElement;
  }

  @Nullable
  @Override
  public PsiElement modifyTargetElement(@Nonnull PsiElement element, @Nonnull Set<String> flags) {
    if (element instanceof PsiKeyword) {
      if (element.getParent() instanceof PsiThisExpression) {
        if (!flags.contains(THIS_ACCEPTED)) {
          return null;
        }
        PsiType type = ((PsiThisExpression) element.getParent()).getType();
        if (!(type instanceof PsiClassType)) {
          return null;
        }
        return ((PsiClassType) type).resolve();
      }

      if (element.getParent() instanceof PsiSuperExpression) {
        if (!flags.contains(SUPER_ACCEPTED)) {
          return null;
        }
        PsiType type = ((PsiSuperExpression) element.getParent()).getType();
        if (!(type instanceof PsiClassType)) {
          return null;
        }
        return ((PsiClassType) type).resolve();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement adjustElement(Editor editor, Set<String> flags, PsiElement element, PsiElement contextElement) {
    if (element != null) {
      if (element instanceof PsiAnonymousClass) {
        return ((PsiAnonymousClass) element).getBaseClassType().resolve();
      }
      return element;
    }
    if (contextElement == null) {
      return null;
    }
    final PsiElement parent = contextElement.getParent();
    if (parent instanceof XmlText || parent instanceof XmlAttributeValue) {
      return TargetElementUtil.findTargetElement(editor, flags, parent.getParent().getTextRange().getStartOffset() + 1);
    } else if (parent instanceof XmlTag || parent instanceof XmlAttribute) {
      return TargetElementUtil.findTargetElement(editor, flags, parent.getTextRange().getStartOffset() + 1);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement adjustReference(@Nonnull PsiReference ref) {
    final PsiElement parent = ref.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression) {
      return parent;
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement getNamedElement(@Nonnull PsiElement element) {
    PsiElement parent = element.getParent();
    if (element instanceof PsiIdentifier) {
      if (parent instanceof PsiClass && element.equals(((PsiClass) parent).getNameIdentifier())) {
        return parent;
      } else if (parent instanceof PsiVariable && element.equals(((PsiVariable) parent).getNameIdentifier())) {
        return parent;
      } else if (parent instanceof PsiMethod && element.equals(((PsiMethod) parent).getNameIdentifier())) {
        return parent;
      } else if (parent instanceof PsiLabeledStatement && element.equals(((PsiLabeledStatement) parent).getLabelIdentifier())) {
        return parent;
      }
    } else if ((parent = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false)) != null) {
      // A bit hacky depends on navigation offset correctly overridden
      if (parent.getTextOffset() == element.getTextRange().getStartOffset() && !(parent instanceof XmlAttribute) && !(parent instanceof PsiFile
          && InjectedLanguageManager.getInstance(parent.getProject()).isInjectedFragment((PsiFile) parent))) {
        return parent;
      }
    }
    return null;
  }

  @Nullable
  public static PsiReferenceExpression findReferenceExpression(Editor editor) {
    final PsiReference ref = TargetElementUtil.findReference(editor);
    return ref instanceof PsiReferenceExpression ? (PsiReferenceExpression) ref : null;
  }

}
