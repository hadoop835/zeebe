/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.logstreams.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.junit.Before;
import org.junit.Test;

public class HashIndexSnapshotSupportTest
{
    private IndexStore indexStore;
    private Long2LongHashIndex hashIndex;
    private HashIndexSnapshotSupport<Long2LongHashIndex> snapshotSupport;

    @Before
    public void init()
    {
        indexStore = FileChannelIndexStore.tempFileIndexStore();
    }

    protected void initIndex(int indexSize, int blockLength)
    {
        hashIndex = new Long2LongHashIndex(indexStore, indexSize, blockLength);
        snapshotSupport = new HashIndexSnapshotSupport<>(hashIndex, indexStore);
    }

    @Test
    public void shouldRecover() throws Exception
    {
        initIndex(16, 1);

        hashIndex.put(0, 10);
        hashIndex.put(1, 11);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        snapshotSupport.writeSnapshot(outputStream);

        hashIndex.clear();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        snapshotSupport.recoverFromSnapshot(inputStream);

        assertThat(hashIndex.get(0, -1)).isEqualTo(10);
        assertThat(hashIndex.get(1, -1)).isEqualTo(11);
    }

    @Test
    public void shouldReset() throws Exception
    {
        initIndex(16, 1);

        assertThat(hashIndex.blockCount()).isEqualTo(1);

        hashIndex.put(0, 10);
        hashIndex.put(1, 11);

        assertThat(hashIndex.blockCount()).isEqualTo(2);

        snapshotSupport.reset();

        assertThat(hashIndex.get(0, -1)).isEqualTo(-1);
        assertThat(hashIndex.get(1, -1)).isEqualTo(-1);

        // should only have the initial block
        assertThat(hashIndex.blockCount()).isEqualTo(1);
    }

    @Test
    public void shouldRecoverAnEmptyIndex() throws Exception
    {
        initIndex(16, 1);

        assertThat(hashIndex.blockCount()).isEqualTo(1);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        snapshotSupport.writeSnapshot(outputStream);

        snapshotSupport.reset();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        snapshotSupport.recoverFromSnapshot(inputStream);

        // should only have the initial block
        assertThat(hashIndex.blockCount()).isEqualTo(1);
    }

    @Test
    public void shouldRecoverWhenIndexLargerThanSnapshotBuffer() throws Exception
    {
        // given
        // note: this test uses an internal parameter for setup, which is of course not guaranteed to be
        //   used for anything.
        //   However, this is probably more focused than just testing with a "very large" index
        final int snapshotBufferSize = HashIndexSnapshotSupport.BUFFER_SIZE;
        final int numEntries = (snapshotBufferSize / 16) + 1;
        initIndex(numEntries, numEntries);

        for (int i = 0; i < numEntries; i++)
        {
            hashIndex.put(i, i);
        }

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // when
        snapshotSupport.writeSnapshot(outputStream);
        hashIndex.clear();

        // then
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        snapshotSupport.recoverFromSnapshot(inputStream);

        for (int i = 0; i < numEntries; i++)
        {
            assertThat(hashIndex.get(i, -1)).isEqualTo(i);
        }
    }
}
