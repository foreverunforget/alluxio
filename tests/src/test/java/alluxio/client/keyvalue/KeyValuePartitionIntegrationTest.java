/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package alluxio.client.keyvalue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.Lists;

import alluxio.Constants;
import alluxio.LocalAlluxioClusterResource;
import alluxio.AlluxioURI;
import alluxio.client.file.FileSystem;
import alluxio.exception.AlluxioException;
import alluxio.util.io.PathUtils;

/**
 * Integration tests for {@link KeyValuePartitionReader} and {@link KeyValuePartitionWriter}.
 */
public final class KeyValuePartitionIntegrationTest {
  private static final int BLOCK_SIZE = 512 * Constants.MB;
  private static final String BASE_KEY = "base_key";
  private static final String BASE_VALUE = "base_value";
  /** The number of pairs generated by {@link #genKeyValuePairs(int)} can be held by a partition. */
  private static final int BASE_KEY_VALUE_NUMBER = 100;
  private static final byte[] KEY1 = "key1".getBytes();
  private static final byte[] KEY2 = "key2_foo".getBytes();
  private static final byte[] VALUE1 = "value1".getBytes();
  private static final byte[] VALUE2 = "value2_bar".getBytes();
  private static FileSystem sFileSystem;
  private KeyValuePartitionWriter mKeyValuePartitionWriter;
  private KeyValuePartitionReader mKeyValuePartitionReader;
  private AlluxioURI mPartitionUri;

  /**
   * Generate a sequence of key-value pairs in the format like
   * ({@link #BASE_KEY}_{@code i}, {@link #BASE_VALUE}_{@code i}), {@code i} is in the interval
   * [0, {@code length}).
   *
   * @param length the number of key-value pairs
   * @return the list of generated key-value pairs
   */
  private List<KeyValuePair> genKeyValuePairs(int length) {
    List<KeyValuePair> pairs = Lists.newArrayListWithExpectedSize(length);
    for (int i = 0; i < length; i ++) {
      String key = String.format("%s_%d", BASE_KEY, i);
      String value = String.format("%s_%d", BASE_VALUE, i);
      pairs.add(new KeyValuePair(key.getBytes(), value.getBytes()));
    }
    return pairs;
  }

  @ClassRule
  public static LocalAlluxioClusterResource sLocalAlluxioClusterResource =
      new LocalAlluxioClusterResource(Constants.GB, BLOCK_SIZE,
          /* ensure key-value service is turned on */
          Constants.KEY_VALUE_ENABLED, "true");

  @BeforeClass
  public static void beforeClass() throws Exception {
    sFileSystem = sLocalAlluxioClusterResource.get().getClient();
  }

  private AlluxioURI getUniqURI() {
    return new AlluxioURI(PathUtils.uniqPath());
  }

  @Before
  public void before() {
    mPartitionUri = getUniqURI();
  }

  /**
   * Tests a {@link KeyValuePartitionWriter} can create a partition, write key-value pairs and
   * close. Meanwhile the {@link KeyValuePartitionReader} can open this saved partition and find
   * keys store by the writer.
   *
   * @throws IOException if unexpected non-Tachyon error happens
   * @throws AlluxioException if unexpected Tachyon error happens
   */
  @Test
  public void readerWriterTest() throws IOException, AlluxioException {
    mKeyValuePartitionWriter = KeyValuePartitionWriter.Factory.create(mPartitionUri);
    mKeyValuePartitionWriter.put(KEY1, VALUE1);
    mKeyValuePartitionWriter.put(KEY2, VALUE2);
    mKeyValuePartitionWriter.close();
    // Expect the key-value partition exists as a Tachyon file
    Assert.assertTrue(sFileSystem.exists(mPartitionUri));
    mKeyValuePartitionReader = KeyValuePartitionReader.Factory.create(mPartitionUri);
    Assert.assertArrayEquals(VALUE1, mKeyValuePartitionReader.get(KEY1));
    Assert.assertArrayEquals(VALUE2, mKeyValuePartitionReader.get(KEY2));
    Assert.assertNull(mKeyValuePartitionReader.get("NoSuchKey".getBytes()));
  }

  /**
   * Tests that {@link KeyValuePartitionReader#size()} is correct when a new reader is created.
   */
  @Test
  public void sizeTest() throws Exception {
    byte[][] keys = new byte[][]{KEY1, KEY2};
    byte[][] values = new byte[][]{VALUE1, VALUE2};
    for (int size = 0; size <= 2; size ++) {
      mKeyValuePartitionWriter = KeyValuePartitionWriter.Factory.create(mPartitionUri);
      for (int i = 0; i < size; i ++) {
        mKeyValuePartitionWriter.put(keys[i], values[i]);
      }
      mKeyValuePartitionWriter.close();

      mKeyValuePartitionReader = KeyValuePartitionReader.Factory.create(mPartitionUri);
      Assert.assertEquals(size, mKeyValuePartitionReader.size());
      mKeyValuePartitionReader.close();

      mPartitionUri = getUniqURI();
    }
  }

  /**
   * Tests that the iterator returned by {@link KeyValuePartitionReader#iterator()} for an empty
   * partition has no elements to be iterated.
   */
  @Test
  public void emptyPartitionIteratorTest() throws Exception {
    // Creates an empty partition.
    KeyValuePartitionWriter.Factory.create(mPartitionUri).close();
    Assert.assertFalse(KeyValuePartitionReader.Factory.create(mPartitionUri).iterator().hasNext());
  }

  /**
   * Tests that {@link KeyValuePartitionReader#iterator()} can iterate over a partition correctly.
   * <p>
   * There is no assumption about the order of iteration, it just makes sure all key-value pairs are
   * iterated.
   */
  @Test
  public void noOrderIteratorTest() throws Exception {
    List<KeyValuePair> pairs = genKeyValuePairs(BASE_KEY_VALUE_NUMBER);
    List<KeyValuePair> iteratedPairs = Lists.newArrayListWithExpectedSize(pairs.size());

    mKeyValuePartitionWriter = KeyValuePartitionWriter.Factory.create(mPartitionUri);
    for (KeyValuePair pair : pairs) {
      mKeyValuePartitionWriter.put(pair.getKey().array(), pair.getValue().array());
    }
    mKeyValuePartitionWriter.close();

    mKeyValuePartitionReader = KeyValuePartitionReader.Factory.create(mPartitionUri);
    KeyValueIterator iterator = mKeyValuePartitionReader.iterator();
    while (iterator.hasNext()) {
      iteratedPairs.add(iterator.next());
    }
    Assert.assertEquals(pairs.size(), iteratedPairs.size());

    // Sort both pairs and iteratedPairs, then compare them.
    Collections.sort(pairs);
    Collections.sort(iteratedPairs);
    Assert.assertEquals(pairs, iteratedPairs);
  }
}
