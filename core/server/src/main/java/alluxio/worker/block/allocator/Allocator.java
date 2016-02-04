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

package alluxio.worker.block.allocator;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import alluxio.Constants;
import alluxio.annotation.PublicApi;
import alluxio.Configuration;
import alluxio.util.CommonUtils;
import alluxio.worker.block.BlockMetadataManagerView;
import alluxio.worker.block.BlockStoreLocation;
import alluxio.worker.block.meta.StorageDirView;

/**
 * Interface for the allocation policy of Tachyon managed data.
 */
@PublicApi
public interface Allocator {

  /**
   * Factory for {@link Allocator}.
   */
  class Factory {
    /**
     * Factory for {@link Allocator}.
     *
     * @param conf Tachyon configuration used to define the {@link Allocator} type
     * @param view {@link BlockMetadataManagerView} to pass to {@link Allocator}
     * @return the generated {@link Allocator}, it will be a {@link MaxFreeAllocator} by default
     */
    public static Allocator create(Configuration conf, BlockMetadataManagerView view) {
      BlockMetadataManagerView managerView = Preconditions.checkNotNull(view);
      try {
        return CommonUtils.createNewClassInstance(
            conf.<Allocator>getClass(Constants.WORKER_ALLOCATOR_CLASS),
            new Class[] {BlockMetadataManagerView.class}, new Object[] {managerView});
      } catch (Exception e) {
        throw Throwables.propagate(e);
      }
    }
  }

  /**
   * Allocates a block from the given block store location under a given view. The location can be a
   * specific location, or {@link BlockStoreLocation#anyTier()} or
   * {@link BlockStoreLocation#anyDirInTier(String)}. The view is generated by a
   * {@link alluxio.worker.block.BlockStore}.
   *
   * @param sessionId the id of session to apply for the block allocation
   * @param blockSize the size of block in bytes
   * @param location the location in block store
   * @param view of the metadata manager
   * @return a {@link StorageDirView} in which to create the temp block meta if success, null
   *         otherwise
   */
  StorageDirView allocateBlockWithView(long sessionId, long blockSize, BlockStoreLocation location,
      BlockMetadataManagerView view);
}
