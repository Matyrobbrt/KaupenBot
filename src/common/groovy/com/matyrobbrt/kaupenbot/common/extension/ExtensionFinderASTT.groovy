package com.matyrobbrt.kaupenbot.common.extension

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
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
    private static final ClassNode MANAGER_TYPE = ClassHelper.make(ExtensionManager)
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source)
        final ann = nodes[0] as AnnotationNode
        final method = nodes[1] as MethodNode
        assert method.parameters.length === 2 && method.parameters[0].type == MANAGER_TYPE && method.parameters[1].type == ClassHelper.MAP_TYPE

        final botId = getMemberStringValue(ann, 'value')

        final argsArg = GeneralUtils.varX(method.parameters.find { it.type == ClassHelper.MAP_TYPE })
        final managerArg = GeneralUtils.varX(method.parameters.find { it.type == MANAGER_TYPE })
        final List<Expression> expressions = []

        final ctors = ExtensionDiscoveryASTT.extensions.getOrDefault(botId, [])
        ctors.each {
            final List<Expression> args = []
            it.ctor().parameters.each {
                final name = getMemberStringValue(it.annotations.find { it.classNode == ARG_TYPE }, 'value')
                args.add(GeneralUtils.castX(
                        it.type, GeneralUtils.callX(argsArg, 'get', GeneralUtils.constX(name))
                ))
            }
            expressions.add(GeneralUtils.callX(
                    managerArg, 'register', GeneralUtils.args(
                        GeneralUtils.constX(it.name()),  GeneralUtils.ctorX(it.type(), GeneralUtils.args(args))
                    )
            ))
        }

        method.setCode(GeneralUtils.block(new VariableScope(), expressions.stream()
            .map { GeneralUtils.stmt(it) }
            .toList()))
    }

    @Override
    int priority() {
        return -100
    }
}
