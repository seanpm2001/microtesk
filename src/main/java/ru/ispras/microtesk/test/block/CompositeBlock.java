/*
 * Copyright (c) 2013 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * CompositeBlock.java, May 6, 2013 3:09:44 PM Andrei Tatarnikov
 */

package ru.ispras.microtesk.test.block;

import ru.ispras.microtesk.test.sequence.Sequence;
import ru.ispras.microtesk.test.sequence.iterator.IIterator;

final class CompositeBlock implements Block
{
    private final IIterator<Sequence<AbstractCall>> generator;

    public CompositeBlock(IIterator<Sequence<AbstractCall>> generator)
    {
        this.generator = generator;
    }

    @Override
    public IIterator<Sequence<AbstractCall>> getIterator()
    {
        return generator;
    }
}
