/*
 * Copyright (c) 2013 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * WalkerFactoryBase.java, Jul 22, 2013 8:00:02 PM Andrei Tatarnikov
 */

package ru.ispras.microtesk.translator.simnml.antlrex;

import java.util.Map;
import ru.ispras.microtesk.translator.antlrex.IErrorReporter;
import ru.ispras.microtesk.translator.antlrex.ISemanticError;
import ru.ispras.microtesk.translator.antlrex.SemanticException;
import ru.ispras.microtesk.translator.antlrex.Where;
import ru.ispras.microtesk.translator.antlrex.symbols.SymbolTable;
import ru.ispras.microtesk.translator.simnml.ESymbolKind;
import ru.ispras.microtesk.translator.simnml.ir.IR;
import ru.ispras.microtesk.translator.simnml.ir.primitive.Primitive;

public class WalkerFactoryBase implements WalkerContext
{
    private final WalkerContext context;

    public WalkerFactoryBase(WalkerContext context)
    {
        assert null != context;
        this.context = context;
    }

    @Override
    public IErrorReporter getReporter()
    {
        return context.getReporter();
    }

    @Override
    public SymbolTable<ESymbolKind> getSymbols()
    {
        return context.getSymbols();
    }

    @Override
    public IR getIR()
    {
        return context.getIR();
    }

    @Override
    public Map<String, Primitive> getThisArgs()
    {
        return context.getThisArgs();
    }

    @Override
    public Primitive.Holder getThis()
    {
        return context.getThis();
    }

    protected final void raiseError(final Where where, final String what) throws SemanticException
    {
        raiseError(where, new ISemanticError()
        {
            @Override
            public String getMessage() { return what; }
        });
    }

    protected final void raiseError(Where where, ISemanticError what) throws SemanticException
    {
        getReporter().raiseError(where, what);
    }
}
