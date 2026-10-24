package dev.prestwich.autv.provider

import android.content.Context
import android.util.AtomicFile
import dev.prestwich.autv.data.provider.*
import java.io.File
import java.util.UUID

/** Access/client registration, proof channel key and optional DAI network code must be provider-approved. */
internal fun createTenProvider(context: Context, access: TenProviderAccess, proofChannelKey: String,
    daiNetworkCode: String? = null): TenAdapter {
    val store=EncryptedProviderSessionStore(context,"autv.ten.session.v1","ten-session-v1")
    val file=AtomicFile(File(context.noBackupFilesDir,"ten-device-v1"))
    val id=runCatching { file.readFully().toString(Charsets.UTF_8).also { UUID.fromString(it) } }.getOrElse {
        UUID.randomUUID().toString().also { value ->
            val stream=file.startWrite()
            try { stream.write(value.toByteArray(Charsets.UTF_8));file.finishWrite(stream) }
            catch(error: Exception) { file.failWrite(stream);throw error }
        }
    }
    return TenAdapter(access,store,store,id,proofChannelKey,daiNetworkCode)
}

/** Uses Ten's registered inputs; no public Google sample identity is supplied to a broadcaster. */
internal fun createRegisteredTenProvider(context: Context, registration: TenClientRegistration,
    returnedProofChannelKey: String, issuedDaiNetworkCode: String? = null): TenAdapter =
    createTenProvider(context,TenHttpAccess(registration),returnedProofChannelKey,issuedDaiNetworkCode)
