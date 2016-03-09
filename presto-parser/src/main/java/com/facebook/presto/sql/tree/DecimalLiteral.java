/*
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

package com.facebook.presto.sql.tree;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class DecimalLiteral
        extends Literal
{
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("(\\+|-)?((\\d*\\.\\d+)|(\\d+\\.?))");

    private final String value;

    public DecimalLiteral(String value)
    {
        this(Optional.empty(), value);
    }

    public DecimalLiteral(NodeLocation location, String value)
    {
        this(Optional.of(location), value);
    }

    public DecimalLiteral(Optional<NodeLocation> location, String value)
    {
        super(location);
        requireNonNull(value, "value is null");
        checkArgument(DECIMAL_PATTERN.matcher(value).matches(), "invalid decimal value %s", value);
        if (value.startsWith("+")) {
            value = value.substring(1);
        }
        this.value = value;
    }

    public String getValue()
    {
        return value;
    }

    public String getIntegralPart()
    {
        return Iterables.get(splitValue(), 0);
    }

    public String getFractionalPart()
    {
        return Iterables.get(splitValue(), 1, "");
    }

    public String getUnscaledValue()
    {
        return getIntegralPart() + getFractionalPart();
    }

    public int getPrecision()
    {
        int precision = getIntegralPart().length() + getFractionalPart().length();
        if (getIntegralPart().startsWith("-")) {
            --precision;
        }
        return precision;
    }

    public int getScale()
    {
        return getFractionalPart().length();
    }

    public long getLongValue()
    {
        return Long.parseLong(getIntegralPart() + getFractionalPart());
    }

    private Iterable<String> splitValue()
    {
        return Splitter.on('.').split(value);
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context)
    {
        return visitor.visitDecimalLiteral(this, context);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DecimalLiteral that = (DecimalLiteral) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value);
    }
}
