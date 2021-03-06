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
package org.kaazing.nuklei.ws.internal.types.control;

import static java.lang.String.format;
import static org.kaazing.nuklei.ws.internal.types.control.Types.TYPE_ID_ROUTED_RESPONSE;

import org.kaazing.nuklei.ws.internal.types.Flyweight;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public final class RoutedFW extends Flyweight
{
    private static final int FIELD_OFFSET_CORRELATION_ID = 0;
    private static final int FIELD_SIZE_CORRELATION_ID = BitUtil.SIZE_OF_LONG;

    public RoutedFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        checkLimit(limit(), maxLimit);

        return this;
    }

    public int typeId()
    {
        return TYPE_ID_ROUTED_RESPONSE;
    }

    public long correlationId()
    {
        return buffer().getLong(offset() + FIELD_OFFSET_CORRELATION_ID);
    }

    public int limit()
    {
        return offset() + FIELD_SIZE_CORRELATION_ID;
    }

    @Override
    public String toString()
    {
        return format("ROUTED [correlationId=%d]", correlationId());
    }

    public static final class Builder extends Flyweight.Builder<RoutedFW>
    {
        public Builder()
        {
            super(new RoutedFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            return this;
        }

        public Builder correlationId(long correlationId)
        {
            buffer().putLong(offset() + FIELD_OFFSET_CORRELATION_ID, correlationId);
            return this;
        }
    }
}
