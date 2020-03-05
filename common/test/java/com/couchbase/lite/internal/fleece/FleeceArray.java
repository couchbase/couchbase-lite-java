//
// FleeceArray.java
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
package com.couchbase.lite.internal.fleece;

import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;


// This class and its constructor are referenced by name, from native code.
public class FleeceArray implements List<Object>, Encodable {
    //---------------------------------------------
    // Internal classes
    //---------------------------------------------

    private class Itr implements Iterator<Object> {
        protected int cursor = 0;       // index of next element to return

        protected Itr() {}

        @Override
        public boolean hasNext() { return cursor != size(); }

        @Override
        public Object next() {
            int i = cursor;
            if (i >= size()) { throw new NoSuchElementException(); }
            cursor = i + 1;
            return get(i);
        }

        @Override
        public void remove() { throw new UnsupportedOperationException(); }
    }

    private class ListItr extends Itr implements ListIterator<Object> {
        ListItr(int index) {
            super();
            cursor = index;
        }

        @Override
        public boolean hasPrevious() { return cursor != 0; }

        @Override
        public int nextIndex() { return cursor; }

        @Override
        public int previousIndex() { return cursor - 1; }

        @Override
        public Object previous() {
            int i = cursor - 1;
            if (i < 0) { throw new NoSuchElementException(); }
            cursor = i;
            return get(i);
        }

        @Override
        public void set(Object e) { throw new UnsupportedOperationException(); }

        @Override
        public void add(Object e) { throw new UnsupportedOperationException(); }
    }

    //---------------------------------------------
    // Data members
    //---------------------------------------------

    private MArray mArray;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    private FleeceArray() { mArray = new MArray(); }

    // Call from native method
    FleeceArray(MValue mv, MCollection parent) {
        this();
        mArray.initInSlot(mv, parent);
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------

    @Override
    public int size() { return (int) mArray.count(); }

    @Override
    public boolean isEmpty() { return size() == 0; }

    @Override
    @NonNull
    public Iterator<Object> iterator() { return new Itr(); }

    @Override
    public boolean add(Object o) {
        mArray.append(o);
        return true;
    }

    @Override
    public void clear() { mArray.clear(); }

    @Override
    public Object get(int i) {
        MValue val = mArray.get(i);
        if (val.isEmpty()) { throw new IndexOutOfBoundsException(); }
        return val.asNative(mArray);
    }

    @Override
    public Object set(int i, Object o) {
        Object prev = get(i);
        mArray.set(i, o);
        return prev;
    }

    @Override
    public void add(int i, Object o) { mArray.insert(i, o); }

    @Override
    public Object remove(int i) {
        Object prev = get(i);
        mArray.remove(i);
        return prev;
    }

    @Override
    @NonNull
    public ListIterator<Object> listIterator() { return new ListItr(0); }

    @Override
    @NonNull
    public ListIterator<Object> listIterator(int index) {
        if (index < 0 || index > size()) { throw new IndexOutOfBoundsException("Index: " + index); }
        return new ListItr(index);
    }

    @Override
    public boolean contains(Object o) { throw new UnsupportedOperationException(); }

    @Override
    @NonNull
    public Object[] toArray() { throw new UnsupportedOperationException(); }

    @Override
    @NonNull
    public <T> T[] toArray(@NonNull T[] ts) { throw new UnsupportedOperationException(); }

    @Override
    public int indexOf(Object o) { throw new UnsupportedOperationException(); }

    @Override
    public int lastIndexOf(Object o) { throw new UnsupportedOperationException(); }

    @Override
    @NonNull
    public List<Object> subList(int i, int i1) { throw new UnsupportedOperationException(); }

    @Override
    public boolean remove(Object o) { throw new UnsupportedOperationException(); }

    @Override
    public boolean containsAll(    @NonNull Collection<?> collection) { throw new UnsupportedOperationException(); }

    @Override
    public boolean addAll(@NonNull Collection<?> collection) { throw new UnsupportedOperationException(); }

    @Override
    public boolean addAll(int i, @NonNull Collection<?> collection) { throw new UnsupportedOperationException(); }

    @Override
    public boolean removeAll(@NonNull Collection<?> collection) { throw new UnsupportedOperationException(); }

    @Override
    public boolean retainAll(@NonNull Collection<?> collection) { throw new UnsupportedOperationException(); }

    // Implementation of FLEncodable
    @Override
    public void encodeTo(FLEncoder enc) { mArray.encodeTo(enc); }

    public boolean isMutated() { return mArray.isMutated(); }

    //---------------------------------------------
    // Package and private methods
    //---------------------------------------------

    // For MValue
    MCollection toMCollection() { return mArray; }

    private void requireMutable() {
        if (!mArray.isMutable()) { throw new IllegalStateException(); }
    }
}
