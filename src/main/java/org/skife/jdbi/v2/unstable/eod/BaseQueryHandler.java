/*
 * Copyright 2004 - 2011 Brian McCallister
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

package org.skife.jdbi.v2.unstable.eod;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.members.ResolvedMethod;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.exceptions.DBIException;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

abstract class BaseQueryHandler extends CustomizingStatementHandler
{
    private final List<Bindifier> binders = new ArrayList<Bindifier>();

    private final String         sql;
    private final ResolvedMethod method;
    private final MapFunc        mapFunc;

    public BaseQueryHandler(ResolvedMethod method)
    {
        super(method);
        this.method = method;
        this.sql = method.getRawMember().getAnnotation(SqlQuery.class).value();

        Annotation[][] param_annotations = method.getRawMember().getParameterAnnotations();
        for (int param_idx = 0; param_idx < param_annotations.length; param_idx++) {
            Annotation[] annotations = param_annotations[param_idx];
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> anno_class = annotation.annotationType();
                if (Bind.class.isAssignableFrom(anno_class)) {
                    Bind bind = (Bind) annotation;
                    try {
                        binders.add(new Bindifier(bind, param_idx, bind.binder().newInstance()));
                    }
                    catch (Exception e) {
                        throw new IllegalStateException("unable to instantiate specified binder", e);
                    }
                }
            }
        }

        if (method.getRawMember().isAnnotationPresent(Mapper.class)) {
            try {
                final ResultSetMapper mapper = method.getRawMember().getAnnotation(Mapper.class).value().newInstance();
                this.mapFunc = new MapFunc()
                {
                    public Query map(Query q)
                    {
                        return q.map(mapper);
                    }
                };
            }
            catch (Exception e) {
                throw new DBIException("Unable to instantiate declared mapper", e)
                {
                };
            }
        }
        else {
            this.mapFunc = new MapFunc()
            {
                public Query map(Query q)
                {
                    return q.mapTo(mapTo().getErasedType());
                }
            };
        }
    }

    public Object invoke(HandleDing h, Object target, Object[] args)
    {
        Query q = h.getHandle().createQuery(sql);
        for (Bindifier binder : binders) {
            binder.bind(q, args);
        }
        applyCustomizers(q,args);
        return result(mapFunc.map(q), h);

    }

    protected abstract Object result(Query q, HandleDing baton);

    protected abstract ResolvedType mapTo();

    protected ResolvedMethod getMethod()
    {
        return method;
    }

    private static interface MapFunc
    {
        public Query map(Query q);
    }
}
