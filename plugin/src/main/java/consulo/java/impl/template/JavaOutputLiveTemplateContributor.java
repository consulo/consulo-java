package consulo.java.impl.template;

import com.intellij.java.impl.codeInsight.template.JavaLikeCodeContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeStatementContextType;
import com.intellij.java.impl.codeInsight.template.impl.ShortenFQNamesProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.localize.JavaLiveTemplateLocalize;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;

@ExtensionImpl
public class JavaOutputLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    public String groupId() {
        return "javaoutput";
    }

    @Override
    public LocalizeValue groupName() {
        return JavaLiveTemplateLocalize.groupNameJavaOutput();
    }

    @Override
    public void contribute(LiveTemplateContributor.Factory factory) {
        try (Builder builder = factory.newBuilder(
            "javaoutputSerr",
            "serr",
            "System.err.println(\"$END$\");",
            JavaLiveTemplateLocalize.descriptionSerr()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaoutputSout",
            "sout",
            "System.out.println($END$);",
            JavaLiveTemplateLocalize.descriptionSout()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaoutputSouf",
            "souf",
            "System.out.printf(\"$END$\");",
            JavaLiveTemplateLocalize.descriptionSouf()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaoutputSoutm",
            "soutm",
            "System.out.println(\"$CLASS_NAME$.$METHOD_NAME$\");",
            JavaLiveTemplateLocalize.descriptionSoutm()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("CLASS_NAME", "className()", "", false);
            builder.withVariable("METHOD_NAME", "methodName()", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaoutputSoutp",
            "soutp",
            "System.out.println($FORMAT$);",
            JavaLiveTemplateLocalize.descriptionSoutp()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable(
                "FORMAT",
                "groovyScript(\"'\\\"' + _1.collect { it + ' = [\\\" + ' + it + ' + \\\"]'}.join(', ') + '\\\"'\", methodParameters())",
                "",
                false
            );

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaoutputSoutv",
            "soutv",
            "System.out.println(\"$EXPR_COPY$ = \" + $EXPR$);",
            JavaLiveTemplateLocalize.descriptionSoutv()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("EXPR", "variableOfType(\"\")", "\"expr\"", true);
            builder.withVariable("EXPR_COPY", "EXPR", "", false);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }
    }
}
