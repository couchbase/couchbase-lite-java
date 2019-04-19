//
// Join.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;


/**
 * A Join component representing a single JOIN clause in the query statement.
 */
public class Join {
    enum Type {
        INNER("INNER"), OUTER("OUTER"), LEFT_OUTER("LEFT OUTER"), CROSS("CROSS");

        private final String tag;

        Type(@NonNull String tag) { this.tag = tag; }

        public String getTag() { return tag; }
    }

    /**
     * On component used for specifying join conditions.
     */
    public static final class On extends Join {
        //---------------------------------------------
        // Member variables
        //---------------------------------------------
        private Expression onExpression;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        private On(Type type, DataSource datasource) {
            super(type, datasource);
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * Specify join conditions from the given expression.
         *
         * @param expression The Expression object specifying the join conditions.
         * @return The Join object that represents a single JOIN clause of the query.
         */
        @NonNull
        public Join on(@NonNull Expression expression) {
            if (expression == null) {
                throw new IllegalArgumentException("expression cannot be null.");
            }
            this.onExpression = expression;
            return this;
        }

        //---------------------------------------------
        // Package level access
        //---------------------------------------------

        @Override
        Object asJSON() {
            final Map<String, Object> json = new HashMap<>();
            json.put("JOIN", super.type.getTag());
            json.put("ON", onExpression.asJSON());
            json.putAll(super.dataSource.asJSON());
            return json;
        }
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    /**
     * Create a JOIN (same as INNER JOIN) component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    @NonNull
    public static On join(@NonNull DataSource datasource) {
        return innerJoin(datasource);
    }

    /**
     * Create an INNER JOIN component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    @NonNull
    public static On innerJoin(@NonNull DataSource datasource) {
        if (datasource == null) {
            throw new IllegalArgumentException("datasource cannot be null.");
        }
        return new On(Type.INNER, datasource);
    }

    /**
     * Create a LEFT JOIN (same as LEFT OUTER JOIN) component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    @NonNull
    public static On leftJoin(@NonNull DataSource datasource) {
        return leftOuterJoin(datasource);
    }

    /**
     * Create a LEFT OUTER JOIN component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    @NonNull
    public static On leftOuterJoin(@NonNull DataSource datasource) {
        if (datasource == null) {
            throw new IllegalArgumentException("datasource cannot be null.");
        }
        return new On(Type.LEFT_OUTER, datasource);
    }

    /**
     * Create an CROSS JOIN component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The Join object used for specifying join conditions.
     */
    @NonNull
    public static Join crossJoin(@NonNull DataSource datasource) {
        if (datasource == null) {
            throw new IllegalArgumentException("datasource cannot be null.");
        }
        return new Join(Type.CROSS, datasource);
    }


    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Type type;
    private final DataSource dataSource;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private Join(Type type, DataSource dataSource) {
        this.type = type;
        this.dataSource = dataSource;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    Object asJSON() {
        final Map<String, Object> json = new HashMap<>();
        json.put("JOIN", type.getTag());
        json.putAll(dataSource.asJSON());
        return json;
    }
}
