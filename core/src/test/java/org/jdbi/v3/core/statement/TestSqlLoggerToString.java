/*
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
package org.jdbi.v3.core.statement;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.argument.LoggableArgument;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.rule.DatabaseRule;
import org.jdbi.v3.core.rule.SqliteDatabaseRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSqlLoggerToString {
    private static final String INSERT_POSITIONAL = "insert into foo(bar) values(?)";
    private static final String INSERT_NAMED = "insert into foo(bar) values(:x)";
    private static final String NAME = "x";

    @Rule
    public DatabaseRule db = new SqliteDatabaseRule();

    private Handle handle;
    private String positional = null;
    private String named = null;

    @Before
    public void before() {
        handle = db.getJdbi().open();

        handle.execute("create table foo(bar binary)");

        handle.setSqlLogger(new SqlLogger() {
            @Override
            public void logBeforeExecution(StatementContext context) {
                context.getBinding().findForPosition(0).ifPresent(value -> positional = Objects.toString(value));
                context.getBinding().findForName(NAME, context).ifPresent(value -> named = Objects.toString(value));
            }
        });
    }

    @Test
    public void testInt() {
        handle.createUpdate(INSERT_POSITIONAL).bind(0, 1).execute();

        assertThat(positional).isEqualTo("1");
    }

    @Test
    public void testString() {
        handle.createUpdate(INSERT_POSITIONAL).bind(0, "herp").execute();

        assertThat(positional).isEqualTo("herp");
    }

    @Test
    public void testBean() {
        handle.createUpdate(INSERT_NAMED).bindBean(new StringXBean("herp")).execute();

        assertThat(named).isEqualTo("herp");
    }

    @Test
    // this is why we need LoggableArgument
    public void testArgument() {
        handle.createUpdate(INSERT_POSITIONAL).bind(0, (position, statement, ctx) -> statement.setString(1, "derp")).execute();

        assertThat(positional).isNotEqualTo("derp");
    }

    @Test
    public void testLoggableArgument() {
        handle.createUpdate(INSERT_POSITIONAL).bind(0, new LoggableArgument("derp", (position, statement, ctx) -> statement.setString(1, "derp"))).execute();

        assertThat(positional).isEqualTo("derp");
    }

    @Test
    // this is why SqlStatement#toArgument uses LoggableArgument
    public void testFoo() {
        handle.registerArgument(new FooArgumentFactory());

        handle.createUpdate(INSERT_POSITIONAL).bind(0, new Foo()).execute();

        assertThat(positional).isEqualTo("I'm a foo");
    }

    public static class StringXBean {
        private final String x;

        private StringXBean(String x) {
            this.x = x;
        }

        public String getX() {
            return x;
        }
    }

    private static class Foo {
        @Override
        public String toString() {
            return "I'm a foo";
        }
    }

    private static class FooArgumentFactory implements ArgumentFactory {
        @Override
        public Optional<Argument> build(Type type, Object value, ConfigRegistry config) {
            if (value instanceof Foo) {
                return Optional.of((position, statement, ctx) -> statement.setString(1, "foo"));
            } else {
                return Optional.empty();
            }
        }
    }
}
