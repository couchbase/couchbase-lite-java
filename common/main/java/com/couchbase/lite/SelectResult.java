//
// SelectResult.java
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

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * SelectResult represents a single return value of the query statement.
 */
public class SelectResult {

    //---------------------------------------------
    // Public Inner Classes
    //---------------------------------------------

    /**
     * SelectResult.From is a SelectResult that you can specify the data source alias name.
     */
    public static final class From extends SelectResult {
        private From(Expression expression) { super(expression); }

        /**
         * Species the data source alias name to the SelectResult object.
         *
         * @param alias The data source alias name.
         * @return The SelectResult object with the data source alias name specified.
         */
        @NonNull
        public SelectResult from(@NonNull String alias) {
            Preconditions.assertNotNull(alias, "alias");
            this.selectExpression = PropertyExpression.allFrom(alias);
            this.alias = alias;
            return this;
        }
    }

    /**
     * SelectResult.As is a SelectResult that you can specify an alias name to it. The
     * alias name can be used as the key for accessing the result value from the query Result
     * object.
     */
    public static final class As extends SelectResult {
        private As(Expression expression) { super(expression); }

        /**
         * Specifies the alias name to the SelectResult object.
         *
         * @param alias The alias name.
         * @return The SelectResult object with the alias name specified.
         */
        @NonNull
        public SelectResult as(@NonNull String alias) {
            Preconditions.assertNotNull(alias, "alias");
            this.alias = alias;
            return this;
        }
    }

    /**
     * Creates a SelectResult object with the given property name.
     *
     * @param property The property name.
     * @return The SelectResult.As object that you can give the alias name to the returned value.
     */
    @NonNull
    public static SelectResult.As property(@NonNull String property) {
        Preconditions.assertNotNull(property, "property");
        return new SelectResult.As(PropertyExpression.property(property));
    }

    /**
     * Creates a SelectResult object with the given expression.
     *
     * @param expression The expression.
     * @return The SelectResult.As object that you can give the alias name to the returned value.
     */
    @NonNull
    public static SelectResult.As expression(@NonNull Expression expression) {
        Preconditions.assertNotNull(expression, "expression");
        return new SelectResult.As(expression);
    }

    /**
     * Creates a SelectResult object that returns all properties data. The query returned result
     * will be grouped into a single CBLMutableDictionary object under the key of the data source name.
     *
     * @return The SelectResult.From object that you can specify the data source alias name.
     */
    @NonNull
    public static SelectResult.From all() { return new SelectResult.From(PropertyExpression.allFrom(null)); }


    //---------------------------------------------
    // member variables
    //---------------------------------------------
    Expression selectExpression;
    String alias;

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    String getColumnName() {
        if (alias != null) { return alias; }

        if (selectExpression instanceof PropertyExpression) {
            return ((PropertyExpression) selectExpression).getColumnName();
        }
        if (selectExpression instanceof MetaExpression) {
            return ((MetaExpression) selectExpression).getColumnName();
        }

        return null;
    }

    Object asJSON() { return selectExpression.asJSON(); }

    private SelectResult(Expression expression) { this.selectExpression = expression; }
}
