package com.matyrobbrt.kaupenbot.common.extension

import groovy.transform.CompileStatic
import groovy.transform.ImmutableOptions
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
final class ExtensionDiscoveryASTT extends AbstractASTTransformation implements TransformWithPriority {
    static final Map<String, List<Ctor>> extensions = [:]

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source)
        final ann = nodes[0] as AnnotationNode
        final bid = getMemberStringValue(ann, 'botId')
        final object = nodes[1] as ClassNode
        final ctor = object.declaredConstructors.find() ?: object.addConstructor(Opcodes.ACC_PUBLIC, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, GeneralUtils.block())
        extensions.computeIfAbsent(bid) { new ArrayList<>() }.add(new Ctor(ctor, object, getMemberStringValue(ann, 'value')))
    }

    @Override
    int priority() {
        return 100
    }

    @ImmutableOptions(knownImmutableClasses = [ConstructorNode, ClassNode])
    static record Ctor(ConstructorNode ctor, ClassNode type, String name) {}
}
