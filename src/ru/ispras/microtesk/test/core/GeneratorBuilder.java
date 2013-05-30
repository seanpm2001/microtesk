/*
 * Copyright 2013 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.ispras.microtesk.test.core;

import ru.ispras.microtesk.test.core.internal.CompositeIterator;

/**
 * This class implements the test sequence generator.
 *
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public class GeneratorBuilder<T> extends CompositeIterator<Sequence<T>>
{
    /// The default combinator.
    public static final ECombinator DEFAULT_COMBINATOR = ECombinator.RANDOM;
    /// The default compositor.
    public static final ECompositor DEFAULT_COMPOSITOR = ECompositor.RANDOM;

    /// The configuration of the test sequence generator.
    private Configuration<T> config = new Configuration<T>();

    /// The combinator used in the generator.
    private String combinator = null;
    /// The compositor used in the generator.
    private String compositor = null;

    /**
     * Constructs a test sequence generator.
     */
    public GeneratorBuilder() {}

    /**
     * Sets the combinator used in the generator.
     *
     * @param combinator the combinator name.
     */

    public void setCombinator(final String combinator)
    {
        this.combinator = combinator;
    }

    /**
     * Sets the compositor used in the generator.
     *
     * @param compositor the compositor name.
     */

    public void setCompositor(final String compositor)
    {
        this.compositor = compositor;
    }

    /**
     * Returns the test sequence generator for the template block.
     *
     * @return the test sequence generator.
     */

    public Generator<T> getGenerator()
    {
        // If no compositor and no combinator is specified
        // we use the single sequence generator.

        if ((null == combinator) && (null == compositor))
            return new GeneratorSingle<T>(getIterators());

        if (null == combinator)
            combinator = DEFAULT_COMBINATOR.name();

        if (null == compositor)
            compositor = DEFAULT_COMPOSITOR.name();

        return new GeneratorMerge<T>(
            config.getCombinator(combinator),
            config.getCompositor(compositor),
            getIterators()
            );
    }
}
