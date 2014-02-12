/*
 * Copyright (c) 2014 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * NodeInfo.java, Jan 27, 2014 2:09:43 PM Andrei Tatarnikov
 */

package ru.ispras.microtesk.translator.simnml.ir.expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.ispras.fortress.expression.Node;
import ru.ispras.microtesk.translator.simnml.ir.location.Location;
import ru.ispras.microtesk.translator.simnml.ir.shared.LetConstant;
import ru.ispras.microtesk.translator.simnml.ir.valueinfo.ValueInfo;

/**
 * The NodeInfo class is used as an additional custom attribute of a Fortress expression node
 * that provides additional information on the node and the subexpression it represents.
 * 
 * It has the following important attributes:
 * <pre>
 * - Kind (kind of element representing the node, determines the set of maintained attributes).
 * - Source (Location, NamedConstant, Constant, Operator (including conditions), depending on Kind).
 * - ValueInfo (current, resulting, top-level, final value).</pre>
 * 
 * Coercions (explicit casts) can be applied zero or more times to all element kinds.
 * To support them, the following attributes are included:
 * <pre>
 * - PreviousValueInfo, array of ValueInfo: first is value before final coercion, last is initial
 *   value before first coercion. Value after the final coercion is ValueInfo (current).
 * - CoercionChain, based on PreviousValueInfo, a list of applied coercions.</pre>
 *
 * Also, operators have an additional attribute related to coercion that goes to the 'source' object:
 * <pre>
 *  - CastValueInfo (operands are cast to a common type (implicit cast), if their types are different).</pre>
 * 
 * @author Andrei Tatarnikov
 */

public final class NodeInfo
{
    public static enum Kind
    {
        LOCATION    (Location.class,       Node.Kind.VARIABLE),
        NAMED_CONST (LetConstant.class,    Node.Kind.VALUE),
        CONST       (SourceConstant.class, Node.Kind.VALUE),
        OPERATOR    (SourceOperator.class, Node.Kind.EXPR);

        private final Class<?>  sourceClass;
        private final Node.Kind    nodeKind;

        private Kind(Class<?> sourceClass, Node.Kind nodeKind)
        {
            this.sourceClass = sourceClass;
            this.nodeKind    = nodeKind;
        }

        boolean isCompatibleSource(Object source)
        {
            return this.sourceClass.isAssignableFrom(source.getClass());
        }

        boolean isCompatibleNode(Node.Kind nodeKind)
        {
            return this.nodeKind == nodeKind;
        }
    }

    static NodeInfo newLocation(Location source)
    {
        checkNotNull(source);

        return new NodeInfo(
            NodeInfo.Kind.LOCATION, source, ValueInfo.createModel(source.getType()));
    }

    static NodeInfo newNamedConst(LetConstant source)
    {
        checkNotNull(source);

        return new NodeInfo(
            NodeInfo.Kind.NAMED_CONST, source, source.getExpr().getValueInfo());
    }

    static NodeInfo newConst(SourceConstant source)
    {
        checkNotNull(source);

        return new NodeInfo(
            NodeInfo.Kind.CONST, source, ValueInfo.createNative(source.getValue()));
    }

    static NodeInfo newOperator(SourceOperator source)
    {
        checkNotNull(source);

        return new NodeInfo(
            NodeInfo.Kind.OPERATOR, source, source.getResultValueInfo());
    }

    private final Kind            kind;
    private final Object          source;
    private final ValueInfo       currentVI;
    private final List<ValueInfo> previousVI;

    private NodeInfo(
        Kind            kind,
        Object          source,
        ValueInfo       current,
        List<ValueInfo> previous
        )
    {
        if (!kind.isCompatibleSource(source))
            throw new IllegalArgumentException(
                String.format("%s is not proper source for %s.", source.getClass().getSimpleName(), kind));

        this.kind        = kind;
        this.source      = source;
        this.currentVI   = current;
        this.previousVI  = Collections.unmodifiableList(previous);
    }

    private NodeInfo(Kind kind, Object source, ValueInfo current)
    {
        this(kind, source, current, Collections.<ValueInfo>emptyList());
    }

    public NodeInfo coerceTo(ValueInfo newValueInfo)
    {
        checkNotNull(newValueInfo);

        if (getValueInfo().equals(newValueInfo))
            return this;

        final List<ValueInfo> previous = 
            new ArrayList<ValueInfo>(this.previousVI.size() + 1);

        previous.add(getValueInfo());
        for (ValueInfo vi : this.previousVI)
            previous.add(vi);

        return new NodeInfo(
            getKind(), getSource(), newValueInfo, previous);
    }

    public Kind getKind()
    {
        return kind;
    }

    public Object getSource()
    {
        return source;
    }

    public ValueInfo getValueInfo()
    {
        return currentVI;
    }
    
    public boolean isCoersionApplied()
    {
        return !previousVI.isEmpty();  
    }
    
    public List<ValueInfo> getPreviousValueInfo()
    {
        return previousVI;
    }

    public List<ValueInfo> getCoercionChain()
    {
        if (!isCoersionApplied())
            return Collections.<ValueInfo>emptyList();

        final List<ValueInfo> result =
            new ArrayList<ValueInfo>(previousVI.size());

        result.add(getValueInfo().typeInfoOnly());

        for (int index = 0; index < previousVI.size()-1; ++index)
            result.add(previousVI.get(index).typeInfoOnly());

        return Collections.unmodifiableList(result);
    }

    private static void checkNotNull(Object o)
    {
        if (null == o)
            throw new NullPointerException();
    }
}
