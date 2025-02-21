/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.flink.source;

import org.apache.paimon.table.source.TableRead;

import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.flink.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import org.apache.flink.connector.base.source.reader.synchronization.FutureCompletingBlockingQueue;
import org.apache.flink.connector.file.src.reader.BulkFormat.RecordIterator;
import org.apache.flink.table.data.RowData;

import javax.annotation.Nullable;

import java.util.Map;

/** A {@link SourceReader} that read records from {@link FileStoreSourceSplit}. */
public class FileStoreSourceReader
        extends SingleThreadMultiplexSourceReaderBase<
                RecordIterator<RowData>, RowData, FileStoreSourceSplit, FileStoreSourceSplitState> {

    public FileStoreSourceReader(
            SourceReaderContext readerContext, TableRead tableRead, @Nullable Long limit) {
        // limiter is created in SourceReader, it can be shared in all split readers
        super(
                () -> new FileStoreSourceSplitReader(tableRead, RecordLimiter.create(limit)),
                FlinkRecordsWithSplitIds::emitRecord,
                readerContext.getConfiguration(),
                readerContext);
    }

    public FileStoreSourceReader(
            SourceReaderContext readerContext,
            TableRead tableRead,
            @Nullable Long limit,
            FutureCompletingBlockingQueue<RecordsWithSplitIds<RecordIterator<RowData>>>
                    elementsQueue) {
        super(
                elementsQueue,
                () -> new FileStoreSourceSplitReader(tableRead, RecordLimiter.create(limit)),
                FlinkRecordsWithSplitIds::emitRecord,
                readerContext.getConfiguration(),
                readerContext);
    }

    @Override
    public void start() {
        // we request a split only if we did not get splits during the checkpoint restore
        if (getNumberOfCurrentlyAssignedSplits() == 0) {
            context.sendSplitRequest();
        }
    }

    @Override
    protected void onSplitFinished(Map<String, FileStoreSourceSplitState> finishedSplitIds) {
        // this method is called each time when we consume one split
        // it is possible that one response from the coordinator contains multiple splits
        // we should only require for more splits after we've consumed all given splits
        if (getNumberOfCurrentlyAssignedSplits() == 0) {
            context.sendSplitRequest();
        }
    }

    @Override
    protected FileStoreSourceSplitState initializedState(FileStoreSourceSplit split) {
        return new FileStoreSourceSplitState(split);
    }

    @Override
    protected FileStoreSourceSplit toSplitType(
            String splitId, FileStoreSourceSplitState splitState) {
        return splitState.toSourceSplit();
    }
}
