package com.matyrobbrt.kaupenbot.common.sql

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlUpdate

@CompileStatic
@GroovyASTTransformationClass(value = 'com.matyrobbrt.kaupenbot.common.sql.SqlInsertTransform')
@interface SqlInsert {
    String tableName()
}

@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
final class SqlInsertTransform extends AbstractASTTransformation {
    static final ClassNode BIND_TYPE = ClassHelper.make(Bind)
    static final ClassNode SQLUPDATE_TYPE = ClassHelper.make(SqlUpdate)

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        this.init(nodes, source)
        final annotation = nodes[0] as AnnotationNode
        final method = nodes[1] as MethodNode
        final List<String> paramOrder = []
        method.parameters.each {
            final pAn = new AnnotationNode(BIND_TYPE)
            pAn.setMember('value', GeneralUtils.constX(it.name))
            paramOrder.add(it.name)
            it.addAnnotation(pAn)
        }
        final man = new AnnotationNode(SQLUPDATE_TYPE)
        man.setMember('value', GeneralUtils.constX(("insert into ${getMemberStringValue(annotation, 'tableName')}" +
            "(${paramOrder.join(', ')}) values (${paramOrder.stream().map { ':' + it }.toList().join(', ')})").toString()))
        method.addAnnotation(man)
    }
}