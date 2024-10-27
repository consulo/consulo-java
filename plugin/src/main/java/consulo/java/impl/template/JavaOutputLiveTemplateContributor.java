package consulo.java.impl.template;

import com.intellij.java.impl.codeInsight.template.JavaLikeCodeContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeStatementContextType;
import com.intellij.java.impl.codeInsight.template.impl.ShortenFQNamesProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaOutputLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    @Nonnull
    public String groupId() {
        return "javaoutput";
    }

    @Override
    @Nonnull
    public LocalizeValue groupName() {
        return LocalizeValue.localizeTODO("Java Output");
    }

    @Override
    public void contribute(@Nonnull LiveTemplateContributor.Factory factory) {
        try (Builder builder = factory.newBuilder("javaoutputSerr", "serr", "System.err.println(\"$END$\");", CodeInsightLocalize.livetemplateDescriptionSerr())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaoutputSout", "sout", "System.out.println($END$);", CodeInsightLocalize.livetemplateDescriptionSout())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaoutputSouf", "souf", "System.out.printf(\"$END$\");", CodeInsightLocalize.livetemplateDescriptionSouf())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaoutputSoutm", "soutm", "System.out.println(\"$CLASS_NAME$.$METHOD_NAME$\");", CodeInsightLocalize.livetemplateDescriptionSoutm())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("CLASS_NAME", "className()", "", false);
            builder.withVariable("METHOD_NAME", "methodName()", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaoutputSoutp", "soutp", "System.out.println($FORMAT$);", CodeInsightLocalize.livetemplateDescriptionSoutp())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("FORMAT", "groovyScript(\"'\\\"' + _1.collect { it + ' = [\\\" + ' + it + ' + \\\"]'}.join(', ') + '\\\"'\", methodParameters())", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaoutputSoutv", "soutv", "System.out.println(\"$EXPR_COPY$ = \" + $EXPR$);", CodeInsightLocalize.livetemplateDescriptionSoutv())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("EXPR", "variableOfType(\"\")", "\"expr\"", true);
            builder.withVariable("EXPR_COPY", "EXPR", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }
    }
}
