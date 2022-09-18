package com.matyrobbrt.kaupenbot.common.extension

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

@CompileStatic
@GroovyASTTransformation
final class ExtensionFinderASTT extends AbstractASTTransformation implements TransformWithPriority {
    private static final ClassNode ARG_TYPE = ClassHelper.make(ExtensionArgument)
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source)
        final ann = nodes[0] as AnnotationNode
        final method = nodes[1] as MethodNode
        final botId = getMemberStringValue(ann, 'value')

        final argsArg = method.parameters.find { it.type == ClassHelper.MAP_TYPE }
        final List<Expression> extensions = []

        final ctors = ExtensionDiscoveryASTT.extensions.getOrDefault(botId, [])
        ctors.each {
            final List<Expression> args = []
            it.ctor().parameters.each {
                final name = getMemberStringValue(it.annotations.find { it.classNode == ARG_TYPE }, 'value')
                args.add(GeneralUtils.castX(
                        it.type, GeneralUtils.callX(GeneralUtils.varX(argsArg), 'get', GeneralUtils.constX(name))
                ))
            }
            extensions.add(GeneralUtils.ctorX(it.type(), GeneralUtils.args(args)))
        }

        method.setCode(GeneralUtils.returnS(GeneralUtils.callX(
                ClassHelper.LIST_TYPE, 'of', GeneralUtils.args(extensions)
        )))
    }

    @Override
    int priority() {
        return -100
    }
}
