package com.github.adamantcheese.chan.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import kotlin.random.Random;
import okio.ByteString;

public class JavaUtils {
    public static boolean in(int value, int[] array) {
        return Arrays.binarySearch(array, value) >= 0;
    }

    public static boolean arrayPrefixedWith(byte[] array, byte[] prefix) {
        if (prefix.length > array.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (prefix[i] != array[i]) {
                return false;
            }
        }
        return true;
    }

    public static String stringMD5hash(String inputString) {
        try {
            return ByteString.encodeUtf8(inputString).md5().hex();
        } catch (Throwable t) {
            return ByteString.encodeUtf8(String.valueOf(Random.Default.nextLong())).md5().hex();
        }
    }

    public static class NoDeleteArrayList<E>
            extends ArrayList<E> {
        public NoDeleteArrayList(List<E> list) {
            super(list);
        }

        public NoDeleteArrayList() {}

        @Override
        protected void removeRange(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public boolean remove(@Nullable Object o) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public boolean removeAll(@NonNull Collection<?> c) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public boolean removeIf(@NonNull Predicate<? super E> filter) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public E remove(int index) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public boolean retainAll(@NonNull Collection<?> c) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public E set(int index, E element) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Prevented in this class!");
        }

        @Override
        public void replaceAll(@NonNull UnaryOperator<E> operator) {
            throw new UnsupportedOperationException("Prevented in this class!");
        }
    }
}
