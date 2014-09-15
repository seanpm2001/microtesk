/*
 * Copyright (c) 2014 ISPRAS (www.ispras.ru)
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * PrimitiveBuilder.java, Aug 27, 2014 11:08:31 AM Andrei Tatarnikov
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package ru.ispras.microtesk.test.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.ispras.microtesk.model.api.instruction.AddressingModeImm;
import ru.ispras.microtesk.model.api.metadata.MetaAddressingMode;
import ru.ispras.microtesk.model.api.metadata.MetaArgument;
import ru.ispras.microtesk.model.api.metadata.MetaModel;
import ru.ispras.microtesk.model.api.metadata.MetaOperation;
import ru.ispras.microtesk.model.api.metadata.MetaShortcut;
import ru.ispras.microtesk.test.template.Primitive.Kind;

public interface PrimitiveBuilder
{   
    Primitive build();
    void setContext(String contextName);

    void addArgument(int value);
    void addArgument(String value);
    void addArgument(RandomValueBuilder value);
    void addArgument(Primitive value);
    void addArgument(PrimitiveBuilder value);

    void setArgument(String name, int value);
    void setArgument(String name, String value);
    void setArgument(String name, RandomValueBuilder value);
    void setArgument(String name, Primitive value);
    void setArgument(String name, PrimitiveBuilder value);
}

final class PrimitiveBuilderFactory
{
    private final MetaModel metaModel;

    public PrimitiveBuilderFactory(MetaModel metaModel)
    {
        if (null == metaModel)
            throw new NullPointerException();

        this.metaModel = metaModel;
    }

    public PrimitiveBuilder newOperationBuilder(
        String name, CallBuilder callBuilder)
    {
        if (null == name)
            throw new NullPointerException();

        return new PrimitiveBuilderOperation(
            name, metaModel, callBuilder);
    }

    public PrimitiveBuilder newAddressingModeBuilder(
        String name, CallBuilder callBuilder)
    {
        final MetaAddressingMode metaData =
            metaModel.getAddressingMode(name);

        if (null == metaData)
            throw new IllegalArgumentException(
                "No such addressing mode: " + name);

        return new PrimitiveBuilderCommon(callBuilder, metaData);
    }
}

final class PrimitiveBuilderOperation implements PrimitiveBuilder
{
    private final CallBuilder callBuilder;
    private final MetaModel metaModel;

    private final String name;
    private String contextName;

    private final List<Argument> argumentList;
    private final Map<String, Argument> argumentMap;

    private static final String ERR_WRONG_USE =
        "Illegal use: Arguments can be added using either " + 
        "addArgument or setArgument methods, but not both.";

    PrimitiveBuilderOperation(
        String name, MetaModel metaModel, CallBuilder callBuilder)
    {
        if (null == name)
            throw new NullPointerException();

        if (null == metaModel)
            throw new NullPointerException();

        if (null == callBuilder)
            throw new NullPointerException();

        this.callBuilder = callBuilder;
        this.metaModel = metaModel;

        this.name = name;
        this.contextName = null;

        this.argumentList = new ArrayList<Argument>();
        this.argumentMap = new LinkedHashMap<String, Argument>();
    }

    public Primitive build()
    {
        final MetaOperation metaData = metaModel.getOperation(name);
        if (null == metaData)
            throw new IllegalArgumentException("No such operation: " + name);

        final MetaShortcut metaShortcut =
           metaData.getShortcut(contextName);

        final PrimitiveBuilder builder;
        if (null != metaShortcut)
        {
            builder = new PrimitiveBuilderCommon(
                callBuilder, metaShortcut.getOperation(), contextName);
        }
        else
        {
            // If there is no shortcut for the given context,
            // the operation is used as it is.
            builder = new PrimitiveBuilderCommon(
                callBuilder, metaData, null);
        }

        if (!argumentList.isEmpty())
        {
            for (Argument argument : argumentList)
                argument.addToBuilder(builder);
        }
        else if (!argumentMap.isEmpty())
        {
            for (Argument argument : argumentMap.values())
                argument.addToBuilder(builder);
        }

        return builder.build();
    }

    public void setContext(String contextName)
    {
        this.contextName = contextName;
    }

    private void registerArgument(Argument argument)
    {
        if (argument.hasName())
        {
            if (!argumentList.isEmpty())
                throw new IllegalStateException(ERR_WRONG_USE);
            argumentMap.put(argument.getName(), argument);
        }
        else
        {
            if (!argumentMap.isEmpty())
                throw new IllegalStateException(ERR_WRONG_USE);
            argumentList.add(argument);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // For Array-based syntax

    public void addArgument(int value)
    {
        registerArgument(new ArgumentInt(value));
    }

    public void addArgument(String value)
    {
        checkNotNull(value);
        registerArgument(new ArgumentStr(value));
    }

    public void addArgument(RandomValueBuilder value)
    {
        checkNotNull(value);
        registerArgument(new ArgumentRVB(value));
    }

    public void addArgument(Primitive value)
    {
        checkNotNull(value);
        registerArgument(new ArgumentPrim(value));
    }
    
    @Override
    public void addArgument(PrimitiveBuilder value)
    {
        checkNotNull(value);
        registerArgument(new ArgumentPrimB(value));
    }

    ///////////////////////////////////////////////////////////////////////////
    // For Hash-based syntax

    public void setArgument(String name, int value)
    {
        checkNotNull(name);
        registerArgument(new ArgumentInt(name, value));
    }

    public void setArgument(String name, String value)
    {
        checkNotNull(name);
        checkNotNull(value);
        registerArgument(new ArgumentStr(name, value));
    }

    public void setArgument(String name, RandomValueBuilder value)
    {
        checkNotNull(name);
        checkNotNull(value);
        registerArgument(new ArgumentRVB(name, value));
    }

    public void setArgument(String name, Primitive value)
    {
        checkNotNull(name);
        checkNotNull(value);
        registerArgument(new ArgumentPrim(name, value));
    }

    @Override
    public void setArgument(String name, PrimitiveBuilder value)
    {
        checkNotNull(name);
        checkNotNull(value);
        registerArgument(new ArgumentPrimB(name, value));
    }

    private static void checkNotNull(Object o)
    {
        if (o == null)
            throw new NullPointerException();
    }

    private interface Argument
    {
        boolean hasName();
        String getName();
        void addToBuilder(PrimitiveBuilder builder);
    }

    private static abstract class AbstractArgument<T> implements Argument
    {
        private final String name;
        private final T value;

        public AbstractArgument(String name, T value)
            { this.name = name; this.value = value; }

        public AbstractArgument(T value) 
            { this(null, value); }

        @Override
        public final String getName()
            { return name; }

        @Override
        public final boolean hasName()
            { return null != name; }

        public final T getValue()
            { return value; }
    }

    private static class ArgumentInt extends AbstractArgument<Integer>
    {
        public ArgumentInt(String name, int value)
            { super(name, value); }

        public ArgumentInt(int value)
            { super(value); }

        @Override
        public void addToBuilder(PrimitiveBuilder builder)
        {
            if (hasName())
                builder.setArgument(getName(), getValue());
            else
                builder.addArgument(getValue());
        }
    }

    private static class ArgumentStr extends AbstractArgument<String>
    {
        public ArgumentStr(String name, String value)
            { super(name, value); }

        public ArgumentStr(String value)
            { super(value); }

        @Override
        public void addToBuilder(PrimitiveBuilder builder)
        {
            if (hasName())
                builder.setArgument(getName(), getValue());
            else
                builder.addArgument(getValue());
        }
    }

    private static class ArgumentRVB extends AbstractArgument<RandomValueBuilder>
    {
        public ArgumentRVB(String name, RandomValueBuilder value)
            { super(name, value); }

        public ArgumentRVB(RandomValueBuilder value)
            { super(value); }

        @Override
        public void addToBuilder(PrimitiveBuilder builder)
        {
            if (hasName())
                builder.setArgument(getName(), getValue());
            else
                builder.addArgument(getValue());
        }
    }

    private static class ArgumentPrim extends AbstractArgument<Primitive>
    {
        public ArgumentPrim(String name, Primitive value)
            { super(name, value); }

        public ArgumentPrim(Primitive value)
            { super(value); }

        @Override
        public void addToBuilder(PrimitiveBuilder builder)
        {
            if (hasName())
                builder.setArgument(getName(), getValue());
            else
                builder.addArgument(getValue());
        }
    }

    private static class ArgumentPrimB
        extends AbstractArgument<PrimitiveBuilder>
    {
        public ArgumentPrimB(String name, PrimitiveBuilder value)
            { super(name, value); }

        public ArgumentPrimB(PrimitiveBuilder value)
            { super(value); }

        @Override
        public void addToBuilder(PrimitiveBuilder builder)
        {
            if (hasName())
                builder.setArgument(getName(), getValue());
            else
                builder.addArgument(getValue());
        }
    }
}

final class PrimitiveBuilderCommon implements PrimitiveBuilder
{
    private interface Strategy
    {
        String getName();
        String getTypeName();
        String getDescription();
        boolean isRoot();
        String getNextArgumentName();
        void checkValidArgument(Argument arg);
        void checkAllArgumentsAssigned(Set<String> argNames);
    }

    private final CallBuilder callBuilder;
    private final Strategy strategy;
    private final Kind kind;
    private final Map<String, Argument> args;
    private String contextName;

    PrimitiveBuilderCommon(
        CallBuilder callBuilder, MetaOperation metaData, String contextName)
    {
        this(
            callBuilder,
            new StrategyOperation(metaData, contextName),
            Kind.OP,
            contextName
        );
    }

    PrimitiveBuilderCommon(
        CallBuilder callBuilder, MetaAddressingMode metaData)
    {
        this(
            callBuilder,
            new StrategyAddressingMode(metaData),
            Kind.MODE,
            null
        );
    }

    private PrimitiveBuilderCommon(
        CallBuilder callBuilder,
        Strategy strategy,
        Kind kind,
        String contextName
        )
    {
        if (null == callBuilder)
            throw new NullPointerException();

        this.callBuilder = callBuilder;
        this.strategy = strategy;
        this.kind = kind;
        this.args = new HashMap<String, Argument>();
        this.contextName = contextName;
    }

    private void putArgument(Argument arg)
    {
        args.put(arg.getName(), arg);
    }

    public Primitive build()
    {
        checkAllArgumentsSet(Collections.unmodifiableSet(args.keySet()));

        return new Primitive(
            kind,
            getName(),
            strategy.getTypeName(),
            strategy.isRoot(),
            args,
            contextName
            );
    }

    @Override
    public void setContext(String contextName)
    {
        if (null != this.contextName)
            throw new IllegalStateException("Context is already assigned."); 

        this.contextName = contextName;
    }

    ///////////////////////////////////////////////////////////////////////////
    // For Array-based syntax

    public void addArgument(int value)
    {
        final String name = getNextArgumentName();
        setArgument(name, value);
    }
    
    // For labels
    public void addArgument(String value)
    {
        final String name = getNextArgumentName();
        setArgument(name, value);
    }

    public void addArgument(RandomValueBuilder value)
    {
        final String name = getNextArgumentName();
        setArgument(name, value);
    }

    public void addArgument(Primitive value)
    {
        final String name = getNextArgumentName();
        setArgument(name, value);
    }

    @Override
    public void addArgument(PrimitiveBuilder value)
    {
        final String name = getNextArgumentName();
        setArgument(name, value);
    }

    ///////////////////////////////////////////////////////////////////////////
    // For Hash-based syntax

    public void setArgument(String name, int value)
    {
        if (null == name)
            throw new NullPointerException();

        final Argument arg = new Argument(name, Argument.Kind.IMM, value);
        checkValidArgument(arg);
        putArgument(arg);
    }

    // For labels
    public void setArgument(String name, String value)
    {
        // TODO: Current limitation: 0 is instead of
        // the actual label address/offset.

        final int fakeValue = 0;
        setArgument(name, fakeValue);

        callBuilder.addLabelReference(value, getName(), name, fakeValue);
    }

    public void setArgument(String name, RandomValueBuilder value)
    {
        if (null == name)
            throw new NullPointerException();

        if (null == value)
            throw new NullPointerException();

        final Argument arg = 
            new Argument(name, Argument.Kind.IMM_RANDOM, value.build());

        checkValidArgument(arg);
        putArgument(arg);
    }

    public void setArgument(String name, Primitive value)
    {
        if (null == name)
            throw new NullPointerException();

        if (null == value)
            throw new NullPointerException();

        if ((value.getKind() != Primitive.Kind.MODE) &&
            (value.getKind() != Primitive.Kind.OP))
        {
            throw new IllegalArgumentException(
                "Unsupported primitive kind: " + value.getKind());
        }

        final Argument.Kind kind = value.getKind() == Primitive.Kind.MODE ?
            Argument.Kind.MODE : Argument.Kind.OP;

        final Argument arg = new Argument(name, kind, value);
        checkValidArgument(arg);
        putArgument(arg);
    }

    @Override
    public void setArgument(String name, PrimitiveBuilder value)
    {
        value.setContext(getName());
        setArgument(name, value.build());
    }

    private String getName()
    {
        return strategy.getName();
    }

    private String getNextArgumentName()
    {
        return strategy.getNextArgumentName();
    }

    private void checkValidArgument(Argument arg)
    {
        strategy.checkValidArgument(arg);
    }

    private void checkAllArgumentsSet(Set<String> argNames)
    {
        strategy.checkAllArgumentsAssigned(argNames); 
    }
    
    private static final String ERR_UNASSIGNED_ARGUMENT = 
        "The %s argument of %s is not assigned.";

    private static final String ERR_NO_MORE_ARGUMENTS = 
        "Too many arguments: %s has only %d arguments.";

    private static final String ERR_UNDEFINED_ARGUMENT =
        "The %s argument is not defined for %s.";
    
    private static final String ERR_TYPE_NOT_ACCEPTED =
        "The %s type is not accepted for the %s argument of %s.";
    
    private static final String ERR_ARG_NOT_PRIMITIVE =
        "The %s argument of %s is not a primitive (OP or MODE).";

    private static final class StrategyOperation implements Strategy
    {
        private final MetaOperation metaData;
        private final String contextName;

        private int argumentCount;
        private final Iterator<MetaArgument> argumentIterator;

        StrategyOperation(MetaOperation metaData, String contextName)
        {
            if (null == metaData)
                throw new NullPointerException();

            this.metaData = metaData;
            this.contextName = contextName;

            this.argumentCount = 0;
            this.argumentIterator = metaData.getArguments().iterator();
        }

        @Override
        public String getName()
        {
            return metaData.getName();
        }

        @Override
        public String getTypeName()
        {
            return metaData.getTypeName();
        }

        @Override
        public String getDescription()
        {
            final String basicDescription =
               String.format("the %s operation", getName());

            if (null == contextName)
                return basicDescription;

            return String.format("%s (shortcut for context %s)",
                basicDescription, contextName);
        }

        @Override
        public boolean isRoot()
        {
            return metaData.isRoot();
        }

        @Override
        public String getNextArgumentName()
        {
            if (!argumentIterator.hasNext())
                throw new IllegalStateException(String.format(
                    ERR_NO_MORE_ARGUMENTS, getDescription(), argumentCount));

            final MetaArgument argument = argumentIterator.next();
            argumentCount++;

            return argument.getName();
        }

        @Override
        public void checkValidArgument(Argument arg)
        {
            final MetaArgument metaArgument =
                metaData.getArgument(arg.getName());

            if (null == metaArgument)
                throw new IllegalStateException(String.format(
                    ERR_UNDEFINED_ARGUMENT, arg.getName(), getDescription()));

            final String typeName;
            if (arg.isImmediate())
            {
                typeName = AddressingModeImm.NAME;
            }
            else
            {
                if (!(arg.getValue() instanceof Primitive))
                    throw new IllegalArgumentException(String.format(
                        ERR_ARG_NOT_PRIMITIVE,
                        arg.getName(),
                        getDescription()));
                
                typeName = ((Primitive) arg.getValue()).getTypeName();
            }

            if (!metaArgument.isTypeAccepted(typeName))
                throw new IllegalStateException(String.format(
                    ERR_TYPE_NOT_ACCEPTED,
                    typeName,
                    arg.getName(),
                    getDescription()));
        }

        @Override
        public void checkAllArgumentsAssigned(Set<String> argNames)
        {
            for (MetaArgument arg : metaData.getArguments())
            {
                if (!argNames.contains(arg.getName()))
                    throw new IllegalStateException(String.format(
                        ERR_UNASSIGNED_ARGUMENT,
                        arg.getName(),
                        getDescription()));
            }
        }
    }

    private static final class StrategyAddressingMode implements Strategy
    {
        private static final String ERR_WRONG_ARGUMENT_KIND =
            "The %s argument of %s is %s, but it must be an immediate value.";

        private final MetaAddressingMode metaData;

        private int argumentCount;
        private final Iterator<String> argumentNameIterator;

        StrategyAddressingMode(MetaAddressingMode metaData)
        {
            if (null == metaData)
                throw new NullPointerException();

            this.metaData = metaData;

            this.argumentCount = 0;
            this.argumentNameIterator = metaData.getArgumentNames().iterator();
        }

        @Override
        public String getName()
        {
            return metaData.getName();
        }

        @Override
        public String getTypeName()
        {
            return getName();
        }

        @Override
        public String getDescription()
        {
            return String.format("the %s addressing mode");
        }

        @Override
        public boolean isRoot()
        {
            // Always false because addressing modes always have parents.
            return false;
        }

        @Override
        public String getNextArgumentName()
        {
            if (!argumentNameIterator.hasNext())
                throw new IllegalStateException(String.format(
                    ERR_NO_MORE_ARGUMENTS, getDescription(), argumentCount));

            final String argumentName = argumentNameIterator.next();
            argumentCount++;

            return argumentName;
        }

        @Override
        public void checkValidArgument(Argument arg)
        {
            if (!metaData.isArgumentDefined(arg.getName()))
                throw new IllegalStateException(String.format(
                    ERR_UNDEFINED_ARGUMENT, arg.getName(), getDescription()));

            if (!arg.isImmediate())
                throw new IllegalStateException(String.format(
                    ERR_WRONG_ARGUMENT_KIND,
                    arg.getName(),
                    getDescription(),
                    arg.getKind()));
        }

        @Override
        public void checkAllArgumentsAssigned(Set<String> argNames)
        {
            for (String argName : metaData.getArgumentNames())
            {
                if (!argNames.contains(argName))
                    throw new IllegalStateException(String.format(
                        ERR_UNASSIGNED_ARGUMENT, argName, getDescription()));
            }
        }
    }
}
