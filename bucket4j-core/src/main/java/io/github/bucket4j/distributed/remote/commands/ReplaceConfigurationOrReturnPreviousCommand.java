/*-
 * ========================LICENSE_START=================================
 * Bucket4j
 * %%
 * Copyright (C) 2015 - 2020 Vladimir Bukhtoyarov
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * =========================LICENSE_END==================================
 */

package io.github.bucket4j.distributed.remote.commands;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.remote.CommandResult;
import io.github.bucket4j.distributed.remote.MutableBucketEntry;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import io.github.bucket4j.distributed.remote.RemoteCommand;
import io.github.bucket4j.distributed.serialization.DeserializationAdapter;
import io.github.bucket4j.distributed.serialization.SerializationAdapter;
import io.github.bucket4j.distributed.serialization.SerializationHandle;
import io.github.bucket4j.distributed.versioning.Version;
import io.github.bucket4j.distributed.versioning.Versions;
import io.github.bucket4j.util.ComparableByContent;

import java.io.IOException;

import static io.github.bucket4j.distributed.versioning.Versions.v_5_0_0;


public class ReplaceConfigurationOrReturnPreviousCommand implements RemoteCommand<BucketConfiguration>, ComparableByContent<ReplaceConfigurationOrReturnPreviousCommand> {

    private BucketConfiguration newConfiguration;

    public static final SerializationHandle<ReplaceConfigurationOrReturnPreviousCommand> SERIALIZATION_HANDLE = new SerializationHandle<ReplaceConfigurationOrReturnPreviousCommand>() {
        @Override
        public <S> ReplaceConfigurationOrReturnPreviousCommand deserialize(DeserializationAdapter<S> adapter, S input, Version backwardCompatibilityVersion) throws IOException {
            int formatNumber = adapter.readInt(input);
            Versions.check(formatNumber, v_5_0_0, v_5_0_0);

            BucketConfiguration newConfiguration = BucketConfiguration.SERIALIZATION_HANDLE.deserialize(adapter, input, backwardCompatibilityVersion);
            return new ReplaceConfigurationOrReturnPreviousCommand(newConfiguration);
        }

        @Override
        public <O> void serialize(SerializationAdapter<O> adapter, O output, ReplaceConfigurationOrReturnPreviousCommand command, Version backwardCompatibilityVersion) throws IOException {
            adapter.writeInt(output, v_5_0_0.getNumber());

            BucketConfiguration.SERIALIZATION_HANDLE.serialize(adapter, output, command.newConfiguration, backwardCompatibilityVersion);
        }

        @Override
        public int getTypeId() {
            return 32;
        }

        @Override
        public Class<ReplaceConfigurationOrReturnPreviousCommand> getSerializedType() {
            return ReplaceConfigurationOrReturnPreviousCommand.class;
        }

    };

    public ReplaceConfigurationOrReturnPreviousCommand(BucketConfiguration newConfiguration) {
        this.newConfiguration = newConfiguration;
    }

    @Override
    public CommandResult<BucketConfiguration> execute(MutableBucketEntry mutableEntry, long currentTimeNanos) {
        if (!mutableEntry.exists()) {
            return CommandResult.bucketNotFound();
        }

        RemoteBucketState state = mutableEntry.get();
        state.refillAllBandwidth(currentTimeNanos);
        BucketConfiguration previousConfiguration = state.replaceConfigurationOrReturnPrevious(newConfiguration);
        if (previousConfiguration != null) {
            return CommandResult.success(previousConfiguration, BucketConfiguration.SERIALIZATION_HANDLE);
        }
        mutableEntry.set(state);
        return CommandResult.empty();
    }

    public BucketConfiguration getNewConfiguration() {
        return newConfiguration;
    }

    @Override
    public SerializationHandle getSerializationHandle() {
        return SERIALIZATION_HANDLE;
    }

    @Override
    public boolean equalsByContent(ReplaceConfigurationOrReturnPreviousCommand other) {
        return ComparableByContent.equals(newConfiguration, other.newConfiguration);
    }

    @Override
    public boolean isImmediateSyncRequired(long unsynchronizedTokens, long nanosSinceLastSync) {
        return true;
    }

    @Override
    public long estimateTokensToConsume() {
        return 0;
    }

    @Override
    public long getConsumedTokens(BucketConfiguration result) {
        return 0;
    }

}