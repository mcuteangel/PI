package dev.sanmer.pi.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.sanmer.pi.datastore.model.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

class PreferenceSerializer @Inject constructor() : Serializer<Preference> {
    override val defaultValue = Preference()

    override suspend fun readFrom(input: InputStream) =
        try {
            ProtoBuf.decodeFromByteArray<Preference>(input.readBytes())
        } catch (e: SerializationException) {
            throw CorruptionException("Failed to read proto", e)
        }

    override suspend fun writeTo(t: Preference, output: OutputStream) =
        withContext(Dispatchers.IO) {
            output.write(ProtoBuf.encodeToByteArray(t))
        }

    @Module
    @InstallIn(SingletonComponent::class)
    object Impl {
        @Provides
        @Singleton
        fun dataStore(
            @ApplicationContext context: Context,
            serializer: PreferenceSerializer
        ): DataStore<Preference> =
            DataStoreFactory.create(
                serializer = serializer
            ) {
                context.dataStoreFile("user_preferences.pb")
            }
    }
}