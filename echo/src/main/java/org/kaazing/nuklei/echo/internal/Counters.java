/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.nuklei.echo.internal;

import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.CountersManager;

public final class Counters implements AutoCloseable
{
    private final AtomicCounter acceptedCount;
    private final AtomicCounter connectedCount;
    private final AtomicCounter reflectedBytes;

    Counters(CountersManager countersManager)
    {
        acceptedCount = countersManager.newCounter("acceptedCount");
        connectedCount = countersManager.newCounter("connectedCount");
        reflectedBytes = countersManager.newCounter("reflectedBytes");
    }

    @Override
    public void close() throws Exception
    {
        acceptedCount.close();
        connectedCount.close();
        reflectedBytes.close();
    }

    public AtomicCounter acceptedCount()
    {
        return acceptedCount;
    }

    public AtomicCounter connectedCount()
    {
        return connectedCount;
    }

    public AtomicCounter reflectedBytes()
    {
        return reflectedBytes;
    }
}
