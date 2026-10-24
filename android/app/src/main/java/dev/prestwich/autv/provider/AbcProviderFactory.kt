package dev.prestwich.autv.provider

import android.content.Context
import android.util.AtomicFile
import dev.prestwich.autv.data.provider.*
import java.io.File
import java.util.UUID

/** Only call after supplying an authorised ABC access implementation and returned proof-feed ID. */
internal fun createAbcProvider(context: Context, access: AbcProviderAccess, proofHouseNumber: String,
    anonymousLiveApproved: Boolean = false): AbcAdapter {
    val store=EncryptedProviderSessionStore(context,"autv.abc.session.v1","abc-session-v1")
    val file=AtomicFile(File(context.noBackupFilesDir,"abc-device-v1"))
    val id=runCatching { file.readFully().toString(Charsets.UTF_8).also { UUID.fromString(it) } }.getOrElse {
        UUID.randomUUID().toString().also { value ->
            val stream=file.startWrite()
            try { stream.write(value.toByteArray(Charsets.UTF_8));file.finishWrite(stream) }
            catch(error: Exception) { file.failWrite(stream);throw error }
        }
    }
    return AbcAdapter(access,store,store,id,proofHouseNumber,anonymousLiveApproved)
}

/** Enable only with ABC-issued AUTV configuration and the discovered Victorian proof identity. */
internal fun createRegisteredAbcProvider(context: Context, registration: AbcClientRegistration,
    anonymousLiveApproved: Boolean = false): AbcAdapter =
    createAbcProvider(context,AbcHttpAccess(registration),registration.mainHouseNumber,anonymousLiveApproved)
