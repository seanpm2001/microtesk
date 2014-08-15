/*
 * Copyright (c) 2014 ISPRAS (www.ispras.ru)
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * Printer.java, Aug 11, 2014 12:46:57 PM Andrei Tatarnikov
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

package ru.ispras.microtesk.test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

import ru.ispras.microtesk.model.api.exception.ConfigurationException;
import ru.ispras.microtesk.model.api.state.IModelStateObserver;
import ru.ispras.microtesk.test.data.ConcreteCall;
import ru.ispras.microtesk.test.sequence.Sequence;
import ru.ispras.microtesk.test.template.Label;
import ru.ispras.microtesk.test.template.Output;

/**
 * The Printer class is responsible for printing generated symbolic test
 * programs (sequences of concrete calls to a file and to the screen). 
 * 
 * @author Andrei Tatarnikov
 */

public final class Printer
{
    private static final String HEADER_FRMT =
        "%sThis test program was automatically generated by MicroTESK\n" +
        "%sGeneration started: %s\n%s\n%s" +
        "Institute for System Programming of the Russian Academy of Sciences" +
        " (ISPRAS)\n%s25, Alexander Solzhenitsyn st., Moscow, 109004, Russia" +
        "\n%shttp://forge.ispras.ru/projects/microtesk\n";

    private final PrintWriter fileWritter;
    private final IModelStateObserver observer;
    private final String commentToken;
    private final boolean printToScreen;

    private boolean isHeaderPrinted;

    /**
     * Constructs a printer object.
     * 
     * @param fileName Test program file name (if null or empty
     * no file is generated),
     * @param observer Model state observer to evaluate outputs.
     * @param commentToken Token for comments (used to generate the header).
     * @param printToScreen Specifies whether the test program is to be
     * printed to the screen.
     *
     * @throws NullPointerException if the observer or commentToken parameter
     * is null.
     * @throws IOException if failed to open the specified file for writing.
     */

    public Printer(
        String fileName,
        IModelStateObserver observer,
        String commentToken,
        boolean printToScreen
        ) throws IOException
    {
        if (null == observer)
            throw new NullPointerException();

        if (null == commentToken)
            throw new NullPointerException();

        this.fileWritter     = createFileWritter(fileName);
        this.observer        = observer;
        this.commentToken    = commentToken;
        this.printToScreen   = printToScreen;
        this.isHeaderPrinted = false;
    }

    /**
     * Prints the specified instruction call sequence.
     * 
     * @param sequence Instruction call sequence.
     * @throws NullPointerException if the parameter is null.
     * @throws IllegalArgumentException if a attribute of an instruction 
     * call, which is used in test program generation has an invalid format.
     * @throws ConfigurationException if failed to evaluate one of
     * the output object associated with an instruction call in the
     * sequence.
     */

    public void printSequence(Sequence<ConcreteCall> sequence) throws ConfigurationException
    {
        if (null == sequence)
            throw new NullPointerException();

        printHeader();
        for (ConcreteCall inst : sequence)
        {
            printOutputs(inst.getAttribute("b_output"));
            printLabels(inst.getAttribute("b_labels"));
            printText(inst.getExecutable().getText());
            printLabels(inst.getAttribute("f_labels"));
            printOutputs(inst.getAttribute("f_output"));
        }
    }

    /**
     * Closes the generated file.
     */

    public void close()
    {
        if (null != fileWritter)
            fileWritter.close();
    }

    private static PrintWriter createFileWritter(String fileName) throws IOException
    {
        if ((null == fileName) || fileName.isEmpty())
            return null;

        final FileWriter outFile = new FileWriter(fileName);
        return new PrintWriter(outFile);
    }

    private void printHeader()
    {
        if (isHeaderPrinted)
            return;

        final String   slcs = commentToken.trim() + " "; 
        final String header = String.format(
            HEADER_FRMT, slcs, slcs, new Date(), slcs, slcs, slcs, slcs);

        printToFile(header);
        isHeaderPrinted = true;
    }

    private void printOutputs(Object o) throws ConfigurationException
    {
        if (null == o)
            return;

        final List<?> list = toList(o);
        for (Object item : list)
        {
            if (!(item instanceof Output))
                throw new IllegalArgumentException(
                    item + " is not an Output object!");

            final Output output = (Output) item;
            printText(output.evaluate(observer));
        }
    }

    private void printLabels(Object o)
    {
        if (null == o)
            return;

        final List<?> list = toList(o);
        for (Object item : list)
        {
            if (!(item instanceof Label))
                throw new IllegalArgumentException(
                    item + " is not a Label object!");

            final Label label = (Label) item;
            printText(label.getUniqueName() + ":");
        }
    }

    private void printText(String text)
    {
        printToScreen(text);
        printToFile(text);
    }

    private void printToScreen(String text)
    {
        if (printToScreen)
            System.out.println(text);
    }

    private void printToFile(String text)
    {
        if (null != fileWritter)
            fileWritter.println(text);
    }

    private static List<?> toList(Object o)
    {
        if (!(o instanceof List))
            throw new IllegalArgumentException(
                o + " is not a List object.");

        return (List<?>) o;
    }
}
