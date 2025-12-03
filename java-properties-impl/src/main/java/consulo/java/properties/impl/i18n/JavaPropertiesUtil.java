package consulo.java.properties.impl.i18n;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.java.properties.impl.psi.PropertyCreationHandler;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.project.Project;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2022-12-20
 */
public class JavaPropertiesUtil {
    public static final PropertyCreationHandler DEFAULT_PROPERTY_CREATION_HANDLER =
        (project, propertiesFiles, key, value, parameters) ->
            I18nUtil.createProperty(project, propertiesFiles, key, value);

    public static boolean isPropertyRef(PsiLiteralExpression expression, String key, String resourceBundleName) {
        if (resourceBundleName == null) {
            return !PropertiesUtil.findPropertiesByKey(expression.getProject(), key).isEmpty();
        }
        else {
            List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(resourceBundleName, expression);
            boolean containedInPropertiesFile = false;
            for (PropertiesFile propertiesFile : propertiesFiles) {
                containedInPropertiesFile |= propertiesFile.findPropertyByKey(key) != null;
            }
            return containedInPropertiesFile;
        }
    }

    /**
     * Returns number of different parameters in i18n message. For example, for string
     * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i>
     * number of parameters is 3.
     *
     * @param expression i18n literal
     * @return number of parameters
     */
    @RequiredReadAction
    public static int getPropertyValueParamsMaxCount(PsiLiteralExpression expression) {
        int maxCount = -1;
        for (PsiReference reference : expression.getReferences()) {
            if (reference instanceof PsiPolyVariantReference polyVariantRef) {
                for (ResolveResult result : polyVariantRef.multiResolve(false)) {
                    if (result.isValidResult() && result.getElement() instanceof IProperty property) {
                        String value = property.getValue();
                        MessageFormat format;
                        try {
                            format = new MessageFormat(value);
                        }
                        catch (Exception e) {
                            continue; // ignore syntax error
                        }
                        try {
                            int count = format.getFormatsByArgumentIndex().length;
                            maxCount = Math.max(maxCount, count);
                        }
                        catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        }
        return maxCount;
    }

    public static boolean isValidPropertyReference(
        @Nonnull Project project,
        @Nonnull PsiLiteralExpression expression,
        @Nonnull String key,
        @Nonnull SimpleReference<String> outResourceBundle
    ) {
        Map<String, Object> annotationAttributeValues = new HashMap<>();
        annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        if (JavaI18nUtil.mustBePropertyKey(project, expression, annotationAttributeValues)) {
            Object resourceBundleName = annotationAttributeValues.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
            if (!(resourceBundleName instanceof PsiExpression expr)) {
                return false;
            }
            Object value = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr);
            if (value == null) {
                return false;
            }
            String bundleName = value.toString();
            outResourceBundle.set(bundleName);
            return isPropertyRef(expression, key, bundleName);
        }
        return true;
    }
}
