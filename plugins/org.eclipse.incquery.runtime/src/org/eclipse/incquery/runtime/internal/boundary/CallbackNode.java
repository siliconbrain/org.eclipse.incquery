/*******************************************************************************
 * Copyright (c) 2010-2012, Bergmann Gabor, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Bergmann Gabor - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.runtime.internal.boundary;

import org.apache.log4j.Logger;
import org.eclipse.incquery.runtime.api.IMatchUpdateListener;
import org.eclipse.incquery.runtime.api.IPatternMatch;
import org.eclipse.incquery.runtime.api.IncQueryEngine;
import org.eclipse.incquery.runtime.matchers.tuple.Tuple;
import org.eclipse.incquery.runtime.rete.misc.SimpleReceiver;
import org.eclipse.incquery.runtime.rete.network.Direction;
import org.eclipse.incquery.runtime.rete.network.ReteContainer;

/**
 * @author Bergmann Gabor
 * 
 */
public abstract class CallbackNode<Match extends IPatternMatch> extends SimpleReceiver {

	IncQueryEngine engine;
    IMatchUpdateListener<? super Match> listener;
    private Logger logger;

    public abstract Match statelessConvert(Tuple t);

    public CallbackNode(ReteContainer reteContainer, IncQueryEngine engine, Logger logger, IMatchUpdateListener<? super Match> listener) {
        super(reteContainer);
        this.engine = engine;
        this.logger = logger;
        this.listener = listener;
    }

    @Override
    public void update(Direction direction, Tuple updateElement) {
        Match match = statelessConvert(updateElement);
        try {
            if (direction == Direction.INSERT)
                listener.notifyAppearance(match);
            else
                listener.notifyDisappearance(match);
        } catch (Throwable e) { // NOPMD
            if (e instanceof Error)
                throw (Error) e;
            logger.warn(String.format(
                            "The incremental pattern matcher encountered an error during executing a callback on %s of match %s of pattern %s. Error message: %s. (Developer note: %s in %s called from CallbackNode)",
                            direction == Direction.INSERT ? "insertion" : "removal", match.prettyPrint(),
                            match.patternName(), e.getMessage(), e.getClass().getSimpleName(), listener), e);
        }
    }

}
